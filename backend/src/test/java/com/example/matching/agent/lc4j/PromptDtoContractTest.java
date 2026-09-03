package com.example.matching.agent.lc4j;

import com.example.matching.agent.dto.AgentRunResult;
import com.example.matching.agent.dto.AgentSourceRef;
import com.example.matching.agent.dto.person.PersonAbilityExtractionResult;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.lang.reflect.Field;
import java.util.Arrays;

import static org.assertj.core.api.Assertions.assertThat;

class PromptDtoContractTest {

    @Test
    void employeeAbilityPromptUsesStructuredSourceReferencesMatchingItsResultDto() throws IOException {
        assertThat(resource("ai/prompt/employee-ability-system.txt"))
                .contains("\"sourceRefs\": [{")
                .doesNotContain("\"sourceRefs\": [\"fact:EMP_ABILITY");

        Field sourceRefs = field(AgentRunResult.class, "sourceRefs");
        assertThat(sourceRefs).isNotNull();
        assertThat(sourceRefs.getGenericType().getTypeName())
                .isEqualTo("java.util.List<" + AgentSourceRef.class.getName() + ">");
    }

    @Test
    void pmsExtractionResultRetainsTheRootSourceReferencesDefinedByItsPrompt() throws IOException {
        assertThat(resource("ai/prompt/pms-ability-analysis-system.txt"))
                .contains("\"sourceRefs\": [\"source:PMS_ANALYSIS_TASK:{id}\"]");

        Field sourceRefs = field(PersonAbilityExtractionResult.class, "sourceRefs");
        assertThat(sourceRefs).isNotNull();
        assertThat(sourceRefs.getGenericType().getTypeName())
                .isEqualTo("java.util.List<java.lang.String>");
    }

    private String resource(String resourceName) throws IOException {
        try (var input = getClass().getClassLoader().getResourceAsStream(resourceName)) {
            assertThat(input).as("resource %s", resourceName).isNotNull();
            return new String(input.readAllBytes());
        }
    }

    private Field field(Class<?> type, String fieldName) {
        return Arrays.stream(type.getDeclaredFields())
                .filter(field -> field.getName().equals(fieldName))
                .findFirst()
                .orElse(null);
    }
}
