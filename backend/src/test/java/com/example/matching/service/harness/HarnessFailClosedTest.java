package com.example.matching.service.harness;

import com.example.matching.ai.context.service.AiContextSourceRefService;
import com.example.matching.common.source.SourceRefValidationResult;
import com.example.matching.dto.harness.AiHarnessClaimDTO;
import com.example.matching.dto.harness.AiHarnessDecisionDTO;
import com.example.matching.service.harness.impl.AiTrustHarnessServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@DisplayName("Harness Fail-Closed")
class HarnessFailClosedTest {

    private AiTrustHarnessServiceImpl service;
    private AiContextSourceRefService sourceRefService;

    @BeforeEach
    void setUp() {
        sourceRefService = mock(AiContextSourceRefService.class);
        @SuppressWarnings("unchecked")
        ObjectProvider<AiContextSourceRefService> sourceRefProvider = mock(ObjectProvider.class);
        when(sourceRefProvider.getIfAvailable()).thenReturn(sourceRefService);
        service = new AiTrustHarnessServiceImpl(null, null, sourceRefProvider, null);
    }

    @Test
    @DisplayName("All refs unverifiable (DEPENDENCY_ERROR) -> RETRY, never REVIEW")
    void allRefsUnverifiableReturnsRetry() {
        when(sourceRefService.resolveWithStatus("fact:EMP_ABILITY:1"))
                .thenReturn(new AiContextSourceRefService.ResolveOutcome(SourceRefValidationResult.DEPENDENCY_ERROR, null));

        AiHarnessDecisionDTO decision = service.verify(claim("fact:EMP_ABILITY:1"));

        assertThat(decision.getDecision()).isEqualTo(AiHarnessDecisionDTO.RETRY);
        assertThat(decision.getUnverifiableSourceRefs()).contains("fact:EMP_ABILITY:1");
    }

    @Test
    @DisplayName("All refs NOT_FOUND -> BLOCK, not REVIEW")
    void allRefsNotFoundReturnsBlock() {
        when(sourceRefService.resolveWithStatus("fact:EMP_ABILITY:999"))
                .thenReturn(new AiContextSourceRefService.ResolveOutcome(SourceRefValidationResult.NOT_FOUND, null));

        AiHarnessDecisionDTO decision = service.verify(claim("fact:EMP_ABILITY:999"));

        assertThat(decision.getDecision()).isEqualTo(AiHarnessDecisionDTO.BLOCK);
        assertThat(decision.getInvalidSourceRefs()).contains("fact:EMP_ABILITY:999");
    }

    @Test
    @DisplayName("Mixed accepted + unverifiable -> cannot auto-PASS")
    void mixedAcceptedAndUnverifiableCannotPass() {
        var valid = new AiContextSourceRefService.ResolveOutcome(
                SourceRefValidationResult.VALID, new com.example.matching.ai.context.dto.AiContextSourceRefDTO());
        when(sourceRefService.resolveWithStatus("fact:EMP_ABILITY:1")).thenReturn(valid);
        when(sourceRefService.resolveWithStatus("fact:EMP_ABILITY:2"))
                .thenReturn(new AiContextSourceRefService.ResolveOutcome(SourceRefValidationResult.DEPENDENCY_ERROR, null));

        AiHarnessClaimDTO claim = claimWithRefs("fact:EMP_ABILITY:1", "fact:EMP_ABILITY:2");
        claim.setMatchedTagId(7L);
        claim.setEvidenceText("real evidence");
        claim.setSourceType("RESUME_PARSE");

        AiHarnessDecisionDTO decision = service.verify(claim);

        assertThat(decision.getDecision()).isEqualTo(AiHarnessDecisionDTO.REVIEW);
        assertThat(decision.getDecision()).isNotEqualTo(AiHarnessDecisionDTO.PASS);
    }

    @Test
    @DisplayName("All valid refs + evidence + tag -> PASS")
    void allValidRefsPass() {
        var valid = new AiContextSourceRefService.ResolveOutcome(
                SourceRefValidationResult.VALID, new com.example.matching.ai.context.dto.AiContextSourceRefDTO());
        when(sourceRefService.resolveWithStatus("fact:EMP_ABILITY:1")).thenReturn(valid);

        AiHarnessClaimDTO claim = claimWithRefs("fact:EMP_ABILITY:1");
        claim.setMatchedTagId(7L);
        claim.setSimilarTagId(8L);
        claim.setEvidenceText("real evidence text");
        claim.setSourceType("RESUME_PARSE");

        AiHarnessDecisionDTO decision = service.verify(claim);

        assertThat(decision.getDecision()).isEqualTo(AiHarnessDecisionDTO.PASS);
    }

    @Test
    @DisplayName("UNSUPPORTED ref -> BLOCK")
    void unsupportedRefReturnsBlock() {
        when(sourceRefService.resolveWithStatus("fact:UNKNOWN_TYPE:1"))
                .thenReturn(new AiContextSourceRefService.ResolveOutcome(SourceRefValidationResult.UNSUPPORTED, null));

        AiHarnessDecisionDTO decision = service.verify(claim("fact:UNKNOWN_TYPE:1"));

        assertThat(decision.getDecision()).isEqualTo(AiHarnessDecisionDTO.BLOCK);
    }

    private AiHarnessClaimDTO claim(String ref) {
        return claimWithRefs(ref);
    }

    private AiHarnessClaimDTO claimWithRefs(String... refs) {
        AiHarnessClaimDTO claim = new AiHarnessClaimDTO();
        claim.setScenario("PERSON_ABILITY");
        claim.setClaimType("PERSON_ABILITY");
        claim.setClaimText("Java");
        claim.setSourceRefs(List.of(refs));
        return claim;
    }
}
