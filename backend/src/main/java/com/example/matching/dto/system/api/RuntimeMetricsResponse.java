package com.example.matching.dto.system.api;

import io.swagger.v3.oas.annotations.media.Schema;

import java.io.Serializable;

/**
 * Agent 运行时指标快照（token 消耗 / LLM 与工具响应时间）。
 *
 * <p>数据源是 Micrometer 指标（{@code agent.llm.tokens}、{@code agent.llm.calls}、
 * {@code agent.llm.duration}、{@code agent.tool.*}、{@code agent.json.guard}），
 * 由 {@code agent/config/AgentObservationMetrics} 在每次 LLM/工具调用后累加。
 *
 * <p>⚠️ 这些是**进程级累计计数**：只有聚合值，没有「按小时/按模型」的明细，且**应用重启后归零**。
 * 需要逐次明细时看 Prompt 调用日志（{@code /api/admin/runtime-audit/prompt-logs/page}）。
 */
@Schema(description = "Agent 运行时指标快照（进程级累计，重启归零）")
public record RuntimeMetricsResponse(
        @Schema(description = "输入 token 累计") long inputTokens,
        @Schema(description = "输出 token 累计") long outputTokens,
        @Schema(description = "总 token 累计") long totalTokens,
        @Schema(description = "LLM 调用次数") long llmCallCount,
        @Schema(description = "LLM 失败次数") long llmErrorCount,
        @Schema(description = "LLM 平均响应时间（毫秒）") double llmAvgMs,
        @Schema(description = "LLM 最大响应时间（毫秒）") double llmMaxMs,
        @Schema(description = "工具调用次数") long toolCallCount,
        @Schema(description = "工具调用失败次数") long toolErrorCount,
        @Schema(description = "工具缓存命中次数") long toolCacheHitCount,
        @Schema(description = "工具平均耗时（毫秒）") double toolAvgMs,
        @Schema(description = "工具最大耗时（毫秒）") double toolMaxMs,
        @Schema(description = "JSON 守卫拦截次数") long jsonGuardCount,
        @Schema(description = "口径说明") String note
) implements Serializable {
}
