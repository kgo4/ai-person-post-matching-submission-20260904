package com.example.matching.service.interview;

import com.example.matching.agent.lc4j.InterviewFollowUpAiService;
import com.example.matching.agent.service.impl.AgentOutputValidator;
import com.example.matching.ai.service.PromptTemplateService;
import com.example.matching.dto.interview.FollowUpDecision;
import com.example.matching.entity.employee.EmpVideoInterviewQuestion;
import com.example.matching.entity.interview.InterviewFollowUpQuestion;
import com.example.matching.integration.volcengine.DoubaoChatClient;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class InterviewFollowUpGenerationServiceTest {

    @Test
    void ruleFallbackUsesTheCanonicalExpectedEvidenceType() {
        @SuppressWarnings("unchecked")
        ObjectProvider<InterviewFollowUpAiService> provider = mock(ObjectProvider.class);
        InterviewFollowUpGenerationService service = new InterviewFollowUpGenerationService(
                mock(com.example.matching.infrastructure.llm.EnterpriseChatLanguageModel.class),
                mock(PromptTemplateService.class), new ObjectMapper(), provider,
                new com.example.matching.ai.validation.InterviewFollowUpValidator(),
                mock(com.example.matching.mapper.employee.EmpVideoInterviewSessionMapper.class),
                mock(AgentOutputValidator.class),
                new com.example.matching.infrastructure.llm.LlmResponseParser(new ObjectMapper()));
        EmpVideoInterviewQuestion question = new EmpVideoInterviewQuestion();
        question.setId(1L);
        question.setSessionId(2L);
        question.setQuestionText("Describe a production incident you resolved.");

        InterviewFollowUpQuestion followUp = service.generate(
                FollowUpDecision.followUp("STAR_MISSING", "detail", 0, 2),
                question, null, "Incident response", null, null, null, List.of());

        assertThat(followUp.getExpectedEvidenceType()).isEqualTo("PROJECT_DETAIL");
    }
}
