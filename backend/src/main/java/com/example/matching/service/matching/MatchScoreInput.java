package com.example.matching.service.matching;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * 统一匹配评分输入 —— 所有评分路径的唯一公开入口。
 * <p>
 * 执行匹配、推荐预览、训练回放必须通过此 DTO 调用 {@link MatchingScoreService#score(MatchScoreInput)}。
 * 评分计算细节封装在 MatchingScoreService 内，不对外暴露最终分数。
 */
public record MatchScoreInput(
        BigDecimal abilityScore,
        BigDecimal semanticScore,
        BigDecimal evidenceScore,
        BigDecimal aiScore,
        MatchingTrainingWeightProfileStore.WeightProfile weightProfile
) {
    public static MatchScoreInput deterministic(
            BigDecimal abilityScore, BigDecimal semanticScore, BigDecimal evidenceScore,
            MatchingTrainingWeightProfileStore.WeightProfile profile) {
        return new MatchScoreInput(abilityScore, semanticScore, evidenceScore, null, profile);
    }

    public static MatchScoreInput withAi(
            BigDecimal abilityScore, BigDecimal semanticScore, BigDecimal evidenceScore,
            BigDecimal aiScore,
            MatchingTrainingWeightProfileStore.WeightProfile profile) {
        return new MatchScoreInput(abilityScore, semanticScore, evidenceScore, aiScore, profile);
    }
}
