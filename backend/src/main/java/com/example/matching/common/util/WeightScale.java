package com.example.matching.common.util;

import java.math.BigDecimal;
import java.math.RoundingMode;

/** Single boundary for configurable weights: persistence/API use percentage points (0-100). */
public final class WeightScale {
    private static final BigDecimal ONE_HUNDRED = BigDecimal.valueOf(100);

    private WeightScale() {}

    public static BigDecimal toFraction(BigDecimal percentage) {
        if (percentage == null) return BigDecimal.ZERO;
        return percentage.divide(ONE_HUNDRED, 8, RoundingMode.HALF_UP);
    }

    public static BigDecimal toPercentage(BigDecimal fraction) {
        if (fraction == null) return BigDecimal.ZERO;
        return fraction.multiply(ONE_HUNDRED).setScale(4, RoundingMode.HALF_UP);
    }

    public static double toFraction(double percentage) {
        return percentage / 100d;
    }

    public static double toPercentage(double fraction) {
        return fraction * 100d;
    }
}
