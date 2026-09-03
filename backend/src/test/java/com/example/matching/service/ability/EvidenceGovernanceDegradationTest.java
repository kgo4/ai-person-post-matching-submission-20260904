package com.example.matching.service.ability;

import com.example.matching.application.agent.GovernanceDecision;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Regression tests for EvidenceGovernance degradation behavior.
 * <p>
 * BUG-2: The current fallbackEvidenceGovernance(null) throws NPE.
 * The refactored code must handle null requests gracefully.
 * <p>
 * These tests document the expected behavior that the refactored
 * EvidenceGovernanceUseCase must satisfy.
 */
@DisplayName("Evidence Governance Degradation Rules")
class EvidenceGovernanceDegradationTest {

    @Nested
    @DisplayName("BLOCK cannot be downgraded")
    class BlockImmutability {

        @Test
        @DisplayName("block() always returns BLOCK decision")
        void blockAlwaysReturnsBlock() {
            GovernanceDecision decision = GovernanceDecision.block("空声明");
            assertEquals(GovernanceDecision.Decision.BLOCK, decision.decision());
            assertEquals(GovernanceDecision.RiskLevel.HIGH, decision.riskLevel());
        }

        @Test
        @DisplayName("block() has zero support score")
        void blockHasZeroScore() {
            GovernanceDecision decision = GovernanceDecision.block("test");
            assertEquals(BigDecimal.ZERO, decision.supportScore());
        }
    }

    @Nested
    @DisplayName("Fallback REVIEW behavior")
    class FallbackReview {

        @Test
        @DisplayName("fallbackReview returns REVIEW decision")
        void fallbackReturnsReview() {
            GovernanceDecision decision = GovernanceDecision.fallbackReview("服务不可用");
            assertEquals(GovernanceDecision.Decision.REVIEW, decision.decision());
        }

        @Test
        @DisplayName("fallbackReview marks fallbackUsed=true")
        void fallbackMarksUsed() {
            GovernanceDecision decision = GovernanceDecision.fallbackReview("test");
            assertTrue(decision.fallbackUsed());
        }

        @Test
        @DisplayName("fallbackReview returns MEDIUM risk")
        void fallbackReturnsMediumRisk() {
            GovernanceDecision decision = GovernanceDecision.fallbackReview("test");
            assertEquals(GovernanceDecision.RiskLevel.MEDIUM, decision.riskLevel());
        }

        @Test
        @DisplayName("fallbackReview sets FALLBACK reason code")
        void fallbackSetsReasonCode() {
            GovernanceDecision decision = GovernanceDecision.fallbackReview("test");
            assertEquals("FALLBACK", decision.reasonCode());
        }

        @Test
        @DisplayName("fallbackReview suggests human review")
        void fallbackSuggestsHumanReview() {
            GovernanceDecision decision = GovernanceDecision.fallbackReview("test");
            assertNotNull(decision.suggestedAction());
        }
    }

    @Nested
    @DisplayName("Decision predicates")
    class DecisionPredicates {

        @Test
        @DisplayName("PASS is admitted")
        void passIsAdmitted() {
            GovernanceDecision pass = new GovernanceDecision(
                    GovernanceDecision.Decision.PASS,
                    GovernanceDecision.RiskLevel.LOW,
                    new BigDecimal("80"), false, List.of(), List.of(), null, false, null
            );
            assertTrue(pass.isAdmitted());
        }

        @Test
        @DisplayName("REVIEW is admitted (pending review)")
        void reviewIsAdmitted() {
            GovernanceDecision review = GovernanceDecision.fallbackReview("test");
            assertTrue(review.isAdmitted());
        }

        @Test
        @DisplayName("BLOCK is NOT admitted")
        void blockIsNotAdmitted() {
            GovernanceDecision block = GovernanceDecision.block("test");
            assertFalse(block.isAdmitted());
        }
    }
}
