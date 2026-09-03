package com.example.matching.dto.post.api;

import io.swagger.v3.oas.annotations.media.Schema;

import java.io.Serializable;
import java.time.LocalDateTime;

@Schema(description = "岗位原型响应")
public record PostPrototypeResponse(
        @Schema(description = "原型ID") Long id,
        @Schema(description = "原型名称") String prototypeName,
        @Schema(description = "原型描述") String description,
        @Schema(description = "行业方向") String industry,
        @Schema(description = "岗位族分类") String category,
        @Schema(description = "状态：0停用，1启用") Integer status,
        @Schema(description = "创建时间") LocalDateTime createdTime
) implements Serializable {
}
