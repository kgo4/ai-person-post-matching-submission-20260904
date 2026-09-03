package com.example.matching.dto.post.api;

import io.swagger.v3.oas.annotations.media.Schema;

import java.io.Serializable;

@Schema(description = "岗位原型请求")
public record PostPrototypeRequest(
        @Schema(description = "原型名称") String prototypeName,
        @Schema(description = "原型描述") String description,
        @Schema(description = "行业方向") String industry,
        @Schema(description = "岗位族分类") String category,
        @Schema(description = "状态：0停用，1启用") Integer status
) implements Serializable {
}
