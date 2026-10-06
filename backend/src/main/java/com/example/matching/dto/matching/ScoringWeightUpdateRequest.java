package com.example.matching.dto.matching;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;

/** 单层正式匹配分权重，四项必须精确合计 1.0。 */
public record ScoringWeightUpdateRequest(
        // 配置边界统一使用百分比（0-100）；内部评分运行时转换为 0-1。
        Double abilityWeight,
        Double semanticWeight,
        Double evidenceWeight,
        Double aiWeight,
        Boolean whitelistBypassHardRules,
        String l2MatchingMode,
        @DecimalMin("0.0") @DecimalMax("1.0") Double requiredSemanticThreshold,
        @DecimalMin("0.0") @DecimalMax("1.0") Double coreSemanticThreshold,
        @DecimalMin("0.0") @DecimalMax("1.0") Double optionalSemanticThreshold,
        @DecimalMin("0.0") @DecimalMax("1.0") Double similarTagMinimumConfidence,
        @DecimalMin("0") @DecimalMax("3") Integer allowedLevelGap,
        @DecimalMin("0.0") @DecimalMax("1.0") Double coreCoverageThreshold,
        @DecimalMin("0.0") @DecimalMax("1.0") Double requiredCoverageThreshold,
        @DecimalMin("0") @DecimalMax("100") Integer l2PassThreshold,
        @DecimalMin("0") @DecimalMax("100") Integer aiTriggerThreshold
) {
    public ScoringWeightUpdateRequest(Double abilityWeight, Double semanticWeight, Double evidenceWeight,
                                      Double aiWeight, Boolean whitelistBypassHardRules) {
        this(abilityWeight, semanticWeight, evidenceWeight, aiWeight, whitelistBypassHardRules,
                null, null, null, null, null, null, null, null, null, null);
    }
    /** @deprecated 仅兼容历史调用方；旧双权重字段不再进入正式评分。 */
    @Deprecated
    public ScoringWeightUpdateRequest(Double noLlmAbilityWeight, Double noLlmSemanticWeight,
                                      Double noLlmEvidenceWeight, Double withLlmAbilityWeight,
                                      Double withLlmSemanticWeight, Double withLlmEvidenceWeight,
                                      Double withLlmLlmWeight, Double qualityWeightNoLlm,
                                      Double qualityWeightWithLlm, Double feedbackScale, Double ragWeight) {
        this(noLlmAbilityWeight, noLlmSemanticWeight, noLlmEvidenceWeight, withLlmLlmWeight, null);
    }

    /** @deprecated 仅兼容历史调用方；层级权重不再参与正式评分。 */
    @Deprecated
    public ScoringWeightUpdateRequest(Double noLlmAbilityWeight, Double noLlmSemanticWeight,
                                      Double noLlmEvidenceWeight, Double withLlmAbilityWeight,
                                      Double withLlmSemanticWeight, Double withLlmEvidenceWeight,
                                      Double withLlmLlmWeight, Double qualityWeightNoLlm,
                                      Double qualityWeightWithLlm, Double feedbackScale, Double ragWeight,
                                      Double l1Weight, Double l2Weight, Double l3Weight,
                                      Boolean whitelistBypassHardRules) {
        this(noLlmAbilityWeight, noLlmSemanticWeight, noLlmEvidenceWeight, withLlmLlmWeight,
                whitelistBypassHardRules);
    }
}
