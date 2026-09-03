package com.example.matching.dto.ability.api;

import io.swagger.v3.oas.annotations.media.Schema;

import java.io.Serializable;

@Schema(description = "替换能力标签请求")
public record ReplaceTagRequest(
        @Schema(description = "员工ID") Long empId,
        @Schema(description = "原标签ID") Long oldTagId,
        @Schema(description = "新标签ID") Long newTagId,
        @Schema(description = "修改原因") String reason,
        @Schema(description = "是否泛化为全局规则（勾选\"作为同类提取规则\"）") boolean generalizeRule
) implements Serializable {
}
