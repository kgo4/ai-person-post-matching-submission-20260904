package com.example.matching.agent.lc4j;

import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

class InterviewAnswerQualityPromptContractTest {

    @Test
    void answerQualityPromptRestrictsSuggestedFollowUpTypeToServerEnum() throws Exception {
        String prompt = readResource("ai/prompt/interview-answer-quality-system.txt");

        assertThat(prompt).contains("`STAR_MISSING`");
        assertThat(prompt).contains("`PERSONAL_CONTRIBUTION`");
        assertThat(prompt).contains("`RESUME_VERIFICATION`");
        assertThat(prompt).contains("`SCENARIO_SIMULATION`");
        assertThat(prompt).contains("Never output `CLARIFY`");
    }

    private String readResource(String path) throws Exception {
        try (InputStream input = getClass().getClassLoader().getResourceAsStream(path)) {
            assertThat(input).as("resource %s", path).isNotNull();
            return new String(input.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
