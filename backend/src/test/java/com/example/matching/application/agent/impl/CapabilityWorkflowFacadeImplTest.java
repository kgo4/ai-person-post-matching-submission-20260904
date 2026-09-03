package com.example.matching.application.agent.impl;

import com.example.matching.application.agent.AbilityClaimCandidate;
import com.example.matching.application.agent.CapabilityClaimRepository;
import com.example.matching.application.agent.CapabilityWorkflowFacade;
import com.example.matching.application.agent.ClaimSource;
import com.example.matching.application.agent.ClaimExtractor;
import com.example.matching.application.agent.EvidenceBundle;
import com.example.matching.application.agent.EvidenceGovernanceUseCase;
import com.example.matching.application.agent.GovernanceDecision;
import com.example.matching.application.agent.PersonProfileRepository;
import com.example.matching.application.agent.ProfileBuildUseCase;
import com.example.matching.application.agent.ReviewProfileUseCase;
import com.example.matching.service.governance.GovernedAdmissionService;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class CapabilityWorkflowFacadeImplTest {

    @Test
    void extractAndAdmit_extractsClaimsBeforeGovernanceAndPersistence() {
        EvidenceGovernanceUseCase governance = mock(EvidenceGovernanceUseCase.class);
        CapabilityClaimRepository repository = mock(CapabilityClaimRepository.class);
        ClaimExtractor extractor = mock(ClaimExtractor.class);
        AbilityClaimCandidate claim = claim();
        when(extractor.extract(any())).thenReturn(List.of(claim));
        when(governance.evaluate(claim)).thenReturn(passDecision());
        when(repository.admitClaim(claim, passDecision())).thenReturn(101L);

        CapabilityWorkflowFacade facade = new CapabilityWorkflowFacadeImpl(
                governance,
                mock(ProfileBuildUseCase.class),
                mock(ReviewProfileUseCase.class),
                repository,
                extractor,
                mock(GovernedAdmissionService.class)
        );

        CapabilityWorkflowFacade.AdmissionResult result = facade.extractAndAdmit(request());

        assertEquals(new CapabilityWorkflowFacade.AdmissionResult(1, 1, 0, 0, 0, 0), result);
        verify(extractor).extract(request());
        verify(governance).evaluate(claim);
        verify(repository).admitClaim(claim, passDecision());
    }

    @Test
    void extractAndAdmit_retryDecisionIsPersistedForGovernedRetry() {
        EvidenceGovernanceUseCase governance = mock(EvidenceGovernanceUseCase.class);
        CapabilityClaimRepository repository = mock(CapabilityClaimRepository.class);
        GovernedAdmissionService governedAdmissionService = mock(GovernedAdmissionService.class);
        ClaimExtractor extractor = mock(ClaimExtractor.class);
        AbilityClaimCandidate claim = claim();
        GovernanceDecision retryDecision = new GovernanceDecision(
                GovernanceDecision.Decision.RETRY, GovernanceDecision.RiskLevel.HIGH,
                new BigDecimal("40"), false,
                List.of("source refs unverifiable; fail closed with RETRY"),
                List.of(), null, false, "AGENT_GOVERNANCE");
        when(extractor.extract(any())).thenReturn(List.of(claim));
        when(governance.evaluate(claim)).thenReturn(retryDecision);

        CapabilityWorkflowFacade facade = new CapabilityWorkflowFacadeImpl(
                governance,
                mock(ProfileBuildUseCase.class),
                mock(ReviewProfileUseCase.class),
                repository,
                extractor,
                governedAdmissionService
        );

        CapabilityWorkflowFacade.AdmissionResult result = facade.extractAndAdmit(request());

        assertEquals(new CapabilityWorkflowFacade.AdmissionResult(1, 0, 0, 0, 0, 1), result);
        verify(repository, org.mockito.Mockito.never())
                .admitClaim(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any());
        verify(governedAdmissionService).deferPersonAbilityRetry(
                org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.contains("source refs unverifiable"));
    }

    @Test
    void extractAndAdmit_rejectsBlankSourceTextInsteadOfSilentlyReportingNoClaims() {
        CapabilityWorkflowFacade facade = new CapabilityWorkflowFacadeImpl(
                mock(EvidenceGovernanceUseCase.class),
                mock(ProfileBuildUseCase.class),
                mock(ReviewProfileUseCase.class),
                mock(CapabilityClaimRepository.class),
                mock(ClaimExtractor.class),
                mock(GovernedAdmissionService.class)
        );
        CapabilityWorkflowFacade.AdmissionRequest blankRequest = new CapabilityWorkflowFacade.AdmissionRequest(
                7L, ClaimSource.RESUME_PARSE, 11L, "  ", List.of());

        assertThrows(IllegalArgumentException.class, () -> facade.extractAndAdmit(blankRequest));
    }

    private CapabilityWorkflowFacade.AdmissionRequest request() {
        return new CapabilityWorkflowFacade.AdmissionRequest(
                7L, ClaimSource.RESUME_PARSE, 11L, "Built a Java service", List.of());
    }

    private AbilityClaimCandidate claim() {
        return new AbilityClaimCandidate(7L, null, "Java", "Java", 4,
                ClaimSource.RESUME_PARSE, 11L, EvidenceBundle.of("Built a service", List.of()),
                new BigDecimal("85"), null, null, null);
    }

    private GovernanceDecision passDecision() {
        return new GovernanceDecision(GovernanceDecision.Decision.PASS, GovernanceDecision.RiskLevel.LOW,
                new BigDecimal("85"), false, List.of(), List.of(), null, false, "PASS");
    }
}
