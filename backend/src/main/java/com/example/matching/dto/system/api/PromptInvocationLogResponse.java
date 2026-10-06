package com.example.matching.dto.system.api;

import io.swagger.v3.oas.annotations.media.Schema;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * Prompt 调用明细（逐次），数据源 {@code prompt_invocation_log}。
 *
 * <p>这是「响应时间」类审计里**粒度最细**的一份：每次 LLM 调用都会写一行，
 * 含总耗时、工具耗时、排队等待、模型轮次、重试次数、是否命中缓存、是否降级到备用模型。
 *
 * <p>过去这张表**没有任何 HTTP 接口**（只有 {@code /api/admin/prompts/experiments}
 * 给出的按 Prompt×版本聚合的均值），平台管理员在界面上看不到明细。
 */
@Schema(description = "Prompt 调用明细")
public record PromptInvocationLogResponse(
        @Schema(description = "主键ID") Long id,
        @Schema(description = "Prompt 名称") String promptName,
        @Schema(description = "Prompt 版本") String promptVersion,
        @Schema(description = "业务场景") String scenario,
        @Schema(description = "模型名") String modelName,
        @Schema(description = "总耗时（毫秒）") Long latencyMs,
        @Schema(description = "工具耗时（毫秒）") Long toolLatencyMs,
        @Schema(description = "排队等待（毫秒）") Long queueWaitMs,
        @Schema(description = "模型轮次") Integer modelRounds,
        @Schema(description = "重试次数") Integer retryCount,
        @Schema(description = "是否命中缓存") Boolean cacheHit,
        @Schema(description = "是否成功") Boolean success,
        @Schema(description = "是否降级到备用模型") Boolean fallbackUsed,
        @Schema(description = "输入字符数") Integer inputChars,
        @Schema(description = "输出字符数") Integer outputChars,
        @Schema(description = "调用人ID") Long userId,
        @Schema(description = "追踪ID") String traceId,
        @Schema(description = "人工反馈分") Integer feedbackScore,
        @Schema(description = "创建时间") LocalDateTime createdTime
) implements Serializable {
}
