package com.example.matching.application.agent;

/**
 * Profile review state machine.
 * <p>
 * Transitions:
 * <pre>
 *   AUTO -> PENDING -> APPROVED
 *                    -> REJECTED
 *   LEGACY_REVIEWED -> APPROVED | REJECTED  (migration only)
 * </pre>
 * A REJECTED profile is excluded from matching context and cannot be silently
 * reused by profile refresh. New evidence after a decision produces a new
 * PENDING revision rather than overwriting the reviewed profile.
 */
public enum ReviewState {

    /**
     * No human review was required (auto-generated profile).
     */
    AUTO,

    /**
     * Awaiting human review.
     */
    PENDING,

    /**
     * Human reviewer approved the profile.
     */
    APPROVED,

    /**
     * Human reviewer rejected the profile.
     */
    REJECTED,

    /**
     * Migration placeholder: old REVIEWED status where we cannot determine
     * whether it was an approval or rejection. Treated as pending for safety.
     */
    LEGACY_REVIEWED;

    /**
     * Whether this state represents a profile that can appear in matching context.
     */
    public boolean isAdmissible() {
        return this == AUTO || this == APPROVED;
    }

    /**
     * Whether this state represents a profile awaiting human action.
     */
    public boolean isPending() {
        return this == PENDING || this == LEGACY_REVIEWED;
    }

    /**
     * Whether this state represents a completed human decision.
     */
    public boolean isDecided() {
        return this == APPROVED || this == REJECTED;
    }

    /**
     * Convert from legacy review_status string.
     */
    public static ReviewState fromLegacyStatus(String legacyStatus) {
        if (legacyStatus == null) {
            return AUTO;
        }
        return switch (legacyStatus) {
            case "PENDING_REVIEW" -> PENDING;
            case "REVIEWED" -> LEGACY_REVIEWED;
            default -> AUTO;
        };
    }

    /**
     * Map to legacy review_status for dual-write compatibility.
     */
    public String toLegacyStatus() {
        return switch (this) {
            case PENDING -> "PENDING_REVIEW";
            case APPROVED, REJECTED, LEGACY_REVIEWED -> "REVIEWED";
            default -> "AUTO";
        };
    }
}
