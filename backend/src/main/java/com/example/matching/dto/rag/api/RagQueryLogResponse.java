package com.example.matching.dto.rag.api;

import io.swagger.v3.oas.annotations.media.Schema;

import java.io.Serializable;
import java.time.LocalDateTime;

@Schema(description = "RAG查询日志响应")
public record RagQueryLogResponse(
        @Schema(description = "主键ID") Long id,
        @Schema(description = "查询编码") String queryCode,
        @Schema(description = "RAG场景") String scenario,
        @Schema(description = "查询文本") String queryText,
        @Schema(description = "请求的topK") Integer topK,
        @Schema(description = "检索到的分块ID") String retrievedChunkIds,
        @Schema(description = "注入到prompt的上下文") String contextText,
        @Schema(description = "最终prompt快照") String promptSnapshot,
        @Schema(description = "模型响应快照") String responseSnapshot,
        @Schema(description = "延迟毫秒") Long latencyMs,
        @Schema(description = "命中数量") Integer hitCount,
        @Schema(description = "创建人ID") Long createdBy,
        @Schema(description = "创建时间") LocalDateTime createdTime
) implements Serializable {
}
