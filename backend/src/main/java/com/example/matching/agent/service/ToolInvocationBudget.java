package com.example.matching.agent.service;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 请求级工具调用配额 —— 在全局 20 次/90 秒预算内再对单个工具设限，
 * 防止模型把预算浪费在重复的宽泛搜索上。
 * <p>
 * 线程安全、请求作用域；配额耗尽返回稳定结果而非抛异常。
 */
public class ToolInvocationBudget {

    /** 单个工具默认上限 */
    public static final int DEFAULT_PER_TOOL_LIMIT = 6;
    /** 宽泛搜索类工具上限 */
    public static final int SEARCH_NODES_LIMIT = 4;
    /** 图连接类工具上限 */
    public static final int GET_NODE_CONNECTIONS_LIMIT = 2;

    private static final String EXHAUSTED_RESULT =
            "{ \"available\": false, \"reason\": \"tool_budget_exhausted\" }";

    private final Map<String, Integer> invocationCounts = new ConcurrentHashMap<>();
    private final Map<String, Integer> perToolLimits;
    private final MeterRegistry meterRegistry;

    public ToolInvocationBudget(MeterRegistry meterRegistry) {
        this(meterRegistry, Map.of(
                "searchNodes", SEARCH_NODES_LIMIT,
                "getNodeConnections", GET_NODE_CONNECTIONS_LIMIT));
    }

    public ToolInvocationBudget(MeterRegistry meterRegistry, Map<String, Integer> perToolLimits) {
        this.meterRegistry = meterRegistry;
        this.perToolLimits = perToolLimits;
    }

    /**
     * 尝试占用一次配额。
     *
     * @param toolName 工具名
     * @return true 允许执行；false 配额耗尽（调用方应返回 {@link #exhaustedResult()})
     */
    public boolean tryAcquire(String toolName) {
        int limit = perToolLimits.getOrDefault(toolName, DEFAULT_PER_TOOL_LIMIT);
        int count = invocationCounts.merge(toolName, 1, Integer::sum);
        if (count > limit) {
            Counter.builder("agent.tool.budget_exhausted")
                    .tag("tool", toolName)
                    .register(meterRegistry)
                    .increment();
            return false;
        }
        return true;
    }

    /**
     * 配额耗尽时返回的稳定工具结果（不抛异常）。
     */
    public String exhaustedResult() {
        return EXHAUSTED_RESULT;
    }
}
