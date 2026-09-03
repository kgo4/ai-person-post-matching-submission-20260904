package com.example.matching.application.agent.impl;

import com.example.matching.agent.dto.EvidenceGovernanceAgentRequest;
import com.example.matching.agent.dto.EvidenceGovernanceAgentResult;
import com.example.matching.agent.service.EvidenceGovernanceAgentService;
import com.example.matching.application.agent.AbilityClaimCandidate;
import com.example.matching.application.agent.ClaimSource;
import com.example.matching.application.agent.EvidenceBundle;
import com.example.matching.application.agent.GovernanceDecision;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class EvidenceGovernanceUseCaseImplTest {

    @Test
    void usesTheGovernanceAgentToMakeAnOtherwisePassingClaimStricter() {
        EvidenceGovernanceAgentService agent = mock(EvidenceGovernanceAgentService.class);
        EvidenceGovernanceAgentResult agentResult = new EvidenceGovernanceAgentResult();
        agentResult.setDecision("REVIEW");
        agentResult.setRiskLevel("MEDIUM");
        agentResult.setSupportScore(new BigDecimal("55"));
        agentResult.setReasons(List.of("Evidence is stale"));
        when(agent.review(any(EvidenceGovernanceAgentRequest.class))).thenReturn(agentResult);

        EvidenceGovernanceUseCaseImpl useCase = new EvidenceGovernanceUseCaseImpl(agent);

        GovernanceDecision result = useCase.evaluate(validClaim());

        assertEquals(GovernanceDecision.Decision.REVIEW, result.decision());
        assertEquals(GovernanceDecision.RiskLevel.MEDIUM, result.riskLevel());
    }

    @Test
    void usesTheAgentResultAsTheSingleDeterministicGovernanceSource() {
        EvidenceGovernanceAgentService agent = mock(EvidenceGovernanceAgentService.class);
        EvidenceGovernanceAgentResult agentResult = new EvidenceGovernanceAgentResult();
        agentResult.setDecision("PASS");
        agentResult.setRiskLevel("LOW");
        agentResult.setSupportScore(new BigDecimal("75"));
        agentResult.setReasons(List.of("Harness accepted the evidence"));
        when(agent.review(any(EvidenceGovernanceAgentRequest.class))).thenReturn(agentResult);

        EvidenceGovernanceUseCaseImpl useCase = new EvidenceGovernanceUseCaseImpl(agent);
        AbilityClaimCandidate lowConfidenceClaim = new AbilityClaimCandidate(
                1L, 2L, "Java", "Java", 3, ClaimSource.RESUME_PARSE, 8L,
                EvidenceBundle.of("Built production services", List.of()),
                new BigDecimal("40"), null, null, null);

        GovernanceDecision result = useCase.evaluate(lowConfidenceClaim);

        assertEquals(GovernanceDecision.Decision.PASS, result.decision());
        assertEquals(GovernanceDecision.RiskLevel.LOW, result.riskLevel());
    }

    @Test
    void preservesRetryDecisionFromAgentAsFailClosedNotAdmitted() {
        EvidenceGovernanceAgentService agent = mock(EvidenceGovernanceAgentService.class);
        EvidenceGovernanceAgentResult agentResult = new EvidenceGovernanceAgentResult();
        agentResult.setDecision("RETRY");
        agentResult.setRiskLevel("HIGH");
        agentResult.setSupportScore(new BigDecimal("40"));
        agentResult.setReasons(List.of("source refs unverifiable; fail closed with RETRY"));
        when(agent.review(any(EvidenceGovernanceAgentRequest.class))).thenReturn(agentResult);

        EvidenceGovernanceUseCaseImpl useCase = new EvidenceGovernanceUseCaseImpl(agent);

        GovernanceDecision result = useCase.evaluate(validClaim());

        assertEquals(GovernanceDecision.Decision.RETRY, result.decision());
        assertEquals(GovernanceDecision.RiskLevel.HIGH, result.riskLevel());
        assertThat(result.isAdmitted()).isFalse();
    }

    private AbilityClaimCandidate validClaim() {
        return new AbilityClaimCandidate(
                1L, 2L, "Java", "Java", 3, ClaimSource.RESUME_PARSE, 8L,
                EvidenceBundle.of("Built production services", List.of()),
                new BigDecimal("80"), null, null, null);
    }
}
