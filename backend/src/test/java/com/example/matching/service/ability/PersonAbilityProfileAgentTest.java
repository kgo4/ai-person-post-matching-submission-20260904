package com.example.matching.service.ability;

import com.example.matching.entity.ability.PersonAbilityClaim;
import com.example.matching.entity.ability.PersonAbilityProfile;
import com.example.matching.mapper.ability.PersonAbilityClaimMapper;
import com.example.matching.mapper.ability.PersonAbilityProfileMapper;
import com.example.matching.mapper.interview.InterviewAbilityObservationMapper;
import com.example.matching.service.ability.impl.PersonAbilityProfileAgentImpl;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PersonAbilityProfileAgentTest {

    @Mock private PersonAbilityExtractionAgent extractionAgent;
    @Mock private PersonAbilityClaimMapper claimMapper;
    @Mock private PersonAbilityProfileBuildService profileBuildService;
    @Mock private PersonAbilityProfileMapper profileMapper;
    @Mock private InterviewAbilityObservationMapper observationMapper;

    @InjectMocks private PersonAbilityProfileAgentImpl profileAgent;

    @Test
    void buildProfile_usesPersistedAdmittedClaimsAndSkipsRawExtraction() {
        PersonAbilityClaim admitted = new PersonAbilityClaim();
        admitted.setEmpId(1L);
        admitted.setTagId(7L);
        admitted.setAbilityName("Java");
        admitted.setClaimedLevel(4);
        admitted.setStatus("FUSED");
        when(claimMapper.selectList(any())).thenReturn(List.of(admitted));
        when(observationMapper.selectList(any())).thenReturn(List.of());
        when(profileBuildService.buildProfile(any(), any())).thenReturn(List.of());

        profileAgent.buildProfile(1L);

        ArgumentCaptor<List<PersonAbilityClaim>> claimsCaptor = ArgumentCaptor.forClass(List.class);
        verify(profileBuildService).buildProfile(org.mockito.ArgumentMatchers.eq(1L), claimsCaptor.capture());
        assertThat(claimsCaptor.getValue()).containsExactly(admitted);
        verify(extractionAgent, never()).extractAll(any());
        verify(claimMapper).selectList(any());
    }

    @Test
    void reviewProfile_rejectionStoresExplicitRejectedState() {
        PersonAbilityProfile profile = new PersonAbilityProfile();
        profile.setId(9L);
        profile.setReviewStatus("PENDING_REVIEW");
        profile.setReviewState("PENDING");
        when(profileMapper.selectById(9L)).thenReturn(profile);

        profileAgent.reviewProfile(9L, 3L, false, "evidence is insufficient");

        assertThat(profile.getReviewState()).isEqualTo("REJECTED");
        assertThat(profile.getReviewStatus()).isEqualTo("REVIEWED");
        verify(profileMapper).updateById(profile);
    }
}
