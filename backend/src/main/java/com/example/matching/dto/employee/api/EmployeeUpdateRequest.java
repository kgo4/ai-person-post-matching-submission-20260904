package com.example.matching.dto.employee.api;

import java.io.Serializable;

public record EmployeeUpdateRequest(
        String empCode,
        String realName,
        Integer gender,
        String idCard,
        String phone,
        String email,
        String extendFields) implements Serializable {
}
