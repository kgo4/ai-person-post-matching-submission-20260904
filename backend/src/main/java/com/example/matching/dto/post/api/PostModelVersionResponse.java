package com.example.matching.dto.post.api;

import io.swagger.v3.oas.annotations.media.Schema;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Schema(description = "岗位模型版本响应")
public record PostModelVersionResponse(
        @Schema(description = "主键ID") Long id,
        @Schema(description = "岗位ID") Long postId,
        @Schema(description = "版本号") String versionNo,
        @Schema(description = "来源类型") String sourceType,
        @Schema(description = "状态") String status,
        @Schema(description = "质量评分") BigDecimal qualityScore,
        @Schema(description = "能力项数量") Integer itemCount,
        @Schema(description = "权重总和") BigDecimal totalWeight,
        @Schema(description = "版本说明") String description,
        @Schema(description = "发布时间") LocalDateTime publishTime,
        @Schema(description = "创建时间") LocalDateTime createdTime,
        @Schema(description = "更新时间") LocalDateTime updatedTime,
        @Schema(description = "未匹配能力标签列表（AI提取但未匹配已有标签的能力，M-07）") List<UnmatchedAbilityDTO> unmatchedAbilities
) implements Serializable {
}
