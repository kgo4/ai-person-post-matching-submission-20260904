package com.example.matching.dto.post.api;

import io.swagger.v3.oas.annotations.media.Schema;

import java.io.Serializable;
import java.math.BigDecimal;

@Schema(description = "模板能力项请求")
public record TemplateAbilityItemRequest(
        @Schema(description = "能力标签ID") Long tagId,
        @Schema(description = "最低要求等级") Integer minRequiredLevel,
        @Schema(description = "权重") BigDecimal weight,
        @Schema(description = "是否必填") Integer isRequired,
        @Schema(description = "是否核心项") Integer isCore,
        @Schema(description = "备注") String remark
) implements Serializable {
}
