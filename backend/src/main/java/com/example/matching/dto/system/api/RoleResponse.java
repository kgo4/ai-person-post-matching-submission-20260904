package com.example.matching.dto.system.api;

import java.time.LocalDateTime;

public record RoleResponse(
    Long id,
    String roleCode,
    String roleName,
    String description,
    Integer dataScope,
    Integer status,
    LocalDateTime createdTime,
    LocalDateTime updatedTime
) {}
