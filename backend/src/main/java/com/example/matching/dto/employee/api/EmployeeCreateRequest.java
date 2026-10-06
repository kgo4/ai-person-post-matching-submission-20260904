package com.example.matching.dto.employee.api;

import java.io.Serializable;

/**
 * 新增人员请求。
 * <p>empCode 为可选字段，留空则由系统自动生成（格式：EMPyyyyMMdd####）。</p>
 */
public record EmployeeCreateRequest(
        String empCode,
        String realName,
        Integer gender,
        String idCard,
        String phone,
        String email,
        String extendFields) implements Serializable {
}
