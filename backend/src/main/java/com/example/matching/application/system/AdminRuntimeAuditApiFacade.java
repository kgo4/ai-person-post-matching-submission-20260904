package com.example.matching.application.system;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.example.matching.common.dto.PageResponse;
import com.example.matching.dto.system.api.PromptInvocationLogResponse;
import com.example.matching.dto.system.api.RagAuditLogResponse;
import com.example.matching.dto.system.api.RuntimeAuditAggregateResponse;
import com.example.matching.dto.system.api.RuntimeMetricsResponse;
import com.example.matching.entity.rag.RagQueryLog;
import com.example.matching.entity.system.PromptInvocationLog;
import com.example.matching.service.rag.RagQueryLogService;
import com.example.matching.service.system.AuditQueryService;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * 运行审计：Agent 运行时指标 + Prompt 调用明细 + RAG 检索日志。
 *
 * <p>页面归属 **PLATFORM_ADMIN**（权限码 {@code AUDIT:READ}）。这三类数据都是
 * 「AI 基础设施的运行状况」，不是业务知识，所以不放在岗位体系管理员那一侧。
 *
 * <p>为什么要把 {@link MeterRegistry} 放在这里而不是 Controller：Controller 只做参数与鉴权，
 * 指标的读取/聚合是可复用逻辑；且本项目约定 Controller 不直接接触基础设施 bean。
 *
 * <p>⚠️ 指标是**进程级累计**（Micrometer Counter/Timer），重启归零、没有时间维度。
 * 需要逐次明细请看 Prompt 调用日志与 RAG 检索日志。
 */
@Service
@RequiredArgsConstructor
public class AdminRuntimeAuditApiFacade {

    /** 与 {@code agent/config/AgentObservationMetrics} 中注册的指标名保持一致。 */
    private static final String METRIC_LLM_TOKENS = "agent.llm.tokens";
    private static final String METRIC_LLM_CALLS = "agent.llm.calls";
    private static final String METRIC_LLM_DURATION = "agent.llm.duration";
    private static final String METRIC_TOOL_CALLS = "agent.tool.calls";
    private static final String METRIC_TOOL_DURATION = "agent.tool.duration";
    private static final String METRIC_JSON_GUARD = "agent.json.guard";

    private static final String NOTE =
            "进程级累计指标（自本次应用启动起），重启归零；逐次明细见下方 Prompt 调用记录。";

    private final MeterRegistry meterRegistry;
    private final AuditQueryService auditQueryService;
    private final RagQueryLogService ragQueryLogService;

    // ------------------------------------------------------------------ 指标

    public RuntimeMetricsResponse metrics() {
        long inputTokens = tokenCount("input");
        long outputTokens = tokenCount("output");
        long totalTokens = tokenCount("total");

        long llmCalls = counterTotal(METRIC_LLM_CALLS);
        long llmErrors = taggedCounter(METRIC_LLM_CALLS, "outcome", "error");
        long toolCalls = counterTotal(METRIC_TOOL_CALLS);
        long toolErrors = taggedCounter(METRIC_TOOL_CALLS, "outcome", "error");
        long toolCacheHits = taggedCounter(METRIC_TOOL_CALLS, "cache", "hit");
        long jsonGuard = counterTotal(METRIC_JSON_GUARD);

        return new RuntimeMetricsResponse(
                inputTokens, outputTokens, totalTokens,
                llmCalls, llmErrors,
                timerAvgMs(METRIC_LLM_DURATION), timerMaxMs(METRIC_LLM_DURATION),
                toolCalls, toolErrors, toolCacheHits,
                timerAvgMs(METRIC_TOOL_DURATION), timerMaxMs(METRIC_TOOL_DURATION),
                jsonGuard, NOTE);
    }

    private long tokenCount(String direction) {
        var search = meterRegistry.find(METRIC_LLM_TOKENS);
        if (direction != null) {
            search = search.tag("direction", direction);
        }
        double sum = 0d;
        for (Counter counter : search.counters()) {
            sum += counter.count();
        }
        return (long) sum;
    }

    private long counterTotal(String name) {
        double sum = 0d;
        for (Counter counter : meterRegistry.find(name).counters()) {
            sum += counter.count();
        }
        return (long) sum;
    }

