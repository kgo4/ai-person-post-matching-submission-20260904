package com.example.matching.service.post;

import com.example.matching.dto.post.PostAbilityModelConfigDTO;
import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import java.util.List;
import static org.assertj.core.api.Assertions.assertThat;

class PostAbilityWeightNormalizerTest {
    @Test
    void convertsRelativeWeightsToPercentageAndPreservesHundredTotal() {
        PostAbilityModelConfigDTO first = config("0.8");
        PostAbilityModelConfigDTO second = config("0.7");
        PostAbilityWeightNormalizer.normalizeLegacyRelativeWeights(List.of(first, second));
        assertThat(first.getWeight()).isEqualByComparingTo("53.33");
        assertThat(second.getWeight()).isEqualByComparingTo("46.67");
        assertThat(first.getWeight().add(second.getWeight())).isEqualByComparingTo("100.00");
    }

    @Test
    void leavesPercentageWeightsUnchanged() {
        PostAbilityModelConfigDTO first = config("60");
        PostAbilityModelConfigDTO second = config("40");
        PostAbilityWeightNormalizer.normalizeLegacyRelativeWeights(List.of(first, second));
        assertThat(first.getWeight()).isEqualByComparingTo("60");
        assertThat(second.getWeight()).isEqualByComparingTo("40");
    }

    private PostAbilityModelConfigDTO config(String weight) {
        PostAbilityModelConfigDTO config = new PostAbilityModelConfigDTO();
        config.setWeight(new BigDecimal(weight));
        return config;
    }
}
