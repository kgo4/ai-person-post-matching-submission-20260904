package com.example.matching.dto.ability.api;

import io.swagger.v3.oas.annotations.media.Schema;

import java.io.Serializable;

@Schema(description = "修改能力等级请求")
public record ChangeLevelRequest(
        @Schema(description = "员工ID") Long empId,
        @Schema(description = "标签ID") Long tagId,
        @Schema(description = "新等级") Integer newLevel,
        @Schema(description = "修改原因") String reason,
        @Schema(description = "是否泛化为全局规则") boolean generalizeRule,
        @Schema(description = "等级上限（泛化规则时可选）") Integer maxLevelCap
) implements Serializable {
}
