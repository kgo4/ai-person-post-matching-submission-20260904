package com.example.matching.service.agent;

import com.example.matching.agent.dto.person.PersonAbilityClaim;
import com.example.matching.agent.dto.person.PersonAbilityExtractionResult;
import com.example.matching.dto.governance.GovernanceAdmission;
import com.example.matching.dto.governance.GovernanceGrant;
import com.example.matching.service.agent.impl.AgentBusinessApplyServiceImpl;
import com.example.matching.service.ability.AbilityEvidenceIngestionService;
import com.example.matching.service.ability.PersonAbilityClaimAdmissionService;
import com.example.matching.service.governance.GovernedAdmissionService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.dao.OptimisticLockingFailureException;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * Tests concurrent agent writes through GovernedAdmissionService.
 * <p>
 * Verifies:
 * <ul>
 *   <li>Unique key violation handled gracefully (no crash, errorCount incremented)</li>
 *   <li>Optimistic lock conflict -> single valid profile survives (error counted)</li>
 *   <li>Both succeed -> final state is consistent</li>
 * </ul>
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("Concurrent Agent Writes (Governed)")
class ConcurrentAgentWriteTest {

    @Mock private GovernedAdmissionService governedAdmissionService;
    @Mock private PersonAbilityClaimAdmissionService admissionService;
    @Mock private AbilityEvidenceIngestionService abilityEvidenceIngestionService;
    @Mock private ApplicationEventPublisher eventPublisher;

    @Mock private com.example.matching.service.common.VectorRecallCacheEpoch vectorRecallCacheEpoch;
    @Mock private com.example.matching.agent.service.AgentClaimConflictDetector conflictDetector;
    @InjectMocks private AgentBusinessApplyServiceImpl service;

    @Test
    @DisplayName("Unique key violation on admission -> errorCount incremented, no crash")
    void uniqueKeyViolation_handledGracefully_noCrash() {
        PersonAbilityClaim claim = personClaim(1L, "RESUME_PARSE", 11L, "Java", 7L, 4);
        when(governedAdmissionService.admitPersonAbility(any()))
                .thenThrow(new DuplicateKeyException("Duplicate entry for key 'uk_emp_tag_source'"));

        var result = service.applyPersonAbilities(extractionResult(claim));

        assertThat(result.getErrorCount()).isEqualTo(1);
        assertThat(result.getPassCount()).isEqualTo(0);
        assertThat(result.getTotalClaims()).isEqualTo(1);
    }

    @Test
    @DisplayName("Optimistic lock conflict on admission -> errorCount incremented, no crash")
    void optimisticLockConflict_handledGracefully_noCrash() {
        PersonAbilityClaim claim = personClaim(1L, "RESUME_PARSE", 11L, "Java", 7L, 4);
        when(governedAdmissionService.admitPersonAbility(any()))
                .thenThrow(new OptimisticLockingFailureException(
                        "Optimistic lock conflict for PersonAbilityClaim id=1"));

        var result = service.applyPersonAbilities(extractionResult(claim));

        assertThat(result.getErrorCount()).isEqualTo(1);
        assertThat(result.getPassCount()).isEqualTo(0);
    }

    @Test
    @DisplayName("Concurrent calls to applyPersonAbilities with same employee/tag -> no crash, consistent error handling")
    void concurrentCalls_sameEmployeeTag_noCrash() throws Exception {
        int threadCount = 10;
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch startLatch = new CountDownLatch(1);
        AtomicInteger totalPass = new AtomicInteger(0);
        AtomicInteger totalErrors = new AtomicInteger(0);

        when(governedAdmissionService.admitPersonAbility(any()))
                .thenReturn(admission(GovernanceGrant.PASS, 500L));

        List<Future<?>> futures = new ArrayList<>();
        for (int i = 0; i < threadCount; i++) {
            futures.add(executor.submit(() -> {
                try {
                    startLatch.await();
                    PersonAbilityClaim claim = personClaim(1L, "RESUME_PARSE", 11L, "Java", 7L, 4);
                    var result = service.applyPersonAbilities(extractionResult(claim));
                    totalPass.addAndGet(result.getPassCount());
                    totalErrors.addAndGet(result.getErrorCount());
                } catch (Exception e) {
                    totalErrors.incrementAndGet();
                }
            }));
        }

        startLatch.countDown(); // release all threads simultaneously
        for (Future<?> f : futures) {
            f.get();
        }
        executor.shutdown();

        // All should succeed since mock always returns PASS admission
        assertThat(totalPass.get()).isEqualTo(threadCount);
        assertThat(totalErrors.get()).isEqualTo(0);
    }

