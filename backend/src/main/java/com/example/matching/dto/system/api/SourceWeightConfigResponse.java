package com.example.matching.dto.system.api;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record SourceWeightConfigResponse(
    Long id,
    String sourceType,
    String sourceLabel,
    BigDecimal weight,
    Integer isActive,
    Integer sortOrder,
    String remark,
    LocalDateTime createdTime,
    LocalDateTime updatedTime
) {}