    private long taggedCounter(String name, String tagKey, String tagValue) {
        Counter counter = meterRegistry.find(name).tag(tagKey, tagValue).counter();
        return counter == null ? 0L : (long) counter.count();
    }

    private double timerAvgMs(String name) {
        long count = 0L;
        double totalMs = 0d;
        for (Timer timer : meterRegistry.find(name).timers()) {
            count += timer.count();
            totalMs += timer.totalTime(TimeUnit.MILLISECONDS);
        }
        return count == 0L ? 0d : round2(totalMs / count);
    }

    private double timerMaxMs(String name) {
        double max = 0d;
        for (Timer timer : meterRegistry.find(name).timers()) {
            max = Math.max(max, timer.max(TimeUnit.MILLISECONDS));
        }
        return round2(max);
    }

    private static double round2(double value) {
        return Math.round(value * 100d) / 100d;
    }

    // ------------------------------------------------------- Prompt 调用明细

    public PageResponse<PromptInvocationLogResponse> pagePromptLogs(long current, long size,
                                                                   String promptName, Boolean success) {
        IPage<PromptInvocationLog> page = auditQueryService.pagePromptLogs(
                new Page<>(current, size), promptName, success);
        return PageResponse.from(page, AdminRuntimeAuditApiFacade::toPromptResponse);
    }

    static PromptInvocationLogResponse toPromptResponse(PromptInvocationLog e) {
        if (e == null) return null;
        return new PromptInvocationLogResponse(
                e.getId(), e.getPromptName(), e.getPromptVersion(), e.getScenario(), e.getModelName(),
                e.getLatencyMs(), e.getToolLatencyMs(), e.getQueueWaitMs(),
                e.getModelRounds(), e.getRetryCount(),
                e.getCacheHit(), e.getSuccess(), e.getFallbackUsed(),
                e.getInputChars(), e.getOutputChars(),
                e.getUserId(), e.getTraceId(), e.getFeedbackScore(), e.getCreatedTime());
    }

    // --------------------------------------------------------- RAG 检索日志

    public PageResponse<RagAuditLogResponse> pageRagLogs(long current, long size, String scenario) {
        IPage<RagQueryLog> page = ragQueryLogService.pageLogs(new Page<>(current, size), scenario);
        return PageResponse.from(page, AdminRuntimeAuditApiFacade::toRagResponse);
    }

    // ------------------------------------------------------------- 图表聚合

    /** 聚合窗口上限：避免把"全部历史"拉进内存（图表也画不出那么多点）。 */
    private static final int MAX_WINDOW_HOURS = 24 * 30;

    /**
     * 审计统计的图表数据。
     *
     * <p>四组序列对应页面上四张图，全部在 SQL 侧按小时 / 按维度聚合后返回，
     * 前端只负责画图 —— 不把明细行拉到前端做聚合（审计窗口内可能上万行）。
     */
    public RuntimeAuditAggregateResponse aggregate(int hours) {
        int window = hours > 0 ? Math.min(hours, MAX_WINDOW_HOURS) : 24;
        LocalDateTime since = LocalDateTime.now().minusHours(window);

        List<RuntimeAuditAggregateResponse.LatencyPoint> latencyTrend =
                auditQueryService.promptLatencyTrend(since).stream()
                        .map(AdminRuntimeAuditApiFacade::toLatencyPoint)
                        .toList();
        List<RuntimeAuditAggregateResponse.PromptPoint> promptBreakdown =
                auditQueryService.promptBreakdown(since).stream()
                        .map(AdminRuntimeAuditApiFacade::toPromptPoint)
                        .toList();
        List<RuntimeAuditAggregateResponse.RagTrendPoint> ragTrend =
                ragQueryLogService.latencyTrend(since).stream()
                        .map(AdminRuntimeAuditApiFacade::toRagTrendPoint)
                        .toList();
        List<RuntimeAuditAggregateResponse.RagScenarioPoint> ragScenarios =
                ragQueryLogService.scenarioBreakdown(since).stream()
                        .map(AdminRuntimeAuditApiFacade::toRagScenarioPoint)
                        .toList();

        return new RuntimeAuditAggregateResponse(window, latencyTrend, promptBreakdown, ragTrend, ragScenarios);
    }

