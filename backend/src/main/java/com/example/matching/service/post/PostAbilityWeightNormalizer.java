package com.example.matching.service.post;

import com.example.matching.dto.post.PostAbilityModelConfigDTO;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

/** Keeps persisted post-model weights in percentage points (0-100). */
public final class PostAbilityWeightNormalizer {
    private static final BigDecimal TARGET_TOTAL = new BigDecimal("100");
    private static final BigDecimal MIN_VALID_TOTAL = new BigDecimal("95");
    private static final BigDecimal MAX_VALID_TOTAL = new BigDecimal("105");
    private PostAbilityWeightNormalizer() {}

    public static void normalizeLegacyRelativeWeights(List<PostAbilityModelConfigDTO> configs) {
        if (configs == null || configs.isEmpty()) return;
        BigDecimal total = configs.stream().map(PostAbilityModelConfigDTO::getWeight)
                .filter(w -> w != null && w.compareTo(BigDecimal.ZERO) > 0)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        if (total.signum() <= 0 || (total.compareTo(MIN_VALID_TOTAL) >= 0 && total.compareTo(MAX_VALID_TOTAL) <= 0)) return;
        BigDecimal assigned = BigDecimal.ZERO;
        PostAbilityModelConfigDTO last = null;
        for (PostAbilityModelConfigDTO config : configs) {
            if (config.getWeight() == null) continue;
            last = config;
            BigDecimal converted = config.getWeight().divide(total, 8, RoundingMode.HALF_UP)
                    .multiply(TARGET_TOTAL).setScale(2, RoundingMode.HALF_UP);
            config.setWeight(converted);
            assigned = assigned.add(converted);
        }
        if (last != null && assigned.compareTo(TARGET_TOTAL) != 0) {
            last.setWeight(last.getWeight().add(TARGET_TOTAL.subtract(assigned))
                    .setScale(2, RoundingMode.HALF_UP));
        }
    }

    public static BigDecimal toPercentage(BigDecimal weight, BigDecimal defaultWeight) {
        BigDecimal value = weight != null ? weight : defaultWeight;
        if (value == null) return null;
        return value.compareTo(BigDecimal.ONE) <= 0
                ? value.multiply(new BigDecimal("100")).setScale(2, RoundingMode.HALF_UP)
                : value;
    }
}
