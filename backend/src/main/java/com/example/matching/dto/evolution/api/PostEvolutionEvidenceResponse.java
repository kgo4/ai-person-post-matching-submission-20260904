package com.example.matching.dto.evolution.api;

import io.swagger.v3.oas.annotations.media.Schema;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Schema(description = "演化证据响应")
public record PostEvolutionEvidenceResponse(
        @Schema(description = "主键ID") Long id,
        @Schema(description = "演化任务ID") Long taskId,
        @Schema(description = "关联的变更项ID") Long changeItemId,
        @Schema(description = "证据来源类型") String sourceType,
        @Schema(description = "来源数据ID") Long sourceId,
        @Schema(description = "来源标题") String sourceTitle,
        @Schema(description = "来源链接") String sourceUrl,
        @Schema(description = "证据原文片段") String evidenceText,
        @Schema(description = "来源发布时间") LocalDateTime publishedTime,
        @Schema(description = "采集时间") LocalDateTime collectedTime,
        @Schema(description = "来源可信度权重") BigDecimal sourceWeight,
        @Schema(description = "相关度分数") BigDecimal similarityScore,
        @Schema(description = "综合可信度分数") BigDecimal trustScore,
        @Schema(description = "统一sourceRef格式") String sourceRef,
        @Schema(description = "创建时间") LocalDateTime createdTime
) implements Serializable {
}
