package com.example.matching.dto.matching;

/** 单层匹配评分配置。硬条件是资格门槛，不属于可抵消的分数权重。 */
public record ScoringWeightVO(
        String version,
        double abilityWeight,
        double semanticWeight,
        double evidenceWeight,
        double aiWeight,
        boolean whitelistBypassHardRules,
        String l2MatchingMode,
        double requiredSemanticThreshold,
        double coreSemanticThreshold,
        double optionalSemanticThreshold,
        double similarTagMinimumConfidence,
        int allowedLevelGap,
        double coreCoverageThreshold,
        double requiredCoverageThreshold,
        int l2PassThreshold,
        int aiTriggerThreshold
) {
    public ScoringWeightVO(String version, double abilityWeight, double semanticWeight,
                           double evidenceWeight, double aiWeight, boolean whitelistBypassHardRules) {
        this(version, abilityWeight, semanticWeight, evidenceWeight, aiWeight, whitelistBypassHardRules,
                "BALANCED", 0.85d, 0.82d, 0.78d, 0.80d, 0, 0.80d, 0.75d, 60, 60);
    }
    /** @deprecated 仅兼容旧控制器测试和旧客户端反序列化。 */
    @Deprecated
    public ScoringWeightVO(String version, double noLlmAbilityWeight, double noLlmSemanticWeight,
                           double noLlmEvidenceWeight, double withLlmAbilityWeight,
                           double withLlmSemanticWeight, double withLlmEvidenceWeight,
                           double withLlmLlmWeight, double qualityWeightNoLlm,
                           double qualityWeightWithLlm, double feedbackScale, double ragWeight,
                           double l1Weight, double l2Weight, double l3Weight,
                           boolean whitelistBypassHardRules) {
        this(version, noLlmAbilityWeight, noLlmSemanticWeight, noLlmEvidenceWeight,
                withLlmLlmWeight, whitelistBypassHardRules);
    }

    /** @deprecated 层级合成已移除。 */
    @Deprecated public double l1Weight() { return 0d; }
    /** @deprecated 层级合成已移除。 */
    @Deprecated public double l2Weight() { return 0d; }
    /** @deprecated 层级合成已移除。 */
    @Deprecated public double l3Weight() { return 0d; }
}
