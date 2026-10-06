package com.example.matching.dto.rag.api;

import io.swagger.v3.oas.annotations.media.Schema;

import java.io.Serializable;

@Schema(description = "Harness检查请求")
public record HarnessCheckRequest(
        @Schema(description = "审核状态") String reviewStatus,
        @Schema(description = "审核意见") String reviewComment
) implements Serializable {
}