    static RuntimeAuditAggregateResponse.LatencyPoint toLatencyPoint(Map<String, Object> row) {
        return new RuntimeAuditAggregateResponse.LatencyPoint(
                text(row, "hourBucket"), asLong(row, "callCount"), asDouble(row, "avgLatencyMs"),
                asDouble(row, "maxLatencyMs"), asLong(row, "failedCount"), asLong(row, "cacheHitCount"));
    }

    static RuntimeAuditAggregateResponse.PromptPoint toPromptPoint(Map<String, Object> row) {
        return new RuntimeAuditAggregateResponse.PromptPoint(
                text(row, "promptName"), asLong(row, "callCount"), asDouble(row, "avgLatencyMs"),
                asDouble(row, "maxLatencyMs"), asLong(row, "failedCount"));
    }

    static RuntimeAuditAggregateResponse.RagTrendPoint toRagTrendPoint(Map<String, Object> row) {
        return new RuntimeAuditAggregateResponse.RagTrendPoint(
                text(row, "hourBucket"), asLong(row, "queryCount"), asDouble(row, "avgLatencyMs"),
                asDouble(row, "maxLatencyMs"), asDouble(row, "avgHitCount"), asLong(row, "degradedCount"));
    }

    static RuntimeAuditAggregateResponse.RagScenarioPoint toRagScenarioPoint(Map<String, Object> row) {
        return new RuntimeAuditAggregateResponse.RagScenarioPoint(
                text(row, "scenario"), asLong(row, "queryCount"),
                asDouble(row, "avgLatencyMs"), asDouble(row, "avgHitCount"));
    }

    /**
     * 按别名取列值，**忽略大小写**。
     *
     * <p>必须容错：JDBC 驱动/连接参数不同时，同一个 `AS hourBucket` 可能以
     * `hourBucket`、`hourbucket` 甚至 `HOURBUCKET` 出现在 Map 里 —— 只按原样取键
     * 会安静地拿到 null，图表画出来全是 0，没有任何报错。
     */
    private static Object value(Map<String, Object> row, String alias) {
        if (row == null || row.isEmpty()) {
            return null;
        }
        Object direct = row.get(alias);
        if (direct != null) {
            return direct;
        }
        for (Map.Entry<String, Object> entry : row.entrySet()) {
            if (entry.getKey() != null && entry.getKey().equalsIgnoreCase(alias)) {
                return entry.getValue();
            }
        }
        return null;
    }

    private static String text(Map<String, Object> row, String alias) {
        Object v = value(row, alias);
        return v == null ? "" : String.valueOf(v);
    }

    /** 聚合结果里的数值可能是 Long / Integer / BigDecimal / Double，统一收敛。 */
    private static long asLong(Map<String, Object> row, String alias) {
        Object v = value(row, alias);
        if (v instanceof Number n) {
            return n.longValue();
        }
        if (v == null) {
            return 0L;
        }
        try {
            return Long.parseLong(String.valueOf(v).trim());
        } catch (NumberFormatException e) {
            return 0L;
        }
    }

    private static double asDouble(Map<String, Object> row, String alias) {
        Object v = value(row, alias);
        if (v instanceof Number n) {
            return round2(n.doubleValue());
        }
        if (v == null) {
            return 0d;
        }
        try {
            return round2(Double.parseDouble(String.valueOf(v).trim()));
        } catch (NumberFormatException e) {
            return 0d;
        }
    }

    static RagAuditLogResponse toRagResponse(RagQueryLog e) {
        if (e == null) return null;
        return new RagAuditLogResponse(
                e.getId(), e.getQueryCode(), e.getScenario(),
                truncate(e.getQueryText(), 200),
                e.getTopK(), e.getHitCount(), e.getLatencyMs(),
                e.getIsDegraded(), e.getFallbackReason(), e.getContextTokenEstimate(),
                e.getCreatedBy(), e.getCreatedTime());
    }

    /** 列表只展示摘要，避免把整段 JD/简历正文带进页面。 */
    private static String truncate(String text, int max) {
        if (text == null || text.length() <= max) {
            return text;
        }
        return text.substring(0, max) + "…";
    }
}


