package com.example.matching.service.learning;

/**
 * 学习证据置信度策略
 * <p>
 * 根据审核评分和提交完整性计算证据置信度和可信度。
 *
 * @author system
 */
public class LearningEvidenceConfidencePolicy {

    private static final int MIN_CONFIDENCE = 40;
    private static final int MAX_CONFIDENCE = 95;
    private static final int MIN_CREDIBILITY = 35;
    private static final int MAX_CREDIBILITY = 90;

    /**
     * 计算证据置信度。
     *
     * @param reviewScore       审核评分 (0..100)
     * @param hasRepoUrl        提交是否包含仓库URL
     * @param hasDeliverableText 提交是否包含交付物文本
     * @return (confidence, credibility)
     */
    public ConfidenceResult calculate(int reviewScore, boolean hasRepoUrl, boolean hasDeliverableText) {
        int completenessBonus = (hasRepoUrl && hasDeliverableText) ? 10 : 0;

        int confidence = 40 + (int) (reviewScore * 0.5) + completenessBonus;
        confidence = Math.max(MIN_CONFIDENCE, Math.min(MAX_CONFIDENCE, confidence));

        int credibility = confidence - 5;
        credibility = Math.max(MIN_CREDIBILITY, Math.min(MAX_CREDIBILITY, credibility));

        return new ConfidenceResult(confidence, credibility);
    }

    public record ConfidenceResult(int confidence, int credibility) {
    }
}
