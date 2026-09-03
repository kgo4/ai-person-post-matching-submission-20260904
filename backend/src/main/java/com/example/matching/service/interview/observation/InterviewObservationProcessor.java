package com.example.matching.service.interview.observation;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.example.matching.agent.dto.interview.InterviewObservationDTO;
import com.example.matching.common.constant.SourceRefConstants;
import com.example.matching.common.exception.BusinessException;
import com.example.matching.entity.employee.EmpVideoInterviewAbility;
import com.example.matching.entity.employee.EmpVideoInterviewQuestion;
import com.example.matching.entity.employee.EmpVideoInterviewSession;
import com.example.matching.entity.interview.InterviewAbilityObservation;
import com.example.matching.entity.interview.InterviewFollowUpQuestion;
import com.example.matching.entity.system.AbilityTag;
import com.example.matching.entity.workflow.PersonAbilityClaimGroup;
import com.example.matching.mapper.employee.EmpVideoInterviewAbilityMapper;
import com.example.matching.mapper.employee.EmpVideoInterviewQuestionMapper;
import com.example.matching.mapper.employee.EmpVideoInterviewSessionMapper;
import com.example.matching.mapper.interview.InterviewAbilityObservationMapper;
import com.example.matching.mapper.interview.InterviewFollowUpQuestionMapper;
import com.example.matching.mapper.system.AbilityTagMapper;
import com.example.matching.mapper.workflow.PersonAbilityClaimGroupMapper;
import com.example.matching.common.enums.TagResolutionStatusEnum;
import com.example.matching.service.assessment.impl.AssessmentTestResultProvider;
import com.example.matching.agent.lc4j.InterviewObservationAiService;
import com.example.matching.agent.service.impl.AgentOutputValidator;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
public class InterviewObservationProcessor {

    /** Single Spring injection constructor; the compatibility constructor below is test-only. */
    @org.springframework.beans.factory.annotation.Autowired
    public InterviewObservationProcessor(EmpVideoInterviewSessionMapper sessionMapper,
                                         EmpVideoInterviewQuestionMapper questionMapper,
                                         EmpVideoInterviewAbilityMapper abilityMapper,
                                         InterviewAbilityObservationMapper observationMapper,
                                         InterviewFollowUpQuestionMapper followUpQuestionMapper,
                                         AbilityTagMapper abilityTagMapper,
                                         PersonAbilityClaimGroupMapper claimGroupMapper,
                                         ObjectMapper objectMapper,
                                         ObjectProvider<InterviewObservationAiService> interviewObservationAiServiceProvider,
                                         AgentOutputValidator agentOutputValidator,
                                         com.example.matching.infrastructure.llm.LlmResponseParser llmResponseParser,
                                         PlatformTransactionManager transactionManager,
                                         com.example.matching.agent.service.AgentGraphContextAssembler agentGraphContextAssembler,
                                         AssessmentTestResultProvider assessmentTestResultProvider) {
        this.sessionMapper = sessionMapper;
        this.questionMapper = questionMapper;
        this.abilityMapper = abilityMapper;
        this.observationMapper = observationMapper;
        this.followUpQuestionMapper = followUpQuestionMapper;
        this.abilityTagMapper = abilityTagMapper;
        this.claimGroupMapper = claimGroupMapper;
        this.objectMapper = objectMapper;
        this.interviewObservationAiServiceProvider = interviewObservationAiServiceProvider;
        this.agentOutputValidator = agentOutputValidator;
        this.llmResponseParser = llmResponseParser;
        this.transactionManager = transactionManager;
        this.agentGraphContextAssembler = agentGraphContextAssembler;
        this.assessmentTestResultProvider = assessmentTestResultProvider;
    }

    /** Compatibility constructor for fixtures created before interview Harness was
     * moved into the unified aggregate stage. The Harness argument is ignored. */
    public InterviewObservationProcessor(EmpVideoInterviewSessionMapper sessionMapper,
                                         EmpVideoInterviewQuestionMapper questionMapper,
                                         EmpVideoInterviewAbilityMapper abilityMapper,
                                         InterviewAbilityObservationMapper observationMapper,
                                         InterviewFollowUpQuestionMapper followUpQuestionMapper,
                                         AbilityTagMapper abilityTagMapper,
                                         PersonAbilityClaimGroupMapper claimGroupMapper,
                                         ObjectMapper objectMapper,
                                         com.example.matching.service.harness.AiTrustHarnessService ignoredHarness,
                                         ObjectProvider<InterviewObservationAiService> interviewObservationAiServiceProvider,
                                         AgentOutputValidator agentOutputValidator,
                                         com.example.matching.infrastructure.llm.LlmResponseParser llmResponseParser,
                                         PlatformTransactionManager transactionManager,
                                         com.example.matching.agent.service.AgentGraphContextAssembler agentGraphContextAssembler,
                                         AssessmentTestResultProvider assessmentTestResultProvider) {
        this(sessionMapper, questionMapper, abilityMapper, observationMapper, followUpQuestionMapper,
                abilityTagMapper, claimGroupMapper, objectMapper, interviewObservationAiServiceProvider,
                agentOutputValidator, llmResponseParser, transactionManager, agentGraphContextAssembler,
                assessmentTestResultProvider);
    }

