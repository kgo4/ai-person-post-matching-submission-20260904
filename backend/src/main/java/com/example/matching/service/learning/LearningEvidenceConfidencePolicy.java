package com.example.matching.service.learning;

/**
 * 学习证据置信度策略
 * <p>
 * 两个口径，对应两种证据产生方式：
 * <ul>
 *   <li>{@link #calculate} —— 有人工审核评分时（历史口径，保留以兼容既有调用）；</li>
 *   <li>{@link #calculateByCompleteness} —— <b>无人工审核</b>，只按材料完整度计分。
 *       项目任务材料由员工自行提交、不再经过独立复核，因此走这个口径。</li>
 * </ul>
 *
 * @author system
 */
public class LearningEvidenceConfidencePolicy {

    private static final int MIN_CONFIDENCE = 40;
    private static final int MAX_CONFIDENCE = 95;
    private static final int MIN_CREDIBILITY = 35;
    private static final int MAX_CREDIBILITY = 90;

    /** 员工自提材料的基础置信度：无人工背书，起点低于有人工审核的场景 */
    private static final int SELF_REPORTED_BASE = 40;

    /** 每多一项有效材料（仓库/演示/报告/文字说明）的加成 */
    private static final int PER_MATERIAL_BONUS = 8;

    /**
     * 员工自提材料的置信度上限。
     * <p>
     * 刻意低于 {@link #MAX_CONFIDENCE}（95，有人工评分时可达）：材料齐备只代表
     * 「员工交得完整」，不等于「内容已被核验」。能力等级是否更新由 HR 在
     * 「能力提升申请」里判断，这里的分数只用于给证据排序与展示，不构成等级依据。
     */
    private static final int SELF_REPORTED_MAX_CONFIDENCE = 82;

    /**
     * 计算证据置信度（人工评分口径）。
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

    /**
     * 按材料完整度计算证据置信度（<b>无人工审核</b>口径）。
     *
     * <p>用于员工自行提交、不经过独立复核的材料。四项材料每有一项就加
     * {@link #PER_MATERIAL_BONUS} 分；仓库URL与文字说明同时具备时再给完整性加成
     * （与 {@link #calculate} 保持一致）；上限为 {@link #SELF_REPORTED_MAX_CONFIDENCE}。</p>
     *
     * @param hasRepoUrl         是否提供仓库URL
     * @param hasDemoUrl         是否提供演示URL
     * @param hasReportUrl       是否提供报告URL
     * @param hasDeliverableText 是否提供文字说明
     * @return (confidence, credibility)
     */
    public ConfidenceResult calculateByCompleteness(boolean hasRepoUrl, boolean hasDemoUrl,
                                                     boolean hasReportUrl, boolean hasDeliverableText) {
        int materialCount = 0;
        if (hasRepoUrl) {
            materialCount++;
        }
        if (hasDemoUrl) {
            materialCount++;
        }
        if (hasReportUrl) {
            materialCount++;
        }
        if (hasDeliverableText) {
            materialCount++;
        }

        int completenessBonus = (hasRepoUrl && hasDeliverableText) ? 10 : 0;
        int confidence = SELF_REPORTED_BASE + materialCount * PER_MATERIAL_BONUS + completenessBonus;
        confidence = Math.max(MIN_CONFIDENCE, Math.min(SELF_REPORTED_MAX_CONFIDENCE, confidence));

        int credibility = confidence - 5;
        credibility = Math.max(MIN_CREDIBILITY, Math.min(MAX_CREDIBILITY, credibility));

        return new ConfidenceResult(confidence, credibility);
    }

    public record ConfidenceResult(int confidence, int credibility) {
    }
}
