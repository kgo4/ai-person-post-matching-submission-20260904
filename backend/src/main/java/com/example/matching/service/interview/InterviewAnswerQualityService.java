package com.example.matching.service.interview;

import com.example.matching.agent.dto.interview.InterviewAnswerQualityDTO;
import com.example.matching.agent.lc4j.InterviewAnswerQualityAiService;
import com.example.matching.agent.service.impl.AgentOutputValidator;
import com.example.matching.ai.service.PromptTemplateService;
import com.example.matching.ai.validation.InterviewAnswerQualityValidator;
import com.example.matching.ai.validation.AiOutputValidationException;
import com.example.matching.common.constant.SourceRefConstants;
import com.example.matching.common.exception.BusinessException;
import com.example.matching.common.exception.ErrorCodeEnum;
import com.example.matching.dto.interview.AnswerQualityEvaluation;
import com.example.matching.entity.employee.EmpVideoInterviewQuestion;
import com.example.matching.infrastructure.llm.EnterpriseChatLanguageModel;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 面试回答质量评估服务
 * <p>
 * 输入：主问题、学生回答、岗位能力标签、简历摘要、历史追问
 * 输出：结构化回答质量评估
 * 负责 STAR 完整性、模糊度、个人贡献、量化结果、逻辑风险判断
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class InterviewAnswerQualityService {

    private final EnterpriseChatLanguageModel enterpriseChatLanguageModel;
    private final PromptTemplateService promptTemplateService;
    private final ObjectMapper objectMapper;
    private final ObjectProvider<InterviewAnswerQualityAiService> interviewAnswerQualityAiServiceProvider;
    private final InterviewAnswerQualityValidator answerQualityValidator;
    private final AgentOutputValidator agentOutputValidator;
    private final com.example.matching.infrastructure.llm.LlmResponseParser llmResponseParser;

    private static final int QUALITY_THRESHOLD = 65;

    /**
     * 评估主问题回答质量
     */
    public AnswerQualityEvaluation evaluate(Long sessionId,
                                             EmpVideoInterviewQuestion question,
                                             String answerText,
                                             String abilityName,
                                             String abilityRequirement,
                                             String resumeClaim) {
        // 1. 规则层快速判断
        AnswerQualityEvaluation ruleResult = applyRules(answerText, abilityName);
        if (ruleResult != null) {
            log.info("规则层判定不需要追问，sessionId={}, questionId={}", sessionId, question.getId());
            return withSourceRefs(ruleResult, sessionId, question);
        }

        // 2. LLM 层评估
        try {
            return validateWithServerSourceRefs(
                    evaluateWithLlm(sessionId, question, answerText, abilityName, abilityRequirement, resumeClaim),
                    sessionId, question, answerText);
        } catch (Exception e) {
            log.warn("LLM 回答质量评估失败，使用默认评估: {}", e.getMessage());
            return withSourceRefs(buildDefaultEvaluation(answerText), sessionId, question);
        }
    }

    /**
     * 先由服务端补全 sourceRefs，再执行校验；校验失败时降级为确定性默认评估。
     * sourceRefs 必须只由服务端生成，不能采用 LLM 输出，否则合法结果会因空引用被误降级。
     */
    private AnswerQualityEvaluation validateWithServerSourceRefs(AnswerQualityEvaluation evaluation,
                                                                  Long sessionId,
                                                                  EmpVideoInterviewQuestion question,
                                                                  String answerText) {
        AnswerQualityEvaluation withRefs = withSourceRefs(evaluation, sessionId, question);
        try {
            answerQualityValidator.validate(withRefs, sessionId, question.getId());
            return withRefs;
        } catch (AiOutputValidationException e) {
            log.warn("[AI_OUTPUT_INVALID] 回答质量评估不合法，使用确定性降级: sessionId={}, questionId={}, reason={}",
                    sessionId, question.getId(), e.getMessage());
            return withSourceRefs(buildDefaultEvaluation(answerText), sessionId, question);
        }
    }

    /**
     * 评估追问回答质量
     */
    public AnswerQualityEvaluation evaluateFollowUp(Long sessionId, EmpVideoInterviewQuestion question,
                                                      String answerText, String abilityName, String abilityRequirement) {
        try {
            return evaluate(sessionId, question, answerText, abilityName, abilityRequirement, null);
        } catch (Exception e) {
            log.warn("LLM 追问回答评估失败，使用规则兜底: {}", e.getMessage());
            return withSourceRefs(evaluateWithRules(answerText), sessionId, question);
        }
    }

    private AnswerQualityEvaluation evaluateWithRules(String answerText) {
        if (answerText == null || answerText.isBlank()) {
            return new AnswerQualityEvaluation(
                    new AnswerQualityEvaluation.StarCompleteness(false, false, false, false),
                    20, 20, 20, 20,
                    false, "追问无回答", null, null,
                    List.of("无回答"), List.of(), "追问未回答"
            );
        }

        int wordCount = answerText.length();
        boolean hasSpecificDetail = answerText.matches(".*\\d+.*");
        boolean hasPersonal = answerText.contains("我");

        int specificity = wordCount > 100 ? 70 : (wordCount > 50 ? 50 : 30);
        int evidence = hasSpecificDetail ? 70 : 40;
        int contribution = hasPersonal ? 65 : 35;
        int logic = 65;

        boolean needFollowUp = (specificity + evidence + contribution + logic) / 4 < QUALITY_THRESHOLD;

        return new AnswerQualityEvaluation(
                new AnswerQualityEvaluation.StarCompleteness(true, true, true, hasSpecificDetail),
                specificity, evidence, contribution, logic,
                needFollowUp,
                needFollowUp ? "追问回答仍不够具体" : null,
                needFollowUp ? "detail" : null,
                needFollowUp ? "STAR_MISSING" : null,
                needFollowUp ? List.of("缺少量化数据") : List.of(),
                List.of(),
                needFollowUp ? "追问回答质量不足" : "追问回答质量可接受"
        );
    }

    /**
     * 规则层快速判断
     *
     * @return 如果规则层能判定不需要追问返回评估结果，否则返回 null 表示需要 LLM 评估
     */
    private AnswerQualityEvaluation applyRules(String answerText, String abilityName) {
        if (answerText == null || answerText.isBlank()) {
            return new AnswerQualityEvaluation(
                    new AnswerQualityEvaluation.StarCompleteness(false, false, false, false),
                    0, 0, 0, 0,
                    false, "无回答", null, null,
                    List.of("无回答"), List.of(), "候选人未提供回答"
            );
        }

        String trimmed = answerText.trim();
        int wordCount = trimmed.length();

        // 规则 1：明确否认简历、无经验或不知道，不能产生能力证据，也不能继续追问。
        String[] denialPhrases = {"造假", "假的", "骗你的", "我骗", "不会", "什么都不会",
                "不知道", "没做过", "不记得", "没有经验", "不了解", "不清楚", "没接触过"};
        for (String phrase : denialPhrases) {
            if (trimmed.contains(phrase)) {
                return new AnswerQualityEvaluation(
                        new AnswerQualityEvaluation.StarCompleteness(false, false, false, false),
                        0, 0, 0, 0,
                        false, "候选人明确否认或不具备相关经历", null, null,
                        List.of("未提供可核验的能力证据"), List.of(), "候选人明确否认简历中的相关经历，未形成能力证据"
                );
            }
        }

        // 规则 2：明显无关的寒暄/外貌等短句不属于能力核验回答，不追问。
        if (isClearlyIrrelevant(trimmed)) {
            return new AnswerQualityEvaluation(
                    new AnswerQualityEvaluation.StarCompleteness(false, false, false, false),
                    0, 0, 0, 0,
                    false, "回答与本题核验能力无关", null, null,
                    List.of("未提供与本题能力相关的事实"), List.of(), "回答与本题核验能力无关，未形成能力证据"
            );
        }

        // 规则 3：回答过短时，只有明确提到本题能力或实施动作才允许一次补证据追问。
        if (wordCount < 30) {
            if (!isPlausiblyRelevantShortAnswer(trimmed, abilityName)) {
                return new AnswerQualityEvaluation(
                        new AnswerQualityEvaluation.StarCompleteness(false, false, false, false),
                        0, 0, 0, 0,
                        false, "回答未涉及本题核验能力", null, null,
                        List.of("未提供与本题能力相关的事实"), List.of(), "回答未涉及本题核验能力，未形成能力证据"
                );
            }
            return new AnswerQualityEvaluation(
                    new AnswerQualityEvaluation.StarCompleteness(false, false, false, false),
                    30, 25, 30, 50,
                    true, "回答过短，缺少具体内容", "detail", "STAR_MISSING",
                    List.of("缺少详细描述"), List.of(), "回答过短"
            );
        }

        // 规则 4：30-80 字且无实质内容 → 可能是敷衍回答，标记追问
        if (wordCount <= 80 && !hasSubstance(trimmed)) {
            return new AnswerQualityEvaluation(
                    new AnswerQualityEvaluation.StarCompleteness(false, false, false, false),
                    35, 30, 35, 50,
                    true, "回答过于简略且缺乏实质性内容", "detail", "PERSONAL_CONTRIBUTION",
                    List.of("缺少具体事例或数据支撑"), List.of(), "回答过于简略"
            );
        }

        // 返回 null 表示需要 LLM 进一步评估
        return null;
    }

    /**
     * LLM 层评估
     */
    private AnswerQualityEvaluation evaluateWithLlm(Long sessionId, EmpVideoInterviewQuestion question,
                                                     String answerText,
                                                     String abilityName,
                                                     String abilityRequirement,
                                                     String resumeClaim) throws Exception {
        Map<String, Object> model = new HashMap<>();
        model.put("abilityName", abilityName != null ? abilityName : "未知能力");
        model.put("abilityRequirement", abilityRequirement != null ? abilityRequirement : "无明确要求");
        model.put("questionText", question.getQuestionText());
        model.put("questionId", question.getId());
        model.put("sessionId", sessionId);
        model.put("answerText", answerText);
        model.put("resumeClaim", resumeClaim);
        model.put("sessionId", sessionId);
        model.put("questionId", question.getId());

        // LangChain4j 优先调用
        InterviewAnswerQualityAiService aiService = interviewAnswerQualityAiServiceProvider.getIfAvailable();
        if (aiService != null) {
            String context = objectMapper.writeValueAsString(model);
            InterviewAnswerQualityDTO qualityDto = com.example.matching.agent.config.AgentToolProvider
                    .withScope(() -> aiService.evaluate(sessionId, context));
            if (qualityDto == null) {
                throw new BusinessException(ErrorCodeEnum.AI_SERVICE_ERROR, "LLM 返回空响应");
            }
            agentOutputValidator.validateOrThrow(qualityDto, "INTERVIEW_ANSWER_QUALITY");
            return convertFromDto(qualityDto);
        } else {
            // 统一走企业全局模型门面（不再回退到任何硬编码厂商模型）
            String prompt = promptTemplateService.render("interview-answer-quality-prompt", model);
            String response = enterpriseChatLanguageModel.chat(prompt);
            if (response == null || response.isBlank()) {
                throw new BusinessException(ErrorCodeEnum.AI_SERVICE_ERROR, "LLM 返回空响应");
            }
            return parseEvaluation(response);
        }
    }

    private AnswerQualityEvaluation parseEvaluation(String response) {
        try {
            String json = llmResponseParser.extractJson(response);
            Map<String, Object> map = objectMapper.readValue(json, Map.class);

            Map<String, Boolean> starMap = (Map<String, Boolean>) map.get("starCompleteness");
            AnswerQualityEvaluation.StarCompleteness star = new AnswerQualityEvaluation.StarCompleteness(
                    getBool(starMap, "situation"),
                    getBool(starMap, "task"),
                    getBool(starMap, "action"),
                    getBool(starMap, "result")
            );

            return new AnswerQualityEvaluation(
                    star,
                    getInt(map, "specificityScore"),
                    getInt(map, "evidenceScore"),
                    getInt(map, "personalContributionScore"),
                    getInt(map, "logicConsistencyScore"),
                    getBool(map, "needFollowUp"),
                    (String) map.get("followUpReason"),
                    (String) map.get("targetDimension"),
                    (String) map.get("suggestedFollowUpType"),
                    getStringList(map, "missingEvidence"),
                    getStringList(map, "logicRisks"),
                    (String) map.get("conclusion")
            );
        } catch (Exception e) {
            log.warn("解析评估 JSON 失败: {}", e.getMessage());
            return buildDefaultEvaluation(null);
        }
    }

    /**
     * 构建默认评估（LLM 调用失败时使用）
     */
    private AnswerQualityEvaluation buildDefaultEvaluation(String answerText) {
        return new AnswerQualityEvaluation(
                new AnswerQualityEvaluation.StarCompleteness(false, false, false, false),
                0, 0, 0, 0,
                false, "回答质量服务不可用，不能自动追问", null, null,
                List.of("自动评分不可用"), List.of(), "回答质量服务不可用，未形成可自动采纳的能力证据"
        );
    }

    private AnswerQualityEvaluation withSourceRefs(AnswerQualityEvaluation evaluation,
                                                    Long sessionId,
                                                    EmpVideoInterviewQuestion question) {
        List<String> sourceRefs = new ArrayList<>(2);
        if (sessionId != null) {
            sourceRefs.add(SourceRefConstants.factRef(SourceRefConstants.ENTITY_INTERVIEW_SESSION, sessionId));
        }
        if (question != null && question.getId() != null) {
            sourceRefs.add(SourceRefConstants.factRef(
                    SourceRefConstants.ENTITY_INTERVIEW_QUESTION, question.getId()));
        }
        return new AnswerQualityEvaluation(
                evaluation.starCompleteness(),
                evaluation.specificityScore(),
                evaluation.evidenceScore(),
                evaluation.personalContributionScore(),
                evaluation.logicConsistencyScore(),
                evaluation.needFollowUp(),
                evaluation.followUpReason(),
                evaluation.targetDimension(),
                evaluation.suggestedFollowUpType(),
                evaluation.missingEvidence(),
                evaluation.logicRisks(),
                evaluation.conclusion(),
                List.copyOf(sourceRefs));
    }

    @SuppressWarnings("unchecked")
    private List<String> getStringList(Map<String, Object> map, String key) {
        Object val = map.get(key);
        if (val instanceof List<?> list) {
            return list.stream().map(Object::toString).toList();
        }
        return List.of();
    }

    private Boolean getBool(Map<String, ?> map, String key) {
        Object val = map.get(key);
        if (val instanceof Boolean b) return b;
        return null;
    }

    private Integer getInt(Map<String, Object> map, String key) {
        Object val = map.get(key);
        if (val instanceof Number n) return n.intValue();
        return null;
    }

    /**
     * 检测回答是否有实质内容：必须有个人实施动作和可核验细节。
     */
    private boolean hasSubstance(String text) {
        boolean hasPersonal = text.contains("我");
        boolean hasNumber = text.matches(".*\\d+.*");
        boolean hasTechnology = text.matches(".*[A-Z][A-Za-z0-9.+#-]{1,}.*");
        boolean hasAction = text.contains("实现") || text.contains("设计") || text.contains("开发")
                || text.contains("排查") || text.contains("优化") || text.contains("上线") || text.contains("负责");
        return hasPersonal && hasAction && (hasNumber || hasTechnology || text.contains("具体"));
    }

    private boolean isClearlyIrrelevant(String text) {
        return text.contains("长得帅") || text.contains("漂亮") || text.contains("好看")
                || text.contains("天气") || text.contains("吃饭") || text.contains("唱歌");
    }

    private boolean isPlausiblyRelevantShortAnswer(String text, String abilityName) {
        if (abilityName != null && !abilityName.isBlank() && text.contains(abilityName)) {
            return true;
        }
        return text.contains("实现") || text.contains("开发") || text.contains("设计")
                || text.contains("接口") || text.contains("数据库") || text.contains("部署");
    }

    private AnswerQualityEvaluation convertFromDto(InterviewAnswerQualityDTO dto) {
        InterviewAnswerQualityDTO.StarCompleteness star = dto.getStarCompleteness();
        return new AnswerQualityEvaluation(
                new AnswerQualityEvaluation.StarCompleteness(
                        star != null && Boolean.TRUE.equals(star.getSituation()),
                        star != null && Boolean.TRUE.equals(star.getTask()),
                        star != null && Boolean.TRUE.equals(star.getAction()),
                        star != null && Boolean.TRUE.equals(star.getResult())
                ),
                dto.getSpecificityScore(),
                dto.getEvidenceScore(),
                dto.getPersonalContributionScore(),
                dto.getLogicConsistencyScore(),
                dto.getNeedFollowUp(),
                dto.getFollowUpReason(),
                dto.getTargetDimension(),
                dto.getSuggestedFollowUpType(),
                dto.getMissingEvidence() != null ? dto.getMissingEvidence() : List.of(),
                dto.getLogicRisks() != null ? dto.getLogicRisks() : List.of(),
                dto.getConclusion()
        );
    }
}
