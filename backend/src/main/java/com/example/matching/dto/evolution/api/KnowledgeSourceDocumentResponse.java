package com.example.matching.dto.evolution.api;

import io.swagger.v3.oas.annotations.media.Schema;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Schema(description = "知识源文档响应")
public record KnowledgeSourceDocumentResponse(
        @Schema(description = "主键ID") Long id,
        @Schema(description = "来源类型") String sourceType,
        @Schema(description = "资料类别") String sourceCategory,
        @Schema(description = "来源引用ID") Long sourceRefId,
        @Schema(description = "文档标题") String title,
        @Schema(description = "文档版本") String documentVersion,
        @Schema(description = "行业") String industry,
        @Schema(description = "业务领域") String businessDomain,
        @Schema(description = "上传人ID") Long uploaderId,
        @Schema(description = "上传人角色") String uploaderRole,
        @Schema(description = "资料负责人") String sourceOwner,
        @Schema(description = "权威等级") String authorityLevel,
        @Schema(description = "权威度评分") BigDecimal authorityScore,
        @Schema(description = "发布时间") LocalDateTime publishedTime,
        @Schema(description = "采集时间") LocalDateTime collectedTime,
        @Schema(description = "资料生效时间") LocalDateTime effectiveTime,
        @Schema(description = "可信等级") String trustLevel,
        @Schema(description = "时效性评分") BigDecimal freshnessScore,
        @Schema(description = "质量评分") BigDecimal qualityScore,
        @Schema(description = "内容哈希") String contentHash,
        @Schema(description = "近似哈希") String simHash,
        @Schema(description = "重复组键") String duplicateGroupKey,
        @Schema(description = "知识库ID") String knowledgeBaseId,
        @Schema(description = "云文档ID") String cloudDocumentId,
        @Schema(description = "可见范围") String visibility,
        @Schema(description = "文档状态") String status,
        @Schema(description = "是否参与岗位演化") Integer evolutionEnabled,
        @Schema(description = "切片数量") Integer chunkCount,
        @Schema(description = "最后索引时间") LocalDateTime lastIndexedTime,
        @Schema(description = "创建人ID") Long createdBy,
        @Schema(description = "创建时间") LocalDateTime createdTime,
        @Schema(description = "更新时间") LocalDateTime updatedTime
) implements Serializable {
}
