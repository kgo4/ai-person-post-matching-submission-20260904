package com.example.matching.application.system;

import com.example.matching.dto.system.api.RuntimeAuditAggregateResponse;
import com.example.matching.service.rag.RagQueryLogService;
import com.example.matching.service.system.AuditQueryService;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * 审计图表聚合的映射测试。
 *
 * <p>这里真正要防的是**静默拿到 0**：聚合查询返回的是 `List<Map<String, Object>>`，
 * 值的实际类型取决于驱动（COUNT 是 Long、AVG/ROUND 是 BigDecimal），列名的**大小写**
 * 也可能随驱动/连接参数变化。一旦按原样取键或强转，图表会安静地画出全 0 —— 没有异常、
 * 没有日志，只有"看着像没数据"。
 */
class AdminRuntimeAuditApiFacadeAggregateTest {

    private static AdminRuntimeAuditApiFacade facade(AuditQueryService audit, RagQueryLogService rag) {
        return new AdminRuntimeAuditApiFacade(new SimpleMeterRegistry(), audit, rag);
    }

    private static Map<String, Object> promptTrendRow(String hour, Object callCount, Object avgMs) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("hourBucket", hour);
        row.put("callCount", callCount);
        row.put("avgLatencyMs", avgMs);
        row.put("maxLatencyMs", 4200L);
        row.put("failedCount", 1);
        row.put("cacheHitCount", 3);
        return row;
    }

    @Test
    @DisplayName("数值列无论返回 Long/BigDecimal/Integer 都能取到，不会静默变 0")
    void coercesNumericTypesFromAggregateRows() {
        AuditQueryService audit = mock(AuditQueryService.class);
        RagQueryLogService rag = mock(RagQueryLogService.class);
        when(audit.promptLatencyTrend(any(LocalDateTime.class))).thenReturn(List.of(
                promptTrendRow("09:00", 12L, new BigDecimal("1234.56")),
                promptTrendRow("10:00", 7, new BigDecimal("900.00"))));
        when(audit.promptBreakdown(any(LocalDateTime.class))).thenReturn(List.of());
        when(rag.latencyTrend(any(LocalDateTime.class))).thenReturn(List.of());
        when(rag.scenarioBreakdown(any(LocalDateTime.class))).thenReturn(List.of());

        RuntimeAuditAggregateResponse result = facade(audit, rag).aggregate(24);

        assertEquals(2, result.latencyTrend().size());
        RuntimeAuditAggregateResponse.LatencyPoint first = result.latencyTrend().get(0);
        assertEquals("09:00", first.hour());
        assertEquals(12L, first.callCount(), "Long 计数要原样取到");
        assertEquals(1234.56d, first.avgLatencyMs(), 0.01d, "BigDecimal 均值要转成 double");
        assertEquals(4200d, first.maxLatencyMs(), 0.01d);
        assertEquals(1L, first.failedCount());
        assertEquals(3L, first.cacheHitCount());

        // Integer 也要能取到（第二次调用的 callCount 是 int 字面量）
        assertEquals(7L, result.latencyTrend().get(1).callCount());
    }

    @Test
    @DisplayName("列名大小写不同也能取到值（否则图表会全是 0 且没有任何报错）")
    void toleratesColumnNameCaseDifferences() {
        Map<String, Object> lowercased = new LinkedHashMap<>();
        lowercased.put("hourbucket", "11:00");
        lowercased.put("callcount", 5L);
        lowercased.put("avglatencyms", new BigDecimal("50.5"));
        lowercased.put("maxlatencyms", 60L);
        lowercased.put("failedcount", 0L);
        lowercased.put("cachehitcount", 0L);

        RuntimeAuditAggregateResponse.LatencyPoint point =
                AdminRuntimeAuditApiFacade.toLatencyPoint(lowercased);

        assertEquals("11:00", point.hour());
        assertEquals(5L, point.callCount());
        assertEquals(50.5d, point.avgLatencyMs(), 0.01d);
    }

    @Test
    @DisplayName("空行 / null 值不抛异常，落到 0 与空字符串")
    void handlesEmptyAndNullValues() {
        RuntimeAuditAggregateResponse.LatencyPoint fromNull =
                AdminRuntimeAuditApiFacade.toLatencyPoint(null);
        assertEquals("", fromNull.hour());
        assertEquals(0L, fromNull.callCount());
        assertEquals(0d, fromNull.avgLatencyMs());

        Map<String, Object> sparse = new LinkedHashMap<>();
        sparse.put("hourBucket", null);
        RuntimeAuditAggregateResponse.PromptPoint fromSparse =
                AdminRuntimeAuditApiFacade.toPromptPoint(sparse);
        assertEquals("", fromSparse.promptName());
        assertEquals(0L, fromSparse.callCount());
    }

    @Test
    @DisplayName("非数值脏值不抛异常（聚合列被写成字符串时不能让整个审计页 500）")
    void survivesNonNumericValues() {
        Map<String, Object> dirty = new LinkedHashMap<>();
        dirty.put("hourBucket", "12:00");
        dirty.put("callCount", "not-a-number");
        dirty.put("avgLatencyMs", "");

        RuntimeAuditAggregateResponse.LatencyPoint point =
                AdminRuntimeAuditApiFacade.toLatencyPoint(dirty);

        assertEquals("12:00", point.hour());
        assertEquals(0L, point.callCount());
        assertEquals(0d, point.avgLatencyMs());
    }

    @Test
    @DisplayName("统计窗口：默认 24 小时，非法值回落，超大值封顶 720 小时")
    void clampsAggregateWindow() {
        AuditQueryService audit = mock(AuditQueryService.class);
        RagQueryLogService rag = mock(RagQueryLogService.class);
        when(audit.promptLatencyTrend(any(LocalDateTime.class))).thenReturn(List.of());
        when(audit.promptBreakdown(any(LocalDateTime.class))).thenReturn(List.of());
        when(rag.latencyTrend(any(LocalDateTime.class))).thenReturn(List.of());
        when(rag.scenarioBreakdown(any(LocalDateTime.class))).thenReturn(List.of());

        AdminRuntimeAuditApiFacade facade = facade(audit, rag);
        assertEquals(24, facade.aggregate(0).windowHours(), "0/负数回落为默认 24 小时");
        assertEquals(24, facade.aggregate(-5).windowHours());
        assertEquals(6, facade.aggregate(6).windowHours());
        assertEquals(720, facade.aggregate(100000).windowHours(), "上限 720 小时，避免把全部历史拉进内存");
        assertTrue(facade.aggregate(24).latencyTrend().isEmpty(), "没有数据时返回空列表而不是 null");
    }

    @Test
    @DisplayName("RAG 聚合行同样按别名取值（场景列名 scenario / queryCount / avgHitCount）")
    void mapsRagAggregateRows() {
        Map<String, Object> ragTrend = new LinkedHashMap<>();
        ragTrend.put("hourBucket", "08:00");
        ragTrend.put("queryCount", 4L);
        ragTrend.put("avgLatencyMs", new BigDecimal("280.4"));
        ragTrend.put("maxLatencyMs", 900L);
        ragTrend.put("avgHitCount", new BigDecimal("3.5"));
        ragTrend.put("degradedCount", 1L);

        RuntimeAuditAggregateResponse.RagTrendPoint trend =
                AdminRuntimeAuditApiFacade.toRagTrendPoint(ragTrend);
        assertEquals("08:00", trend.hour());
        assertEquals(4L, trend.queryCount());
        assertEquals(280.4d, trend.avgLatencyMs(), 0.01d);
        assertEquals(3.5d, trend.avgHitCount(), 0.01d);
        assertEquals(1L, trend.degradedCount());

        Map<String, Object> scenario = new LinkedHashMap<>();
        scenario.put("scenario", "JD_ABILITY_EXTRACT");
        scenario.put("queryCount", 9L);
        scenario.put("avgLatencyMs", new BigDecimal("100.0"));
        scenario.put("avgHitCount", new BigDecimal("2.0"));

        RuntimeAuditAggregateResponse.RagScenarioPoint point =
                AdminRuntimeAuditApiFacade.toRagScenarioPoint(scenario);
        assertEquals("JD_ABILITY_EXTRACT", point.scenario());
        assertEquals(9L, point.queryCount());
        assertEquals(2.0d, point.avgHitCount(), 0.01d);
    }
}
