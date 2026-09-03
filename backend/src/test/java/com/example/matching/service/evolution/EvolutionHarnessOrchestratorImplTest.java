package com.example.matching.service.evolution;

import com.example.matching.dto.evolution.PostEvolutionAgentResult.PostEvolutionChangeProposal;
import com.example.matching.dto.harness.AiHarnessClaimDTO;
import com.example.matching.dto.harness.AiHarnessDecisionDTO;
import com.example.matching.service.evolution.impl.EvolutionHarnessOrchestratorImpl;
import com.example.matching.service.harness.AiTrustHarnessService;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class EvolutionHarnessOrchestratorImplTest {

    @Test
    void removalProposalCarriesHighImpactChangeTypeToHarness() {
        AiTrustHarnessService harnessService = mock(AiTrustHarnessService.class);
        AiHarnessDecisionDTO decision = new AiHarnessDecisionDTO();
        decision.setDecision("REVIEW");
        when(harnessService.verify(org.mockito.ArgumentMatchers.any(AiHarnessClaimDTO.class))).thenReturn(decision);
        EvolutionHarnessOrchestratorImpl orchestrator = new EvolutionHarnessOrchestratorImpl(harnessService);

        PostEvolutionChangeProposal proposal = new PostEvolutionChangeProposal();
        proposal.setAbilityName("Legacy deployment");
        proposal.setChangeType("REMOVE");
        proposal.setEvidenceText("Market evidence");
        proposal.setSourceRefs(List.of("source:MARKET_JD:1"));

        orchestrator.verifyProposal(proposal, 9L, 12L);

        ArgumentCaptor<AiHarnessClaimDTO> captor = ArgumentCaptor.forClass(AiHarnessClaimDTO.class);
        verify(harnessService).verify(captor.capture());
        assertThat(captor.getValue().getScenario()).isEqualTo("POST_EVOLUTION");
        assertThat(captor.getValue().getChangeType()).isEqualTo("REMOVE_ABILITY");
    }
}
