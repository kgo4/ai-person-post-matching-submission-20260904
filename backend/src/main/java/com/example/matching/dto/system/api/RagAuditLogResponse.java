package com.example.matching.dto.system.api;

import io.swagger.v3.oas.annotations.media.Schema;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * RAG 检索日志（审计精简版），数据源 {@code rag_query_log}。
 *
 * <p>与 {@code /api/rag/logs/page} 返回的完整版区别：**不带** contextText / promptSnapshot /
 * responseSnapshot 三个 longtext 字段 —— 审计列表只需要看「检索延迟、命中数、是否降级」，
 * 把长文本带给列表会让单页响应变得很重。
 */
@Schema(description = "RAG 检索日志（审计精简版）")
public record RagAuditLogResponse(
        @Schema(description = "主键ID") Long id,
        @Schema(description = "查询编码") String queryCode,
        @Schema(description = "RAG 场景") String scenario,
        @Schema(description = "查询文本") String queryText,
        @Schema(description = "请求 topK") Integer topK,
        @Schema(description = "命中分块数") Integer hitCount,
        @Schema(description = "检索延迟（毫秒）") Long latencyMs,
        @Schema(description = "是否降级") Boolean isDegraded,
        @Schema(description = "降级原因") String fallbackReason,
        @Schema(description = "上下文 token 估算") Integer contextTokenEstimate,
        @Schema(description = "调用人ID") Long createdBy,
        @Schema(description = "创建时间") LocalDateTime createdTime
) implements Serializable {
}
