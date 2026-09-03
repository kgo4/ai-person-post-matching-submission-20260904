package com.example.matching.service.interview.plan;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.example.matching.agent.dto.interview.InterviewPlanDTO;
import com.example.matching.agent.service.impl.AgentOutputValidator;
import com.example.matching.common.exception.BusinessException;
import com.example.matching.entity.employee.EmpVideoInterviewQuestion;
import com.example.matching.entity.employee.EmpVideoInterviewSession;
import com.example.matching.entity.post.PostAbilityModel;
import com.example.matching.entity.system.AbilityTag;
import com.example.matching.mapper.employee.EmpVideoInterviewQuestionMapper;
import com.example.matching.mapper.employee.EmpVideoInterviewSessionMapper;
import com.example.matching.mapper.post.PostAbilityModelMapper;
import com.example.matching.mapper.system.AbilityTagMapper;
import com.example.matching.service.interview.AIInterviewAgent.InterviewEligibilityCheck;
import com.example.matching.service.interview.AIInterviewAgent.InterviewPlan;
import com.example.matching.service.interview.AIInterviewAgent.InterviewPlanRequest;
import com.example.matching.service.interview.AIInterviewAgent.InterviewQuestion;
import com.example.matching.service.interview.eligibility.InterviewEligibilityChecker;
import com.example.matching.service.assessment.impl.AssessmentTestResultProvider;
import com.example.matching.service.assessment.AssessmentScopeService;
import com.example.matching.dto.assessment.AssessmentScopeDTO;
import com.example.matching.ai.service.LlmInputGuard;
import com.example.matching.agent.lc4j.InterviewPlanAiService;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class InterviewPlanComposer {

    private final InterviewEligibilityChecker eligibilityChecker;
    private final EmpVideoInterviewSessionMapper sessionMapper;
    private final EmpVideoInterviewQuestionMapper questionMapper;
    private final PostAbilityModelMapper postAbilityModelMapper;
    private final AbilityTagMapper abilityTagMapper;
    private final ObjectMapper objectMapper;
    private final ObjectProvider<InterviewPlanAiService> interviewPlanAiServiceProvider;
    private final AgentOutputValidator agentOutputValidator;
    private final com.example.matching.infrastructure.llm.LlmResponseParser llmResponseParser;
    private final PlatformTransactionManager transactionManager;
    private final com.example.matching.agent.service.AgentGraphContextAssembler agentGraphContextAssembler;
    private final AssessmentTestResultProvider assessmentTestResultProvider;
    private final AssessmentScopeService assessmentScopeService;
    private final LlmInputGuard llmInputGuard;
    private com.example.matching.service.assessment.CapabilityAssessmentOrchestrator assessmentOrchestrator;

    private static final String MODE_POST_BASED = "POST_BASED";

    @Autowired(required = false)
    void setAssessmentOrchestrator(com.example.matching.service.assessment.CapabilityAssessmentOrchestrator orchestrator) {
        this.assessmentOrchestrator = orchestrator;
    }

    /** 注入面试计划上下文的最大岗位能力模型数（避免全量塞入） */
    private static final int MAX_CONTEXT_ABILITY_MODELS = 8;
    /** 面试主问题由系统配置控制；追问是运行时行为，不计入该数量。 */
    private static final int DEFAULT_QUESTION_COUNT = 6;
    private static final int MIN_QUESTION_COUNT = 3;
    private static final int MAX_QUESTION_COUNT = 10;

    private TransactionTemplate shortTransaction() {
        TransactionTemplate template = new TransactionTemplate(transactionManager);
        template.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRED);
        return template;
    }

    private static class PlanPreparation {
        final EmpVideoInterviewSession session;
        final InterviewEligibilityCheck eligibility;
        final String resumeText;
        final String resumeStructuredData;
        final String resumeAbilityClaims;
        final List<PostAbilityModel> abilityModels;
        final List<Map<String, Object>> resumeClaims;

        PlanPreparation(EmpVideoInterviewSession session, InterviewEligibilityCheck eligibility,
                        String resumeText, String resumeStructuredData, String resumeAbilityClaims,
                        List<PostAbilityModel> abilityModels, List<Map<String, Object>> resumeClaims) {
            this.session = session;
            this.eligibility = eligibility;
            this.resumeText = resumeText;
            this.resumeStructuredData = resumeStructuredData;
            this.resumeAbilityClaims = resumeAbilityClaims;
            this.abilityModels = abilityModels;
            this.resumeClaims = resumeClaims;
        }
    }

    public InterviewPlan generateInterviewPlan(InterviewPlanRequest request) {
        log.info("生成面试计划，empId={}, postId={}", request.empId(), request.postId());

        InterviewEligibilityCheck eligibility = eligibilityChecker.checkInterviewEligibility(request.empId());
        if (!eligibility.eligible()) {
            throw new BusinessException(400, eligibility.reason());
        }

        String resumeText = firstNonBlank(request.resumeText(), eligibility.resumeText());
        String resumeAbilityClaims = firstNonBlank(request.resumeAbilityClaims(), eligibility.resumeAbilityClaims());

        PlanPreparation prep = preparePlan(request, eligibility, resumeText, resumeAbilityClaims);

        int maxQuestions = normalizeQuestionCount(request.questionCount());
        // A configured interview length is a hard limit. Keep the full claim scope and prioritize
        // it for the Agent rather than silently extending the interview to cover every tag one by one.
        List<Map<String, Object>> roundClaims = selectClaimsForRound(prep.resumeClaims, prep.abilityModels);

        InterviewPlanAiService planAiService = interviewPlanAiServiceProvider.getIfAvailable();
        String fallbackReason = "AI_SERVICE_UNAVAILABLE";
        if (planAiService != null) {
            try {
                java.util.Set<Long> allowedTagIds = resumeClaimTagIds(roundClaims);
                String context = buildInterviewPlanContext(request, eligibility, prep.abilityModels,
                        roundClaims, prep.resumeText, prep.session.getId(), allowedTagIds, prep.session.getWorkflowId(),
                        maxQuestions);
                InterviewPlanDTO planDto = com.example.matching.agent.config.AgentToolProvider
                        .withScope(() -> planAiService.generatePlan(prep.session.getId(), context));
                agentOutputValidator.validateOrThrow(planDto, "INTERVIEW_PLAN");
                InterviewPlan aiPlan = convertPlanFromDto(planDto, prep.session.getId(), maxQuestions, allowedTagIds,
                        prep.resumeText);
                if (aiPlan != null && aiPlan.questions() != null && !aiPlan.questions().isEmpty()) {
                    return persistPlan(prep.session.getId(), aiPlan);
                }
                fallbackReason = "AI_PLAN_EMPTY_OR_REJECTED";
            } catch (Exception e) {
                fallbackReason = "AI_PLAN_EXCEPTION_" + e.getClass().getSimpleName();
                log.warn("[INTERVIEW_PLAN_FALLBACK] sessionId={}, reason={}, message={}",
                        prep.session.getId(), fallbackReason, e.getMessage());
            }
        }

        log.warn("[INTERVIEW_PLAN_FALLBACK] sessionId={}, reason={}, configuredQuestions={}, candidateClaims={}",
                prep.session.getId(), fallbackReason, maxQuestions, roundClaims.size());
        InterviewPlan rulePlan = generateRuleBasedQuestions(prep, roundClaims, maxQuestions);
        return persistPlan(prep.session.getId(), rulePlan);
    }

    private int normalizeQuestionCount(Integer requestedQuestionCount) {
        if (requestedQuestionCount == null || requestedQuestionCount <= 0) {
            return DEFAULT_QUESTION_COUNT;
        }
        return Math.max(MIN_QUESTION_COUNT, Math.min(MAX_QUESTION_COUNT, requestedQuestionCount));
    }

    private PlanPreparation preparePlan(InterviewPlanRequest request, InterviewEligibilityCheck eligibility,
                                        String resumeText, String resumeAbilityClaims) {
        return shortTransaction().execute(status -> {
            EmpVideoInterviewSession session;
            if (request.sessionId() != null) {
                session = sessionMapper.selectById(request.sessionId());
                if (session == null) {
                    throw new BusinessException(404, "Interview session not found");
                }
                if (session.getStatus() != null && session.getStatus() >= 1) {
                    List<EmpVideoInterviewQuestion> existingQuestions = questionMapper.selectList(
                            Wrappers.<EmpVideoInterviewQuestion>lambdaQuery()
                                    .eq(EmpVideoInterviewQuestion::getSessionId, session.getId())
                                    .orderByAsc(EmpVideoInterviewQuestion::getQuestionOrder)
                    );
                    if (!existingQuestions.isEmpty()) {
                        List<InterviewQuestion> questions = existingQuestions.stream()
                                .map(q -> new InterviewQuestion(
                                        q.getQuestionOrder(),
                                        q.getQuestionText(),
                                        q.getQuestionType(),
                                        q.getDifficulty(),
                                        parseExpectedTagIds(q.getExpectedTagsJson()),
                                        null
                                ))
                                .toList();
                        InterviewPlan existingPlan = new InterviewPlan(
                                session.getId(), questions,
                                "使用已生成的面试问题", questions.size() * 3);
                        throw new PlanAlreadyExistsException(existingPlan);
                    }
                }
            } else {
                EmpVideoInterviewSession existingSession = sessionMapper.selectOne(
                        Wrappers.<EmpVideoInterviewSession>lambdaQuery()
                                .eq(EmpVideoInterviewSession::getEmpId, request.empId())
                                .eq(EmpVideoInterviewSession::getPostId, request.postId())
                                .in(EmpVideoInterviewSession::getStatus, 0, 1)
                                .orderByDesc(EmpVideoInterviewSession::getCreatedTime)
                                .last("LIMIT 1")
                );
                if (existingSession != null && existingSession.getStatus() >= 1) {
                    List<EmpVideoInterviewQuestion> existingQuestions = questionMapper.selectList(
                            Wrappers.<EmpVideoInterviewQuestion>lambdaQuery()
                                    .eq(EmpVideoInterviewQuestion::getSessionId, existingSession.getId())
                                    .orderByAsc(EmpVideoInterviewQuestion::getQuestionOrder)
                    );
                    if (!existingQuestions.isEmpty()) {
                        List<InterviewQuestion> questions = existingQuestions.stream()
                                .map(q -> new InterviewQuestion(
                                        q.getQuestionOrder(),
                                        q.getQuestionText(),
                                        q.getQuestionType(),
                                        q.getDifficulty(),
                                        parseExpectedTagIds(q.getExpectedTagsJson()),
                                        null
                                ))
                                .toList();
                        InterviewPlan existingPlan = new InterviewPlan(
                                existingSession.getId(), questions,
                                "使用已生成的面试问题", questions.size() * 3);
                        throw new PlanAlreadyExistsException(existingPlan);
                    }
                }
                session = new EmpVideoInterviewSession();
                session.setEmpId(request.empId());
                session.setPostId(request.postId());
                session.setInterviewMode(MODE_POST_BASED);
                session.setStatus(0);
                Long currentUserId = com.example.matching.utils.SecurityUtils.getCurrentUserId();
                session.setCreatedBy(currentUserId != null && currentUserId > 0 ? currentUserId : request.empId());
                session.setCreatedTime(LocalDateTime.now());
                sessionMapper.insert(session);
            }

            List<PostAbilityModel> abilityModels = postAbilityModelMapper.selectList(
                    Wrappers.<PostAbilityModel>lambdaQuery()
                            .eq(request.postId() != null, PostAbilityModel::getPostId, request.postId())
                            .orderByDesc(PostAbilityModel::getIsCore)
                            .orderByDesc(PostAbilityModel::getWeight)
            );

            List<Map<String, Object>> resumeClaims = session.getWorkflowId() != null
                    ? buildCanonicalWorkflowClaims(session)
                    : parseResumeAbilityClaims(resumeAbilityClaims);

            return new PlanPreparation(session, eligibility, resumeText, eligibility.resumeStructuredData(),
                    resumeAbilityClaims, abilityModels, resumeClaims);
        });
    }

    private List<Map<String, Object>> buildCanonicalWorkflowClaims(EmpVideoInterviewSession session) {
        AssessmentScopeDTO scope = assessmentOrchestrator != null
                ? assessmentOrchestrator.freezeScope(session.getWorkflowId(), session.getEmpId(), session.getPostId())
                : assessmentScopeService.build(session.getWorkflowId(), session.getEmpId(), session.getPostId());
        if (scope.items().isEmpty()) {
            throw new BusinessException(400, "简历未提供可核验的能力标签，不能生成 AI 面试题");
        }
        return scope.items().stream().map(item -> {
            Map<String, Object> claim = new LinkedHashMap<>();
            claim.put("tagId", item.abilityTagId());
            claim.put("tagName", item.abilityName());
            claim.put("level", item.claimedLevel());
            claim.put("sourceClaimIds", item.resumeClaimIds());
            claim.put("sourceEvidenceRefs", item.resumeEvidenceRefs());
            return claim;
        }).toList();
    }

    private InterviewPlan persistPlan(Long sessionId, InterviewPlan plan) {
        if (plan == null || plan.questions() == null || plan.questions().isEmpty()) {
            return plan;
        }
        return shortTransaction().execute(status -> {
            EmpVideoInterviewSession session = sessionMapper.selectById(sessionId);
            if (session == null) {
                throw new BusinessException(404, "Interview session not found during persist");
            }
            List<EmpVideoInterviewQuestion> existing = questionMapper.selectList(
                    Wrappers.<EmpVideoInterviewQuestion>lambdaQuery()
                            .eq(EmpVideoInterviewQuestion::getSessionId, sessionId)
            );
            if (!existing.isEmpty()) {
                return plan;
            }
            for (InterviewQuestion question : plan.questions()) {
                EmpVideoInterviewQuestion dbQuestion = new EmpVideoInterviewQuestion();
                dbQuestion.setSessionId(sessionId);
                dbQuestion.setQuestionOrder(question.order());
                dbQuestion.setQuestionText(question.text());
                dbQuestion.setQuestionType(question.type());
                dbQuestion.setDifficulty(question.difficulty());
                dbQuestion.setExpectedTagsJson(toJson(question.expectedTagIds()));
                questionMapper.insert(dbQuestion);
            }
            session.setStatus(1);
            session.setQuestionCount(plan.questions().size());
            sessionMapper.updateById(session);
            return plan;
        });
    }

    private InterviewPlan generateRuleBasedQuestions(PlanPreparation prep,
                                                      List<Map<String, Object>> resumeClaims,
                                                      int maxQuestions) {
        List<InterviewQuestion> questions = new ArrayList<>();
        if (resumeClaims == null || resumeClaims.isEmpty()) {
            return new InterviewPlan(prep.session.getId(), questions, "无可核验的简历能力声明", 0);
        }
        int questionLimit = Math.max(1, maxQuestions);
        // Rules cannot establish that arbitrary capabilities belong to the same project chain.
        // Keep their fallback questions focused; the Agent plan may aggregate any factually related capabilities.
        int groupSize = 1;
        int order = 1;
        for (int start = 0; start < resumeClaims.size() && questions.size() < questionLimit; start += groupSize) {
            List<Map<String, Object>> group = resumeClaims.subList(start, Math.min(start + groupSize, resumeClaims.size()));
            List<Long> tagIds = group.stream().map(claim -> toLong(claim.get("tagId")))
                    .filter(Objects::nonNull).distinct().toList();
            if (tagIds.isEmpty()) {
                continue;
            }
            String abilityNames = group.stream().map(claim -> String.valueOf(claim.get("tagName")))
                    .filter(name -> name != null && !name.isBlank() && !"null".equals(name))
                    .distinct().collect(Collectors.joining("、"));
            int targetLevel = group.stream().map(claim -> toLong(claim.get("level")))
                    .filter(Objects::nonNull).mapToInt(Long::intValue).max().orElse(1);
            questions.add(new InterviewQuestion(
                    order++,
                    buildResumeAnchoredQuestion(
                            extractBestResumeProjectEvidence(prep.resumeText, prep.resumeStructuredData), abilityNames),
                    "VERIFICATION",
                    mapDifficulty(targetLevel),
                    tagIds,
                    "围绕每项能力分别追问真实项目细节、具体贡献和量化结果"
            ));
        }

        return new InterviewPlan(
                prep.session.getId(),
                questions,
                "基于简历能力声明的结构化核验面试，岗位信息仅用于补充场景和难度参考",
                questions.size() * 3
        );
    }

    public static class PlanAlreadyExistsException extends RuntimeException {
        public final InterviewPlan existingPlan;
        public PlanAlreadyExistsException(InterviewPlan existingPlan) {
            super("Plan already exists");
            this.existingPlan = existingPlan;
        }
    }

    private String buildInterviewPlanContext(InterviewPlanRequest request,
                                             InterviewEligibilityCheck eligibility,
                                             List<PostAbilityModel> abilityModels,
                                             List<Map<String, Object>> resumeClaims,
                                             String resumeText,
                                             Long sessionId,
                                             java.util.Set<Long> allowedTagIds,
                                             Long workflowId,
                                             int plannedQuestionCount) {
        Map<String, Object> context = new LinkedHashMap<>();
        context.put("empId", request.empId());
        context.put("postId", request.postId());
        context.put("resumeParseId", eligibility.resumeParseId());
        // 完整简历仅做脱敏和基础清洗，由计划 Agent 为每道题独立选择对应项目背景。
        context.put("cleanedResumeBackground", llmInputGuard.untrusted(buildInterviewResumeBackground(resumeText)));
        context.put("resumeClaims", pruneResumeClaims(resumeClaims));
        context.put("postAbilityModels", prunePostAbilityModels(abilityModels));
        context.put("interviewHistory", request.interviewHistory());
        context.put("questionCount", plannedQuestionCount);
        // 注入裁剪后的测试结果摘要（每能力测试等级+简历等级+薄弱题）供交叉核验
        context.put("testResultSummary", assessmentTestResultProvider.buildSummary(workflowId));
        // 图谱预构建：面试计划子图（session/员工/岗位/能力模型/等级/差距/核心必填/前置关系）
        context.put("graphContext",
                agentGraphContextAssembler.buildForInterviewPlan(
                        sessionId, request.empId(), request.postId(), allowedTagIds));
        context.put("rules", List.of(
                "Questions must be traceable to expectedTagIds.",
                "Do not invent resume or post facts.",
                "For each question, select its own concrete project/work fact from cleanedResumeBackground; never ask the candidate to introduce a project again.",
                "Use cleanedResumeBackground only as project background; it must not add, replace, or infer abilities outside expectedTagIds.",
                "Verify resume-claimed abilities first; the post is optional context only.",
                "Probe abilities where the AI test exposed weak points."
        ));
        try {
            return objectMapper.writeValueAsString(context);
        } catch (Exception e) {
            return String.valueOf(context);
        }
    }

    /**
     * 岗位能力模型投影裁剪：只保留出题所需字段，按 isCore/weight 顺序取前 K 条。
     */
    private List<Map<String, Object>> prunePostAbilityModels(List<PostAbilityModel> abilityModels) {
        List<Map<String, Object>> pruned = new ArrayList<>();
        int limit = Math.min(abilityModels.size(), MAX_CONTEXT_ABILITY_MODELS);
        for (int i = 0; i < limit; i++) {
            PostAbilityModel m = abilityModels.get(i);
            AbilityTag tag = abilityTagMapper.selectById(m.getTagId());
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("tagId", m.getTagId());
            item.put("tagName", tag != null ? tag.getTagName() : null);
            item.put("minRequiredLevel", m.getMinRequiredLevel());
            item.put("isCore", m.getIsCore());
            pruned.add(item);
        }
        return pruned;
    }

    /**
     * 简历能力声明投影裁剪：只保留出题所需字段（tagName/level/evidence），证据截 120 字，
     * 避免简历解析出的任意长 evidence 等字段携带注入载荷。
     */
    private List<Map<String, Object>> pruneResumeClaims(List<Map<String, Object>> resumeClaims) {
        List<Map<String, Object>> pruned = new ArrayList<>();
        if (resumeClaims == null) {
            return pruned;
        }
        for (Map<String, Object> claim : resumeClaims) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("tagId", claim.get("tagId"));
            item.put("abilityName", claim.get("tagName"));
            item.put("level", claim.get("level"));
            item.put("sourceClaimIds", claim.get("sourceClaimIds"));
            item.put("sourceEvidenceRefs", claim.get("sourceEvidenceRefs"));
            Object evidence = claim.getOrDefault("evidence", claim.get("evidenceText"));
            if (evidence instanceof String s && !s.isBlank()) {
                item.put("evidence", s.length() > 120 ? s.substring(0, 120) + "..." : s);
            }
            pruned.add(item);
        }
        return pruned;
    }

    /** Only removes explicit personal-information fields; project selection remains the Agent's task. */
    private String buildInterviewResumeBackground(String resumeText) {
        if (resumeText == null || resumeText.isBlank()) {
            return "";
        }
        List<String> cleanedLines = new ArrayList<>();
        for (String rawLine : resumeText.replace('\r', '\n').split("\\n")) {
            String line = rawLine.replaceAll("\\s+", " ").trim();
            if (line.isBlank()) {
                continue;
            }
            if (line.matches("(?i)^(姓名|name|联系电话|电话|手机|邮箱|email|地址|住址|身份证|出生日期|性别|年龄)\\s*[:：].*$")) {
                cleanedLines.add("[REDACTED_PERSONAL_INFO]");
            } else {
                cleanedLines.add(line);
            }
        }
        return String.join("\n", cleanedLines);
    }

    private boolean isResumeSectionHeading(String line) {
        String normalized = line.trim();
        if (normalized.length() > 32) {
            return false;
        }
        return normalized.matches(".*(经历|经验|背景|评价|技能|教育|证书|项目|实习|实践|experience|skills|education|projects?).*[:：]?");
    }

    /** Extract a bounded project/work-experience excerpt for resume-anchored questions. */
    private String extractResumeProjectEvidence(String resumeText) {
        if (resumeText == null || resumeText.isBlank()) {
            return "";
        }
        String normalized = resumeText.replace('\r', '\n').replace('\t', ' ').trim();
        int start = firstSectionIndex(normalized, "项目经历", "项目经验", "项目案例", "工作经历", "实习经历");
        if (start < 0) {
            return "";
        }
        String evidence = normalized.substring(start);
        int end = firstSectionIndex(evidence, "教育经历", "教育背景", "自我评价", "个人评价", "证书", "技能清单");
        if (end > 0) {
            evidence = evidence.substring(0, end);
        }
        evidence = normalizeProjectEvidence(stripProjectSectionHeading(evidence));
        if (evidence.length() < 8 || isSectionHeading(evidence) || !isUsableProjectEvidence(evidence)) {
            return "";
        }
        evidence = evidence.replaceAll("\\s+", " ").trim();
        final int maxLength = 1800;
        return evidence.length() <= maxLength ? evidence : evidence.substring(0, maxLength) + "...";
    }

    /**
     * PDF/Word plain-text extraction may lose table rows and line breaks. The structured resume
     * parse is an independent, already validated representation of the same resume and is used
     * only when the original text cannot provide a concrete project fact.
     */
    private String extractBestResumeProjectEvidence(String resumeText, String resumeStructuredData) {
        String plainTextEvidence = extractResumeProjectEvidence(resumeText);
        if (!plainTextEvidence.isBlank()) {
            return plainTextEvidence;
        }
        if (resumeStructuredData != null && !resumeStructuredData.isBlank()) {
            try {
                com.fasterxml.jackson.databind.JsonNode root = objectMapper.readTree(resumeStructuredData);
                List<String> candidates = new ArrayList<>();
                collectStructuredProjectEvidence(root, candidates);
                Optional<String> structuredEvidence = candidates.stream()
                        .map(this::normalizeProjectEvidence)
                        .filter(value -> value.length() >= 8 && isUsableProjectEvidence(value))
                        .findFirst()
                        .map(value -> value.length() > 1800 ? value.substring(0, 1800) + "..." : value);
                if (structuredEvidence.isPresent()) {
                    return structuredEvidence.get();
                }
            } catch (Exception e) {
                log.debug("简历结构化项目事实不可用，继续从清洗原文定位: {}", e.getMessage());
            }
        }
        return extractProjectLikeResumeEvidence(buildInterviewResumeBackground(resumeText));
    }

    /**
     * Handles PDF/Word layouts where the project heading is lost but original project facts
     * remain. This only locates resume text and does not infer abilities or alter the scope.
     */
    private String extractProjectLikeResumeEvidence(String resumeBackground) {
        if (resumeBackground == null || resumeBackground.isBlank()) {
            return "";
        }
        List<String> facts = new ArrayList<>();
        int length = 0;
        for (String rawLine : resumeBackground.split("\\n")) {
            String line = normalizeProjectEvidence(rawLine);
            if (line.length() < 8 || isResumeSectionHeading(line)) {
                continue;
            }
            if (isUsableProjectEvidence(line)) {
                facts.add(line);
                length += line.length() + 1;
            }
            if (length >= 1800) {
                break;
            }
        }
        String evidence = String.join(" ", facts).trim();
        return evidence.length() <= 1800 ? evidence : evidence.substring(0, 1800) + "...";
    }

    /**
     * Keeps an actual project fragment when PDF/Word extraction has merged it with an earlier
     * education or award fragment. This is text cleanup only; it does not infer project facts.
     */
    private String normalizeProjectEvidence(String value) {
        if (value == null || value.isBlank()) {
            return "";
        }
        String normalized = value.replaceAll("\\s+", " ").trim();
        int lastAcademicEnd = lastAcademicOrAwardSignalEnd(normalized);
        if (lastAcademicEnd < 0) {
            return normalized;
        }
        int firstWorkSignal = firstConcreteWorkSignalIndex(normalized, lastAcademicEnd);
        if (firstWorkSignal < 0) {
            return normalized;
        }

        String prefix = normalized.substring(0, firstWorkSignal);
        int projectNameStart = lastProjectNameSignalIndex(prefix);
        int fragmentStart = lastFragmentStart(normalized, lastAcademicEnd, firstWorkSignal);
        String projectFragment = normalized.substring(projectNameStart >= 0 ? projectNameStart : fragmentStart).trim();
        return stripLeadingAwardQualifier(projectFragment);
    }

    private int firstConcreteWorkSignalIndex(String value, int fromIndex) {
        int result = -1;
        for (String signal : List.of("负责", "开发", "设计", "实现", "架构", "部署", "优化", "系统", "平台", "服务", "业务")) {
            int index = value.indexOf(signal, fromIndex);
            if (index >= 0 && (result < 0 || index < result)) {
                result = index;
            }
        }
        return result;
    }

    private int lastAcademicOrAwardSignalEnd(String value) {
        int result = -1;
        for (String signal : List.of("GPA", "主修课程", "奖学金", "竞赛", "获奖", "成绩", "学位", "学历")) {
            int index = value.lastIndexOf(signal);
            if (index >= 0) {
                result = Math.max(result, index + signal.length());
            }
        }
        return result;
    }

    private int lastProjectNameSignalIndex(String value) {
        int result = -1;
        for (String signal : List.of("系统", "平台", "项目", "模块", "应用", "网站", "小程序")) {
            result = Math.max(result, value.lastIndexOf(signal));
        }
        return result;
    }

    private int lastFragmentStart(String value, int fromIndex, int toIndex) {
        int boundary = fromIndex - 1;
        for (char delimiter : new char[]{'\n', '；', ';', '。', '！', '!', '？', '?'}) {
            boundary = Math.max(boundary, value.lastIndexOf(delimiter, toIndex - 1));
        }
        return Math.max(fromIndex, boundary + 1);
    }

    private String stripLeadingAwardQualifier(String value) {
        String result = value;
        String previous;
        do {
            previous = result;
            result = result.replaceFirst("^(?:[一二三四五六七八九十]+等奖|[一二三四五六七八九十]+等|校级|省级|国家级)[、，,:：；;\\s]*", "");
        } while (!previous.equals(result));
        return result.trim();
    }

    private boolean containsAcademicOrAwardSignal(String value) {
        return value.contains("GPA") || value.contains("主修课程") || value.contains("奖学金")
                || value.contains("竞赛") || value.contains("获奖") || value.contains("成绩")
                || value.contains("学位") || value.contains("学历");
    }

    /** Reject education/award text even when a parser incorrectly places it under projects. */
    private boolean isUsableProjectEvidence(String value) {
        if (value == null || value.isBlank()) {
            return false;
        }
        String normalized = value.replaceAll("\\s+", "");
        boolean hasConcreteWorkSignal = normalized.contains("系统") || normalized.contains("平台")
                || normalized.contains("负责") || normalized.contains("开发") || normalized.contains("设计")
                || normalized.contains("实现") || normalized.contains("架构") || normalized.contains("业务")
                || normalized.contains("服务") || normalized.contains("部署") || normalized.contains("优化");
        if (!hasConcreteWorkSignal) {
            return false;
        }
        return !containsAcademicOrAwardSignal(normalized);
    }

    private void collectStructuredProjectEvidence(com.fasterxml.jackson.databind.JsonNode node,
                                                  List<String> candidates) {
        if (node == null || node.isNull()) {
            return;
        }
        if (node.isObject()) {
            node.fields().forEachRemaining(field -> {
                String name = field.getKey().toLowerCase(Locale.ROOT);
                if (name.contains("project") || name.contains("experience") || name.contains("项目")
                        || name.contains("工作经历") || name.contains("实习")) {
                    appendStructuredText(field.getValue(), candidates);
                }
                collectStructuredProjectEvidence(field.getValue(), candidates);
            });
        } else if (node.isArray()) {
            for (com.fasterxml.jackson.databind.JsonNode item : node) {
                collectStructuredProjectEvidence(item, candidates);
            }
        }
    }

    private void appendStructuredText(com.fasterxml.jackson.databind.JsonNode node, List<String> candidates) {
        if (node == null || node.isNull()) {
            return;
        }
        if (node.isTextual()) {
            candidates.add(node.asText());
            return;
        }
        if (node.isArray()) {
            for (com.fasterxml.jackson.databind.JsonNode item : node) {
                appendStructuredText(item, candidates);
            }
            return;
        }
        if (node.isObject()) {
            List<String> values = new ArrayList<>();
            node.fields().forEachRemaining(field -> {
                if (field.getValue().isValueNode() && field.getValue().isTextual()) {
                    values.add(field.getValue().asText());
                }
            });
            if (!values.isEmpty()) {
                candidates.add(String.join("；", values));
            }
        }
    }

    private String stripProjectSectionHeading(String evidence) {
        String result = evidence == null ? "" : evidence.trim();
        String[] headings = {"项目经历", "项目经验", "项目案例", "工作经历", "实习经历"};
        for (String heading : headings) {
            if (result.startsWith(heading)) {
                result = result.substring(heading.length()).replaceFirst("^[：:、\\-\\s]+", "").trim();
                break;
            }
        }
        return result;
    }

    private boolean isSectionHeading(String evidence) {
        String compact = evidence.replaceAll("[\\s：:、\\-]", "");
        return compact.equals("项目经历") || compact.equals("项目经验") || compact.equals("项目案例")
                || compact.equals("工作经历") || compact.equals("实习经历");
    }

    private int firstSectionIndex(String text, String... labels) {
        int result = -1;
        for (String label : labels) {
            int index = text.indexOf(label);
            if (index >= 0 && (result < 0 || index < result)) {
                result = index;
            }
        }
        return result;
    }

    private String buildResumeAnchoredQuestion(String evidence, String abilityNames) {
        evidence = normalizeProjectEvidence(evidence);
        if (evidence.isBlank()) {
            return "请围绕简历中与“" + abilityNames + "”相关的一段真实工作或项目经历，说明你的具体职责、"
                    + "关键做法、技术取舍及结果。";
        }
        String anchor = evidence.substring(0, Math.min(evidence.length(), 100));
        return "你的简历提到「" + anchor + "」。围绕这段经历，请聚焦说明你在“"
                + abilityNames + "”上的具体实现或决策：当时的约束是什么、你采取了什么做法、"
                + "为什么这样选择，以及结果如何。无需重复介绍整个项目。";
    }

    private InterviewPlan parseInterviewPlan(String response, Long sessionId, int maxQuestions) {
        try {
            if (response == null || response.isBlank()) {
                response = "{}";
            }
            String json = llmResponseParser.extractJson(response);
            Map<String, Object> map = objectMapper.readValue(json, new TypeReference<>() {});
            List<Map<String, Object>> questionsList = (List<Map<String, Object>>) map.get("questions");
            if (questionsList == null || questionsList.isEmpty()) {
                return null;
            }

            List<InterviewQuestion> questions = new ArrayList<>();
            int order = 1;
            for (Map<String, Object> q : questionsList) {
                if (questions.size() >= maxQuestions) break;
                questions.add(new InterviewQuestion(
                        order++,
                        (String) q.get("text"),
                        (String) q.get("type"),
                        (String) q.get("difficulty"),
                        parseLongList(q.get("expectedTagIds")),
                        (String) q.get("followUpStrategy")
                ));
            }

            return new InterviewPlan(
                    sessionId,
                    questions,
                    (String) map.get("strategy"),
                    map.get("estimatedDuration") != null ? ((Number) map.get("estimatedDuration")).intValue() : questions.size() * 3
            );
        } catch (Exception e) {
            log.warn("Failed to parse LangChain4j interview plan: {}", e.getMessage());
            return null;
        }
    }

    private String buildCoreAbilityQuestion(AbilityTag tag, PostAbilityModel model) {
        String levelDesc = switch (model.getMinRequiredLevel()) {
            case 1 -> "入门";
            case 2 -> "熟悉";
            case 3 -> "掌握";
            case 4 -> "精通";
            case 5 -> "专家";
            default -> "基础";
        };

        return String.format(
                "请结合你过往项目，说明你如何使用「%s」解决真实业务问题。请包含背景、任务、行动、结果，以及你个人负责的部分。要求展示%s级别能力。",
                tag.getTagName(), levelDesc
        );
    }

    private String buildResumeVerificationQuestion(AbilityTag tag, Map<String, Object> claim) {
        Integer level = (Integer) claim.get("level");
        String levelDesc = level != null ? switch (level) {
            case 1 -> "入门";
            case 2 -> "熟悉";
            case 3 -> "掌握";
            case 4 -> "精通";
            case 5 -> "专家";
            default -> "相关";
        } : "相关";

        return String.format(
                "你的简历中提到你具备「%s」的%s级别经验。请选一个最能代表该能力的案例，说明你当时面对的具体问题、技术/业务约束、你的决策过程和最终结果。",
                tag.getTagName(), levelDesc
        );
    }

    private String buildQuestionText(AbilityTag tag, PostAbilityModel model) {
        String levelDesc = switch (model.getMinRequiredLevel()) {
            case 1 -> "入门";
            case 2 -> "熟悉";
            case 3 -> "掌握";
            case 4 -> "精通";
            case 5 -> "专家";
            default -> "基础";
        };

        if (model.getIsCore() == 1) {
            return String.format("请详细描述您在「%s」方面的实际项目经验，展示您达到%s级别能力的具体案例。", tag.getTagName(), levelDesc);
        } else {
            return String.format("请举例说明您在工作中如何运用「%s」能力解决问题。", tag.getTagName());
        }
    }

    private String mapDifficulty(Integer level) {
        if (level == null) return "MEDIUM";
        return switch (level) {
            case 1, 2 -> "EASY";
            case 3 -> "MEDIUM";
            case 4, 5 -> "HARD";
            default -> "MEDIUM";
        };
    }

    private String buildFollowUpStrategy(AbilityTag tag, PostAbilityModel model) {
        if (model.getIsCore() == 1) {
            return String.format("针对「%s」能力，追问具体技术细节、遇到的挑战和解决方案", tag.getTagName());
        } else {
            return String.format("针对「%s」能力，追问实际应用场景和效果", tag.getTagName());
        }
    }

    private String firstNonBlank(String primary, String fallback) {
        if (primary != null && !primary.isBlank()) {
            return primary;
        }
        return fallback;
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> parseResumeAbilityClaims(String resumeAbilityClaims) {
        if (resumeAbilityClaims == null || resumeAbilityClaims.isBlank() || "[]".equals(resumeAbilityClaims)) {
            return new ArrayList<>();
        }
        try {
            return objectMapper.readValue(resumeAbilityClaims, new TypeReference<List<Map<String, Object>>>() {});
        } catch (Exception e) {
            log.warn("解析简历能力声称失败: {}", e.getMessage());
            return new ArrayList<>();
        }
    }

    private Map<String, Object> findRelatedClaim(List<Map<String, Object>> claims, String tagName) {
        if (claims == null || tagName == null) {
            return null;
        }
        for (Map<String, Object> claim : claims) {
            String claimName = (String) claim.get("tagName");
            if (claimName != null && (claimName.contains(tagName) || tagName.contains(claimName))) {
                return claim;
            }
        }
        return null;
    }

    private java.util.Set<Long> resumeClaimTagIds(List<Map<String, Object>> claims) {
        if (claims == null) {
            return java.util.Set.of();
        }
        return claims.stream()
                .map(claim -> toLong(claim.get("tagId")))
                .filter(Objects::nonNull)
                .collect(Collectors.toCollection(LinkedHashSet::new));
    }

    /**
     * A short interview samples the claims most relevant to the current post. Claims not selected
     * here remain available to later assessment stages and are not treated as disproved.
     */
    private List<Map<String, Object>> selectClaimsForRound(List<Map<String, Object>> resumeClaims,
                                                            List<PostAbilityModel> abilityModels) {
        if (resumeClaims == null || resumeClaims.isEmpty()) {
            return List.of();
        }
        Map<Long, PostAbilityModel> modelsByTagId = abilityModels == null ? Map.of() : abilityModels.stream()
                .filter(model -> model.getTagId() != null)
                .collect(Collectors.toMap(PostAbilityModel::getTagId, model -> model, (left, right) -> left));

        return resumeClaims.stream()
                .filter(claim -> toLong(claim.get("tagId")) != null)
                .sorted(Comparator
                        .comparingInt((Map<String, Object> claim) -> {
                            PostAbilityModel model = modelsByTagId.get(toLong(claim.get("tagId")));
                            return model != null && Integer.valueOf(1).equals(model.getIsCore()) ? 0 : 1;
                        })
                        .thenComparing((Map<String, Object> claim) -> {
                            PostAbilityModel model = modelsByTagId.get(toLong(claim.get("tagId")));
                            return model != null && model.getWeight() != null ? model.getWeight() : java.math.BigDecimal.ZERO;
                        }, Comparator.reverseOrder()))
                .toList();
    }

    private List<Long> parseExpectedTagIds(String expectedTagsJson) {
        if (expectedTagsJson == null || expectedTagsJson.isBlank()) {
            return List.of();
        }
        try {
            List<?> values = objectMapper.readValue(expectedTagsJson, new TypeReference<List<?>>() {});
            return values.stream()
                    .map(this::toLong)
                    .filter(Objects::nonNull)
                    .distinct()
                    .collect(Collectors.toList());
        } catch (Exception e) {
            return List.of();
        }
    }

    private String toJson(Object obj) {
        try {
            return objectMapper.writeValueAsString(obj);
        } catch (Exception e) {
            return "[]";
        }
    }

    private Long toLong(Object value) {
        if (value == null) return null;
        if (value instanceof Long l) return l;
        if (value instanceof Number n) return n.longValue();
        try {
            return Long.parseLong(value.toString());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private List<Long> parseLongList(Object obj) {
        if (obj instanceof List<?> list) {
            return list.stream()
                    .map(this::toLong)
                    .filter(Objects::nonNull)
                    .toList();
        }
        return List.of();
    }

    private InterviewPlan convertPlanFromDto(InterviewPlanDTO dto, Long sessionId, int maxQuestions,
                                              java.util.Set<Long> allowedTagIds, String resumeText) {
        if (dto.getQuestions() == null || dto.getQuestions().isEmpty()) {
            log.warn("[INTERVIEW_PLAN_REJECTED] sessionId={}, reason=AI_QUESTIONS_EMPTY", sessionId);
            return null;
        }

        List<InterviewQuestion> questions = new ArrayList<>();
        int order = 1;
        for (InterviewPlanDTO.Question q : dto.getQuestions()) {
            if (questions.size() >= maxQuestions) break;
            // M23：题目 tagId 白名单 = 岗位能力模型 tagId；白名单外的虚构 tagId 剔除
            List<Long> expectedTagIds = q.getExpectedTagIds() != null ? q.getExpectedTagIds() : List.of();
            List<Long> filteredTagIds = expectedTagIds.stream()
                    .filter(tagId -> tagId != null && (allowedTagIds.isEmpty() || allowedTagIds.contains(tagId)))
                    .toList();
            if (filteredTagIds.size() != expectedTagIds.size()) {
                log.warn("题目包含岗位能力白名单外的 tagId，已剔除: questionText={}, original={}, filtered={}",
                        q.getText(), expectedTagIds, filteredTagIds);
            }
            if (filteredTagIds.isEmpty()) {
                throw new BusinessException(400, "面试题缺少当前工作流简历能力标签绑定");
            }
            if (!isValidProjectAnchor(q.getProjectAnchor(), resumeText)) {
                log.warn("[INTERVIEW_PLAN_REJECTED] sessionId={}, reason=PROJECT_ANCHOR_NOT_TRACEABLE, projectAnchor={}",
                        sessionId, q.getProjectAnchor());
                return null;
            }
            if (containsAcademicOrAwardSignal(q.getText())) {
                log.warn("[INTERVIEW_PLAN_REJECTED] sessionId={}, reason=QUESTION_CONTAINS_ACADEMIC_OR_AWARD_TEXT, questionText={}",
                        sessionId, q.getText());
                return null;
            }
            if (isGenericProjectQuestion(q.getText())) {
                log.warn("[INTERVIEW_PLAN_REJECTED] sessionId={}, reason=QUESTION_NOT_RESUME_ANCHORED, questionText={}",
                        sessionId, q.getText());
                return null;
            }
            questions.add(new InterviewQuestion(
                    q.getOrder() != null ? q.getOrder() : order++,
                    normalizeQuestionText(q.getText()),
                    q.getType(),
                    q.getDifficulty(),
                    filteredTagIds,
                    q.getFollowUpStrategy()
            ));
        }

        return new InterviewPlan(
                sessionId,
                questions,
                dto.getStrategy(),
                dto.getEstimatedDuration() != null ? dto.getEstimatedDuration() : questions.size() * 3
        );
    }

    private boolean isValidProjectAnchor(String projectAnchor, String resumeText) {
        if (projectAnchor == null || projectAnchor.isBlank() || containsAcademicOrAwardSignal(projectAnchor)) {
            return false;
        }
        String normalizedAnchor = projectAnchor.replaceAll("\\s+", "");
        if (normalizedAnchor.length() < 4 || resumeText == null || resumeText.isBlank()) {
            return false;
        }
        return resumeText.replaceAll("\\s+", "").contains(normalizedAnchor);
    }

    private boolean isGenericProjectQuestion(String text) {
        if (text == null || text.isBlank()) {
            return true;
        }
        String normalized = text.replaceAll("\\s+", "");
        return normalized.matches(".*请结合(一个|你过往|真实).{0,12}项目.*")
                || normalized.matches(".*请(详细)?描述.{0,12}项目经验.*")
                || normalized.matches(".*请举例说明.*")
                || normalized.matches(".*根据.{0,20}能力.{0,20}(运用|使用).{0,20}能力.*");
    }

    private String normalizeQuestionText(String text) {
        if (text == null) {
            return null;
        }
        String normalized = text.replaceAll("\\s+", " ").trim();
        int length = normalized.length();
        if (length > 1 && length % 2 == 0) {
            String firstHalf = normalized.substring(0, length / 2).trim();
            String secondHalf = normalized.substring(length / 2).trim();
            if (firstHalf.equals(secondHalf)) {
                return firstHalf;
            }
        }
        return normalized;
    }
}
