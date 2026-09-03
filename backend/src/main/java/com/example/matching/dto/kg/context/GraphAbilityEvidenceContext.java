package com.example.matching.dto.kg.context;

import io.swagger.v3.oas.annotations.media.Schema;

import java.io.Serializable;
import java.util.List;

/**
 * 能力的已审核证据上下文（紧凑 DTO）
 */
@Schema(description = "能力证据上下文")
public record GraphAbilityEvidenceContext(
        @Schema(description = "能力ID") Long abilityId,
        @Schema(description = "能力名称") String abilityName,
        @Schema(description = "已审核证据列表") List<GraphEvidenceContext> evidence
) implements Serializable {
}
