package com.example.matching.application.agent;

import java.util.List;
import java.util.Optional;

/**
 * Port for querying and persisting person ability profiles.
 * Implemented by a MyBatis adapter in the infrastructure layer.
 */
public interface PersonProfileRepository {

    /**
     * Load the latest profile for an employee and ability tag.
     */
    Optional<ProfileSnapshot> findByEmployeeAndTag(Long employeeId, Long tagId);

    /**
     * Load all latest profiles for an employee that are admissible for matching.
     * Excludes REJECTED and PENDING profiles.
     */
    List<ProfileSnapshot> findAdmissibleByEmployee(Long employeeId);

    /**
     * Load all latest profiles for an employee (including non-admissible).
     */
    List<ProfileSnapshot> findAllByEmployee(Long employeeId);

    /**
     * Perform an optimistic-lock update of review state.
     * Returns true if the update succeeded (version matched).
     */
    boolean updateReviewState(Long profileId, ReviewState newState,
                              Long reviewerId, String comment,
                              String reasonCode, int expectedVersion);

    /**
     * Persist a new profile revision.
     */
    Long insert(ProfileSnapshot profile);

    /**
     * Immutable snapshot of a profile for the application layer.
     */
    record ProfileSnapshot(
            Long id,
            Long employeeId,
            Long tagId,
            String abilityName,
            Integer finalLevel,
            ReviewState reviewState,
            String legacyReviewStatus,
            Long reviewedBy,
            String reviewComment,
            String reviewDecisionReasonCode,
            int version
    ) {
    }
}
