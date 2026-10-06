package com.example.matching.dto.system.api;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;

public record SourceWeightConfigRequest(
    Long id,
    String sourceType,
    String sourceLabel,
    BigDecimal weight,
    Integer isActive,
    Integer sortOrder,
    String remark
) {}
