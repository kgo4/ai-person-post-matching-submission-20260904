package com.example.matching.dto.employee.api;

import java.io.Serializable;
import java.time.LocalDate;
import java.time.LocalDateTime;

public record EmployeeResponse(
        Long id,
        String empCode,
        String realName,
        Integer gender,
        String phone,
        String email,
        Long departmentId,
        Long currentPostId,
        LocalDate entryDate,
        String level,
        String extendFields,
        Integer isLocked,
        Integer status,
        LocalDateTime createdTime,
        LocalDateTime updatedTime) implements Serializable {
}
