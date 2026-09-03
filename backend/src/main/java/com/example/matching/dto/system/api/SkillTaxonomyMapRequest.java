package com.example.matching.dto.system.api;

import java.math.BigDecimal;

/**
 * 技能→能力规则映射 请求
 */
public record SkillTaxonomyMapRequest(
        String skillName,
        Long abilityTagId,
        String category,
        BigDecimal confidence,
        String source,
        Integer status
) {}
