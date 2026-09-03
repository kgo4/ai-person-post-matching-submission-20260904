package com.example.matching.application.matching;

import com.example.matching.dto.matching.ScoringWeightUpdateRequest;
import com.example.matching.service.matching.MatchingTrainingWeightProfileStore;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@DisplayName("MatchingScoringConfigApiFacade")
class MatchingScoringConfigApiFacadeTest {

    private MatchingTrainingWeightProfileStore weightProfileStore;
    private MatchingTrainingWeightProfileStore.WeightProfile profile;
    private MatchingScoringConfigApiFacade facade;

    @BeforeEach
    void setUp() {
        weightProfileStore = mock(MatchingTrainingWeightProfileStore.class);
        profile = MatchingTrainingWeightProfileStore.WeightProfile.defaultProfile();
        when(weightProfileStore.currentProfile()).thenReturn(profile);
        facade = new MatchingScoringConfigApiFacade(weightProfileStore);
    }

    @Test
    void getConfigExposesTheSingleFourDimensionProfile() {
        var config = facade.getConfig();

        assertThat(config.abilityWeight()).isEqualTo(65d);
        assertThat(config.semanticWeight()).isEqualTo(15d);
        assertThat(config.evidenceWeight()).isEqualTo(10d);
        assertThat(config.aiWeight()).isEqualTo(10d);
        assertThat(config.whitelistBypassHardRules()).isTrue();
    }

    @Test
    void validCompleteUpdatePersistsTheFourWeightsAndBumpsVersion() {
        facade.saveConfig(new ScoringWeightUpdateRequest(60d, 15d, 10d, 15d, false));

        verify(weightProfileStore).saveActiveProfile(profile);
        assertThat(profile.getAbilityWeight()).isEqualTo(0.60d);
        assertThat(profile.getAiWeight()).isEqualTo(0.15d);
        assertThat(profile.isWhitelistBypassHardRules()).isFalse();
        assertThat(profile.getVersion()).isEqualTo("MATCH_SCORE_V2");
    }

    @Test
    void partialUpdateUsesTheCurrentProfileForUnchangedDimensions() {
        facade.saveConfig(new ScoringWeightUpdateRequest(60d, null, null, 15d, null));

        verify(weightProfileStore).saveActiveProfile(profile);
        assertThat(profile.getAbilityWeight()).isEqualTo(0.60d);
        assertThat(profile.getSemanticWeight()).isEqualTo(0.15d);
        assertThat(profile.getEvidenceWeight()).isEqualTo(0.10d);
        assertThat(profile.getAiWeight()).isEqualTo(0.15d);
    }

    @Test
    void rejectsWeightsWhoseTotalIsNotOneHundredPercent() {
        assertThatThrownBy(() -> facade.saveConfig(
                new ScoringWeightUpdateRequest(65d, 15d, 10d, 5d, null)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("之和必须等于 100%");

        verify(weightProfileStore, never()).saveActiveProfile(any());
    }

    @Test
    void rejectsAiWeightOverTwentyPercent() {
        assertThatThrownBy(() -> facade.saveConfig(
                new ScoringWeightUpdateRequest(50d, 15d, 10d, 25d, null)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("AI 权重不能超过 20%");

        verify(weightProfileStore, never()).saveActiveProfile(any());
    }

    @Test
    void nullRequestDoesNotPersistAConfiguration() {
        facade.saveConfig(null);

        verify(weightProfileStore, never()).saveActiveProfile(any());
    }
}
