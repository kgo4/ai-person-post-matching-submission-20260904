package com.example.matching.service.employee;

import com.example.matching.agent.dto.interview.AiTestEvaluationResultDTO;
import com.example.matching.agent.dto.interview.AiTestQuestionItem;
import com.example.matching.agent.dto.interview.AiTestQuestionSetDTO;
import com.example.matching.agent.lc4j.AiTestAiService;
import com.example.matching.agent.service.impl.AgentOutputValidator;
import com.example.matching.ai.validation.AiOutputValidationException;
import com.example.matching.ai.service.LangChain4jChatService;
import com.example.matching.ai.service.PromptTemplateService;
import com.example.matching.infrastructure.llm.LlmResponseParser;
import com.example.matching.resilience.AiServiceResilience;
import com.example.matching.service.employee.impl.AiTestAgentImpl;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;

class AiTestAgentLangChain4jTest {

    @Test
    void questionGenerationUsesAnObjectStructuredResponse() throws Exception {
        assertThat(AiTestAiService.class
                .getMethod("generateQuestions", String.class, int.class)
                .getReturnType())
                .isNotEqualTo(List.class);
    }

    @SuppressWarnings("unchecked")
    @Test
    void generateQuestionsUsesLangChain4jWhenAvailable() {
        AiTestAiService aiService = mock(AiTestAiService.class);
        when(aiService.generateQuestions(org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.anyInt()))
                .thenReturn(new AiTestQuestionSetDTO(List.of(
                        new AiTestQuestionItem("解释 Spring Bean 生命周期", "SHORT_ANSWER", null, null, null, null, null, null),
                        new AiTestQuestionItem("请描述 Spring 事务失效场景", "TEXT", null, null, null, null, null, null),
                        new AiTestQuestionItem("请说明 Spring 循环依赖如何解决", "TEXT", null, null, null, null, null, null)
                )));

        ObjectProvider<AiTestAiService> provider = mock(ObjectProvider.class);
        when(provider.getIfAvailable()).thenReturn(aiService);

        AiTestAgentImpl agent = new AiTestAgentImpl(
                mock(LangChain4jChatService.class),
                mock(PromptTemplateService.class),
                new AiServiceResilience(io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry.ofDefaults(), java.util.concurrent.Executors.newSingleThreadExecutor()),
                new ObjectMapper(),
                new LlmResponseParser(new ObjectMapper()),
                provider,
                new com.example.matching.ai.validation.AiTestQuestionSetValidator(),
                mock(AgentOutputValidator.class)
        );

        String result = agent.generateQuestions(new AiTestAgent.AiTestQuestionRequest(
                "Spring", "TECH", "Spring framework ability",
                null, null, null, null
        ));

        assertThat(result).contains("Spring Bean");
    }

    @SuppressWarnings("unchecked")
    @Test
    void generateQuestionsNormalizesAiQuestionSchemaForTheClient() throws Exception {
        AiTestAiService aiService = mock(AiTestAiService.class);
        when(aiService.generateQuestions(org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.anyInt())).thenReturn(new AiTestQuestionSetDTO(List.of(
                new AiTestQuestionItem("Explain dependency injection", "SINGLE_CHOICE", "EASY",
                        List.of("A", "B"), "A", null, 101L, List.of("fact:POST_ABILITY_MODEL:9")),
                new AiTestQuestionItem("Explain AOP", "TEXT", "MEDIUM", null, "proxy", null, null, null),
                new AiTestQuestionItem("Explain IoC", "TEXT", "MEDIUM", null, "container", null, null, null)
        )));

        ObjectProvider<AiTestAiService> provider = mock(ObjectProvider.class);
        when(provider.getIfAvailable()).thenReturn(aiService);
        ObjectMapper objectMapper = new ObjectMapper();
        AiTestAgentImpl agent = new AiTestAgentImpl(
                mock(LangChain4jChatService.class), mock(PromptTemplateService.class),
                new AiServiceResilience(io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry.ofDefaults(), java.util.concurrent.Executors.newSingleThreadExecutor()),
                objectMapper, new LlmResponseParser(objectMapper), provider,
                new com.example.matching.ai.validation.AiTestQuestionSetValidator(),
                mock(AgentOutputValidator.class));

        List<Map<String, Object>> questions = objectMapper.readValue(agent.generateQuestions(
                        new AiTestAgent.AiTestQuestionRequest("Spring", "TECH", "Spring", null, null, null, null)),
                new com.fasterxml.jackson.core.type.TypeReference<>() {});

        assertThat(questions).first().satisfies(question -> {
            assertThat(question).containsEntry("id", 1);
            assertThat(question).containsEntry("type", "choice_single");
            assertThat(question).containsEntry("difficulty", "easy");
            assertThat(question).containsEntry("referenceAnswer", "A");
            assertThat(question).containsEntry("tagId", 101);
            assertThat(question).containsEntry("sourceRefs", List.of("fact:POST_ABILITY_MODEL:9"));
        });
    }

