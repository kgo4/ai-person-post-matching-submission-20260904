package com.example.matching.service.assessment;

/**
 * 回流人工采纳的聚合 Harness 审核结果到能力融合中心。
 */
public interface AggregateAbilityHarnessReviewService {

    /** Whether this harness log belongs to the assessment workflow's final aggregate batch. */
    default boolean isAggregateHarnessReview(Long harnessLogId) {
        return false;
    }

    /**
     * 将关联能力组恢复为可定级状态，并执行既有的定级与正式画像投影。
     */
    void acceptAndProject(Long harnessLogId, String reviewComment);

    /**
     * Records a rejected aggregate Harness review. Rejected evidence is retained for audit
     * but must never participate in formal ability projection.
     */
    void rejectAndFinalize(Long harnessLogId, String reviewComment);
}
