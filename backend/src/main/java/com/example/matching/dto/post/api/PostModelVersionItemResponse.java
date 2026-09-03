package com.example.matching.dto.post.api;

import io.swagger.v3.oas.annotations.media.Schema;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Schema(description = "岗位模型版本明细响应")
public record PostModelVersionItemResponse(
        @Schema(description = "主键ID") Long id,
        @Schema(description = "版本ID") Long versionId,
        @Schema(description = "能力标签ID") Long tagId,
        @Schema(description = "最低要求等级") Integer minRequiredLevel,
        @Schema(description = "权重") BigDecimal weight,
        @Schema(description = "是否必填") Integer isRequired,
        @Schema(description = "是否核心项") Integer isCore,
        @Schema(description = "配置理由") String reason,
        @Schema(description = "创建时间") LocalDateTime createdTime
) implements Serializable {
}
