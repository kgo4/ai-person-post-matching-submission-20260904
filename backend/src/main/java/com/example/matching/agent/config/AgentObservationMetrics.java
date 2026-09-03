package com.example.matching.agent.config;

import dev.langchain4j.model.output.TokenUsage;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tags;
import io.micrometer.core.instrument.Timer;
import org.springframework.stereotype.Component;

import java.time.Duration;

/** Low-cardinality metrics for Agent tool and LLM execution. */
@Component
public class AgentObservationMetrics {

    private final MeterRegistry meterRegistry;

    public AgentObservationMetrics(MeterRegistry meterRegistry) {
        this.meterRegistry = meterRegistry;
    }

    public void recordToolCall(String toolName, boolean cacheHit, boolean success, long elapsedNanos) {
        Tags tags = Tags.of("tool", toolName, "cache", cacheHit ? "hit" : "miss",
                "outcome", success ? "success" : "error");
        Counter.builder("agent.tool.calls").tags(tags).register(meterRegistry).increment();
        Timer.builder("agent.tool.duration").tags(tags).register(meterRegistry)
                .record(Duration.ofNanos(elapsedNanos));
    }

    public void recordLlmCall(boolean toolEnabled, boolean success, long elapsedNanos, TokenUsage tokenUsage) {
        Tags tags = Tags.of("tools", Boolean.toString(toolEnabled), "outcome", success ? "success" : "error");
        Counter.builder("agent.llm.calls").tags(tags).register(meterRegistry).increment();
        Timer.builder("agent.llm.duration").tags(tags).register(meterRegistry)
                .record(Duration.ofNanos(elapsedNanos));

        if (tokenUsage == null) {
            return;
        }
        recordTokens("input", tokenUsage.inputTokenCount());
        recordTokens("output", tokenUsage.outputTokenCount());
        recordTokens("total", tokenUsage.totalTokenCount());
    }

    private void recordTokens(String direction, Integer count) {
        if (count == null || count < 0) {
            return;
        }
        Counter.builder("agent.llm.tokens").tag("direction", direction)
                .register(meterRegistry).increment(count);
    }

    public void recordJsonGuard(String outcome) {
        Counter.builder("agent.json.guard").tag("outcome", outcome)
                .register(meterRegistry).increment();
    }
}
