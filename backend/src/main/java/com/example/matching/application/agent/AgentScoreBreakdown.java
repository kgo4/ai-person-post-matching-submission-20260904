package com.example.matching.application.agent;

import java.math.BigDecimal;

/**
 * Immutable score breakdown for a single dimension in matching analysis.
 *
 * @param dimension   score dimension name (e.g. "能力匹配", "经验匹配")
 * @param score       score value (0-100)
 * @param weight      dimension weight
 * @param description human-readable description
 */
public record AgentScoreBreakdown(
        String dimension,
        BigDecimal score,
        BigDecimal weight,
        String description
) {
}
