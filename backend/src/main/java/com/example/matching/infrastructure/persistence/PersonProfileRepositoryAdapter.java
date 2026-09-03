package com.example.matching.infrastructure.persistence;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.example.matching.application.agent.PersonProfileRepository;
import com.example.matching.application.agent.ReviewState;
import com.example.matching.entity.ability.PersonAbilityProfile;
import com.example.matching.mapper.ability.PersonAbilityProfileMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * MyBatis adapter for {@link PersonProfileRepository}.
 * <p>
 * Implements dual-read (prefer review_state) and dual-write (write both columns)
 * for backward compatibility during the migration period.
 */
@Slf4j
@Repository
@RequiredArgsConstructor
public class PersonProfileRepositoryAdapter implements PersonProfileRepository {

    private final PersonAbilityProfileMapper profileMapper;

    @Override
    public Optional<ProfileSnapshot> findByEmployeeAndTag(Long employeeId, Long tagId) {
        var wrapper = Wrappers.<PersonAbilityProfile>lambdaQuery()
                .eq(PersonAbilityProfile::getEmpId, employeeId)
                .eq(PersonAbilityProfile::getTagId, tagId)
                .eq(PersonAbilityProfile::getIsDeleted, 0)
                .orderByDesc(PersonAbilityProfile::getCreatedTime)
                .last("LIMIT 1");

        PersonAbilityProfile entity = profileMapper.selectOne(wrapper);
        return Optional.ofNullable(entity).map(this::toSnapshot);
    }

    @Override
    public List<ProfileSnapshot> findAdmissibleByEmployee(Long employeeId) {
        // After Phase B cutover, prefer review_state
        var wrapper = Wrappers.<PersonAbilityProfile>lambdaQuery()
                .eq(PersonAbilityProfile::getEmpId, employeeId)
                .eq(PersonAbilityProfile::getIsDeleted, 0)
                .and(w -> w
                        .eq(PersonAbilityProfile::getReviewState, ReviewState.AUTO.name())
                        .or()
                        .eq(PersonAbilityProfile::getReviewState, ReviewState.APPROVED.name()))
                .orderByDesc(PersonAbilityProfile::getCreatedTime);

        return profileMapper.selectList(wrapper).stream()
                .map(this::toSnapshot)
                .toList();
    }

    @Override
    public List<ProfileSnapshot> findAllByEmployee(Long employeeId) {
        var wrapper = Wrappers.<PersonAbilityProfile>lambdaQuery()
                .eq(PersonAbilityProfile::getEmpId, employeeId)
                .eq(PersonAbilityProfile::getIsDeleted, 0)
                .orderByDesc(PersonAbilityProfile::getCreatedTime);

        return profileMapper.selectList(wrapper).stream()
                .map(this::toSnapshot)
                .toList();
    }

    @Override
    public boolean updateReviewState(Long profileId, ReviewState newState,
                                     Long reviewerId, String comment,
                                     String reasonCode, int expectedVersion) {
        PersonAbilityProfile entity = profileMapper.selectById(profileId);
        if (entity == null) {
            log.warn("Profile not found: profileId={}", profileId);
            return false;
        }

        // Verify the profile is in a reviewable state
        ReviewState currentState = ReviewState.fromLegacyStatus(entity.getReviewStatus());
        if (entity.getReviewState() != null) {
            try {
                currentState = ReviewState.valueOf(entity.getReviewState());
            } catch (IllegalArgumentException e) {
                // Fall back to legacy status
            }
        }

        if (!currentState.isPending()) {
            log.warn("Profile not in reviewable state: profileId={}, currentState={}",
                    profileId, currentState);
            return false;
        }

        // Optimistic lock check
        if (expectedVersion >= 0 && !entity.getVersion().equals(expectedVersion)) {
            log.warn("Optimistic lock conflict: profileId={}, expected={}, actual={}",
                    profileId, expectedVersion, entity.getVersion());
            return false;
        }

        // Dual-write: update both columns
        entity.setReviewState(newState.name());
        entity.setReviewStatus(newState.toLegacyStatus());
        entity.setReviewedBy(reviewerId);
        entity.setReviewedTime(LocalDateTime.now());
        entity.setReviewComment(comment);
        entity.setReviewDecisionReasonCode(reasonCode);

        int rows = profileMapper.updateById(entity);
        if (rows > 0) {
            log.info("Profile review state updated: profileId={}, newState={}, reviewerId={}",
                    profileId, newState, reviewerId);
            return true;
        }

        return false;
    }

    @Override
    public Long insert(ProfileSnapshot profile) {
        PersonAbilityProfile entity = new PersonAbilityProfile();
        entity.setEmpId(profile.employeeId());
        entity.setTagId(profile.tagId());
        entity.setAbilityName(profile.abilityName());
        entity.setFinalLevel(profile.finalLevel());
        entity.setReviewState(profile.reviewState().name());
        entity.setReviewStatus(profile.reviewState().toLegacyStatus());
        entity.setIsDeleted(0);
        entity.setVersion(0);

        profileMapper.insert(entity);
        return entity.getId();
    }

    /**
     * Convert entity to application-layer snapshot.
     * Prefers review_state over legacy review_status.
     */
    private ProfileSnapshot toSnapshot(PersonAbilityProfile entity) {
        ReviewState state;
        if (entity.getReviewState() != null && !entity.getReviewState().isEmpty()) {
            try {
                state = ReviewState.valueOf(entity.getReviewState());
            } catch (IllegalArgumentException e) {
                state = ReviewState.fromLegacyStatus(entity.getReviewStatus());
            }
        } else {
            state = ReviewState.fromLegacyStatus(entity.getReviewStatus());
        }

        return new ProfileSnapshot(
                entity.getId(),
                entity.getEmpId(),
                entity.getTagId(),
                entity.getAbilityName(),
                entity.getFinalLevel(),
                state,
                entity.getReviewStatus(),
                entity.getReviewedBy(),
                entity.getReviewComment(),
                entity.getReviewDecisionReasonCode(),
                entity.getVersion() != null ? entity.getVersion() : 0
        );
    }
}
