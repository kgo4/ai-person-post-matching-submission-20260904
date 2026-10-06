package com.example.matching.service.evolution;

import com.example.matching.config.MarketJdCapabilityAdmissionProperties;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.bind.Bindable;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.context.properties.source.MapConfigurationPropertySource;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * MarketJdCapabilityAdmissionProperties 默认值与绑定校验测试
 */
class MarketJdCapabilityAdmissionPropertiesTest {

    @Test
    void defaultsMatchProductionInitialValues() {
        MarketJdCapabilityAdmissionProperties props = new MarketJdCapabilityAdmissionProperties();

        assertThat(props.isEnabled()).isTrue();
        assertThat(props.isDirectEvidenceAutoAdmit()).isTrue();
        assertThat(props.getSemanticRecommendationMinScore()).isEqualTo(0.88);
        assertThat(props.getHarnessBatchSize()).isEqualTo(50);
        assertThat(props.getHarnessRetryCount()).isEqualTo(1);
        assertThat(props.getNewAbilityMinJdCount()).isEqualTo(3);
        assertThat(props.getNewAbilityMinCompanyCount()).isEqualTo(2);
        assertThat(props.getNewAbilityPassMinScore()).isEqualTo(80);
        assertThat(props.getReviewMaxGroupsPerBatch()).isEqualTo(20);
    }

    @Test
    void bindsKebabCaseKeysFromConfiguration() {
        Map<String, String> map = new LinkedHashMap<>();
        map.put("market-jd.capability-admission.enabled", "false");
        map.put("market-jd.capability-admission.direct-evidence-auto-admit", "false");
        map.put("market-jd.capability-admission.semantic-recommendation-min-score", "0.91");
        map.put("market-jd.capability-admission.harness-batch-size", "10");
        map.put("market-jd.capability-admission.harness-retry-count", "0");
        map.put("market-jd.capability-admission.new-ability-min-jd-count", "5");
        map.put("market-jd.capability-admission.new-ability-min-company-count", "3");
        map.put("market-jd.capability-admission.new-ability-pass-min-score", "75");
        map.put("market-jd.capability-admission.review-max-groups-per-batch", "5");

        Binder binder = new Binder(new MapConfigurationPropertySource(map));
        MarketJdCapabilityAdmissionProperties props = binder
                .bind("market-jd.capability-admission", Bindable.of(MarketJdCapabilityAdmissionProperties.class))
                .get();

        assertThat(props.isEnabled()).isFalse();
        assertThat(props.isDirectEvidenceAutoAdmit()).isFalse();
        assertThat(props.getSemanticRecommendationMinScore()).isEqualTo(0.91);
        assertThat(props.getHarnessBatchSize()).isEqualTo(10);
        assertThat(props.getHarnessRetryCount()).isZero();
        assertThat(props.getNewAbilityMinJdCount()).isEqualTo(5);
        assertThat(props.getNewAbilityMinCompanyCount()).isEqualTo(3);
        assertThat(props.getNewAbilityPassMinScore()).isEqualTo(75);
        assertThat(props.getReviewMaxGroupsPerBatch()).isEqualTo(5);
        props.validate(); // 合法值不抛异常
    }

    @Test
    void rejectsZeroHarnessBatchSize() {
        MarketJdCapabilityAdmissionProperties props = new MarketJdCapabilityAdmissionProperties();
        props.setHarnessBatchSize(0);

        assertThatThrownBy(props::validate)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("harness-batch-size");
    }

    @Test
    void rejectsInvalidRetryCount() {
        MarketJdCapabilityAdmissionProperties props = new MarketJdCapabilityAdmissionProperties();
        props.setHarnessRetryCount(2);

        assertThatThrownBy(props::validate)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("harness-retry-count");
    }

    @Test
    void rejectsOutOfRangeSemanticRecommendationScore() {
        MarketJdCapabilityAdmissionProperties props = new MarketJdCapabilityAdmissionProperties();
        props.setSemanticRecommendationMinScore(1.01);

        assertThatThrownBy(props::validate)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("semantic-recommendation-min-score");
    }

    @Test
    void rejectsOutOfRangePassMinScore() {
        MarketJdCapabilityAdmissionProperties props = new MarketJdCapabilityAdmissionProperties();
        props.setNewAbilityPassMinScore(101);

        assertThatThrownBy(props::validate)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("new-ability-pass-min-score");
    }
}
