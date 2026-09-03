package com.example.matching.application.agent;

import java.util.List;

/**
 * Use case: build or refresh a person's ability profile from admitted claims.
 * <p>
 * Accepts only claims whose {@code employeeId} equals the target employee.
 * Mismatches are rejected before any profile write.
 */
public interface ProfileBuildUseCase {

    /**
     * Build profiles from the employee's current admitted claims.
     * Optionally includes interview observations from the latest session.
     *
     * @param employeeId the target employee
     * @return list of built profiles
     */
    List<PersonProfileRepository.ProfileSnapshot> buildProfile(Long employeeId);

    /**
     * Build profiles including observations from a specific interview session.
     *
     * @param employeeId the target employee
     * @param sessionId  the interview session ID (may be null)
     * @return list of built profiles
     */
    List<PersonProfileRepository.ProfileSnapshot> buildProfileWithInterview(
            Long employeeId, Long sessionId);
}
