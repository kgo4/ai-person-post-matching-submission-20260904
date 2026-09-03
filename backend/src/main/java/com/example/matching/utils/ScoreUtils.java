package com.example.matching.utils;

import java.math.BigDecimal;

public final class ScoreUtils {

    private ScoreUtils() {
    }

    /**
     * Clamp an integer score to [min, max].
     */
    public static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    /**
     * Clamp a BigDecimal score to [min, max].
     */
    public static BigDecimal clamp(BigDecimal value, BigDecimal min, BigDecimal max) {
        if (value == null) {
            return min;
        }
        if (value.compareTo(min) < 0) {
            return min;
        }
        if (value.compareTo(max) > 0) {
            return max;
        }
        return value;
    }

    /**
     * Clamp a BigDecimal score to [0, 100].
     */
    public static BigDecimal clampScore(BigDecimal value) {
        return clamp(value, BigDecimal.ZERO, BigDecimal.valueOf(100));
    }
}
