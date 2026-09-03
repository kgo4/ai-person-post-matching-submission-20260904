package com.example.matching.dto.ability.api;

import io.swagger.v3.oas.annotations.media.Schema;

import java.io.Serializable;

@Schema(description = "治理事件查询参数")
public record GovernanceEventQuery(
        @Schema(description = "页码", example = "1") Integer pageNum,
        @Schema(description = "每页大小", example = "10") Integer pageSize,
        @Schema(description = "修改类型筛选") String modifyType,
        @Schema(description = "员工ID筛选") Long empId,
        @Schema(description = "标签ID筛选") Long tagId
) implements Serializable {
}
