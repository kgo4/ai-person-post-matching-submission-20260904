package com.example.matching.dto.post.api;

import io.swagger.v3.oas.annotations.media.Schema;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Schema(description = "岗位能力模型响应")
public record PostAbilityModelResponse(
        @Schema(description = "主键ID") Long id,
        @Schema(description = "岗位ID") Long postId,
        @Schema(description = "能力标签ID") Long tagId,
        @Schema(description = "岗位能力名称；未关联标签时用于岗位画像展示") String abilityName,
        @Schema(description = "最低要求等级") Integer minRequiredLevel,
        @Schema(description = "权重") BigDecimal weight,
        @Schema(description = "是否必填") Integer isRequired,
        @Schema(description = "是否核心项") Integer isCore,
        @Schema(description = "模型版本号") String modelVersion,
        @Schema(description = "备注") String remark,
        @Schema(description = "创建时间") LocalDateTime createdTime,
        @Schema(description = "更新时间") LocalDateTime updatedTime
) implements Serializable {
}
