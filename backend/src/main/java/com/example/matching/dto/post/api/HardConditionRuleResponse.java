package com.example.matching.dto.post.api;

import io.swagger.v3.oas.annotations.media.Schema;

import java.io.Serializable;
import java.time.LocalDateTime;

@Schema(description = "硬性条件规则响应")
public record HardConditionRuleResponse(
        @Schema(description = "主键ID") Long id,
        @Schema(description = "岗位ID") Long postId,
        @Schema(description = "字段名") String fieldName,
        @Schema(description = "展示名称") String fieldLabel,
        @Schema(description = "字段类型") String fieldType,
        @Schema(description = "操作符") String operator,
        @Schema(description = "期望值") String expectedValue,
        @Schema(description = "枚举等级映射JSON") String valueRankJson,
        @Schema(description = "是否启用") Integer enabled,
        @Schema(description = "排序") Integer sortOrder,
        @Schema(description = "备注") String remark,
        @Schema(description = "创建时间") LocalDateTime createdTime,
        @Schema(description = "更新时间") LocalDateTime updatedTime
) implements Serializable {
}
