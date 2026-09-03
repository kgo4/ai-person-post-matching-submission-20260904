package com.example.matching.service.assessment.impl;

import com.example.matching.entity.ability.PersonAbilityClaim;
import com.example.matching.entity.employee.EmpAiTest;
import com.example.matching.service.assessment.AbilityEvidenceCollectionService;
import com.example.matching.service.employee.AiTestService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AssessmentTestResultProviderTest {

    private AssessmentTestResultProvider provider;
    private AbilityEvidenceCollectionService evidenceCollectionService;
    private AiTestService aiTestService;

    @BeforeEach
    void setUp() {
        evidenceCollectionService = mock(AbilityEvidenceCollectionService.class);
        aiTestService = mock(AiTestService.class);
        provider = new AssessmentTestResultProvider(evidenceCollectionService, aiTestService, new ObjectMapper());
    }

    @Test
    void buildSummary_includesTestLevelResumeLevelAndWeakQuestions() {
        PersonAbilityClaim testClaim = new PersonAbilityClaim();
        testClaim.setSourceType("AI_TEST");
        testClaim.setStatus("ACTIVE");
        testClaim.setNormalizedAbilityName("Java");
        testClaim.setClaimedLevel(3);
        testClaim.setClaimGroupId(100L);
        PersonAbilityClaim resumeClaim = new PersonAbilityClaim();
        resumeClaim.setSourceType("RESUME_PARSE");
        resumeClaim.setStatus("ACTIVE");
        resumeClaim.setClaimGroupId(100L);
        resumeClaim.setClaimedLevel(4);
        when(evidenceCollectionService.listClaimsByWorkflow(1L))
                .thenReturn(List.of(testClaim, resumeClaim));

        EmpAiTest test = new EmpAiTest();
        test.setId(9L);
        test.setMasteryLevel(3);
        test.setQuestions("[{\"id\":1,\"question\":\"What is a Java record?\"}]");
        test.setAiEvaluation("{\"score\":80,\"masteryLevel\":3,"
                + "\"questionResults\":[{\"questionIndex\":0,\"isCorrect\":false,\"score\":0}]}");
        when(aiTestService.getLatestByWorkflowId(1L)).thenReturn(test);

        AssessmentTestResultProvider.TestResultSummary summary = provider.buildSummary(1L);

        assertThat(summary.overallTestLevel()).isEqualTo(3);
        assertThat(summary.abilities()).hasSize(1);
        assertThat(summary.abilities().get(0).abilityName()).isEqualTo("Java");
        assertThat(summary.abilities().get(0).testLevel()).isEqualTo(3);
        assertThat(summary.abilities().get(0).resumeClaimedLevel()).isEqualTo(4);
        assertThat(summary.weakQuestions()).contains("What is a Java record?");
    }

    @Test
    void buildSummary_emptyWhenNoData() {
        when(evidenceCollectionService.listClaimsByWorkflow(1L)).thenReturn(List.of());
        when(aiTestService.getLatestByWorkflowId(1L)).thenReturn(null);

        AssessmentTestResultProvider.TestResultSummary summary = provider.buildSummary(1L);

        assertThat(summary.abilities()).isEmpty();
        assertThat(summary.overallTestLevel()).isNull();
        assertThat(summary.weakQuestions()).isEmpty();
    }

    @Test
    void buildSummary_nullWorkflowId_returnsEmpty() {
        AssessmentTestResultProvider.TestResultSummary summary = provider.buildSummary(null);

        assertThat(summary.abilities()).isEmpty();
        assertThat(summary.overallTestLevel()).isNull();
    }
}
