package com.example.matching.application.system;

import com.example.matching.agent.config.AgentObservationMetrics;
import com.example.matching.dto.system.api.RuntimeMetricsResponse;
import dev.langchain4j.model.output.TokenUsage;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 运行审计指标读取的测试。
 *
 * <p>这里最关键的不是「算得对不对」，而是**读写两端的指标名必须一致**：
 * 指标名是纯字符串，写端（{@code AgentObservationMetrics}）和读端
 * （{@code AdminRuntimeAuditApiFacade}）各自硬编码一份，一旦有人改名，
 * 页面上会安静地全显示 0 —— 没有异常、没有日志。
 * 所以本测试**用真实的写端去写，再用读端去读**。
 */
class AdminRuntimeAuditApiFacadeTest {

    private static AdminRuntimeAuditApiFacade facadeOn(SimpleMeterRegistry registry) {
        // 指标读取只需要 MeterRegistry；另外两个依赖（审计查询/RAG 日志）与本节无关
        return new AdminRuntimeAuditApiFacade(registry, null, null);
    }

    @Test
    @DisplayName("token 消耗：写端记多少，读端就能读到多少")
    void readsTokenCountersWrittenByObservationMetrics() {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        AgentObservationMetrics writer = new AgentObservationMetrics(registry);

        writer.recordLlmCall(false, true, Duration.ofMillis(500).toNanos(), new TokenUsage(100, 40));
        writer.recordLlmCall(true, true, Duration.ofMillis(1500).toNanos(), new TokenUsage(200, 60));
        writer.recordLlmCall(false, false, Duration.ofMillis(100).toNanos(), new TokenUsage(10, 5));

        RuntimeMetricsResponse metrics = facadeOn(registry).metrics();

        assertEquals(310, metrics.inputTokens());
        assertEquals(105, metrics.outputTokens());
        assertEquals(415, metrics.totalTokens());
        assertEquals(3, metrics.llmCallCount());
        assertEquals(1, metrics.llmErrorCount());
        assertTrue(metrics.llmAvgMs() > 0, "LLM 平均响应时间应被读到");
        assertTrue(metrics.llmMaxMs() >= metrics.llmAvgMs(), "最大响应时间不应小于平均值");
        assertTrue(metrics.note() != null && metrics.note().contains("进程级累计"),
                "累计口径必须写在 note 里，避免被当成实时/按天数据");
    }

    @Test
    @DisplayName("工具调用：次数、失败数与缓存命中分别可读")
    void readsToolCounters() {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        AgentObservationMetrics writer = new AgentObservationMetrics(registry);

        writer.recordToolCall("search", true, true, Duration.ofMillis(20).toNanos());
        writer.recordToolCall("search", false, true, Duration.ofMillis(80).toNanos());
        writer.recordToolCall("search", false, false, Duration.ofMillis(5).toNanos());
        writer.recordJsonGuard("retry");

        RuntimeMetricsResponse metrics = facadeOn(registry).metrics();

        assertEquals(3, metrics.toolCallCount());
        assertEquals(1, metrics.toolErrorCount());
        assertEquals(1, metrics.toolCacheHitCount());
        assertTrue(metrics.toolAvgMs() > 0);
        assertEquals(1, metrics.jsonGuardCount());
    }

    @Test
    @DisplayName("没有任何埋点时返回全 0，而不是 null 或抛异常")
    void returnsZerosWhenNothingRecorded() {
        RuntimeMetricsResponse metrics = facadeOn(new SimpleMeterRegistry()).metrics();

        assertEquals(0, metrics.inputTokens());
        assertEquals(0, metrics.outputTokens());
        assertEquals(0, metrics.totalTokens());
        assertEquals(0, metrics.llmCallCount());
        assertEquals(0d, metrics.llmAvgMs());
        assertEquals(0d, metrics.llmMaxMs());
        assertEquals(0, metrics.toolCallCount());
        assertEquals(0d, metrics.toolAvgMs());
    }
}
