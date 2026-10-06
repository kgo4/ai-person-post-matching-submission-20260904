package com.example.matching.dto.kg.context;

import io.swagger.v3.oas.annotations.media.Schema;

import java.io.Serializable;
import java.math.BigDecimal;
import java.util.List;

/**
 * 人岗匹配中单个能力的上下文
 */
@Schema(description = "人岗匹配能力上下文")
public record GraphMatchAbilityContext(
        @Schema(description = "能力ID") Long abilityId,
        @Schema(description = "能力名称") String abilityName,
        @Schema(description = "权重") BigDecimal weight,
        @Schema(description = "岗位要求等级") Integer requiredLevel,
        @Schema(description = "员工掌握等级") Integer employeeMasteryLevel,
        @Schema(description = "是否必须") boolean required,
        @Schema(description = "是否核心") boolean core,
        @Schema(description = "匹配状态") GraphMatchState state,
        @Schema(description = "已审核证据列表") List<GraphEvidenceContext> evidence
) implements Serializable {
}
