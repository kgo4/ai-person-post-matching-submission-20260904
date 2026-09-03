package com.example.matching.service.interview;

import com.example.matching.agent.dto.interview.InterviewFollowUpQuestionDTO;
import com.example.matching.agent.lc4j.InterviewFollowUpAiService;
import com.example.matching.agent.service.impl.AgentOutputValidator;
import com.example.matching.ai.service.PromptTemplateService;
import com.example.matching.ai.validation.AiOutputValidationException;
import com.example.matching.ai.validation.InterviewFollowUpValidator;
import com.example.matching.dto.interview.AnswerQualityEvaluation;
import com.example.matching.common.exception.BusinessException;
import com.example.matching.common.exception.ErrorCodeEnum;
import com.example.matching.dto.interview.FollowUpDecision;
import com.example.matching.entity.employee.EmpVideoInterviewQuestion;
import com.example.matching.entity.employee.EmpVideoInterviewSession;
import com.example.matching.entity.interview.InterviewFollowUpQuestion;
import com.example.matching.infrastructure.llm.EnterpriseChatLanguageModel;
import com.example.matching.mapper.employee.EmpVideoInterviewSessionMapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 追问生成服务
 * <p>
 * 输入：追问决策、原问题、学生回答、岗位能力标签、简历声明
 * 输出：一句自然追问
 * 只负责生成文本，不负责决定是否追问。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class InterviewFollowUpGenerationService {

    private final EnterpriseChatLanguageModel enterpriseChatLanguageModel;
    private final PromptTemplateService promptTemplateService;
    private final ObjectMapper objectMapper;
    private final ObjectProvider<InterviewFollowUpAiService> interviewFollowUpAiServiceProvider;
    private final InterviewFollowUpValidator followUpValidator;
    private final EmpVideoInterviewSessionMapper sessionMapper;
    private final AgentOutputValidator agentOutputValidator;
    private final com.example.matching.infrastructure.llm.LlmResponseParser llmResponseParser;

    /**
     * 生成追问
     *
     * @param decision          追问决策
     * @param originalQuestion  原问题
     * @param answerText        学生回答
     * @param abilityName       能力名称
     * @param abilityRequirement 岗位要求
     * @param resumeClaim       简历声明
     * @param evaluation        回答质量评估
     * @return 追问记录（未保存到数据库）
     */
    public InterviewFollowUpQuestion generate(FollowUpDecision decision,
                                               EmpVideoInterviewQuestion originalQuestion,
                                               String answerText,
                                               String abilityName,
                                               String abilityRequirement,
                                               String resumeClaim,
                                               AnswerQualityEvaluation evaluation,
                                               List<InterviewFollowUpQuestion> existingFollowUps) {
        try {
            int existingCount = existingFollowUps != null ? existingFollowUps.size() : 0;
            // 追问数量不得超过策略限制（与 InterviewFollowUpPolicyService 动态上限一致）
            followUpValidator.validateFollowUpCount(existingCount);
            return generateWithLlm(decision, originalQuestion, answerText,
                    abilityName, abilityRequirement, resumeClaim, evaluation, existingFollowUps, existingCount);
        } catch (Exception e) {
            log.warn("LLM 追问生成失败，使用规则兜底: {}", e.getMessage());
            return generateWithRules(decision, originalQuestion, abilityName, resumeClaim);
        }
    }

    /**
     * LLM 生成追问
     */
    private InterviewFollowUpQuestion generateWithLlm(FollowUpDecision decision,
                                                       EmpVideoInterviewQuestion originalQuestion,
                                                       String answerText,
                                                       String abilityName,
                                                       String abilityRequirement,
                                                       String resumeClaim,
                                                       AnswerQualityEvaluation evaluation,
                                                       List<InterviewFollowUpQuestion> existingFollowUps,
                                                       int existingCount) throws Exception {
        Map<String, Object> model = new HashMap<>();
        model.put("abilityName", abilityName != null ? abilityName : "通用能力");
        model.put("abilityRequirement", abilityRequirement != null ? abilityRequirement : "无特定要求");
        model.put("resumeClaim", resumeClaim != null && !resumeClaim.isBlank()
                ? resumeClaim : "当前题目没有可用的简历项目声明");
        model.put("questionText", originalQuestion.getQuestionText());
        model.put("answerText", answerText != null ? answerText : "（无回答）");
        model.put("evaluationJson", evaluation != null ? toJson(evaluation) : "{}");
        model.put("targetDimension", decision.targetDimension() != null ? decision.targetDimension() : "detail");
        model.put("followUpType", decision.followUpType() != null ? decision.followUpType() : "STAR_MISSING");
        model.put("existingFollowUps", existingFollowUps != null ? existingFollowUps : List.of());

        // LangChain4j 优先调用
        InterviewFollowUpAiService aiService = interviewFollowUpAiServiceProvider.getIfAvailable();
        if (aiService != null) {
            String context = objectMapper.writeValueAsString(model);
            InterviewFollowUpQuestionDTO followUpDto = com.example.matching.agent.config.AgentToolProvider
                    .withScope(() -> aiService.generate(originalQuestion.getSessionId(), context));
            if (followUpDto == null) {
                throw new BusinessException(ErrorCodeEnum.AI_SERVICE_ERROR, "LLM 返回空响应");
            }
            agentOutputValidator.validateOrThrow(followUpDto, "INTERVIEW_FOLLOW_UP");
            return convertFollowUpFromDto(followUpDto, decision, originalQuestion, existingCount, resumeClaim);
        } else {
            // 统一走企业全局模型门面（不再回退到任何硬编码厂商模型）
            String prompt = promptTemplateService.render("interview-follow-up-generation", model);
            String response = enterpriseChatLanguageModel.chat(prompt);
            if (response == null || response.isBlank()) {
                throw new BusinessException(ErrorCodeEnum.AI_SERVICE_ERROR, "LLM 返回空响应");
            }
            return parseFollowUp(response, decision, originalQuestion, existingCount, resumeClaim);
        }
    }

    private InterviewFollowUpQuestion parseFollowUp(String response, FollowUpDecision decision,
                                                      EmpVideoInterviewQuestion originalQuestion,
                                                      int existingCount,
                                                      String resumeClaim) {
        try {
            String json = llmResponseParser.extractJson(response);
            Map<String, Object> map = objectMapper.readValue(json, Map.class);

            InterviewFollowUpQuestion followUp = new InterviewFollowUpQuestion();
            followUp.setQuestionText((String) map.get("questionText"));
            followUp.setTriggerReason(serverTriggerReason(decision));
            followUp.setExpectedEvidenceType((String) map.get("expectedEvidenceType"));
            followUp.setTargetDimension(decision.targetDimension());
            followUp.setFollowUpType(decision.followUpType());
            followUp.setFollowUpStatus("SUGGESTED");

            // 校验 + 服务端上下文回填：sessionId、原题ID、员工ID不信任模型输出
            backfillServerContext(followUp, originalQuestion);
            try {
                followUpValidator.validateAndBackfill(followUp, originalQuestion, existingCount);
            } catch (AiOutputValidationException e) {
                log.warn("[AI_OUTPUT_INVALID] 追问内容不合法，使用规则兜底: {}", e.getMessage());
                return buildFallbackFollowUp(decision, originalQuestion, null, resumeClaim);
            }
            setTargetAbilityTagId(followUp, originalQuestion);

            return followUp;
        } catch (Exception e) {
            log.warn("解析追问 JSON 失败: {}", e.getMessage());
            return buildFallbackFollowUp(decision, originalQuestion, null, resumeClaim);
        }
    }

    /**
     * 从服务端上下文回填 sessionId、原题 ID、员工 ID，不信任模型输出
     */
    private void backfillServerContext(InterviewFollowUpQuestion followUp,
                                       EmpVideoInterviewQuestion originalQuestion) {
        followUp.setParentQuestionId(originalQuestion.getId());
        followUp.setSessionId(originalQuestion.getSessionId());
        if (originalQuestion.getSessionId() != null) {
            try {
                EmpVideoInterviewSession session = sessionMapper.selectById(originalQuestion.getSessionId());
                if (session != null && session.getEmpId() != null) {
                    followUp.setCreatedBy(session.getEmpId());
                }
            } catch (Exception e) {
                log.debug("回填追问员工ID失败，忽略: {}", e.getMessage());
            }
        }
    }

    /**
     * 规则兜底生成追问
     */
    private InterviewFollowUpQuestion generateWithRules(FollowUpDecision decision,
                                                         EmpVideoInterviewQuestion originalQuestion,
                                                         String abilityName,
                                                         String resumeClaim) {
        return buildFallbackFollowUp(decision, originalQuestion, abilityName, resumeClaim);
    }

    /**
     * 构建兜底追问
     */
    private InterviewFollowUpQuestion buildFallbackFollowUp(FollowUpDecision decision,
                                                              EmpVideoInterviewQuestion originalQuestion,
                                                              String abilityName,
                                                              String resumeClaim) {
        InterviewFollowUpQuestion followUp = new InterviewFollowUpQuestion();
        followUp.setParentQuestionId(originalQuestion.getId());
        followUp.setQuestionText(buildFallbackQuestion(decision, abilityName, resumeClaim));
        followUp.setTriggerReason(decision.followUpType());
        followUp.setExpectedEvidenceType("PROJECT_DETAIL");
        followUp.setTargetDimension(decision.targetDimension());
        followUp.setFollowUpType(decision.followUpType());
        followUp.setFollowUpStatus("SUGGESTED");
        setTargetAbilityTagId(followUp, originalQuestion);
        return followUp;
    }

    /**
     * 设置目标能力标签ID
     */
    private void setTargetAbilityTagId(InterviewFollowUpQuestion followUp,
                                         EmpVideoInterviewQuestion originalQuestion) {
        String tagsJson = originalQuestion.getExpectedTagsJson();
        if (tagsJson != null && !tagsJson.isBlank()) {
            try {
                java.util.List<?> tagIds = objectMapper.readValue(tagsJson, java.util.List.class);
                if (!tagIds.isEmpty()) {
                    Object firstTag = tagIds.get(0);
                    if (firstTag instanceof Number n) {
                        followUp.setTargetAbilityTagId(n.longValue());
                    }
                }
            } catch (Exception exception) {
                log.debug("解析目标能力标签失败，忽略该标签", exception);
            }
        }
    }

    /**
     * 构建兜底追问文本
     */
    private String buildFallbackQuestion(FollowUpDecision decision, String abilityName, String resumeClaim) {
        String ability = abilityName != null ? abilityName : "该能力";
        String type = decision.followUpType();
        boolean anchored = resumeClaim != null && !resumeClaim.isBlank();

        if ("STAR_MISSING".equals(type)) {
            return String.format("请补充说明你在「%s」方面的具体行动和最终结果。", ability);
        } else if ("PERSONAL_CONTRIBUTION".equals(type)) {
            return String.format("请具体说明你在「%s」相关项目中个人负责的部分和具体贡献。", ability);
        } else if ("RESUME_VERIFICATION".equals(type)) {
            return anchored
                    ? String.format("你的简历中记录了相关经历，请核实你在「%s」方面遇到的最大挑战及解决过程。", ability)
                    : String.format("请具体说明你在「%s」方面的一次真实经历、遇到的挑战及解决过程。", ability);
        }
        return String.format("请进一步详细说明你在「%s」方面的具体经验。", ability);
    }

    private String toJson(Object obj) {
        try {
            return objectMapper.writeValueAsString(obj);
        } catch (Exception e) {
            log.debug("序列化追问内容失败", e);
            return "{}";
        }
    }

    private InterviewFollowUpQuestion convertFollowUpFromDto(InterviewFollowUpQuestionDTO dto,
                                                               FollowUpDecision decision,
                                                               EmpVideoInterviewQuestion originalQuestion,
                                                               int existingCount,
                                                               String resumeClaim) {
        InterviewFollowUpQuestion followUp = new InterviewFollowUpQuestion();
        followUp.setQuestionText(dto.getQuestionText());
        followUp.setTriggerReason(serverTriggerReason(decision));
        followUp.setExpectedEvidenceType(dto.getExpectedEvidenceType());
        followUp.setTargetDimension(decision.targetDimension());
        followUp.setFollowUpType(decision.followUpType());
        followUp.setFollowUpStatus("SUGGESTED");

        backfillServerContext(followUp, originalQuestion);
        try {
            followUpValidator.validateAndBackfill(followUp, originalQuestion, existingCount);
        } catch (AiOutputValidationException e) {
            log.warn("[AI_OUTPUT_INVALID] 追问内容不合法，使用规则兜底: {}", e.getMessage());
            return buildFallbackFollowUp(decision, originalQuestion, null, resumeClaim);
        }
        setTargetAbilityTagId(followUp, originalQuestion);

        return followUp;
    }

    /** Decision metadata is policy-owned, never supplied by the LLM. */
    private String serverTriggerReason(FollowUpDecision decision) {
        String type = decision.followUpType() != null ? decision.followUpType() : "STAR_MISSING";
        String dimension = decision.targetDimension() != null ? decision.targetDimension() : "detail";
        return type + ":" + dimension;
    }
}
