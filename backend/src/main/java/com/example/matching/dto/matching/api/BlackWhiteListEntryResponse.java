package com.example.matching.dto.matching.api;

import io.swagger.v3.oas.annotations.media.Schema;

import java.io.Serializable;
import java.time.LocalDateTime;

@Schema(description = "黑白名单响应")
public record BlackWhiteListEntryResponse(
    @Schema(description = "主键ID") Long id,
    @Schema(description = "员工ID") Long empId,
    @Schema(description = "岗位ID") Long postId,
    @Schema(description = "名单类型：1白名单，2黑名单") Integer listType,
    @Schema(description = "设置原因备注") String remark,
    @Schema(description = "状态：0失效，1生效") Integer status,
    @Schema(description = "设置人ID") Long createdBy,
    @Schema(description = "设置时间") LocalDateTime createdTime
) implements Serializable {

    private static final long serialVersionUID = 1L;
}
