package com.example.matching.dto.system.api;

import io.swagger.v3.oas.annotations.media.Schema;

import java.io.Serializable;
import java.util.List;

/**
 * 运行审计的**图表聚合**数据（审计页把统计做成图而不是只列表格）。
 *
 * <p>四个序列分别对应页面上的四张图：
 * <ol>
 *   <li>{@link #latencyTrend()} —— LLM 调用量与平均/最大响应时间（按时段）</li>
 *   <li>{@link #promptBreakdown()} —— 各 Prompt 的调用量与平均耗时（找出最慢/最热的 Prompt）</li>
 *   <li>{@link #ragLatencyTrend()} —— RAG 检索量与延迟（按时段）</li>
 *   <li>{@link #ragScenarioBreakdown()} —— 各 RAG 场景的检索量与命中数</li>
 * </ol>
 *
 * <p>⚠️ 时段标签**只到小时**（如 {@code 09:00}），不带日期：窗口是"最近 N 小时"的滚动区间，
 * 服务端不返回日期也就不会在界面上出现与窗口无关的日期。
 */
@Schema(description = "运行审计图表聚合数据")
public record RuntimeAuditAggregateResponse(
        @Schema(description = "统计窗口（小时）") int windowHours,
        @Schema(description = "LLM 调用趋势（按时段）") List<LatencyPoint> latencyTrend,
        @Schema(description = "按 Prompt 聚合") List<PromptPoint> promptBreakdown,
        @Schema(description = "RAG 检索趋势（按时段）") List<RagTrendPoint> ragLatencyTrend,
        @Schema(description = "按 RAG 场景聚合") List<RagScenarioPoint> ragScenarioBreakdown
) implements Serializable {

    @Schema(description = "LLM 调用趋势点")
    public record LatencyPoint(
            @Schema(description = "时段，如 09:00") String hour,
            @Schema(description = "调用量") long callCount,
            @Schema(description = "平均耗时（毫秒）") double avgLatencyMs,
            @Schema(description = "最大耗时（毫秒）") double maxLatencyMs,
            @Schema(description = "失败数") long failedCount,
            @Schema(description = "缓存命中数") long cacheHitCount
    ) implements Serializable {
    }

    @Schema(description = "Prompt 聚合点")
    public record PromptPoint(
            @Schema(description = "Prompt 名称") String promptName,
            @Schema(description = "调用量") long callCount,
            @Schema(description = "平均耗时（毫秒）") double avgLatencyMs,
            @Schema(description = "最大耗时（毫秒）") double maxLatencyMs,
            @Schema(description = "失败数") long failedCount
    ) implements Serializable {
    }

    @Schema(description = "RAG 检索趋势点")
    public record RagTrendPoint(
            @Schema(description = "时段，如 09:00") String hour,
            @Schema(description = "检索量") long queryCount,
            @Schema(description = "平均耗时（毫秒）") double avgLatencyMs,
            @Schema(description = "最大耗时（毫秒）") double maxLatencyMs,
            @Schema(description = "平均命中数") double avgHitCount,
            @Schema(description = "降级次数") long degradedCount
    ) implements Serializable {
    }

    @Schema(description = "RAG 场景聚合点")
    public record RagScenarioPoint(
            @Schema(description = "场景") String scenario,
            @Schema(description = "检索量") long queryCount,
            @Schema(description = "平均耗时（毫秒）") double avgLatencyMs,
            @Schema(description = "平均命中数") double avgHitCount
    ) implements Serializable {
    }
}
