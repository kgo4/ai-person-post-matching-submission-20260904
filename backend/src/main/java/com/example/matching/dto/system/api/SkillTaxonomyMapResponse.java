package com.example.matching.dto.system.api;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 技能→能力规则映射 响应
 */
public record SkillTaxonomyMapResponse(
        Long id,
        String skillName,
        Long abilityTagId,
        String category,
        BigDecimal confidence,
        String source,
        Integer status,
        LocalDateTime createdTime,
        LocalDateTime updatedTime
) {}
