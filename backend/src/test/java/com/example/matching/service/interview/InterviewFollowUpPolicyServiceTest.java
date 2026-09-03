package com.example.matching.service.interview;

import com.example.matching.dto.interview.AnswerQualityEvaluation;
import com.example.matching.dto.interview.FollowUpDecision;
import com.example.matching.entity.interview.InterviewFollowUpQuestion;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class InterviewFollowUpPolicyServiceTest {

    private final InterviewFollowUpPolicyService service = new InterviewFollowUpPolicyService();

    @Test
    void skipsFollowUpWhenTheMainAnswerAlreadyHasSufficientEvidence() {
        FollowUpDecision decision = service.decide(1L, 2L, evaluation(85, true), List.of());

        assertThat(decision.shouldFollowUp()).isFalse();
        assertThat(decision.terminationReason()).contains("达标");
    }

    @Test
    void allowsOnlyOneEvidenceSeekingFollowUpForEachMainQuestion() {
        FollowUpDecision first = service.decide(1L, 2L, evaluation(45, true), List.of());
        InterviewFollowUpQuestion asked = new InterviewFollowUpQuestion();
        asked.setFollowUpStatus("ANSWERED");
        asked.setTargetDimension("action");

        FollowUpDecision second = service.decide(1L, 2L, evaluation(45, true), List.of(asked));

        assertThat(first.shouldFollowUp()).isTrue();
        assertThat(second.shouldFollowUp()).isFalse();
        assertThat(second.terminationReason()).contains("上限");
    }

    @Test
    void neverFollowsUpOnExplicitDenialEvenWhenAnUpstreamEvaluationIsWrong() {
        AnswerQualityEvaluation denial = new AnswerQualityEvaluation(null,
                0, 0, 0, 0, true, "候选人明确否认简历项目", "detail", "STAR_MISSING",
                List.of(), List.of(), "候选人明确否认简历中的项目经历", List.of());

        FollowUpDecision decision = service.decide(1L, 2L, denial, List.of());

        assertThat(decision.shouldFollowUp()).isFalse();
        assertThat(decision.terminationReason()).contains("否认");
    }

    private AnswerQualityEvaluation evaluation(int score, boolean needFollowUp) {
        return new AnswerQualityEvaluation(null, score, score, score, score, needFollowUp,
                "缺少行动细节", "action", "STAR_MISSING", List.of("action"), List.of(), "证据不足");
    }
}
