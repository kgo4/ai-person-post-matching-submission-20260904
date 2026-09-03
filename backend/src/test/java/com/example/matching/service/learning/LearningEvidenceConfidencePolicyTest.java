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
}
