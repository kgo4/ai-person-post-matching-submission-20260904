package com.example.matching.agent.config;

import dev.langchain4j.model.chat.Capability;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;

import java.util.Set;

/** Wraps the LangChain4j model so every Agent LLM round-trip is observable. */
public class ObservedChatLanguageModel implements ChatModel {

    private final ChatModel delegate;
    private final AgentObservationMetrics metrics;

    public ObservedChatLanguageModel(ChatModel delegate, AgentObservationMetrics metrics) {
        this.delegate = delegate;
        this.metrics = metrics;
    }

    @Override
    public ChatResponse chat(ChatRequest request) {
        boolean toolEnabled = request.toolSpecifications() != null && !request.toolSpecifications().isEmpty();
        long startNanos = System.nanoTime();
        try {
            ChatResponse response = delegate.chat(request);
            metrics.recordLlmCall(toolEnabled, true, System.nanoTime() - startNanos, response.tokenUsage());
            return response;
        } catch (RuntimeException ex) {
            metrics.recordLlmCall(toolEnabled, false, System.nanoTime() - startNanos, null);
            throw ex;
        }
    }

    @Override
    public Set<Capability> supportedCapabilities() {
        return delegate.supportedCapabilities();
    }
}
