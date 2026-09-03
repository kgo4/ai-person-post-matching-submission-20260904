package com.example.matching.ai.validation;

import com.example.matching.dto.interview.FollowUpDecision;
import com.example.matching.entity.employee.EmpVideoInterviewQuestion;
import com.example.matching.entity.interview.InterviewFollowUpQuestion;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class InterviewFollowUpValidatorTest {

    private final InterviewFollowUpValidator validator = new InterviewFollowUpValidator();

    private EmpVideoInterviewQuestion question() {
        EmpVideoInterviewQuestion q = new EmpVideoInterviewQuestion();
        q.setId(23L);
        q.setSessionId(17L);
        return q;
    }

    @Test
    void backfillsSessionAndQuestionIdFromServerContext() {
        InterviewFollowUpQuestion followUp = new InterviewFollowUpQuestion();
        followUp.setQuestionText("请补充具体结果");

        validator.validateAndBackfill(followUp, question(), 0);

        assertThat(followUp.getParentQuestionId()).isEqualTo(23L);
        assertThat(followUp.getSessionId()).isEqualTo(17L);
    }

    @Test
    void rejectsBlankQuestionText() {
        InterviewFollowUpQuestion followUp = new InterviewFollowUpQuestion();
        followUp.setQuestionText("   ");
        assertThatThrownBy(() -> validator.validateAndBackfill(followUp, question(), 0))
                .isInstanceOf(AiOutputValidationException.class)
                .hasMessageContaining("questionText");
    }

    @Test
    void rejectsOverlongQuestionText() {
        InterviewFollowUpQuestion followUp = new InterviewFollowUpQuestion();
        followUp.setQuestionText("问".repeat(301));
        assertThatThrownBy(() -> validator.validateAndBackfill(followUp, question(), 0))
                .isInstanceOf(AiOutputValidationException.class)
                .hasMessageContaining("超长");
    }

    @Test
    void rejectsFollowUpCountAbovePolicyLimit() {
        assertThatThrownBy(() -> validator.validateFollowUpCount(2))
                .isInstanceOf(AiOutputValidationException.class)
                .hasMessageContaining("上限");
        assertThatCode(() -> validator.validateFollowUpCount(1)).doesNotThrowAnyException();
    }

    @Test
    void rejectsIllegalDecisionType() {
        assertThatThrownBy(() -> validator.validateDecision(FollowUpDecision.followUp("FAKE_TYPE", "detail", 0, 2)))
                .isInstanceOf(AiOutputValidationException.class)
                .hasMessageContaining("followUpType");
        assertThatCode(() -> validator.validateDecision(
                FollowUpDecision.followUp("STAR_MISSING", "detail", 0, 2)))
                .doesNotThrowAnyException();
    }
}
