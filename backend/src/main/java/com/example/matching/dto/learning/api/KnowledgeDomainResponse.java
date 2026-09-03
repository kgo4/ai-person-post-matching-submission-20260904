package com.example.matching.dto.learning.api;

import io.swagger.v3.oas.annotations.media.Schema;

import java.io.Serializable;
import java.time.LocalDateTime;

@Schema(description = "知识领域响应")
public record KnowledgeDomainResponse(
        @Schema(description = "主键ID") Long id,
        @Schema(description = "领域编码") String domainCode,
        @Schema(description = "领域名称") String domainName,
        @Schema(description = "领域图标") String domainIcon,
        @Schema(description = "领域颜色") String domainColor,
        @Schema(description = "领域权重") Integer domainWeight,
        @Schema(description = "领域描述") String domainDescription,
        @Schema(description = "父领域ID") Long parentId,
        @Schema(description = "排序序号") Integer sortOrder,
        @Schema(description = "状态") String status,
        @Schema(description = "创建人ID") Long createdBy,
        @Schema(description = "创建时间") LocalDateTime createdTime,
        @Schema(description = "更新人ID") Long updatedBy,
        @Schema(description = "更新时间") LocalDateTime updatedTime,
        @Schema(description = "乐观锁版本号") Integer version
) implements Serializable {
}
