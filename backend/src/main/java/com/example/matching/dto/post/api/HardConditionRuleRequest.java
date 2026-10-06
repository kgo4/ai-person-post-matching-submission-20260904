package com.example.matching.dto.post.api;

import io.swagger.v3.oas.annotations.media.Schema;

import java.io.Serializable;

@Schema(description = "硬性条件规则请求")
public record HardConditionRuleRequest(
        @Schema(description = "岗位ID") Long postId,
        @Schema(description = "规则字段名") String ruleField,
        @Schema(description = "操作符") String ruleOperator,
        @Schema(description = "期望值") String ruleValue,
        @Schema(description = "是否启用") Integer isRequired,
        @Schema(description = "排序") Integer sortOrder
) implements Serializable {
}
