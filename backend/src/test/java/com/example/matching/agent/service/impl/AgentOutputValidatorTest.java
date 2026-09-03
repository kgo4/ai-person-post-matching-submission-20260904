package com.example.matching.agent.service.impl;

import com.example.matching.agent.dto.interview.InterviewPlanDTO;
import com.example.matching.ai.validation.AiTestQuestionSetValidator;
import com.example.matching.ai.validation.InterviewAnswerQualityValidator;
import com.example.matching.ai.validation.InterviewFollowUpValidator;
import jakarta.validation.Validator;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import java.util.List;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AgentOutputValidatorTest {

    @Test
    void rejectsInterviewPlanWithoutQuestionsEvenWhenBeanConstraintsAreAbsent() {
        Validator beanValidator = mock(Validator.class);
        when(beanValidator.validate(org.mockito.ArgumentMatchers.any())).thenReturn(java.util.Set.of());
        AgentOutputValidator validator = new AgentOutputValidator(
                beanValidator,
                new AiTestQuestionSetValidator(),
                new InterviewAnswerQualityValidator(),
                new InterviewFollowUpValidator());

        AgentOutputValidator.ValidationResult result = validator.validate(
                new InterviewPlanDTO(), "INTERVIEW_PLAN");

        assertThat(result.passed()).isFalse();
        assertThat(result.field()).isEqualTo("questions");
    }

    @Test
    void rejectsInterviewPlanQuestionWithoutVerificationTags() {
        Validator beanValidator = mock(Validator.class);
        when(beanValidator.validate(org.mockito.ArgumentMatchers.any())).thenReturn(java.util.Set.of());
        AgentOutputValidator validator = new AgentOutputValidator(
                beanValidator, new AiTestQuestionSetValidator(),
                new InterviewAnswerQualityValidator(), new InterviewFollowUpValidator());
        InterviewPlanDTO.Question question = new InterviewPlanDTO.Question(
                1, "请说明项目经验", "VERIFICATION", "MEDIUM", List.of(), null, "订单系统");

        AgentOutputValidator.ValidationResult result = validator.validate(
                new InterviewPlanDTO(List.of(question), "验证", 3), "INTERVIEW_PLAN");

        assertThat(result.passed()).isFalse();
        assertThat(result.field()).isEqualTo("questions.expectedTagIds");
    }
}
