package com.example.matching.dto.post.api;

import io.swagger.v3.oas.annotations.media.Schema;

import java.io.Serializable;

@Schema(description = "岗位能力模型请求")
public record PostAbilityModelRequest(
        @Schema(description = "岗位ID") Long postId,
        @Schema(description = "能力标签ID") Long abilityTagId,
        @Schema(description = "最低要求等级") Integer minRequiredLevel,
        @Schema(description = "权重") java.math.BigDecimal weight
) implements Serializable {
}
