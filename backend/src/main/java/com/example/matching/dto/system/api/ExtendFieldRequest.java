package com.example.matching.dto.system.api;

import jakarta.validation.constraints.NotBlank;

public record ExtendFieldRequest(
    @NotBlank String businessModule,
    @NotBlank String fieldName,
    @NotBlank String fieldLabel,
    String fieldType,
    String selectOptions,
    Integer isRequired,
    Integer sortOrder,
    Integer status
) {}
