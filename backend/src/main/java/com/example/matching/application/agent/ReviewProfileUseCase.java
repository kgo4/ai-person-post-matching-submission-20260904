package com.example.matching.application.agent;

/**
 * Use case: process a human review decision on a profile.
 * <p>
 * Performs an optimistic-lock update with a predicate of {@code review_state = PENDING}.
 * Concurrent reviewers cannot both succeed.
 */
public interface ReviewProfileUseCase {

    /**
     * Process a review decision.
     *
     * @param command the review command
     * @return the review result
     * @throws IllegalStateException if the profile is not in PENDING state
     * @throws org.springframework.orm.ObjectOptimisticLockingFailureException
     *                               if a concurrent review already succeeded
     */
    ReviewResult review(ReviewCommand command);

    /**
     * Command for profile review.
     *
     * @param profileId  the profile to review
     * @param reviewerId the human reviewer's ID
     * @param approved   whether the reviewer approves or rejects
     * @param comment    optional human-readable comment
     * @param reasonCode optional machine-readable reason code
     */
    record ReviewCommand(
            Long profileId,
            Long reviewerId,
            boolean approved,
            String comment,
            String reasonCode
    ) {
    }

    /**
     * Result of a profile review.
     *
     * @param profileId the reviewed profile ID
     * @param newState  the new review state (APPROVED or REJECTED)
     * @param success   whether the review was applied
     */
    record ReviewResult(
            Long profileId,
            ReviewState newState,
            boolean success
    ) {
    }
}
