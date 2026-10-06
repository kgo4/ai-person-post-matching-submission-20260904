package com.example.matching.dto.post.api;

import io.swagger.v3.oas.annotations.media.Schema;

import java.io.Serializable;

@Schema(description = "岗位模型模板请求")
public record PostModelTemplateRequest(
        @Schema(description = "模板名称") String templateName,
        @Schema(description = "模板描述") String description,
        @Schema(description = "状态：0停用，1启用") Integer status
) implements Serializable {
}
