package com.example.matching.infrastructure.persistence;

import com.example.matching.application.agent.PersonProfileRepository;
import com.example.matching.application.agent.ProfileBuildUseCase;
import com.example.matching.application.agent.ReviewState;
import com.example.matching.entity.ability.PersonAbilityProfile;
import com.example.matching.service.ability.PersonAbilityProfileAgent;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class ProfileBuildUseCaseAdapter implements ProfileBuildUseCase {

    private final PersonAbilityProfileAgent profileAgent;

    @Override
    public List<PersonProfileRepository.ProfileSnapshot> buildProfile(Long employeeId) {
        return profileAgent.buildProfile(employeeId).stream().map(this::toSnapshot).toList();
    }

    @Override
    public List<PersonProfileRepository.ProfileSnapshot> buildProfileWithInterview(Long employeeId, Long sessionId) {
        return profileAgent.buildProfileWithInterview(employeeId, sessionId).stream().map(this::toSnapshot).toList();
    }

    private PersonProfileRepository.ProfileSnapshot toSnapshot(PersonAbilityProfile profile) {
        ReviewState state = profile.getReviewState() == null
                ? ReviewState.fromLegacyStatus(profile.getReviewStatus())
                : ReviewState.valueOf(profile.getReviewState());
        return new PersonProfileRepository.ProfileSnapshot(profile.getId(), profile.getEmpId(), profile.getTagId(),
                profile.getAbilityName(), profile.getFinalLevel(), state, profile.getReviewStatus(),
                profile.getReviewedBy(), profile.getReviewComment(), profile.getReviewDecisionReasonCode(),
                profile.getVersion() == null ? 0 : profile.getVersion());
    }
}
