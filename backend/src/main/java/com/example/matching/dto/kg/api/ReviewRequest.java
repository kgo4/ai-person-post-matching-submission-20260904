package com.example.matching.dto.kg.api;

import io.swagger.v3.oas.annotations.media.Schema;

import java.io.Serializable;

@Schema(description = "审核请求")
public record ReviewRequest(
        @Schema(description = "审核状态") String reviewStatus,
        @Schema(description = "审核原因") String reviewReason
) implements Serializable {
}
