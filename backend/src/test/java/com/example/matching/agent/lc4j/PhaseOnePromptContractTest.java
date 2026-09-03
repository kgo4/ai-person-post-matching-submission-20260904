package com.example.matching.agent.lc4j;

import dev.langchain4j.service.UserMessage;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class PhaseOnePromptContractTest {

    @Test
    void phaseOneUserMessagesDelegateTheJsonSchemaToTheirSystemPrompts() throws Exception {
        for (Method method : List.of(
                AiTestAiService.class.getMethod("generateQuestions", String.class, int.class),
                AiTestAiService.class.getMethod("evaluateAnswers", String.class),
                EmployeeAbilityAiService.class.getMethod("analyze", String.class),
                EmployeeAbilityAiService.class.getMethod("extractAbilities", String.class),
                EvidenceGovernanceAiService.class.getMethod("review", String.class),
                PostAbilityAiService.class.getMethod("analyze", String.class),
                PostAbilityAiService.class.getMethod("extractAbilities", String.class),
                MatchingAnalysisAiService.class.getMethod("analyze", String.class),
                LearningPathAiService.class.getMethod("generatePath", String.class),
                InterviewPlanAiService.class.getMethod("generatePlan", Long.class, String.class),
                InterviewFollowUpAiService.class.getMethod("generate", Long.class, String.class),
                InterviewObservationAiService.class.getMethod("extractObservations", Long.class, String.class),
                PmsAbilityAnalysisAiService.class.getMethod("extractAbilities", String.class),
                InterviewReportAiService.class.getMethod("generateReport", Long.class, String.class))) {
            String message = String.join("\n", method.getAnnotation(UserMessage.class).value());

            assertThat(message)
                    .as("%s should not duplicate its system prompt schema", method.getDeclaringClass().getSimpleName())
                    .doesNotContain("Required JSON format")
                    .contains("Follow the system output schema exactly.", "{{context}}");
        }
    }

    @Test
    void systemPromptSchemasMatchTheirStructuredResultContracts() throws Exception {
        assertThat(readPrompt("evidence-governance-system.txt"))
                .contains("\"riskLevel\"", "\"selfEvidence\"", "\"missingEvidence\"");
        assertThat(readPrompt("post-ability-system.txt"))
                .contains("\"modelSummary\"", "\"coreAbilities\"", "\"weightRisks\"")
                .doesNotContain("\"requirements\"");
        assertThat(readPrompt("matching-analysis-system.txt"))
                .contains("\"suggestedLlmScore\"", "\"humanAttentionPoints\"")
                .doesNotContain("\"aiScore\"");
        assertThat(readPrompt("learning-path-system.txt"))
                .contains("\"abilityTagId\"", "\"title\"", "\"description\"")
                .doesNotContain("\"content\"", "\"abilityNames\"");
        assertThat(readPrompt("interview-plan-system.txt"))
                .contains("\"text\"", "\"estimatedDuration\"", "\"followUpStrategy\"")
                .doesNotContain("\"questionText\"", "\"expectedDuration\"");
        assertThat(readPrompt("post-ability-extract-system.txt"))
                .contains("\"weight\": \"number (0-1)\"");
    }

    private String readPrompt(String fileName) throws Exception {
        try (InputStream stream = getClass().getClassLoader().getResourceAsStream("ai/prompt/" + fileName)) {
            assertThat(stream).as("Prompt resource %s", fileName).isNotNull();
            return new String(stream.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
