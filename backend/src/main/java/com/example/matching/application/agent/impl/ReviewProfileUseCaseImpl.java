package com.example.matching.application.agent.impl;

import com.example.matching.application.agent.PersonProfileRepository;
import com.example.matching.application.agent.ReviewProfileUseCase;
import com.example.matching.application.agent.ReviewState;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Implementation of the profile review use case.
 * <p>
 * Performs an optimistic-lock update with a predicate of {@code review_state = PENDING}.
 * Concurrent reviewers cannot both succeed.
 * <p>
 * Fixes BUG-1: the approved parameter is now properly used to set APPROVED or REJECTED.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ReviewProfileUseCaseImpl implements ReviewProfileUseCase {

    private final PersonProfileRepository profileRepository;

    @Override
    @Transactional
    public ReviewResult review(ReviewCommand command) {
        ReviewState newState = command.approved() ? ReviewState.APPROVED : ReviewState.REJECTED;

        boolean success = profileRepository.updateReviewState(
                command.profileId(),
                newState,
                command.reviewerId(),
                command.comment(),
                command.reasonCode(),
                -1 // version check handled by repository
        );

        if (success) {
            log.info("Profile reviewed: profileId={}, newState={}, reviewerId={}",
                    command.profileId(), newState, command.reviewerId());
        } else {
            log.warn("Profile review failed (concurrent modification or wrong state): profileId={}, newState={}",
                    command.profileId(), newState);
        }

        return new ReviewResult(command.profileId(), newState, success);
    }
}
