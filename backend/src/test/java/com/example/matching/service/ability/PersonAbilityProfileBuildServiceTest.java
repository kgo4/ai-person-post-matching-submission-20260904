package com.example.matching.service.ability;

import com.example.matching.entity.ability.PersonAbilityClaim;
import com.example.matching.entity.ability.PersonAbilityProfile;
import com.example.matching.mapper.ability.PersonAbilityClaimMapper;
import com.example.matching.mapper.ability.PersonAbilityProfileMapper;
import com.example.matching.service.ability.impl.PersonAbilityProfileBuildServiceImpl;
import com.example.matching.service.system.SourceWeightResolver;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PersonAbilityProfileBuildServiceTest {

    @Mock private PersonAbilityClaimMapper claimMapper;
    @Mock private PersonAbilityProfileMapper profileMapper;
    @Mock private ObjectMapper objectMapper;
    @Mock private SourceWeightResolver weightResolver;

    @InjectMocks private PersonAbilityProfileBuildServiceImpl service;

    @Test
    void buildProfile_skipsPendingHarnessReviewClaimsFromFormalProfile() {
        PersonAbilityClaim claim = new PersonAbilityClaim();
        claim.setEmpId(1L);
        claim.setTagId(7L);
        claim.setAbilityName("Java");
        claim.setClaimedLevel(3);
        claim.setSourceType("RESUME_PARSE");
        claim.setConfidenceScore(new BigDecimal("70"));
        claim.setStatus("PENDING_HARNESS_REVIEW");

        service.buildProfile(1L, List.of(claim));

        // 待确立（PENDING_HARNESS_REVIEW）Claim 不参与正式画像计算
        verify(profileMapper, never()).insert(any(PersonAbilityProfile.class));
        verify(profileMapper, never()).updateById(any(PersonAbilityProfile.class));
    }

    @Test
    void buildProfile_marksExistingProvisionalProfileAutoAfterClaimIsAccepted() {
        PersonAbilityClaim claim = new PersonAbilityClaim();
        claim.setEmpId(1L);
        claim.setTagId(7L);
        claim.setAbilityName("Java");
        claim.setClaimedLevel(3);
        claim.setSourceType("RESUME_PARSE");
        claim.setConfidenceScore(new BigDecimal("70"));
        claim.setStatus("FUSED");
        PersonAbilityProfile existing = new PersonAbilityProfile();
        existing.setEmpId(1L);
        existing.setTagId(7L);
        existing.setReviewState("PENDING");
        existing.setReviewStatus("PENDING_REVIEW");
        when(weightResolver.resolveConfigWeight("RESUME_PARSE")).thenReturn(new BigDecimal("0.15"));
        when(profileMapper.selectOne(any())).thenReturn(existing);

        service.buildProfile(1L, List.of(claim));

        assertThat(existing.getReviewState()).isEqualTo("AUTO");
        assertThat(existing.getReviewStatus()).isEqualTo("AUTO");
        verify(profileMapper).updateById(existing);
    }
}
