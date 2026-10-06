package com.example.matching.agent.service;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AgentFallbackConfidencePolicyTest {

    private final AgentFallbackConfidencePolicy policy = new AgentFallbackConfidencePolicy();

    @Test
    void givesResumeEvidenceMoreConfidenceThanAiTestFallbacks() {
        BigDecimal resume = policy.confidenceFor("RESUME_PARSE");
        BigDecimal aiTest = policy.confidenceFor("AI_TEST");

        assertTrue(resume.compareTo(aiTest) > 0);
        assertEquals(new BigDecimal("70"), resume);
        assertEquals(new BigDecimal("60"), aiTest);
    }
}
