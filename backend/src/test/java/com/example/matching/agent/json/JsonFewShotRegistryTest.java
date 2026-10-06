package com.example.matching.agent.json;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class JsonFewShotRegistryTest {

    @Test
    void providesPositiveAndNegativeExamples() {
        String examples = JsonFewShotRegistry.forScene("EMPLOYEE_ABILITY_EXTRACTION");
        assertTrue(examples.contains("GOOD"));
        assertTrue(examples.contains("BAD"));
    }

    @Test
    void fallsBackToGenericScene() {
        assertNotNull(JsonFewShotRegistry.forScene("UNKNOWN_SCENE"));
    }
}
