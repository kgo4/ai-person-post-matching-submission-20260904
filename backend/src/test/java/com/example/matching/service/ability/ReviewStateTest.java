package com.example.matching.service.ability;

import com.example.matching.application.agent.ReviewState;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Regression tests for ReviewState enum and the state machine defined in the
 * agent architecture refactor spec.
 * <p>
 * Documents the expected behavior that the refactored reviewProfile must implement.
 */
@DisplayName("ReviewState State Machine")
class ReviewStateTest {

    @Nested
    @DisplayName("Admissibility rules")
    class AdmissibilityRules {

        @Test
        @DisplayName("AUTO profiles are admissible for matching context")
        void autoIsAdmissible() {
            assertTrue(ReviewState.AUTO.isAdmissible());
        }

        @Test
        @DisplayName("APPROVED profiles are admissible for matching context")
        void approvedIsAdmissible() {
            assertTrue(ReviewState.APPROVED.isAdmissible());
        }

        @Test
        @DisplayName("PENDING profiles are NOT admissible")
        void pendingIsNotAdmissible() {
            assertFalse(ReviewState.PENDING.isAdmissible());
        }

        @Test
        @DisplayName("REJECTED profiles are NOT admissible")
        void rejectedIsNotAdmissible() {
            assertFalse(ReviewState.REJECTED.isAdmissible());
        }

        @Test
        @DisplayName("LEGACY_REVIEWED profiles are NOT admissible (ambiguous old state)")
        void legacyReviewedIsNotAdmissible() {
            assertFalse(ReviewState.LEGACY_REVIEWED.isAdmissible());
        }
    }

    @Nested
    @DisplayName("Pending state detection")
    class PendingDetection {

        @Test
        @DisplayName("PENDING is pending")
        void pendingIsPending() {
            assertTrue(ReviewState.PENDING.isPending());
        }

        @Test
        @DisplayName("LEGACY_REVIEWED is pending (ambiguous, needs re-review)")
        void legacyIsPending() {
            assertTrue(ReviewState.LEGACY_REVIEWED.isPending());
        }

        @Test
        @DisplayName("AUTO is not pending")
        void autoIsNotPending() {
            assertFalse(ReviewState.AUTO.isPending());
        }

        @Test
        @DisplayName("APPROVED is not pending")
        void approvedIsNotPending() {
            assertFalse(ReviewState.APPROVED.isPending());
        }

        @Test
        @DisplayName("REJECTED is not pending")
        void rejectedIsNotPending() {
            assertFalse(ReviewState.REJECTED.isPending());
        }
    }

    @Nested
    @DisplayName("Legacy status conversion")
    class LegacyConversion {

        @Test
        @DisplayName("PENDING_REVIEW maps to PENDING")
        void pendingReviewMapsToPending() {
            assertEquals(ReviewState.PENDING, ReviewState.fromLegacyStatus("PENDING_REVIEW"));
        }

        @Test
        @DisplayName("REVIEWED maps to LEGACY_REVIEWED")
        void reviewedMapsToLegacy() {
            assertEquals(ReviewState.LEGACY_REVIEWED, ReviewState.fromLegacyStatus("REVIEWED"));
        }

        @Test
        @DisplayName("AUTO maps to AUTO")
        void autoMapsToAuto() {
            assertEquals(ReviewState.AUTO, ReviewState.fromLegacyStatus("AUTO"));
        }

        @Test
        @DisplayName("null maps to AUTO")
        void nullMapsToAuto() {
            assertEquals(ReviewState.AUTO, ReviewState.fromLegacyStatus(null));
        }

        @Test
        @DisplayName("unknown maps to AUTO")
        void unknownMapsToAuto() {
            assertEquals(ReviewState.AUTO, ReviewState.fromLegacyStatus("UNKNOWN_STATUS"));
        }
    }

    @Nested
    @DisplayName("Dual-write legacy status mapping")
    class DualWriteMapping {

        @ParameterizedTest
        @EnumSource(ReviewState.class)
        @DisplayName("toLegacyStatus never returns null")
        void toLegacyStatusNeverNull(ReviewState state) {
            assertNotNull(state.toLegacyStatus());
        }

        @Test
        @DisplayName("PENDING writes PENDING_REVIEW for legacy compatibility")
        void pendingWritesLegacy() {
            assertEquals("PENDING_REVIEW", ReviewState.PENDING.toLegacyStatus());
        }

        @Test
        @DisplayName("APPROVED writes REVIEWED for legacy compatibility")
        void approvedWritesLegacy() {
            assertEquals("REVIEWED", ReviewState.APPROVED.toLegacyStatus());
        }

        @Test
        @DisplayName("REJECTED writes REVIEWED for legacy compatibility")
        void rejectedWritesLegacy() {
            assertEquals("REVIEWED", ReviewState.REJECTED.toLegacyStatus());
        }
    }
}
