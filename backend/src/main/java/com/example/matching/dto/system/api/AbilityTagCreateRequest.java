package com.example.matching.dto.system.api;

public record AbilityTagCreateRequest(
    String tagCode,
    @jakarta.validation.constraints.NotBlank String tagName,
    Long parentId,
    String tagCategory,
    Integer tagLevel,
    String description,
    Integer sortOrder,
    Integer status
) {}
