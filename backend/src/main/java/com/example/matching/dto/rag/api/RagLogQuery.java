package com.example.matching.dto.rag.api;

import io.swagger.v3.oas.annotations.media.Schema;

import java.io.Serializable;

@Schema(description = "RAG日志查询参数")
public record RagLogQuery(
        @Schema(description = "RAG场景") String scenario
) implements Serializable {
}
