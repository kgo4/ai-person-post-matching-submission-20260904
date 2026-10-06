package com.example.matching.dto.kg.api;

import io.swagger.v3.oas.annotations.media.Schema;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Schema(description = "图谱关系候选响应")
public record GraphRelationCandidateResponse(
        @Schema(description = "主键ID") Long id,
        @Schema(description = "候选编码") String candidateCode,
        @Schema(description = "源节点Key") String sourceNodeKey,
        @Schema(description = "目标节点Key") String targetNodeKey,
        @Schema(description = "关系类型") String relationType,
        @Schema(description = "发现方法") String discoveryMethod,
        @Schema(description = "语义评分") BigDecimal semanticScore,
        @Schema(description = "来源引用JSON") String sourceRefsJson,
        @Schema(description = "审核状态") String reviewStatus,
        @Schema(description = "审核原因") String reviewReason,
        @Schema(description = "审核人ID") Long reviewedBy,
        @Schema(description = "审核时间") LocalDateTime reviewedTime,
        @Schema(description = "创建人ID") Long createdBy,
        @Schema(description = "创建时间") LocalDateTime createdTime,
        @Schema(description = "更新时间") LocalDateTime updatedTime
) implements Serializable {
}
