package com.example.matching.dto.contest.api;

import io.swagger.v3.oas.annotations.media.Schema;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Schema(description = "证据响应")
public record ContestEvidenceResponse(
        @Schema(description = "主键ID") Long id,
        @Schema(description = "证据编码") String evidenceCode,
        @Schema(description = "来源类型") String sourceType,
        @Schema(description = "来源记录ID") Long sourceRefId,
        @Schema(description = "来源标题或文件名") String sourceTitle,
        @Schema(description = "原始来源片段") String sourceText,
        @Schema(description = "目标类型") String targetType,
        @Schema(description = "目标实体ID") Long targetRefId,
        @Schema(description = "能力名称") String abilityName,
        @Schema(description = "标签ID") Long tagId,
        @Schema(description = "置信度") BigDecimal confidenceScore,
        @Schema(description = "可信度") BigDecimal credibilityScore,
        @Schema(description = "证据状态") String evidenceStatus,
        @Schema(description = "人工审核意见") String reviewComment,
        @Schema(description = "RAG知识分块ID列表") List<Long> ragChunkIds,
        @Schema(description = "RAG知识文档ID列表") List<Long> ragDocumentIds,
        @Schema(description = "审核人ID") Long reviewedBy,
        @Schema(description = "审核时间") LocalDateTime reviewedTime,
        @Schema(description = "创建人ID") Long createdBy,
        @Schema(description = "创建时间") LocalDateTime createdTime,
        @Schema(description = "更新人ID") Long updatedBy,
        @Schema(description = "更新时间") LocalDateTime updatedTime
) implements Serializable {
}