    private final EmpVideoInterviewSessionMapper sessionMapper;
    private final EmpVideoInterviewQuestionMapper questionMapper;
    private final EmpVideoInterviewAbilityMapper abilityMapper;
    private final InterviewAbilityObservationMapper observationMapper;
    private final InterviewFollowUpQuestionMapper followUpQuestionMapper;
    private final AbilityTagMapper abilityTagMapper;
    private final PersonAbilityClaimGroupMapper claimGroupMapper;
    private final ObjectMapper objectMapper;
    private final ObjectProvider<InterviewObservationAiService> interviewObservationAiServiceProvider;
    private final AgentOutputValidator agentOutputValidator;
    private final com.example.matching.infrastructure.llm.LlmResponseParser llmResponseParser;
    private final PlatformTransactionManager transactionManager;
    private final com.example.matching.agent.service.AgentGraphContextAssembler agentGraphContextAssembler;
    private final AssessmentTestResultProvider assessmentTestResultProvider;

    private TransactionTemplate shortTransaction() {
        TransactionTemplate template = new TransactionTemplate(transactionManager);
        template.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRED);
        return template;
    }


    private static class ObservationSnapshot {
        final EmpVideoInterviewSession session;
        final List<EmpVideoInterviewQuestion> questions;
        final List<InterviewFollowUpQuestion> followUps;
        final List<EmpVideoInterviewAbility> existingAbilities;
        final Map<Long, InterviewAbilityObservation> existingObsByTagId;

        ObservationSnapshot(EmpVideoInterviewSession session, List<EmpVideoInterviewQuestion> questions,
                            List<InterviewFollowUpQuestion> followUps, List<EmpVideoInterviewAbility> existingAbilities,
                            Map<Long, InterviewAbilityObservation> existingObsByTagId) {
            this.session = session;
            this.questions = questions;
            this.followUps = followUps;
            this.existingAbilities = existingAbilities;
            this.existingObsByTagId = existingObsByTagId;
        }
    }

    public List<InterviewAbilityObservation> conductInterviewAndObserve(Long sessionId) {
        log.info("执行面试并生成能力观察，sessionId={}", sessionId);

        ObservationSnapshot snapshot = prepareObservation(sessionId);
        EmpVideoInterviewSession session = snapshot.session;
        Set<Long> allowedTagIds = resolveAllowedTagIds(snapshot);

        if (allowedTagIds.isEmpty()) {
            log.info("面试不存在简历已提取且已归并的能力范围，跳过能力观察，sessionId={}", sessionId);
            projectQuestionResults(snapshot.questions, List.of());
            return List.of();
        }

        if (!hasUsableObservationInput(snapshot)) {
            log.info("面试不存在可验证回答证据，跳过 AI 观察提取，sessionId={}", sessionId);
            List<InterviewAbilityObservation> observations = persistObservations(
                    sessionId, buildRuleBasedObservations(snapshot, allowedTagIds));
            projectQuestionResults(snapshot.questions, observations);
            return observations;
        }

        InterviewObservationAiService observationAiService = interviewObservationAiServiceProvider.getIfAvailable();
        if (observationAiService != null) {
            try {
                String context = buildInterviewObservationContext(sessionId, session,
                        snapshot.questions, snapshot.followUps, allowedTagIds);
                InterviewObservationDTO observationDto = com.example.matching.agent.config.AgentToolProvider
                        .withScope(() -> observationAiService.extractObservations(sessionId, context));
                agentOutputValidator.validateOrThrow(observationDto, "INTERVIEW_OBSERVATION");
                List<InterviewAbilityObservation> aiObservations = convertObservationsFromDto(
                        observationDto, session, allowedTagIds, snapshot);

                if (!aiObservations.isEmpty()) {
                    for (InterviewAbilityObservation obs : aiObservations) {
                        List<String> sourceRefs = new ArrayList<>();
                        sourceRefs.add(SourceRefConstants.sourceRef(SourceRefConstants.SOURCE_AI_INTERVIEW, sessionId));
                        sourceRefs.add(SourceRefConstants.factRef(SourceRefConstants.ENTITY_INTERVIEW_SESSION, sessionId));

                        List<Long> relatedQuestionIds = getRelatedQuestionIdsByTagId(obs.getTagId(), snapshot.questions);
                        for (Long questionId : relatedQuestionIds) {
                            sourceRefs.add(SourceRefConstants.factRef(SourceRefConstants.ENTITY_INTERVIEW_QUESTION, questionId));
                        }

                        List<Long> relatedFollowUpIds = snapshot.followUps.stream()
                                .filter(fu -> obs.getTagId().equals(fu.getTargetAbilityTagId()))
                                .map(InterviewFollowUpQuestion::getId)
                                .toList();
                        for (Long followUpId : relatedFollowUpIds) {
                            sourceRefs.add(SourceRefConstants.factRef(SourceRefConstants.ENTITY_INTERVIEW_FOLLOW_UP, followUpId));
                        }

                        obs.setSourceRefsJson(toJson(sourceRefs));
                        obs.setQuestionIdsJson(toJson(relatedQuestionIds));
                        obs.setAnswerRefsJson(toJson(relatedQuestionIds.stream()
                                .map(id -> SourceRefConstants.factRef(SourceRefConstants.ENTITY_INTERVIEW_QUESTION, id))
                                .toList()));
                        obs.setFollowUpRefsJson(toJson(relatedFollowUpIds));
                    }

                    List<InterviewAbilityObservation> persisted = persistObservations(sessionId, aiObservations);
                    projectQuestionResults(snapshot.questions, persisted);
                    log.info("LangChain4j 面试观察提取完成，sessionId={}, count={}", sessionId, persisted.size());
                    return persisted;
                }
            } catch (Exception e) {
                log.warn("LangChain4j 面试观察提取失败，回退到规则提取: {}", e.getMessage());
            }
        }

        List<InterviewAbilityObservation> ruleObservations = buildRuleBasedObservations(snapshot, allowedTagIds);
        List<InterviewAbilityObservation> persisted = persistObservations(sessionId, ruleObservations);
        projectQuestionResults(snapshot.questions, persisted);
        return persisted;
    }

    /**
     * 面试只能核验简历解析阶段已成功归并的能力，不能因岗位题目或模型输出新增能力。
     */
    private Set<Long> resolveAllowedTagIds(ObservationSnapshot snapshot) {
        Set<Long> questionTagIds = snapshot.questions.stream()
                .flatMap(q -> parseExpectedTagIds(q.getExpectedTagsJson()).stream())
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        Long workflowId = snapshot.session.getWorkflowId();
        if (workflowId == null) {
            return Set.of();
        }
        Set<Long> resumeScopeTagIds = claimGroupMapper.selectList(
                        Wrappers.<PersonAbilityClaimGroup>lambdaQuery()
                                .eq(PersonAbilityClaimGroup::getWorkflowId, workflowId))
                .stream()
                // A resume capability without a system-tag mapping is still a valid
                // assessment subject. Its assessmentAbilityId is the stable workflow
                // identifier used by the generated questions.
                .map(group -> group.getCanonicalTagId() != null ? group.getCanonicalTagId()
                        : (group.getAssessmentAbilityId() != null ? group.getAssessmentAbilityId() : group.getId()))
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        questionTagIds.retainAll(resumeScopeTagIds);
        return questionTagIds;
    }

    private boolean hasUsableObservationInput(ObservationSnapshot snapshot) {
        boolean hasExistingAbility = snapshot.existingAbilities.stream()
                .anyMatch(ability -> ability.getTagId() != null);
        if (hasExistingAbility) {
            return true;
        }
        return snapshot.questions.stream().anyMatch(question ->
                question.getAnswerTranscript() != null
                        && !question.getAnswerTranscript().isBlank()
                        && !isNonEvidenceAnswer(question)
                        && !parseExpectedTagIds(question.getExpectedTagsJson()).isEmpty());
    }

    private ObservationSnapshot prepareObservation(Long sessionId) {
        return shortTransaction().execute(status -> {
            EmpVideoInterviewSession session = sessionMapper.selectById(sessionId);
            if (session == null) {
                throw new BusinessException(404, "面试会话不存在");
            }

            List<EmpVideoInterviewQuestion> questions = questionMapper.selectList(
                    Wrappers.<EmpVideoInterviewQuestion>lambdaQuery()
                            .eq(EmpVideoInterviewQuestion::getSessionId, sessionId)
                            .orderByAsc(EmpVideoInterviewQuestion::getQuestionOrder)
            );

            List<InterviewFollowUpQuestion> followUps = followUpQuestionMapper.selectList(
                    Wrappers.<InterviewFollowUpQuestion>lambdaQuery()
                            .eq(InterviewFollowUpQuestion::getSessionId, sessionId)
                            .eq(InterviewFollowUpQuestion::getIsDeleted, 0)
            );

            List<EmpVideoInterviewAbility> existingAbilities = abilityMapper.selectList(
                    Wrappers.<EmpVideoInterviewAbility>lambdaQuery()
                            .eq(EmpVideoInterviewAbility::getSessionId, sessionId)
            );

            List<InterviewAbilityObservation> existingObservations = observationMapper.selectList(
                    Wrappers.<InterviewAbilityObservation>lambdaQuery()
                            .eq(InterviewAbilityObservation::getSessionId, sessionId)
                            .eq(InterviewAbilityObservation::getIsDeleted, 0)
            );
            Map<Long, InterviewAbilityObservation> existingObsByTagId = existingObservations.stream()
                    .filter(o -> o.getTagId() != null)
                    .collect(Collectors.toMap(InterviewAbilityObservation::getTagId, o -> o, (a, b) -> a));

            return new ObservationSnapshot(session, questions, followUps, existingAbilities, existingObsByTagId);
        });
    }

    private List<InterviewAbilityObservation> persistObservations(Long sessionId,
                                                                   List<InterviewAbilityObservation> observations) {
        if (observations.isEmpty()) {
            return observations;
        }
        return shortTransaction().execute(status -> {
            EmpVideoInterviewSession session = sessionMapper.selectById(sessionId);
            if (session == null) {
                return observations;
            }
            List<InterviewAbilityObservation> result = new ArrayList<>();
            for (InterviewAbilityObservation obs : observations) {
                InterviewAbilityObservation existing = observationMapper.selectOne(
                        Wrappers.<InterviewAbilityObservation>lambdaQuery()
                                .eq(InterviewAbilityObservation::getSessionId, sessionId)
                                .eq(InterviewAbilityObservation::getTagId, obs.getTagId())
                                .eq(InterviewAbilityObservation::getIsDeleted, 0)
                );
                if (existing != null) {
                    obs.setId(existing.getId());
                    observationMapper.updateById(obs);
                } else {
                    observationMapper.insert(obs);
                }
                result.add(obs);
            }
            return result;
        });
    }

    /**
     * 将范围内能力观察投影为题目结果。题目得分始终来自本题回答关联的能力观察，
     * 不使用简历、视觉帧或范围外能力补分。
     */
    private void projectQuestionResults(List<EmpVideoInterviewQuestion> questions,
                                        List<InterviewAbilityObservation> observations) {
        Map<Long, InterviewAbilityObservation> observationByTagId = observations.stream()
                .filter(observation -> observation.getTagId() != null)
                .collect(Collectors.toMap(InterviewAbilityObservation::getTagId,
                        observation -> observation, (left, right) -> left));

        for (EmpVideoInterviewQuestion question : questions) {
            String answer = question.getAnswerTranscript();
            if (answer == null || answer.isBlank()) {
                question.setAnswerScore(null);
                question.setAnalysisComment("未采集到可用回答转写，证据不足，未评分");
                questionMapper.updateById(question);
                continue;
            }

            // 实时回答质量核验是题目得分和评语的唯一来源。面试后能力观察只供报告使用，
            // 不得按标签关联或回答篇幅覆盖原始回答判断。
            if (question.getAnswerScore() != null) {
                if (question.getAnalysisComment() == null || question.getAnalysisComment().isBlank()) {
                    question.setAnalysisComment("已完成本题实时回答质量核验");
                    questionMapper.updateById(question);
                }
                continue;
            }

            List<InterviewAbilityObservation> related = parseExpectedTagIds(question.getExpectedTagsJson()).stream()
                    .map(observationByTagId::get)
                    .filter(Objects::nonNull)
                    .toList();
            if (related.isEmpty()) {
                question.setAnswerScore(null);
                question.setAnalysisComment("回答已采集，但未形成范围内能力核验证据，未评分");
                questionMapper.updateById(question);
                continue;
            }

            int score = (int) Math.round(related.stream()
                    .map(InterviewAbilityObservation::getObservedLevel)
                    .filter(Objects::nonNull)
                    .mapToInt(level -> Math.max(0, Math.min(level, 5)) * 20)
                    .average()
                    .orElse(0));
            String abilities = related.stream()
                    .map(observation -> observation.getAbilityName() != null
                            ? observation.getAbilityName() : String.valueOf(observation.getTagId()))
                    .distinct()
                    .collect(Collectors.joining("、"));
            question.setAnswerScore(BigDecimal.valueOf(score));
            question.setAnalysisComment("已基于本题回答核验范围内能力：" + abilities);
            questionMapper.updateById(question);
        }
    }

    private List<InterviewAbilityObservation> buildRuleBasedObservations(ObservationSnapshot snapshot,
                                                                           Set<Long> allowedTagIds) {
        List<InterviewAbilityObservation> observations = new ArrayList<>();
        Set<Long> coveredTagIds = new HashSet<>();

        // 1. EmpVideoInterviewAbility 作为规则兜底的增强输入（保留其确定性评估结果），
        //    但不再是唯一输入
        for (EmpVideoInterviewAbility ability : snapshot.existingAbilities) {
            if (ability.getTagId() == null || !allowedTagIds.contains(ability.getTagId())) {
                continue;
            }
            String evidenceText = buildEvidenceText(snapshot.session.getId(), ability, snapshot.questions);

            List<String> answerRefs = getAnswerRefs(ability, snapshot.questions);

            List<Long> followUpIds = findAnsweredFollowUpIds(snapshot.session.getId(), ability.getTagId());

            List<String> sourceRefs = new ArrayList<>();
            sourceRefs.add(SourceRefConstants.sourceRef(SourceRefConstants.SOURCE_AI_INTERVIEW, snapshot.session.getId()));
            sourceRefs.add(SourceRefConstants.factRef(SourceRefConstants.ENTITY_INTERVIEW_SESSION, snapshot.session.getId()));
            sourceRefs.addAll(answerRefs);
            sourceRefs.addAll(followUpIds.stream()
                    .map(id -> SourceRefConstants.factRef(SourceRefConstants.ENTITY_INTERVIEW_FOLLOW_UP, id))
                    .toList());

            InterviewAbilityObservation observation = snapshot.existingObsByTagId.get(ability.getTagId());
            boolean isNew = (observation == null);

            if (isNew) {
                observation = new InterviewAbilityObservation();
                observation.setSessionId(snapshot.session.getId());
                observation.setEmpId(snapshot.session.getEmpId());
                observation.setPostId(snapshot.session.getPostId());
                observation.setTagId(ability.getTagId());
            }

            observation.setAbilityName(resolveAbilityName(snapshot.session, ability.getTagId()));
            observation.setObservedLevel(ability.getMasteryLevel());
            observation.setConfidenceScore(ability.getConfidenceScore());
            observation.setEvidenceText(evidenceText);
            observation.setQuestionIdsJson(toJson(getRelatedQuestionIds(ability, snapshot.questions)));
            observation.setAnswerRefsJson(toJson(answerRefs));
            observation.setFollowUpRefsJson(toJson(followUpIds));
            observation.setRiskSignalsJson(toJson(buildRiskSignals(ability)));
            observation.setInterviewConclusion(ability.getAnalysisComment());
            observation.setSourceRefsJson(toJson(sourceRefs));

            observations.add(observation);
            coveredTagIds.add(ability.getTagId());
        }

        // 2. 从当前 session 的题目派生能力候选：题目关联 tagId、回答文本作为 evidence；
        //    没有 tagId 或没有回答的题目不生成虚构能力观察
        for (EmpVideoInterviewQuestion question : snapshot.questions) {
            for (Long tagId : parseExpectedTagIds(question.getExpectedTagsJson())) {
                if (tagId == null || !allowedTagIds.contains(tagId) || coveredTagIds.contains(tagId)) {
                    continue;
                }
                String answer = question.getAnswerTranscript();
                if (answer == null || answer.isBlank() || isNonEvidenceAnswer(question)) {
                    continue;
                }
                observations.add(buildDerivedObservation(snapshot, tagId, question, answer));
                coveredTagIds.add(tagId);
            }
        }

        if (observations.isEmpty()) {
            log.info("INTERVIEW_OBSERVATION_EMPTY: sessionId={}, 无有效题目关联（tagId+回答），未生成任何能力观察",
                    snapshot.session.getId());
        } else {
            log.info("面试能力观察规则生成完成，sessionId={}, observationCount={}",
                    snapshot.session.getId(), observations.size());
        }
        return observations;
    }

    /**
     * 从题目/回答派生确定性能力观察：回答文本作为 evidence，回答长度与质量分、
     * 追问数量作为确定性等级依据。
     */
    private InterviewAbilityObservation buildDerivedObservation(ObservationSnapshot snapshot, Long tagId,
                                                                EmpVideoInterviewQuestion question, String answer) {
        List<Long> relatedQuestionIds = new ArrayList<>();
        for (EmpVideoInterviewQuestion q : snapshot.questions) {
            if (parseExpectedTagIds(q.getExpectedTagsJson()).contains(tagId)
                    && q.getAnswerTranscript() != null && !q.getAnswerTranscript().isBlank()
                    && !isNonEvidenceAnswer(q)) {
                relatedQuestionIds.add(q.getId());
            }
        }
        List<String> answerRefs = relatedQuestionIds.stream()
                .map(id -> SourceRefConstants.factRef(SourceRefConstants.ENTITY_INTERVIEW_QUESTION, id))
                .toList();
        List<Long> followUpIds = findAnsweredFollowUpIds(snapshot.session.getId(), tagId);

        List<String> sourceRefs = new ArrayList<>();
        sourceRefs.add(SourceRefConstants.sourceRef(SourceRefConstants.SOURCE_AI_INTERVIEW, snapshot.session.getId()));
        sourceRefs.add(SourceRefConstants.factRef(SourceRefConstants.ENTITY_INTERVIEW_SESSION, snapshot.session.getId()));
        sourceRefs.addAll(answerRefs);
        sourceRefs.addAll(followUpIds.stream()
                .map(id -> SourceRefConstants.factRef(SourceRefConstants.ENTITY_INTERVIEW_FOLLOW_UP, id))
                .toList());

        Integer observedLevel = deriveObservedLevel(snapshot, tagId, question, answer);

        InterviewAbilityObservation observation = new InterviewAbilityObservation();
        observation.setSessionId(snapshot.session.getId());
        observation.setEmpId(snapshot.session.getEmpId());
        observation.setPostId(snapshot.session.getPostId());
        observation.setTagId(tagId);
        observation.setAbilityName(resolveAbilityName(snapshot.session, tagId));
        observation.setObservedLevel(observedLevel);
        observation.setConfidenceScore(deriveConfidenceScore(answer));
        observation.setEvidenceText(buildEvidenceTextByTagId(snapshot.session.getId(), tagId, snapshot.questions));
        observation.setQuestionIdsJson(toJson(relatedQuestionIds));
        observation.setAnswerRefsJson(toJson(answerRefs));
        observation.setFollowUpRefsJson(toJson(followUpIds));
        observation.setRiskSignalsJson(toJson(deriveRiskSignals(snapshot, tagId, question)));
        observation.setInterviewConclusion(deriveConclusion(observedLevel));
        observation.setSourceRefsJson(toJson(sourceRefs));
        return observation;
    }

    private Integer deriveObservedLevel(ObservationSnapshot snapshot, Long tagId,
                                        EmpVideoInterviewQuestion question, String answer) {
        int level = 1;
        if (answer != null) {
            if (answer.length() >= 300) {
                level = 3;
            } else if (answer.length() >= 100) {
                level = 2;
            }
        }
        if (question.getAnswerScore() != null) {
            if (question.getAnswerScore().compareTo(BigDecimal.valueOf(70)) >= 0) {
                level = Math.max(level, 3);
            } else if (question.getAnswerScore().compareTo(BigDecimal.valueOf(40)) < 0) {
                level = Math.min(level, 1);
            }
        }
        // 已有追问被回答说明首次回答不足，限制派生等级
        long answeredFollowUps = snapshot.followUps.stream()
                .filter(fu -> tagId.equals(fu.getTargetAbilityTagId()) && "ANSWERED".equals(fu.getFollowUpStatus()))
                .count();
        if (answeredFollowUps > 0) {
            level = Math.min(level, 2);
        }
        return level;
    }

    private boolean isNonEvidenceAnswer(EmpVideoInterviewQuestion question) {
        return question.getAnswerScore() != null && question.getAnswerScore().compareTo(BigDecimal.ZERO) <= 0;
    }

    private BigDecimal deriveConfidenceScore(String answer) {
        if (answer == null || answer.isBlank()) {
            return BigDecimal.valueOf(30);
        }
        return answer.length() >= 200 ? BigDecimal.valueOf(60) : BigDecimal.valueOf(50);
    }

    private String deriveConclusion(Integer observedLevel) {
        if (observedLevel == null || observedLevel <= 1) {
            return "回答证据有限，能力等级存疑，建议人工复核";
        }
        if (observedLevel == 2) {
            return "回答具备一定细节，能力等级中等，可结合追问结论综合判断";
        }
        return "回答细节充分，具备该能力的基础证据";
    }

    private List<String> deriveRiskSignals(ObservationSnapshot snapshot, Long tagId, EmpVideoInterviewQuestion question) {
        List<String> risks = new ArrayList<>();
        if (question.getAnswerTranscript() == null || question.getAnswerTranscript().isBlank()) {
            risks.add("缺少回答证据");
        } else if (question.getAnswerTranscript().length() < 100) {
            risks.add("回答篇幅较短，证据充分性存疑");
        }
        if (question.getAnswerScore() == null) {
            risks.add("缺少回答质量评分");
        } else if (question.getAnswerScore().compareTo(BigDecimal.valueOf(40)) < 0) {
            risks.add("回答质量评分较低");
        }
        long answeredFollowUps = snapshot.followUps.stream()
                .filter(fu -> tagId.equals(fu.getTargetAbilityTagId()) && "ANSWERED".equals(fu.getFollowUpStatus()))
                .count();
        if (answeredFollowUps > 0) {
            risks.add("存在追问记录，首次回答可能不充分");
        }
        return risks;
    }

    @SuppressWarnings("unchecked")
    private List<String> parseSourceRefsList(String sourceRefsJson) {
        if (sourceRefsJson == null || sourceRefsJson.isBlank()) {
            return List.of();
        }
        try {
            return objectMapper.readValue(sourceRefsJson, new TypeReference<List<String>>() {});
        } catch (Exception e) {
            return List.of();
        }
    }

    private String buildInterviewObservationContext(Long sessionId,
                                                     EmpVideoInterviewSession session,
                                                     List<EmpVideoInterviewQuestion> questions,
                                                     List<InterviewFollowUpQuestion> followUps,
                                                     java.util.Set<Long> allowedTagIds) {
        Map<String, Object> context = new LinkedHashMap<>();
        context.put("sessionId", sessionId);
        context.put("empId", session.getEmpId());
        context.put("postId", session.getPostId());
        // 裁剪：只投影观察所需字段 + 回答截断，不序列化整个实体
        context.put("questions", pruneQuestions(questions, allowedTagIds));
        context.put("followUps", pruneFollowUps(followUps));
        // 注入裁剪后的测试结果摘要供交叉核验
        context.put("testResultSummary", assessmentTestResultProvider.buildSummary(session.getWorkflowId()));
        // 图谱预构建：仅当前会话子图（session/问题/追问/回答引用/岗位能力白名单），
        // 禁止放入其他员工证据、其他会话、全局图谱节点、未审核证据
        context.put("graphContext",
                agentGraphContextAssembler.buildForInterviewObservation(sessionId, allowedTagIds));
        context.put("rules", List.of(
                "Every observation must cite interview answer evidence.",
                "Only evaluate ability tagIds in the supplied assessment scope; never infer or create a new ability.",
                "Observations are candidates and must pass Harness before persistence.",
                "If evidence is insufficient, lower confidenceScore."
        ));
        try {
            return objectMapper.writeValueAsString(context);
        } catch (Exception e) {
            return String.valueOf(context);
        }
    }

    private static final int ANSWER_MAX_LEN = 300;

    private List<Map<String, Object>> pruneQuestions(List<EmpVideoInterviewQuestion> questions,
                                                      Set<Long> allowedTagIds) {
        List<Map<String, Object>> pruned = new ArrayList<>();
        for (EmpVideoInterviewQuestion q : questions) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("questionId", q.getId());
            item.put("questionText", q.getQuestionText());
            item.put("expectedTagIds", parseExpectedTagIds(q.getExpectedTagsJson()).stream()
                    .filter(allowedTagIds::contains)
                    .toList());
            item.put("answerTranscript", isNonEvidenceAnswer(q) ? null : truncateAnswer(q.getAnswerTranscript()));
            item.put("answerQualityScore", q.getAnswerScore());
            pruned.add(item);
        }
        return pruned;
    }

    private List<Map<String, Object>> pruneFollowUps(List<InterviewFollowUpQuestion> followUps) {
        List<Map<String, Object>> pruned = new ArrayList<>();
        for (InterviewFollowUpQuestion fu : followUps) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("questionId", fu.getId());
            item.put("questionText", fu.getQuestionText());
            item.put("answerText", truncateAnswer(fu.getAnswerText()));
            pruned.add(item);
        }
        return pruned;
    }

    private String truncateAnswer(String text) {
        if (text == null) {
            return null;
        }
        return text.length() <= ANSWER_MAX_LEN ? text : text.substring(0, ANSWER_MAX_LEN) + "...";
    }

    private List<InterviewAbilityObservation> parseInterviewObservations(String response, EmpVideoInterviewSession session) {
        try {
            if (response == null || response.isBlank()) {
                response = "{}";
            }
            String json = llmResponseParser.extractJson(response);
            Map<String, Object> map = objectMapper.readValue(json, new TypeReference<>() {});
            List<Map<String, Object>> obsList = (List<Map<String, Object>>) map.get("observations");
            if (obsList == null || obsList.isEmpty()) {
                return List.of();
            }

            List<InterviewAbilityObservation> observations = new ArrayList<>();
            for (Map<String, Object> obs : obsList) {
                InterviewAbilityObservation observation = new InterviewAbilityObservation();
                observation.setSessionId(session.getId());
                observation.setEmpId(session.getEmpId());
                observation.setPostId(session.getPostId());
                observation.setTagId(obs.get("tagId") != null ? ((Number) obs.get("tagId")).longValue() : null);
                observation.setAbilityName((String) obs.get("abilityName"));
                observation.setObservedLevel(obs.get("observedLevel") != null ? ((Number) obs.get("observedLevel")).intValue() : null);
                observation.setConfidenceScore(obs.get("confidenceScore") != null ? new BigDecimal(obs.get("confidenceScore").toString()) : null);
                observation.setEvidenceText((String) obs.get("evidenceText"));
                observations.add(observation);
            }

            return observations;
        } catch (Exception e) {
            log.warn("Failed to parse LangChain4j interview observations: {}", e.getMessage());
            return List.of();
        }
    }

    /**
     * Question tag IDs may be formal catalog tag IDs or workflow-local
     * assessmentAbilityIds. The latter must be named from the frozen resume
     * claim group, never looked up as a system tag.
     */
    private String resolveAbilityName(EmpVideoInterviewSession session, Long tagId) {
        if (session != null && session.getWorkflowId() != null) {
            for (PersonAbilityClaimGroup group : claimGroupMapper.selectList(
                    Wrappers.<PersonAbilityClaimGroup>lambdaQuery()
                            .eq(PersonAbilityClaimGroup::getWorkflowId, session.getWorkflowId()))) {
                boolean matchesScopeId = tagId != null && (tagId.equals(group.getCanonicalTagId())
                        || tagId.equals(group.getAssessmentAbilityId()) || tagId.equals(group.getId()));
                if (matchesScopeId && group.getNormalizedAbilityName() != null
                        && !group.getNormalizedAbilityName().isBlank()) {
                    return group.getNormalizedAbilityName();
                }
            }
        }
        AbilityTag tag = abilityTagMapper.selectById(tagId);
        return tag != null ? tag.getTagName() : "能力#" + tagId;
    }

    private List<Long> getRelatedQuestionIdsByTagId(Long tagId, List<EmpVideoInterviewQuestion> questions) {
        List<Long> questionIds = new ArrayList<>();
        for (EmpVideoInterviewQuestion question : questions) {
            List<Long> questionTagIds = parseExpectedTagIds(question.getExpectedTagsJson());
            if (questionTagIds.contains(tagId)
                    && question.getAnswerTranscript() != null && !question.getAnswerTranscript().isBlank()
                    && !isNonEvidenceAnswer(question)) {
                questionIds.add(question.getId());
            }
        }
        return questionIds;
    }

    private List<String> getAnswerRefs(EmpVideoInterviewAbility ability, List<EmpVideoInterviewQuestion> questions) {
        return getAnswerRefs(ability.getTagId(), questions);
    }

    private List<String> getAnswerRefs(Long tagId, List<EmpVideoInterviewQuestion> questions) {
        List<String> answerRefs = new ArrayList<>();
        for (EmpVideoInterviewQuestion question : questions) {
            List<Long> questionTagIds = parseExpectedTagIds(question.getExpectedTagsJson());
            if (questionTagIds.contains(tagId) && question.getAnswerTranscript() != null
                    && !question.getAnswerTranscript().isBlank() && !isNonEvidenceAnswer(question)) {
                answerRefs.add(SourceRefConstants.factRef(SourceRefConstants.ENTITY_INTERVIEW_QUESTION, question.getId()));
            }
        }
        return answerRefs;
    }

    /** Post-analysis may only reference follow-ups that were actually asked and answered live. */
    private List<Long> findAnsweredFollowUpIds(Long sessionId, Long tagId) {
        List<InterviewFollowUpQuestion> answeredFollowUps = followUpQuestionMapper.selectList(
                Wrappers.<InterviewFollowUpQuestion>lambdaQuery()
                        .eq(InterviewFollowUpQuestion::getSessionId, sessionId)
                        .eq(InterviewFollowUpQuestion::getTargetAbilityTagId, tagId)
                        .eq(InterviewFollowUpQuestion::getFollowUpStatus, "ANSWERED")
                        .eq(InterviewFollowUpQuestion::getIsDeleted, 0)
                        .orderByAsc(InterviewFollowUpQuestion::getFollowUpOrder)
        );

        return answeredFollowUps.stream()
                .map(InterviewFollowUpQuestion::getId)
                .filter(Objects::nonNull)
                .toList();
    }

    private String buildEvidenceText(Long sessionId, EmpVideoInterviewAbility ability,
                                      List<EmpVideoInterviewQuestion> questions) {
        StringBuilder sb = new StringBuilder();

        if (ability.getEvidenceSummary() != null) {
            sb.append("证据摘要：").append(ability.getEvidenceSummary()).append("\n");
        }

        if (ability.getAnalysisComment() != null) {
            sb.append("分析评语：").append(ability.getAnalysisComment()).append("\n");
        }

        sb.append(buildQuestionEvidence(ability.getTagId(), questions));
        sb.append(buildFollowUpEvidence(sessionId, ability.getTagId()));
        return sb.toString();
    }

    /**
     * 从题目回答构造证据文本（无 EmpVideoInterviewAbility 时使用）
     */
    private String buildEvidenceTextByTagId(Long sessionId, Long tagId,
                                             List<EmpVideoInterviewQuestion> questions) {
        StringBuilder sb = new StringBuilder();
        sb.append(buildQuestionEvidence(tagId, questions));
        sb.append(buildFollowUpEvidence(sessionId, tagId));
        return sb.toString();
    }

    private String buildQuestionEvidence(Long tagId, List<EmpVideoInterviewQuestion> questions) {
        StringBuilder sb = new StringBuilder();
        for (EmpVideoInterviewQuestion question : questions) {
            List<Long> questionTagIds = parseExpectedTagIds(question.getExpectedTagsJson());
            if (questionTagIds.contains(tagId) && !isNonEvidenceAnswer(question)) {
                sb.append("问题").append(question.getQuestionOrder()).append("：").append(question.getQuestionText()).append("\n");
                if (question.getAnswerTranscript() != null) {
                    sb.append("回答：").append(question.getAnswerTranscript()).append("\n");
                }
            }
        }
        return sb.toString();
    }

    private String buildFollowUpEvidence(Long sessionId, Long tagId) {
        StringBuilder sb = new StringBuilder();
        List<InterviewFollowUpQuestion> answeredFollowUps = followUpQuestionMapper.selectList(
                Wrappers.<InterviewFollowUpQuestion>lambdaQuery()
                        .eq(InterviewFollowUpQuestion::getSessionId, sessionId)
                        .eq(InterviewFollowUpQuestion::getTargetAbilityTagId, tagId)
                        .eq(InterviewFollowUpQuestion::getFollowUpStatus, "ANSWERED")
                        .eq(InterviewFollowUpQuestion::getIsDeleted, 0)
                        .orderByAsc(InterviewFollowUpQuestion::getFollowUpOrder)
        );
        if (!answeredFollowUps.isEmpty()) {
            sb.append("\n=== 追问记录 ===\n");
            for (InterviewFollowUpQuestion fu : answeredFollowUps) {
                sb.append("追问：").append(fu.getQuestionText()).append("\n");
                if (fu.getAnswerText() != null && !fu.getAnswerText().isBlank()) {
                    sb.append("追问回答：").append(fu.getAnswerText()).append("\n");
                }
                if (fu.getAnswerQualityScore() != null) {
                    sb.append("追问回答质量：").append(fu.getAnswerQualityScore()).append("\n");
                }
            }
        }
        return sb.toString();
    }

    private List<String> buildRiskSignals(EmpVideoInterviewAbility ability) {
        List<String> risks = new ArrayList<>();

        if (ability.getConfidenceScore() == null) {
            risks.add("缺少置信度评分，建议人工复核");
        } else if (ability.getConfidenceScore().compareTo(BigDecimal.valueOf(50)) < 0) {
            risks.add("置信度较低，建议人工复核");
        }

        if (ability.getMasteryLevel() == null) {
            risks.add("缺少能力等级判断，不能直接用于画像融合");
        } else if (ability.getMasteryLevel() <= 2) {
            risks.add("能力等级较低，可能不满足岗位要求");
        }

        if (ability.getEvidenceSummary() == null || ability.getEvidenceSummary().isBlank()) {
            risks.add("缺少可追溯证据摘要");
        }

        if (ability.getAnalysisComment() == null || ability.getAnalysisComment().isBlank()) {
            risks.add("缺少分析说明");
        }

        return risks;
    }

    private List<Long> getRelatedQuestionIds(EmpVideoInterviewAbility ability, List<EmpVideoInterviewQuestion> questions) {
        List<Long> questionIds = new ArrayList<>();
        for (EmpVideoInterviewQuestion question : questions) {
            List<Long> questionTagIds = parseExpectedTagIds(question.getExpectedTagsJson());
            if (questionTagIds.contains(ability.getTagId())
                    && question.getAnswerTranscript() != null && !question.getAnswerTranscript().isBlank()
                    && !isNonEvidenceAnswer(question)) {
                questionIds.add(question.getId());
            }
        }
        return questionIds;
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

    private List<InterviewAbilityObservation> convertObservationsFromDto(InterviewObservationDTO dto,
                                                                          EmpVideoInterviewSession session,
                                                                          java.util.Set<Long> allowedTagIds,
                                                                          ObservationSnapshot snapshot) {
        if (dto.getObservations() == null || dto.getObservations().isEmpty()) {
            return List.of();
        }
        List<InterviewAbilityObservation> observations = new ArrayList<>();
        for (InterviewObservationDTO.Observation obs : dto.getObservations()) {
            if (obs.getTagId() == null || !allowedTagIds.contains(obs.getTagId())) {
                log.warn("观察 tagId 不在简历能力评估范围内，剔除: tagId={}, abilityName={}",
                        obs.getTagId(), obs.getAbilityName());
                continue;
            }
            validateObservationSourceRefs(obs, snapshot);
            InterviewAbilityObservation observation = new InterviewAbilityObservation();
            observation.setSessionId(session.getId());
            observation.setEmpId(session.getEmpId());
            observation.setPostId(session.getPostId());
            observation.setTagId(obs.getTagId());
            // The model can score only the supplied scope ID; the authoritative
            // display name comes from the frozen scope rather than model text.
            observation.setAbilityName(resolveAbilityName(session, obs.getTagId()));
            observation.setObservedLevel(obs.getObservedLevel());
            observation.setConfidenceScore(obs.getConfidenceScore() != null ? BigDecimal.valueOf(obs.getConfidenceScore()) : null);
            observation.setEvidenceText(obs.getEvidenceText());
            observations.add(observation);
        }
        return observations;
    }

    private void validateObservationSourceRefs(InterviewObservationDTO.Observation observation,
                                               ObservationSnapshot snapshot) {
        List<String> sourceRefs = observation.getSourceRefs();
        if (sourceRefs == null || sourceRefs.isEmpty()) {
            throw new BusinessException(400, "面试观察缺少回答证据引用");
        }
        Set<String> allowedAnswerRefs = new HashSet<>();
        for (EmpVideoInterviewQuestion question : snapshot.questions) {
            if (parseExpectedTagIds(question.getExpectedTagsJson()).contains(observation.getTagId())
                    && !isNonEvidenceAnswer(question)
                    && question.getAnswerTranscript() != null && !question.getAnswerTranscript().isBlank()) {
                allowedAnswerRefs.add(SourceRefConstants.factRef(
                        SourceRefConstants.ENTITY_INTERVIEW_QUESTION, question.getId()));
            }
        }
        for (InterviewFollowUpQuestion followUp : snapshot.followUps) {
            if (observation.getTagId().equals(followUp.getTargetAbilityTagId())
                    && "ANSWERED".equals(followUp.getFollowUpStatus())
                    && followUp.getAnswerText() != null && !followUp.getAnswerText().isBlank()) {
                allowedAnswerRefs.add(SourceRefConstants.factRef(
                        SourceRefConstants.ENTITY_INTERVIEW_FOLLOW_UP, followUp.getId()));
            }
        }
        if (allowedAnswerRefs.isEmpty() || !allowedAnswerRefs.containsAll(sourceRefs)) {
            throw new BusinessException(400, "面试观察引用了当前会话外或无回答的证据");
        }
    }
}
