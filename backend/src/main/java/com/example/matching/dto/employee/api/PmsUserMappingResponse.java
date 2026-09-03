package com.example.matching.dto.employee.api;

import java.io.Serializable;
import java.time.LocalDateTime;

public record PmsUserMappingResponse(
        Long id,
        Long empId,
        Long pmsUserId,
        String pmsUsername,
        String pmsNickname,
        String pmsEmployeeId,
        LocalDateTime createdTime) implements Serializable {
}
