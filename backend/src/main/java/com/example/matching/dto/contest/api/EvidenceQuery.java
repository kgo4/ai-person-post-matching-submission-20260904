package com.example.matching.dto.contest.api;

import io.swagger.v3.oas.annotations.media.Schema;

import java.io.Serializable;

@Schema(description = "证据查询参数")
public record EvidenceQuery(
        @Schema(description = "来源类型") String sourceType,
        @Schema(description = "目标类型") String targetType,
        @Schema(description = "证据状态") String evidenceStatus,
        @Schema(description = "能力名称") String abilityName
) implements Serializable {
}
