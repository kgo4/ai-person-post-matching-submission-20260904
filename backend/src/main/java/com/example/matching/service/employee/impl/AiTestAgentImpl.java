package com.example.matching.service.employee.impl;

import com.example.matching.agent.dto.interview.AiTestEvaluationResultDTO;
import com.example.matching.agent.dto.interview.AiTestQuestionItem;
import com.example.matching.agent.dto.interview.AiTestQuestionSetDTO;
import com.example.matching.agent.lc4j.AiTestAiService;
import com.example.matching.agent.service.impl.AgentOutputValidator;
import com.example.matching.ai.service.LangChain4jChatService;
import com.example.matching.ai.service.PromptTemplateService;
import com.example.matching.ai.validation.AiTestQuestionSetValidator;
import com.example.matching.ai.validation.AiOutputValidationException;
import com.example.matching.ai.validation.DeterministicAiFallbacks;
import com.example.matching.infrastructure.llm.LlmResponseParser;
import com.example.matching.infrastructure.llm.ModelResponseParseException;
import com.example.matching.resilience.AiServiceResilience;
import com.example.matching.service.employee.AiTestAgent;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class AiTestAgentImpl implements AiTestAgent {

    /** A single test task must not occupy an AI worker for minutes. */
    private static final long MAX_TEST_REQUEST_TIMEOUT_SECONDS = 60L;

    private final LangChain4jChatService langChain4jChatService;
    private final PromptTemplateService promptTemplateService;
    private final AiServiceResilience aiServiceResilience;
    private final ObjectMapper objectMapper;
    private final LlmResponseParser llmResponseParser;
    private final ObjectProvider<AiTestAiService> aiTestAiServiceProvider;
    private final AiTestQuestionSetValidator questionSetValidator;
    private final AgentOutputValidator agentOutputValidator;

    @org.springframework.beans.factory.annotation.Autowired(required = false)
    private com.example.matching.service.system.SystemAiModelConfigService systemAiModelConfigService;

    @org.springframework.beans.factory.annotation.Value("${ai.test.request-timeout-seconds:300}")
    private long requestTimeoutSeconds = 300L;

    @Override
    public String generateQuestions(AiTestQuestionRequest request) {
        try {
            long effectiveTimeoutSeconds = Math.min(
                    Math.max(1L, requestTimeoutSeconds), MAX_TEST_REQUEST_TIMEOUT_SECONDS);
            int questionCount = systemAiModelConfigService != null
                    ? systemAiModelConfigService.getTestQuestionCount()
                    : 5;
            Map<String, Object> dataModel = new HashMap<>();
            dataModel.put("questionCount", questionCount);
            dataModel.put("minimumAbilityTagsPerQuestion",
                    minimumAbilityTagsPerQuestion(request.resumeClaims(), questionCount));
            if (request.resumeClaims() != null && !request.resumeClaims().isBlank()) {
                dataModel.put("resumeClaims", request.resumeClaims());
            }
            if (request.scopeJson() != null && !request.scopeJson().isBlank()) {
                dataModel.put("assessmentScope", request.scopeJson());
            }
            if (request.blueprintJson() != null && !request.blueprintJson().isBlank()) {
                dataModel.put("assessmentBlueprint", request.blueprintJson());
            }
            if (request.postName() != null) {
                dataModel.put("postName", request.postName());
                dataModel.put("jobDescription", request.jobDescription());
                dataModel.put("abilities", request.abilities());
            } else {
                dataModel.put("abilityTagName", request.abilityTagName());
                dataModel.put("abilityTagCategory", request.abilityTagCategory());
                dataModel.put("abilityTagDescription", request.abilityTagDescription());
            }

            // LangChain4j 优先调用
            AiTestAiService aiService = aiTestAiServiceProvider.getIfAvailable();
            if (aiService != null) {
                String context = objectMapper.writeValueAsString(dataModel);
                String questionSetJson = aiServiceResilience.callWithResilience(
                        "ai-test-generate-lc4j",
                        () -> {
                            AiTestQuestionSetDTO questionSet = com.example.matching.agent.config.AgentToolProvider
                                    .withScope(() -> aiService.generateQuestions(context, questionCount));
                            List<AiTestQuestionItem> items = questionSet == null ? null : questionSet.getQuestions();
                            agentOutputValidator.validateOrThrow(items, "AI_TEST_QUESTION");
                            try {
                                return objectMapper.writeValueAsString(items);
                            } catch (Exception e) {
                                throw new RuntimeException(e);
                            }
                        },
                        DeterministicAiFallbacks.AI_TEST_QUESTIONS,
                        effectiveTimeoutSeconds
                );
                return normalizeQuestions(llmResponseParser.extractJson(questionSetJson));
            }

            // Fallback: LangChain4j + FreeMarker
            String prompt = promptTemplateService.render("ai-test-prompt", dataModel);
            String aiResponse = langChain4jChatService.chat("ai-test-generate", prompt,
                    this::buildFallbackQuestions, effectiveTimeoutSeconds);

            return normalizeQuestions(llmResponseParser.extractJson(aiResponse));
        } catch (Exception e) {
            log.error("AI test question generation failed", e);
            return DeterministicAiFallbacks.get(DeterministicAiFallbacks.AI_TEST_QUESTIONS).get();
        }
    }

    private String normalizeQuestions(String questionsJson) throws Exception {
        String normalizedJson = questionsJson.trim();
        if (normalizedJson.startsWith("{")) {
            normalizedJson = "[" + normalizedJson + "]";
        }

        List<Map<String, Object>> generatedQuestions = objectMapper.readValue(
                normalizedJson, new TypeReference<List<Map<String, Object>>>() {});
        List<Map<String, Object>> normalizedQuestions = new ArrayList<>();
        for (int index = 0; index < generatedQuestions.size(); index++) {
            Map<String, Object> generated = generatedQuestions.get(index);
            Map<String, Object> question = new LinkedHashMap<>();
            question.put("id", index + 1);
            question.put("question", String.valueOf(generated.getOrDefault("question", "")));
            question.put("type", normalizeQuestionType(String.valueOf(generated.getOrDefault("type", "SHORT_ANSWER"))));
            question.put("difficulty", normalizeDifficulty(String.valueOf(generated.getOrDefault("difficulty", "MEDIUM"))));
            question.put("options", generated.getOrDefault("options", List.of()));
            question.put("referenceAnswer", generated.getOrDefault("referenceAnswer", generated.getOrDefault("answer", "")));
            question.put("answer", generated.getOrDefault("answer", ""));
            question.put("score", generated.getOrDefault("score", 10));
            if (generated.containsKey("tagId")) {
                question.put("tagId", generated.get("tagId"));
            }
            if (generated.containsKey("sourceRefs")) {
                question.put("sourceRefs", generated.get("sourceRefs"));
            }
            // 评估范围绑定字段（见实施计划 §1.2）：binding 校验依赖这些字段
            if (generated.containsKey("abilityTagId")) {
                question.put("abilityTagId", generated.get("abilityTagId"));
            } else if (generated.containsKey("tagId")) {
                question.put("abilityTagId", generated.get("tagId"));
            }
            if (generated.containsKey("assessmentAbilityId")) {
                question.put("assessmentAbilityId", generated.get("assessmentAbilityId"));
            } else if (question.containsKey("abilityTagId")) {
                question.put("assessmentAbilityId", question.get("abilityTagId"));
            }
            if (generated.containsKey("abilityTagIds")) {
                question.put("abilityTagIds", generated.get("abilityTagIds"));
            }
            if (generated.containsKey("verificationBindings")) {
                question.put("verificationBindings", generated.get("verificationBindings"));
            }
            if (generated.containsKey("postRequirementId")) {
                question.put("postRequirementId", generated.get("postRequirementId"));
            }
            if (generated.containsKey("sourceClaimIds")) {
                question.put("sourceClaimIds", generated.get("sourceClaimIds"));
            }
            if (generated.containsKey("sourceEvidenceRefs")) {
                question.put("sourceEvidenceRefs", generated.get("sourceEvidenceRefs"));
            }
            if (generated.containsKey("verificationType")) {
                question.put("verificationType", generated.get("verificationType"));
            }
            if (generated.containsKey("targetLevel")) {
                question.put("targetLevel", generated.get("targetLevel"));
            }
            if (generated.containsKey("scoringRubric")) {
                question.put("scoringRubric", generated.get("scoringRubric"));
            }
            if (generated.containsKey("questionId")) {
                question.put("questionId", generated.get("questionId"));
            }
            normalizedQuestions.add(question);
        }

        // 校验器：题数、题型白名单、选项数量、长度、分值
        try {
            questionSetValidator.validate(normalizedQuestions);
        } catch (AiOutputValidationException e) {
            log.warn("[AI_OUTPUT_INVALID] 生成的题目集合不合法，使用确定性模板题: {}", e.getMessage());
            return DeterministicAiFallbacks.get(DeterministicAiFallbacks.AI_TEST_QUESTIONS).get();
        }
        return objectMapper.writeValueAsString(normalizedQuestions);
    }

    private int minimumAbilityTagsPerQuestion(String resumeClaims, int questionCount) {
        if (resumeClaims == null || resumeClaims.isBlank()) {
            return 1;
        }
        try {
            List<?> claims = objectMapper.readValue(resumeClaims, List.class);
            return Math.max(1, (int) Math.ceil(claims.size() / (double) Math.max(1, questionCount)));
        } catch (Exception ignored) {
            return 1;
        }
    }

    private String normalizeQuestionType(String type) {
        return switch (type.toUpperCase()) {
            case "SINGLE_CHOICE" -> "choice_single";
            case "MULTIPLE_CHOICE" -> "choice_multiple";
            case "CHOICE" -> "choice_single";
            case "SCENARIO", "CASE" -> "case";
            default -> "text";
        };
    }

    private String normalizeDifficulty(String difficulty) {
        return switch (difficulty.toUpperCase()) {
            case "EASY" -> "easy";
            case "HARD" -> "hard";
            default -> "medium";
        };
    }

    @Override
    public AiTestEvaluationResult evaluateAnswers(AiTestEvaluationRequest request) {
        try {
            long effectiveTimeoutSeconds = Math.min(
                    Math.max(1L, requestTimeoutSeconds), MAX_TEST_REQUEST_TIMEOUT_SECONDS);
            Map<String, Object> dataModel = new HashMap<>();
            dataModel.put("abilityTagName", request.abilityTagName() != null ? request.abilityTagName() : "综合能力");
            dataModel.put("questions", request.questions());
            dataModel.put("answers", request.answers());

            // LangChain4j 优先调用
            AiTestAiService aiService = aiTestAiServiceProvider.getIfAvailable();
            if (aiService != null) {
                String context = objectMapper.writeValueAsString(dataModel);
                AiTestEvaluationResultDTO dto = null;
                try {
                    dto = aiServiceResilience.callWithResilienceOrThrow(
                            "ai-test-evaluate-lc4j",
                            () -> aiService.evaluateAnswers(context), effectiveTimeoutSeconds);
                    agentOutputValidator.validateOrThrow(dto, "AI_TEST_EVALUATION");
                } catch (AiOutputValidationException e) {
                    log.warn("[AI_OUTPUT_INVALID] AI测试评分结果不合规: field={}, reason={}, status={}, score={}, masteryLevel={}",
                            e.getField(), e.getReason(), dtoStatus(dto), dtoScore(dto), dtoMasteryLevel(dto));
                    return invalidOutputEvaluation();
                } catch (Exception e) {
                    log.warn("AI test evaluation unavailable after retries: {}", e.getMessage());
                    return unavailableEvaluation();
                }
                if (!"VALID".equals(dto.getStatus()) && dto.getScore() == null) {
                    return new AiTestEvaluationResult(
                            "INSUFFICIENT_EVIDENCE", objectMapper.writeValueAsString(dto), null, null,
                            dto.getAnalysisReport(), List.of());
                }
                String evalJson = objectMapper.writeValueAsString(dto);

                String evaluationJson = extractEvaluationJson(evalJson);
                Map<String, Object> evaluation = objectMapper.readValue(
                        evaluationJson, new TypeReference<Map<String, Object>>() {}
                );

                if (!"INSUFFICIENT_EVIDENCE".equals(String.valueOf(evaluation.get("status")))
                        && (evaluation.get("score") == null || evaluation.get("masteryLevel") == null)) {
                    evaluationJson = buildEvaluationFromDetails(evaluationJson);
                    evaluation = objectMapper.readValue(
                            evaluationJson, new TypeReference<Map<String, Object>>() {}
                    );
                }

                return toEvaluationResult(evaluationJson, evaluation);
            }

            // Fallback: LangChain4j + FreeMarker
            String prompt = promptTemplateService.render("ai-test-evaluate-prompt", dataModel);
            String aiResponse = langChain4jChatService.chat("ai-test-evaluate", prompt,
                    this::buildFallbackEvaluation, effectiveTimeoutSeconds);

            String evaluationJson = extractEvaluationJson(aiResponse);
            Map<String, Object> evaluation = objectMapper.readValue(
                    evaluationJson, new TypeReference<Map<String, Object>>() {}
            );

            if (!"INSUFFICIENT_EVIDENCE".equals(String.valueOf(evaluation.get("status")))
                    && (evaluation.get("score") == null || evaluation.get("masteryLevel") == null)) {
                evaluationJson = buildEvaluationFromDetails(evaluationJson);
                evaluation = objectMapper.readValue(
                        evaluationJson, new TypeReference<Map<String, Object>>() {}
                );
            }

            return toEvaluationResult(evaluationJson, evaluation);
        } catch (Exception e) {
            log.error("AI test evaluation failed", e);
            // 评分异常/超时/非法 JSON：UNAVAILABLE，等级为 null，禁止返回默认 60/L2 伪装成正常结果
            return new AiTestEvaluationResult(
                    AiTestEvaluationResult.UNAVAILABLE,
                    buildFallbackEvaluation(),
                    null,
                    null,
                    "AI批阅服务暂时不可用。",
                    List.of()
            );
        }
    }

    /**
     * 调用模型 -> 提取 JSON -> 反序列化 -> 校验器 -> 返回结果
     * <p>
     * 校验失败时禁止向下游（业务写库）返回无效结果，改用确定性降级评分。
     */
    private AiTestEvaluationResult toEvaluationResult(String evaluationJson, Map<String, Object> evaluation) {
        String status = evaluation.get("status") == null ? null : String.valueOf(evaluation.get("status"));
        if (AiTestEvaluationResult.INSUFFICIENT_EVIDENCE.equals(status)) {
            return new AiTestEvaluationResult(
                    AiTestEvaluationResult.INSUFFICIENT_EVIDENCE,
                    evaluationJson,
                    null,
                    null,
                    (String) evaluation.get("analysisReport"),
                    List.of());
        }
        BigDecimal score = getBigDecimal(evaluation.get("score"));
        Integer masteryLevel = getInteger(evaluation.get("masteryLevel"));

        if (score.compareTo(BigDecimal.ZERO) < 0 || score.compareTo(BigDecimal.valueOf(100)) > 0
                || masteryLevel == null || masteryLevel < 1 || masteryLevel > 5) {
            log.warn("[AI_OUTPUT_INVALID] AI测试批阅结果不合法，score={}, masteryLevel={}，标记 INVALID_OUTPUT",
                    score, masteryLevel);
            return new AiTestEvaluationResult(
                    AiTestEvaluationResult.INVALID_OUTPUT,
                    buildFallbackEvaluation(),
                    null,
                    null,
                    "AI批阅结果不合法，建议人工复核。",
                    List.of()
            );
        }

        return new AiTestEvaluationResult(
                AiTestEvaluationResult.VALID,
                evaluationJson,
                score,
                masteryLevel,
                (String) evaluation.get("analysisReport"),
                List.of()
        );
    }

    private String extractEvaluationJson(String aiResponse) {
        try {
            String json = llmResponseParser.extractJson(aiResponse);
            if (json.startsWith("[")) {
                return "{\"details\":" + json + "}";
            }
            return json;
        } catch (ModelResponseParseException e) {
            return buildFallbackEvaluation();
        }
    }

    private String buildEvaluationFromDetails(String evaluationJson) {
        try {
            Map<String, Object> map = objectMapper.readValue(evaluationJson, new TypeReference<>() {});
            Object detailsObj = map.get("details");
            if (detailsObj == null) {
                return evaluationJson;
            }

            List<Map<String, Object>> details = objectMapper.convertValue(detailsObj, new TypeReference<>() {});
            if (details.isEmpty()) {
                return evaluationJson;
            }

            int totalScore = 0;
            int totalMaxScore = 0;
            StringBuilder report = new StringBuilder();
            for (Map<String, Object> d : details) {
                totalScore += getBigDecimal(d.get("score")).intValue();
                totalMaxScore += getBigDecimal(d.getOrDefault("maxScore", d.get("score"))).intValue();
                String comment = (String) d.get("comment");
                if (comment != null && !comment.isEmpty()) {
                    if (!report.isEmpty()) {
                        report.append("; ");
                    }
                    report.append("题").append(d.get("questionId")).append(": ").append(comment);
                }
            }
            int percentScore = totalMaxScore > 0 ? Math.round(totalScore * 100f / totalMaxScore) : 0;
            int masteryLevel = percentScore >= 90 ? 5 : percentScore >= 80 ? 4 : percentScore >= 70 ? 3 : percentScore >= 60 ? 2 : 1;

            Map<String, Object> result = new LinkedHashMap<>();
            result.put("score", percentScore);
            result.put("masteryLevel", masteryLevel);
            result.put("analysisReport", report.toString());
            result.put("details", details);
            return objectMapper.writeValueAsString(result);
        } catch (Exception e) {
            log.warn("Failed to build AI test evaluation from details: {}", e.getMessage());
            return evaluationJson;
        }
    }

    private String buildFallbackQuestions() {
        return """
            [
                {
                    "id": 1,
                    "type": "choice_single",
                    "question": "请描述您对该技能的掌握程度",
                    "options": ["入门", "熟悉", "掌握", "精通", "专家"],
                    "answer": ""
                },
                {
                    "id": 2,
                    "type": "text",
                    "question": "请举例说明您在实际工作中应用该技能的经验",
                    "answer": ""
                }
            ]
            """;
    }

    private String buildFallbackEvaluation() {
        return """
            {
                "score": 60,
                "masteryLevel": 2,
                "analysisReport": "AI批阅服务暂时不可用，已给出默认评分。建议人工复核。",
                "details": []
            }
            """;
    }

    private AiTestEvaluationResult unavailableEvaluation() {
        return new AiTestEvaluationResult(AiTestEvaluationResult.UNAVAILABLE,
                "{\"status\":\"UNAVAILABLE\",\"score\":null,\"masteryLevel\":null}",
                null, null, "AI evaluation service is temporarily unavailable.", List.of());
    }

    private AiTestEvaluationResult invalidOutputEvaluation() {
        return new AiTestEvaluationResult(AiTestEvaluationResult.INVALID_OUTPUT,
                "{\"status\":\"INVALID_OUTPUT\",\"score\":null,\"masteryLevel\":null}",
                null, null, "AI evaluation output is invalid.", List.of());
    }

    private String dtoStatus(AiTestEvaluationResultDTO dto) {
        return dto == null ? null : dto.getStatus();
    }

    private Integer dtoScore(AiTestEvaluationResultDTO dto) {
        return dto == null ? null : dto.getScore();
    }

    private Integer dtoMasteryLevel(AiTestEvaluationResultDTO dto) {
        return dto == null ? null : dto.getMasteryLevel();
    }

    private BigDecimal getBigDecimal(Object value) {
        if (value == null) {
            return BigDecimal.ZERO;
        }
        if (value instanceof BigDecimal bd) {
            return bd;
        }
        if (value instanceof Number number) {
            return new BigDecimal(number.toString());
        }
        return BigDecimal.ZERO;
    }

    /**
     * 可选数值字段：缺失时返回 null（不转为 ZERO），用于置信度等需要区分"未提供"和"值为0"的场景
     */
    private BigDecimal getOptionalBigDecimal(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof BigDecimal bd) {
            return bd;
        }
        if (value instanceof Number number) {
            return new BigDecimal(number.toString());
        }
        try {
            return new BigDecimal(value.toString());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private Integer getInteger(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Integer integer) {
            return integer;
        }
        if (value instanceof Number number) {
            return number.intValue();
        }
        try {
            return Integer.parseInt(value.toString());
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
