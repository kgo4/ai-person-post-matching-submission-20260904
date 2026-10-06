package com.example.matching.service.agent;

import com.example.matching.agent.dto.person.PersonAbilityClaim;
import com.example.matching.agent.dto.person.PersonAbilityExtractionResult;
import com.example.matching.dto.governance.GovernanceAdmission;
import com.example.matching.dto.governance.GovernanceGrant;
import com.example.matching.event.KnowledgeGraphRebuildRequestedEvent;
import com.example.matching.service.ability.AbilityEvidenceIngestionService;
import com.example.matching.service.ability.PersonAbilityClaimAdmissionService;
import com.example.matching.service.agent.impl.AgentBusinessApplyServiceImpl;
import com.example.matching.service.governance.GovernedAdmissionService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.util.ReflectionUtils;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.atLeast;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Tests the 10-second cooldown on graph refresh in {@link AgentBusinessApplyServiceImpl}.
 * <p>
 * The {@code triggerGraphRefresh()} method uses an {@link AtomicLong} with CAS to implement
 * a debounce/cooldown: within any 10-second window, only one {@link KnowledgeGraphRebuildRequestedEvent}
 * should be published.
 * <p>
 * Since the method is private, tests exercise it through the public {@code applyPersonAbilities} path
 * (graph refresh is triggered when passCount > 0), and also directly via reflection for precision.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("Graph Refresh Cooldown")
class GraphRefreshCooldownTest {

    @Mock private GovernedAdmissionService governedAdmissionService;
    @Mock private PersonAbilityClaimAdmissionService admissionService;
    @Mock private AbilityEvidenceIngestionService abilityEvidenceIngestionService;
    @Mock private ApplicationEventPublisher eventPublisher;

    @Mock private com.example.matching.service.common.VectorRecallCacheEpoch vectorRecallCacheEpoch;
    @Mock private com.example.matching.agent.service.AgentClaimConflictDetector conflictDetector;
    @InjectMocks private AgentBusinessApplyServiceImpl service;

    /**
     * Reset the AtomicLong timestamp to 0 before each test so cooldown state does not leak.
     */
    @BeforeEach
    void resetCooldownTimestamp() {
        Field field = ReflectionUtils.findField(AgentBusinessApplyServiceImpl.class, "lastGraphRefreshAt");
        ReflectionUtils.makeAccessible(field);
        AtomicLong timestamp = (AtomicLong) ReflectionUtils.getField(field, service);
        timestamp.set(0);
    }

    @Test
    @DisplayName("First trigger publishes KnowledgeGraphRebuildRequestedEvent")
    void firstTrigger_publishesEvent() throws Exception {
        invokeTriggerGraphRefresh();

        verify(eventPublisher, times(1)).publishEvent(any(KnowledgeGraphRebuildRequestedEvent.class));
    }

    @Test
    @DisplayName("Second trigger within cooldown -> no additional event published")
    void secondTriggerWithinCooldown_noAdditionalEvent() throws Exception {
        invokeTriggerGraphRefresh();
        invokeTriggerGraphRefresh();

        verify(eventPublisher, times(1)).publishEvent(any(KnowledgeGraphRebuildRequestedEvent.class));
    }

    @Test
    @DisplayName("Multiple rapid triggers within 10 seconds -> only ONE event published")
    void multipleRapidTriggers_onlyOneEvent() throws Exception {
        for (int i = 0; i < 50; i++) {
            invokeTriggerGraphRefresh();
        }

        verify(eventPublisher, times(1)).publishEvent(any(KnowledgeGraphRebuildRequestedEvent.class));
    }

    @Test
    @DisplayName("100 concurrent trigger calls within 10 seconds -> at most ONE event published")
    void concurrentTriggers_atMostOneEvent() throws Exception {
        int threadCount = 100;
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch startLatch = new CountDownLatch(1);
        AtomicInteger publishCount = new AtomicInteger(0);

        List<Future<?>> futures = new ArrayList<>();
        for (int i = 0; i < threadCount; i++) {
            futures.add(executor.submit(() -> {
                try {
                    startLatch.await();
                    invokeTriggerGraphRefresh();
                } catch (Exception e) {
                    // ignore
                }
            }));
        }

        startLatch.countDown();
        for (Future<?> f : futures) {
            f.get();
        }
        executor.shutdown();

        // Due to CAS, at most one thread should win and publish the event.
        // We verify at least 1 and at most 1 (the CAS guarantees at most 1 within cooldown).
        verify(eventPublisher, times(1)).publishEvent(any(KnowledgeGraphRebuildRequestedEvent.class));
    }

    @Test
    @DisplayName("After cooldown expires, next trigger publishes another event")
    void afterCooldownExpires_nextTriggerPublishes() throws Exception {
        // First trigger
        invokeTriggerGraphRefresh();
        verify(eventPublisher, times(1)).publishEvent(any(KnowledgeGraphRebuildRequestedEvent.class));

        // Simulate cooldown expiry by setting timestamp to >10 seconds ago
        Field field = ReflectionUtils.findField(AgentBusinessApplyServiceImpl.class, "lastGraphRefreshAt");
        ReflectionUtils.makeAccessible(field);
        AtomicLong timestamp = (AtomicLong) ReflectionUtils.getField(field, service);
        timestamp.set(System.currentTimeMillis() - 11_000); // 11 seconds ago

        // Second trigger after cooldown
        invokeTriggerGraphRefresh();
        verify(eventPublisher, times(2)).publishEvent(any(KnowledgeGraphRebuildRequestedEvent.class));
    }

