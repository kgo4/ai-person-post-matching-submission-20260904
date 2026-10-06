package com.example.matching.agent.config;

import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.output.TokenUsage;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ObservedChatLanguageModelTest {

    @Test
    void recordsLatencyAndTokenUsageForLlmCalls() {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        ChatModel delegate = new ChatModel() {
            @Override
            public ChatResponse chat(ChatRequest request) {
                return ChatResponse.builder()
                        .aiMessage(AiMessage.aiMessage("ok"))
                        .tokenUsage(new TokenUsage(3, 5, 8))
                        .build();
            }
        };
        ObservedChatLanguageModel model = new ObservedChatLanguageModel(
                delegate, new AgentObservationMetrics(registry));

        model.chat(ChatRequest.builder()
                .messages(List.of(dev.langchain4j.data.message.UserMessage.from("hello")))
                .build());

        assertEquals(1d, registry.get("agent.llm.calls").tag("tools", "false")
                .tag("outcome", "success").counter().count());
        assertEquals(3d, registry.get("agent.llm.tokens").tag("direction", "input").counter().count());
        assertEquals(5d, registry.get("agent.llm.tokens").tag("direction", "output").counter().count());
        assertEquals(8d, registry.get("agent.llm.tokens").tag("direction", "total").counter().count());
    }
}
