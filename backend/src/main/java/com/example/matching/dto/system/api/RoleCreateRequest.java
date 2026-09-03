package com.example.matching.dto.system.api;

import jakarta.validation.constraints.NotBlank;

public record RoleCreateRequest(
    @NotBlank String roleCode,
    @NotBlank String roleName,
    String description,
    Integer dataScope,
    Integer status
) {}
