package com.example.matching.agent.service.impl;

import com.example.matching.agent.dto.AgentRunResult;
import com.example.matching.agent.dto.AgentSourceRef;
import lombok.extern.slf4j.Slf4j;

import java.util.List;
import java.util.function.Function;

@Slf4j
public abstract class AbstractAgentService {

    @FunctionalInterface
    protected interface ThrowingSupplier<T> {
        T get() throws Exception;
    }

    protected final AgentRunConfidencePolicy confidencePolicy;

    protected AbstractAgentService(AgentRunConfidencePolicy confidencePolicy) {
        this.confidencePolicy = confidencePolicy;
    }

    protected <T extends AgentRunResult> T runWithFallback(
            ThrowingSupplier<T> primary,
            Function<Exception, T> fallback) {
        try {
            return primary.get();
        } catch (Exception e) {
            log.warn("Agent execution failed, using fallback", e);
            return fallback.apply(e);
        }
    }

    protected <T extends AgentRunResult> T finalizeRun(T result, List<AgentSourceRef> refs,
                                                        boolean fallbackUsed, String rawOutput) {
        result.setSourceRefs(refs == null ? List.of() : refs);
        result.setFallbackUsed(fallbackUsed);
        result.setRawModelOutput(rawOutput);
        result.setOverallConfidence(confidencePolicy.calculate(result.getSourceRefs(), fallbackUsed));
        return result;
    }
}
