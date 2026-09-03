package com.example.matching.common.enums;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class AbilitySourceTypeTest {

    @ParameterizedTest
    @CsvSource({
            "PMS,AI_PROJECT",
            "PROJECT,AI_PROJECT",
            "PMS_ANALYSIS,AI_PROJECT",
            "PROJECT_SYSTEM,AI_PROJECT",
            "AI_ASSESSMENT,AI_TEST",
            "AI_VIDEO_INTERVIEW,AI_INTERVIEW",
            "VIDEO_INTERVIEW,AI_INTERVIEW",
            "LEARNING,LEARNING_PROJECT",
            "LEARNING_OUTCOME,LEARNING_PROJECT",
            "MANUAL_IMPORT,MANUAL",
            "RESUME_PARSE,RESUME_PARSE",
            "AI_TEST,AI_TEST"
    })
    void canonicalizesLegacyAndCurrentSources(String sourceType, String expected) {
        assertThat(AbilitySourceType.canonicalize(sourceType)).isEqualTo(expected);
    }

    @Test
    void canonicalizesBlankAndUnrecognizedSourcesToManual() {
        assertThat(AbilitySourceType.canonicalize(null)).isEqualTo("MANUAL");
        assertThat(AbilitySourceType.canonicalize("  1213  ")).isEqualTo("MANUAL");
        assertThat(AbilitySourceType.canonicalize("人为")).isEqualTo("MANUAL");
    }
}
