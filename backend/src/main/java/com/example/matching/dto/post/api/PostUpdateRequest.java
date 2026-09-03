package com.example.matching.dto.post.api;

import io.swagger.v3.oas.annotations.media.Schema;

import java.io.Serializable;

@Schema(description = "更新岗位请求")
public record PostUpdateRequest(
        @Schema(description = "岗位编码") String postCode,
        @Schema(description = "岗位名称") String postName,
        @Schema(description = "岗位职责描述") String jobDescription,
        @Schema(description = "状态：0停用，1启用") Integer status,
        @Schema(description = "岗位级别") String postLevel
) implements Serializable {
}
