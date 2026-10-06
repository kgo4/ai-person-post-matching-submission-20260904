package com.example.matching.service.learning;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class LearningEvidenceConfidencePolicyTest {

    private final LearningEvidenceConfidencePolicy policy = new LearningEvidenceConfidencePolicy();

    @Test
    void midScoreIncompleteSubmission() {
        LearningEvidenceConfidencePolicy.ConfidenceResult result = policy.calculate(80, false, false);
        assertThat(result.confidence()).isEqualTo(80);
        assertThat(result.credibility()).isEqualTo(75);
    }

    @Test
    void midScoreCompleteSubmission() {
        LearningEvidenceConfidencePolicy.ConfidenceResult result = policy.calculate(80, true, true);
        assertThat(result.confidence()).isEqualTo(90);
        assertThat(result.credibility()).isEqualTo(85);
    }

    @Test
    void maxScoreCompleteSubmission() {
        LearningEvidenceConfidencePolicy.ConfidenceResult result = policy.calculate(100, true, true);
        assertThat(result.confidence()).isEqualTo(95);
        assertThat(result.credibility()).isEqualTo(90);
    }

    @Test
    void lowScoreClampedMin() {
        LearningEvidenceConfidencePolicy.ConfidenceResult result = policy.calculate(0, false, false);
        assertThat(result.confidence()).isEqualTo(40);
        assertThat(result.credibility()).isEqualTo(35);
    }

    @Test
    void rejectedReviewShouldNotCreateEvidence() {
        LearningEvidenceConfidencePolicy.ConfidenceResult result = policy.calculate(30, false, false);
        assertThat(result.confidence()).isGreaterThanOrEqualTo(40);
        assertThat(result.credibility()).isGreaterThanOrEqualTo(35);
    }

    @Test
    void completenessBonusRequiresBothRepoAndDeliverable() {
        LearningEvidenceConfidencePolicy.ConfidenceResult resultRepoOnly = policy.calculate(80, true, false);
        LearningEvidenceConfidencePolicy.ConfidenceResult resultTextOnly = policy.calculate(80, false, true);
        LearningEvidenceConfidencePolicy.ConfidenceResult resultBoth = policy.calculate(80, true, true);

        assertThat(resultRepoOnly.confidence()).isEqualTo(80);
        assertThat(resultTextOnly.confidence()).isEqualTo(80);
        assertThat(resultBoth.confidence()).isEqualTo(90);
    }

    // ===== 无人工审核口径：员工自提材料按完整度计分 =====

    @Test
    void selfReportedEmptySubmissionSitsAtFloor() {
        LearningEvidenceConfidencePolicy.ConfidenceResult result =
                policy.calculateByCompleteness(false, false, false, false);
        assertThat(result.confidence()).isEqualTo(40);
        assertThat(result.credibility()).isEqualTo(35);
    }

    @Test
    void selfReportedGrowsWithEachMaterial() {
        // 单项：40 + 8 = 48
        assertThat(policy.calculateByCompleteness(true, false, false, false).confidence()).isEqualTo(48);
        // 两项（仓库 + 说明，同时具备完整性加成 10）：40 + 16 + 10 = 66
        assertThat(policy.calculateByCompleteness(true, false, false, true).confidence()).isEqualTo(66);
    }

    @Test
    void selfReportedIsClampedBelowReviewedCeiling() {
        LearningEvidenceConfidencePolicy.ConfidenceResult all =
                policy.calculateByCompleteness(true, true, true, true);

        // 四项全齐：40 + 32 + 10 = 82
        assertThat(all.confidence()).isEqualTo(82);
        assertThat(all.credibility()).isEqualTo(77);

        // 材料齐备只说明「交得完整」，不等于内容已被核验：
        // 上限必须低于有人工评分时的上限（95），否则自提材料会与已审核证据同权。
        assertThat(all.confidence()).isLessThan(95);
    }

    @Test
    void selfReportedNeverExceedsReviewedScoreForSameCompleteness() {
        // 同样的「仓库 + 说明」完整度，人工评分口径（80 分）应不低于自提口径。
        int selfReported = policy.calculateByCompleteness(true, false, false, true).confidence();
        int reviewed = policy.calculate(80, true, true).confidence();
        assertThat(selfReported).isLessThan(reviewed);
    }
}
