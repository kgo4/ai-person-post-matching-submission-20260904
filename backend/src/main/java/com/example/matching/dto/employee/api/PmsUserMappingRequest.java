package com.example.matching.dto.employee.api;

import java.io.Serializable;

public record PmsUserMappingRequest(
        Long empId,
        Long pmsUserId) implements Serializable {
}
