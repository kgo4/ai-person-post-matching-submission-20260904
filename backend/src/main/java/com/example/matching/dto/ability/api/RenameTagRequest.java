package com.example.matching.dto.ability.api;

import io.swagger.v3.oas.annotations.media.Schema;

import java.io.Serializable;

@Schema(description = "重命名标签请求")
public record RenameTagRequest(
        @Schema(description = "标签ID") Long tagId,
        @Schema(description = "新标签名称") String newName,
        @Schema(description = "重命名原因") String reason
) implements Serializable {
}
