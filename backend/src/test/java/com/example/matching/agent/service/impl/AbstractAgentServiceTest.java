package com.example.matching.agent.service.impl;

import com.example.matching.agent.dto.AgentRunResult;
import com.example.matching.agent.dto.AgentSourceRef;
import com.fasterxml.jackson.core.JsonProcessingException;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

class AbstractAgentServiceTest {

    private static class FakeResult extends AgentRunResult {
    }

    private static class TestAgentService extends AbstractAgentService {
        TestAgentService(AgentRunConfidencePolicy confidencePolicy) {
            super(confidencePolicy);
        }
    }

    private final AgentRunConfidencePolicy policy = new AgentRunConfidencePolicy();
    private final TestAgentService agent = new TestAgentService(policy);

    @Test
    void runWithFallbackInvokesPrimaryOnceAndReturnsResult() {
        AtomicInteger primaryCalls = new AtomicInteger();
        AtomicInteger fallbackCalls = new AtomicInteger();
        FakeResult expected = new FakeResult();

        FakeResult actual = agent.runWithFallback(
                () -> {
                    primaryCalls.incrementAndGet();
                    return expected;
                },
                ex -> {
                    fallbackCalls.incrementAndGet();
                    return new FakeResult();
                });

        assertThat(actual).isSameAs(expected);
        assertThat(primaryCalls.get()).isEqualTo(1);
        assertThat(fallbackCalls.get()).isEqualTo(0);
    }

    @Test
    void runWithFallbackInvokesFallbackExactlyOnceWhenPrimaryThrows() {
        AtomicInteger primaryCalls = new AtomicInteger();
        AtomicInteger fallbackCalls = new AtomicInteger();
        RuntimeException cause = new RuntimeException("Boom");
        FakeResult fallbackResult = new FakeResult();

        FakeResult actual = agent.runWithFallback(
                () -> {
                    primaryCalls.incrementAndGet();
                    throw cause;
                },
                ex -> {
                    fallbackCalls.incrementAndGet();
                    assertThat(ex).isSameAs(cause);
                    return fallbackResult;
                });

        assertThat(actual).isSameAs(fallbackResult);
        assertThat(primaryCalls.get()).isEqualTo(1);
        assertThat(fallbackCalls.get()).isEqualTo(1);
    }

    @Test
    void runWithFallbackHandlesCheckedExceptions() {
        FakeResult fallbackResult = new FakeResult();

        FakeResult actual = agent.runWithFallback(
                () -> {
                    throw new JsonProcessingException("invalid JSON") { };
                },
                ex -> fallbackResult);

        assertThat(actual).isSameAs(fallbackResult);
    }

    @Test
    void finalizeRunSetsAllFields() {
        AgentSourceRef ref = new AgentSourceRef();
        ref.setConfidenceScore(new BigDecimal("80"));
        List<AgentSourceRef> refs = List.of(ref);
        FakeResult result = new FakeResult();

        FakeResult finalized = agent.finalizeRun(result, refs, true, "raw");

        assertThat(finalized).isSameAs(result);
        assertThat(finalized.getSourceRefs()).isEqualTo(refs);
        assertThat(finalized.getFallbackUsed()).isTrue();
        assertThat(finalized.getRawModelOutput()).isEqualTo("raw");
        assertThat(finalized.getOverallConfidence()).isEqualTo(new BigDecimal("60.00"));
    }

    @Test
    void finalizeRunCapsFallbackConfidence() {
        AgentSourceRef ref = new AgentSourceRef();
        ref.setConfidenceScore(new BigDecimal("90"));
        FakeResult result = new FakeResult();

        agent.finalizeRun(result, List.of(ref), true, null);

        assertThat(result.getFallbackUsed()).isTrue();
        assertThat(result.getOverallConfidence()).isEqualTo(new BigDecimal("60.00"));
    }

    @Test
    void finalizeRunHandlesNullRefs() {
        FakeResult result = new FakeResult();
        agent.finalizeRun(result, null, false, null);

        assertThat(result.getSourceRefs()).isEmpty();
        assertThat(result.getFallbackUsed()).isFalse();
    }
}
