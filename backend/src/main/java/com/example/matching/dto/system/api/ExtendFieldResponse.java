package com.example.matching.dto.system.api;

import java.time.LocalDateTime;

public record ExtendFieldResponse(
    Long id,
    String businessModule,
    String fieldName,
    String fieldLabel,
    String fieldType,
    String selectOptions,
    Integer isRequired,
    Integer sortOrder,
    Integer status,
    LocalDateTime createdTime,
    LocalDateTime updatedTime
) {}
