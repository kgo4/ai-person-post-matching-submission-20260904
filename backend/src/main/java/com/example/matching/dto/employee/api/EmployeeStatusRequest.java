package com.example.matching.dto.employee.api;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;

/**
 * 员工启用 / 禁用请求。
 *
 * <p>用于 HR 对员工档案唯一的“下线”操作，不涉及任何删除语义。</p>
 *
 * @param status 0=禁用，1=启用
 */
@Schema(description = "员工启用/禁用请求")
public record EmployeeStatusRequest(
        @Schema(description = "状态：0=禁用，1=启用", example = "0")
        @NotNull
        Integer status) {
}
