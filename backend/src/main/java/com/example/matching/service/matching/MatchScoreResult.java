package com.example.matching.service.matching;

import java.math.BigDecimal;

/**
 * 统一匹配评分结果 —— 包含各维度得分和算法版本。
 * <p>
 * 每个匹配结果只允许一个权威评分契约。算法版本变更必须记录旧新分数差异和升级原因。
 */
public record MatchScoreResult(
        BigDecimal rankScore,
        BigDecimal qualityAdjustment,
        BigDecimal feedbackAdjustment,
        BigDecimal calibrationAdjustment,
        BigDecimal finalScore,
        BigDecimal abilityWeight,
        BigDecimal semanticWeight,
        BigDecimal evidenceWeight,
        BigDecimal llmWeight,
        boolean hasLlm,
        String algorithmVersion
) {
    /** 当前算法版本号。每次修改评分逻辑须递增并记录差异。 */
    public static final String CURRENT_VERSION = "v1.0-score-gate";

    /** 从 ScoreBreakdown 和 weight 构建 */
    public static MatchScoreResult from(
            MatchingScoreCalculator.ScoreBreakdown breakdown,
            BigDecimal abilityWeight, BigDecimal semanticWeight,
            BigDecimal evidenceWeight, BigDecimal llmWeight,
            boolean hasLlm) {
        return new MatchScoreResult(
                breakdown.getRankScore(), breakdown.getQualityAdjustment(),
                breakdown.getFeedbackAdjustment(), breakdown.getCalibrationAdjustment(),
                breakdown.getFinalScore(),
                abilityWeight, semanticWeight, evidenceWeight, llmWeight,
                hasLlm, CURRENT_VERSION);
    }

    /** 维度得分摘要 */
    public record ScoreDimension(String name, BigDecimal score, BigDecimal weight) {}
}
