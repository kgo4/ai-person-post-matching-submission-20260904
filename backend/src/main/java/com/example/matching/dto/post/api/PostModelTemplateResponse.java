package com.example.matching.dto.post.api;

import io.swagger.v3.oas.annotations.media.Schema;

import java.io.Serializable;
import java.time.LocalDateTime;

@Schema(description = "岗位模型模板响应")
public record PostModelTemplateResponse(
        @Schema(description = "主键ID") Long id,
        @Schema(description = "模板编码") String templateCode,
        @Schema(description = "模板名称") String templateName,
        @Schema(description = "岗位序列") String postSequence,
        @Schema(description = "适用职级范围") String postLevelRange,
        @Schema(description = "模板描述") String description,
        @Schema(description = "状态：0停用，1启用") Integer status,
        @Schema(description = "创建时间") LocalDateTime createdTime,
        @Schema(description = "更新时间") LocalDateTime updatedTime
) implements Serializable {
}
