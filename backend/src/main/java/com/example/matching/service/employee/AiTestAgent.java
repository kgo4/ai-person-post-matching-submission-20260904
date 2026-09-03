package com.example.matching.service.employee;

import java.math.BigDecimal;
import java.util.List;

/**
 * AI test agent.
 * <p>
 * Owns question generation, answer evaluation, and ability-tag extraction for
 * AI tests. Business services persist records and apply results, but do not
 * call the model directly.
 */
public interface AiTestAgent {

    String generateQuestions(AiTestQuestionRequest request);

    AiTestEvaluationResult evaluateAnswers(AiTestEvaluationRequest request);

    record AiTestQuestionRequest(
            String abilityTagName,
            String abilityTagCategory,
            String abilityTagDescription,
            String postName,
            String jobDescription,
            String abilities,
            String resumeClaims,
            String scopeJson,
            String blueprintJson
    ) {
        public AiTestQuestionRequest(String abilityTagName, String abilityTagCategory,
                                     String abilityTagDescription, String postName,
                                     String jobDescription, String abilities, String resumeClaims) {
            this(abilityTagName, abilityTagCategory, abilityTagDescription, postName,
                    jobDescription, abilities, resumeClaims, null, null);
        }
    }

    record AiTestEvaluationRequest(
            String abilityTagName,
            String questions,
            String answers
    ) {
    }

    record AiTestEvaluationResult(
            String status,
            String evaluationJson,
            BigDecimal score,
            Integer masteryLevel,
            String analysisReport,
            List<DiscoveredAbility> discoveredAbilities
    ) {
        /** 状态常量（见实施计划 §1.3）：status != VALID 时 score/masteryLevel 必须为 null */
        public static final String VALID = "VALID";
        public static final String INSUFFICIENT_EVIDENCE = "INSUFFICIENT_EVIDENCE";
        public static final String UNAVAILABLE = "UNAVAILABLE";
        public static final String INVALID_OUTPUT = "INVALID_OUTPUT";
    }

    record DiscoveredAbility(
            String abilityName,
            Integer masteryLevel,
            BigDecimal confidenceScore,
            String evidenceText,
            String extractReason
    ) {
    }
}
