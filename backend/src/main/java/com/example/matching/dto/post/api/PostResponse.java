package com.example.matching.dto.post.api;

import io.swagger.v3.oas.annotations.media.Schema;

import java.io.Serializable;
import java.time.LocalDateTime;

@Schema(description = "岗位响应")
public record PostResponse(
        @Schema(description = "主键ID") Long id,
        @Schema(description = "岗位编码") String postCode,
        @Schema(description = "岗位名称") String postName,
        @Schema(description = "岗位职责描述") String jobDescription,
        @Schema(description = "状态：0停用，1启用") Integer status,
        @Schema(description = "岗位级别") String postLevel,
        @Schema(description = "所属部门ID") Long departmentId,
        @Schema(description = "创建时间") LocalDateTime createdTime,
        @Schema(description = "更新时间") LocalDateTime updatedTime
) implements Serializable {
}
