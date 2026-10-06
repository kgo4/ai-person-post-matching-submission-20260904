package com.example.matching.dto.matching.api;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;

import java.io.Serializable;

@Schema(description = "黑白名单新增/更新请求")
public record BlackWhiteListEntryRequest(
    @NotNull(message = "员工ID不能为空")
    @Schema(description = "员工ID", example = "10001") Long empId,

    @NotNull(message = "岗位ID不能为空")
    @Schema(description = "岗位ID", example = "20001") Long postId,

    @NotNull(message = "名单类型不能为空")
    @Schema(description = "名单类型：1白名单（强制匹配），2黑名单（强制排除）", example = "1") Integer listType,

    @Schema(description = "设置原因备注") String remark
) implements Serializable {

    private static final long serialVersionUID = 1L;
}
