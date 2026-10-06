package com.example.matching.dto.system.api;

import java.math.BigDecimal;

/**
 * 全局企业 AI 模型配置响应/请求 DTO。
 * <p>
 * apiKey 仅在请求（PUT）中出现；响应永不返回明文密钥，只返回 apiKeyConfigured。
 */
public record SystemAiModelConfigDTO(
        Long id,
        Boolean enabled,
        String baseUrl,
        String modelName,
        String apiKey,
        Boolean apiKeyConfigured,
        Integer timeoutSeconds,
        BigDecimal temperature,
        Integer testQuestionCount,
        Integer interviewQuestionCount,
        Integer postAbilityClusterMinMemberCount,
        Integer postAbilityClusterMinPostCount,
        BigDecimal postAbilityClusterJoinSimilarity,
        BigDecimal postAbilityClusterPromotionCohesion,
        Long updatedBy,
        String updatedTime
) {
    /** Compatibility constructor for callers compiled against the former configuration shape. */
    public SystemAiModelConfigDTO(Long id, Boolean enabled, String baseUrl, String modelName, String apiKey,
                                  Boolean apiKeyConfigured, Integer timeoutSeconds, BigDecimal temperature,
                                  Integer testQuestionCount, Integer interviewQuestionCount,
                                  Long updatedBy, String updatedTime) {
        this(id, enabled, baseUrl, modelName, apiKey, apiKeyConfigured, timeoutSeconds, temperature,
                testQuestionCount, interviewQuestionCount, 3, 2,
                new BigDecimal("0.82"), new BigDecimal("0.80"), updatedBy, updatedTime);
    }
}
