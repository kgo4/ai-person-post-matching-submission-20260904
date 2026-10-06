package com.example.matching.dto.learning.api;

import io.swagger.v3.oas.annotations.media.Schema;

import java.io.Serializable;
import java.time.LocalDateTime;

@Schema(description = "知识点响应")
public record KnowledgeNodeResponse(
        @Schema(description = "主键ID") Long id,
        @Schema(description = "知识点编码") String nodeCode,
        @Schema(description = "知识点名称") String nodeName,
        @Schema(description = "所属领域ID") Long domainId,
        @Schema(description = "父知识点ID") Long parentId,
        @Schema(description = "知识点层级") Integer nodeLevel,
        @Schema(description = "知识点描述") String nodeDescription,
        @Schema(description = "学习目标") String learningObjectives,
        @Schema(description = "前置知识点JSON") String prerequisitesJson,
        @Schema(description = "排序序号") Integer sortOrder,
        @Schema(description = "状态") String status,
        @Schema(description = "创建人ID") Long createdBy,
        @Schema(description = "创建时间") LocalDateTime createdTime,
        @Schema(description = "更新人ID") Long updatedBy,
        @Schema(description = "更新时间") LocalDateTime updatedTime,
        @Schema(description = "乐观锁版本号") Integer version
) implements Serializable {
}