    @Test
    @DisplayName("Concurrent calls with intermittent duplicate key -> errors counted, no unhandled exception")
    void concurrentCalls_intermittentDuplicateKey_errorsCountedNoCrash() throws Exception {
        int threadCount = 10;
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch startLatch = new CountDownLatch(1);
        AtomicInteger totalPass = new AtomicInteger(0);
        AtomicInteger totalErrors = new AtomicInteger(0);

        // Alternate: even threads succeed, odd threads throw DuplicateKeyException
        when(governedAdmissionService.admitPersonAbility(any()))
                .thenReturn(admission(GovernanceGrant.PASS, 500L))
                .thenThrow(new DuplicateKeyException("Duplicate"))
                .thenReturn(admission(GovernanceGrant.PASS, 500L))
                .thenThrow(new DuplicateKeyException("Duplicate"))
                .thenReturn(admission(GovernanceGrant.PASS, 500L))
                .thenThrow(new DuplicateKeyException("Duplicate"))
                .thenReturn(admission(GovernanceGrant.PASS, 500L))
                .thenThrow(new DuplicateKeyException("Duplicate"))
                .thenReturn(admission(GovernanceGrant.PASS, 500L))
                .thenThrow(new DuplicateKeyException("Duplicate"));

        List<Future<?>> futures = new ArrayList<>();
        for (int i = 0; i < threadCount; i++) {
            futures.add(executor.submit(() -> {
                try {
                    startLatch.await();
                    PersonAbilityClaim claim = personClaim(1L, "RESUME_PARSE", 11L, "Java", 7L, 4);
                    var result = service.applyPersonAbilities(extractionResult(claim));
                    totalPass.addAndGet(result.getPassCount());
                    totalErrors.addAndGet(result.getErrorCount());
                } catch (Exception e) {
                    totalErrors.incrementAndGet();
                }
            }));
        }

        startLatch.countDown();
        for (Future<?> f : futures) {
            f.get();
        }
        executor.shutdown();

        // 5 pass + 5 errors = 10 total
        assertThat(totalPass.get() + totalErrors.get()).isEqualTo(threadCount);
        assertThat(totalPass.get()).isEqualTo(5);
        assertThat(totalErrors.get()).isEqualTo(5);
    }

    @Test
    @DisplayName("Both succeed -> final counts are consistent with total claims")
    void bothSucceed_finalStateConsistent() {
        PersonAbilityClaim claim1 = personClaim(1L, "RESUME_PARSE", 11L, "Java", 7L, 4);
        PersonAbilityClaim claim2 = personClaim(1L, "RESUME_PARSE", 11L, "Python", 8L, 3);
        when(governedAdmissionService.admitPersonAbility(any()))
                .thenReturn(admission(GovernanceGrant.PASS, 500L));

        PersonAbilityExtractionResult extraction = new PersonAbilityExtractionResult();
        extraction.setClaims(List.of(claim1, claim2));

        var result = service.applyPersonAbilities(extraction);

        assertThat(result.getTotalClaims()).isEqualTo(2);
        assertThat(result.getPassCount()).isEqualTo(2);
        assertThat(result.getErrorCount()).isEqualTo(0);
    }

    @Test
    @DisplayName("Admission service throws unexpected RuntimeException -> error counted, not propagated")
    void unexpectedRuntimeException_handledGracefully() {
        PersonAbilityClaim claim = personClaim(1L, "RESUME_PARSE", 11L, "Java", 7L, 4);
        when(governedAdmissionService.admitPersonAbility(any()))
                .thenThrow(new RuntimeException("Database connection lost"));

        var result = service.applyPersonAbilities(extractionResult(claim));

        assertThat(result.getErrorCount()).isEqualTo(1);
        assertThat(result.getPassCount()).isEqualTo(0);
    }

    // ---- Helpers ----

    private PersonAbilityClaim personClaim(Long empId, String sourceType, Long sourceRefId,
                                            String abilityName, Long tagId, Integer masteryLevel) {
        PersonAbilityClaim claim = new PersonAbilityClaim();
        claim.setEmpId(empId);
        claim.setSourceType(sourceType);
        claim.setSourceRefId(sourceRefId);
        claim.setAbilityName(abilityName);
        claim.setAbilityTagId(tagId);
        claim.setMasteryLevel(masteryLevel);
        claim.setConfidenceScore(new BigDecimal("80"));
        claim.setEvidenceText("Evidence for " + abilityName);
        claim.setSourceRefs(List.of("fact:EMP_ABILITY:" + sourceRefId));
        return claim;
    }

    private PersonAbilityExtractionResult extractionResult(PersonAbilityClaim... claims) {
        PersonAbilityExtractionResult result = new PersonAbilityExtractionResult();
        result.setClaims(List.of(claims));
        return result;
    }

    private GovernanceAdmission admission(GovernanceGrant grant, Long businessTargetId) {
        GovernanceAdmission admission = new GovernanceAdmission();
        admission.setFinalDecision(grant.name());
        admission.setBusinessTargetId(businessTargetId);
        return admission;
    }
}