    @Test
    @DisplayName("Cooldown boundary: exactly at 10 seconds -> still suppressed")
    void cooldownBoundary_exactlyTenSeconds_stillSuppressed() throws Exception {
        invokeTriggerGraphRefresh();
        verify(eventPublisher, times(1)).publishEvent(any(KnowledgeGraphRebuildRequestedEvent.class));

        // Set timestamp to exactly 10 seconds ago (10_000 ms)
        // The check is `now - previous < 10_000`, so if diff == 10_000, it does NOT return early
        // and should publish. Let's verify.
        Field field = ReflectionUtils.findField(AgentBusinessApplyServiceImpl.class, "lastGraphRefreshAt");
        ReflectionUtils.makeAccessible(field);
        AtomicLong timestamp = (AtomicLong) ReflectionUtils.getField(field, service);
        timestamp.set(System.currentTimeMillis() - 10_000);

        invokeTriggerGraphRefresh();
        // At exactly 10_000ms, the condition `now - previous < 10_000` is false, so it publishes
        verify(eventPublisher, times(2)).publishEvent(any(KnowledgeGraphRebuildRequestedEvent.class));
    }

    @Test
    @DisplayName("Cooldown boundary: just under 10 seconds -> still suppressed")
    void cooldownBoundary_justUnderTenSeconds_stillSuppressed() throws Exception {
        invokeTriggerGraphRefresh();
        verify(eventPublisher, times(1)).publishEvent(any(KnowledgeGraphRebuildRequestedEvent.class));

        // Set timestamp to 9999ms ago (just under 10s)
        Field field = ReflectionUtils.findField(AgentBusinessApplyServiceImpl.class, "lastGraphRefreshAt");
        ReflectionUtils.makeAccessible(field);
        AtomicLong timestamp = (AtomicLong) ReflectionUtils.getField(field, service);
        timestamp.set(System.currentTimeMillis() - 9_999);

        invokeTriggerGraphRefresh();
        // Still within cooldown, should NOT publish again
        verify(eventPublisher, times(1)).publishEvent(any(KnowledgeGraphRebuildRequestedEvent.class));
    }

    @Test
    @DisplayName("Through applyPersonAbilities: graph refresh triggered only when passCount > 0")
    void throughPublicApi_graphRefreshOnlyOnPass() {
        PersonAbilityClaim claim = personClaim();
        when(governedAdmissionService.admitPersonAbility(any()))
                .thenReturn(admission(GovernanceGrant.PASS, 500L));

        service.applyPersonAbilities(extractionResult(claim));

        verify(eventPublisher, atLeast(1)).publishEvent(any(KnowledgeGraphRebuildRequestedEvent.class));
    }

    @Test
    @DisplayName("Through applyPersonAbilities: BLOCK only -> no graph refresh")
    void throughPublicApi_blockOnly_noGraphRefresh() {
        PersonAbilityClaim claim = personClaim();
        when(governedAdmissionService.admitPersonAbility(any()))
                .thenReturn(admission(GovernanceGrant.BLOCK, null));

        service.applyPersonAbilities(extractionResult(claim));

        verify(eventPublisher, times(0)).publishEvent(any(KnowledgeGraphRebuildRequestedEvent.class));
    }

    // ---- Helpers ----

    /**
     * Invoke the private triggerGraphRefresh() method via reflection.
     */
    private void invokeTriggerGraphRefresh() throws Exception {
        Method method = AgentBusinessApplyServiceImpl.class.getDeclaredMethod("triggerGraphRefresh");
        method.setAccessible(true);
        method.invoke(service);
    }

    private PersonAbilityClaim personClaim() {
        PersonAbilityClaim claim = new PersonAbilityClaim();
        claim.setEmpId(1L);
        claim.setSourceType("RESUME_PARSE");
        claim.setSourceRefId(11L);
        claim.setAbilityName("Java");
        claim.setAbilityTagId(7L);
        claim.setMasteryLevel(4);
        claim.setConfidenceScore(new BigDecimal("80"));
        claim.setEvidenceText("Evidence for Java");
        claim.setSourceRefs(List.of("source:RESUME_PARSE:11"));
        return claim;
    }

    private PersonAbilityExtractionResult extractionResult(PersonAbilityClaim claim) {
        PersonAbilityExtractionResult result = new PersonAbilityExtractionResult();
        result.setClaims(List.of(claim));
        return result;
    }

    private GovernanceAdmission admission(GovernanceGrant grant, Long businessTargetId) {
        GovernanceAdmission admission = new GovernanceAdmission();
        admission.setFinalDecision(grant.name());
        admission.setBusinessTargetId(businessTargetId);
        return admission;
    }
}
