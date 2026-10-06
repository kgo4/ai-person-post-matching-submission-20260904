package com.example.matching.dto.learning.api;

import io.swagger.v3.oas.annotations.media.Schema;

import java.io.Serializable;

@Schema(description = "学习资源更新请求")
public record LearningResourceUpdateRequest(
        @Schema(description = "主键ID") Long id,
        @Schema(description = "资源编码") String resourceCode,
        @Schema(description = "关联能力名称") String abilityName,
        @Schema(description = "关联能力标签ID") Long tagId,
        @Schema(description = "资源标题") String title,
        @Schema(description = "资源类型") String resourceType,
        @Schema(description = "难度等级") Integer difficultyLevel,
        @Schema(description = "资源URL") String url,
        @Schema(description = "资源描述") String description,
        @Schema(description = "资源平台") String platform,
        @Schema(description = "平台图标标识") String platformIcon,
        @Schema(description = "封面图URL") String coverImageUrl,
        @Schema(description = "学习时长描述") String duration,
        @Schema(description = "排序权重") Integer sortOrder,
        @Schema(description = "状态") Integer status
) implements Serializable {
}
