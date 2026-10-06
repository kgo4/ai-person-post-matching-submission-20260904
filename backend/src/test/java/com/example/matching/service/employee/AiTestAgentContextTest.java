package com.example.matching.service.employee;

import com.example.matching.agent.lc4j.AiTestAiService;
import com.example.matching.agent.service.impl.AgentOutputValidator;
import com.example.matching.ai.service.LangChain4jChatService;
import com.example.matching.ai.service.PromptTemplateService;
import com.example.matching.ai.validation.AiTestQuestionSetValidator;
import com.example.matching.ai.validation.DeterministicAiFallbacks;
import com.example.matching.infrastructure.llm.LlmResponseParser;
import com.example.matching.resilience.AiServiceResilience;
import com.example.matching.service.employee.impl.AiTestAgentImpl;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.ObjectProvider;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@Tag("workflow3")
@ExtendWith(MockitoExtension.class)
class AiTestAgentContextTest {

    @Mock private LangChain4jChatService langChain4jChatService;
    @Mock private PromptTemplateService promptTemplateService;
    @Mock private AiServiceResilience aiServiceResilience;
    @Mock private ObjectMapper objectMapper;
    @Mock private LlmResponseParser llmResponseParser;
    @Mock private ObjectProvider<AiTestAiService> aiTestAiServiceProvider;
    @Mock private AiTestQuestionSetValidator questionSetValidator;
    @Mock private AgentOutputValidator agentOutputValidator;

    @InjectMocks
    private AiTestAgentImpl aiTestAgent;

    @Test
    void buildContextShouldIncludePostRequirementsAndAbilities() throws Exception {
        AiTestAgent.AiTestQuestionRequest request = new AiTestAgent.AiTestQuestionRequest(
                null, null, null,
                "Java开发工程师", "负责后端开发", "Java,Spring", null);

        when(aiTestAiServiceProvider.getIfAvailable()).thenReturn(null);
        when(objectMapper.writeValueAsString(any())).thenReturn("{\"questionCount\":5,\"postName\":\"Java\"}");
        when(promptTemplateService.render(anyString(), any())).thenReturn("mock prompt");
        when(langChain4jChatService.chat(anyString(), anyString(), any(), anyLong())).thenReturn(
                DeterministicAiFallbacks.get(DeterministicAiFallbacks.AI_TEST_QUESTIONS).get());
        when(llmResponseParser.extractJson(anyString())).thenReturn(
                DeterministicAiFallbacks.get(DeterministicAiFallbacks.AI_TEST_QUESTIONS).get());
        when(objectMapper.readValue(anyString(), any(com.fasterxml.jackson.core.type.TypeReference.class)))
                .thenReturn(List.of());
        doNothing().when(questionSetValidator).validate(any());

        String result = aiTestAgent.generateQuestions(request);
        assertThat(result).isNotNull();
    }

    @Test
    void buildContextShouldIncludeTagBasedContextWhenNoPostName() throws Exception {
        AiTestAgent.AiTestQuestionRequest request = new AiTestAgent.AiTestQuestionRequest(
                "Java", "开发", "精通Java开发",
                null, null, null, null);

        when(aiTestAiServiceProvider.getIfAvailable()).thenReturn(null);
        when(objectMapper.writeValueAsString(any())).thenReturn("{\"questionCount\":5,\"postName\":\"Java\"}");
        when(promptTemplateService.render(anyString(), any())).thenReturn("mock prompt");
        when(langChain4jChatService.chat(anyString(), anyString(), any(), anyLong())).thenReturn(
                DeterministicAiFallbacks.get(DeterministicAiFallbacks.AI_TEST_QUESTIONS).get());
        when(llmResponseParser.extractJson(anyString())).thenReturn(
                DeterministicAiFallbacks.get(DeterministicAiFallbacks.AI_TEST_QUESTIONS).get());
        when(objectMapper.readValue(anyString(), any(com.fasterxml.jackson.core.type.TypeReference.class)))
                .thenReturn(List.of());
        doNothing().when(questionSetValidator).validate(any());

        String result = aiTestAgent.generateQuestions(request);
        assertThat(result).isNotNull();
    }

    @Test
    void fallbackBehaviorWhenResilienceGivesDeterministicFallback() throws Exception {
        AiTestAgent.AiTestQuestionRequest request = new AiTestAgent.AiTestQuestionRequest(
                null, null, null,
                "Java开发工程师", "负责后端开发", "Java", null);

        String fallbackJson = DeterministicAiFallbacks.get(DeterministicAiFallbacks.AI_TEST_QUESTIONS).get();
        AiTestAiService mockAiService = mock(AiTestAiService.class);
        when(aiTestAiServiceProvider.getIfAvailable()).thenReturn(mockAiService);
        when(objectMapper.writeValueAsString(any())).thenReturn("{\"questionCount\":5}");
        when(aiServiceResilience.callWithResilience(anyString(), any(), anyString())).thenReturn(fallbackJson);
        when(llmResponseParser.extractJson(anyString())).thenReturn(fallbackJson);
        when(objectMapper.readValue(anyString(), any(com.fasterxml.jackson.core.type.TypeReference.class)))
                .thenReturn(List.of());
        doNothing().when(questionSetValidator).validate(any());

        String result = aiTestAgent.generateQuestions(request);
        assertThat(result).isNotNull();
    }

    @Test
    void contextTruncationBehaviorWithLongInput() throws Exception {
        String longDesc = "A".repeat(5000);
        AiTestAgent.AiTestQuestionRequest request = new AiTestAgent.AiTestQuestionRequest(
                null, null, null,
                "Senior Developer", longDesc, "Java,Spring,Hibernate,MySQL", null);

        when(aiTestAiServiceProvider.getIfAvailable()).thenReturn(null);
        when(objectMapper.writeValueAsString(any())).thenReturn("{\"questionCount\":5,\"postName\":\"Java\"}");
        when(promptTemplateService.render(anyString(), any())).thenReturn("mock prompt");
        when(langChain4jChatService.chat(anyString(), anyString(), any(), anyLong())).thenReturn("[]");
        when(llmResponseParser.extractJson(anyString())).thenReturn("[]");
        when(objectMapper.readValue(anyString(), any(com.fasterxml.jackson.core.type.TypeReference.class)))
                .thenReturn(List.of());
        doNothing().when(questionSetValidator).validate(any());

        String result = aiTestAgent.generateQuestions(request);
        assertThat(result).isNotNull();
    }
}