    @SuppressWarnings("unchecked")
    @Test
    void evaluateAnswersUsesLangChain4jWhenAvailable() {
        AiTestAiService aiService = mock(AiTestAiService.class);
        AiTestEvaluationResultDTO evalDto = new AiTestEvaluationResultDTO();
        evalDto.setScore(85);
        evalDto.setMasteryLevel(4);
        evalDto.setAnalysisReport("Good performance");
        when(aiService.evaluateAnswers(org.mockito.ArgumentMatchers.anyString()))
                .thenReturn(evalDto);

        ObjectProvider<AiTestAiService> provider = mock(ObjectProvider.class);
        when(provider.getIfAvailable()).thenReturn(aiService);

        AiTestAgentImpl agent = new AiTestAgentImpl(
                mock(LangChain4jChatService.class),
                mock(PromptTemplateService.class),
                new AiServiceResilience(io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry.ofDefaults(), java.util.concurrent.Executors.newSingleThreadExecutor()),
                new ObjectMapper(),
                new LlmResponseParser(new ObjectMapper()),
                provider,
                new com.example.matching.ai.validation.AiTestQuestionSetValidator(),
                mock(AgentOutputValidator.class)
        );

        AiTestAgent.AiTestEvaluationResult result = agent.evaluateAnswers(
                new AiTestAgent.AiTestEvaluationRequest(
                        "Spring",
                        "[{\"id\":\"1\",\"question\":\"What is Spring?\",\"type\":\"SHORT_ANSWER\"}]",
                        "[{\"id\":\"1\",\"answer\":\"Spring is a framework\"}]"
                )
        );

        assertThat(result.score()).isEqualByComparingTo(new java.math.BigDecimal("85"));
        assertThat(result.masteryLevel()).isEqualTo(4);
    }

    @Test
    void evaluateAnswersMarksInvalidModelLevelAsInvalidOutput() {
        AiTestAiService aiService = mock(AiTestAiService.class);
        AiTestEvaluationResultDTO evalDto = new AiTestEvaluationResultDTO();
        evalDto.setStatus("VALID");
        evalDto.setScore(85);
        evalDto.setMasteryLevel(0);
        evalDto.setAnalysisReport("Good performance");
        when(aiService.evaluateAnswers(org.mockito.ArgumentMatchers.anyString())).thenReturn(evalDto);

        ObjectProvider<AiTestAiService> provider = mock(ObjectProvider.class);
        when(provider.getIfAvailable()).thenReturn(aiService);
        AgentOutputValidator validator = mock(AgentOutputValidator.class);
        doThrow(new AiOutputValidationException("AI_TEST_EVALUATION", "masteryLevel",
                "value must be between 1 and 5"))
                .when(validator).validateOrThrow(evalDto, "AI_TEST_EVALUATION");

        AiTestAgentImpl agent = new AiTestAgentImpl(
                mock(LangChain4jChatService.class), mock(PromptTemplateService.class),
                new AiServiceResilience(io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry.ofDefaults(), java.util.concurrent.Executors.newSingleThreadExecutor()),
                new ObjectMapper(), new LlmResponseParser(new ObjectMapper()), provider,
                new com.example.matching.ai.validation.AiTestQuestionSetValidator(), validator);

        AiTestAgent.AiTestEvaluationResult result = agent.evaluateAnswers(
                new AiTestAgent.AiTestEvaluationRequest("Spring", "[]", "[]"));

        assertThat(result.status()).isEqualTo(AiTestAgent.AiTestEvaluationResult.INVALID_OUTPUT);
        assertThat(result.masteryLevel()).isNull();
    }
}
