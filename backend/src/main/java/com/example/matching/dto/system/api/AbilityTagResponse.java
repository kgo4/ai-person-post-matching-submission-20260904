package com.example.matching.dto.system.api;

import java.time.LocalDateTime;

public record AbilityTagResponse(
    Long id,
    String tagCode,
    String tagName,
    Long parentId,
    String tagCategory,
    String domain,
    Integer tagLevel,
    String description,
    Integer sortOrder,
    Integer isSystem,
    String sourceType,
    Integer status,
    LocalDateTime createdTime,
    LocalDateTime updatedTime
) {}
