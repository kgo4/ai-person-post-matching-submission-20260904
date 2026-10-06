package com.example.matching.service.assessment.impl;

import com.example.matching.dto.assessment.ResumeAbilityClaimDTO;
import com.example.matching.entity.ability.PersonAbilityClaim;
import com.example.matching.mapper.ability.PersonAbilityClaimMapper;
import com.example.matching.mapper.workflow.PersonAbilityClaimGroupMapper;
import com.example.matching.service.system.AbilityTagService;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.when;

class AbilityEvidenceCollectionServiceImplTest {

    private AbilityEvidenceCollectionServiceImpl service(PersonAbilityClaimMapper claimMapper) {
        return new AbilityEvidenceCollectionServiceImpl(
                claimMapper,
                mock(PersonAbilityClaimGroupMapper.class),
                mock(AbilityTagService.class));
    }

    private ResumeAbilityClaimDTO claim(String name, String evidence, int confidence) {
        ResumeAbilityClaimDTO dto = new ResumeAbilityClaimDTO();
        dto.setAbilityName(name);
        dto.setNormalizedAbilityName(name);
        dto.setClaimedLevel(3);
        dto.setEvidenceText(evidence);
        dto.setEvidenceLocation("resume:75:0-10");
        dto.setSourceRefId(75L);
        dto.setSourceRefs(List.of("source:RESUME_PARSE:75"));
        dto.setConfidenceScore(BigDecimal.valueOf(confidence));
        return dto;
    }

    @Test
    void rejectsStandaloneProductAndModelNames() {
        assertThat(AbilityEvidenceCollectionServiceImpl.isProductOnlyAbilityName("讯飞星火大模型")).isTrue();
        assertThat(AbilityEvidenceCollectionServiceImpl.isProductOnlyAbilityName("ChatGPT v4")).isTrue();
        assertThat(AbilityEvidenceCollectionServiceImpl.isProductOnlyAbilityName(" Git-Hub ")).isTrue();
    }

    @Test
    void keepsConcreteCapabilitiesAndTechnicalStacksEligible() {
        assertThat(AbilityEvidenceCollectionServiceImpl.isProductOnlyAbilityName("大模型 API 集成")).isFalse();
        assertThat(AbilityEvidenceCollectionServiceImpl.isProductOnlyAbilityName("Redis 缓存设计")).isFalse();
        assertThat(AbilityEvidenceCollectionServiceImpl.isProductOnlyAbilityName("Java 后端开发")).isFalse();
    }

    @Test
    void deduplicatesRepeatedResumeClaimsBeforeInsert() {
        PersonAbilityClaimMapper mapper = mock(PersonAbilityClaimMapper.class);
        when(mapper.selectList(any())).thenReturn(List.of());
        AbilityEvidenceCollectionServiceImpl service = service(mapper);

        int saved = service.saveResumeClaims(55L, 101L, 64L, List.of(
                claim("Java 后端开发", "负责 Java 后端开发并完成接口设计", 70),
                claim("Java后端开发", "负责 Java 后端开发并完成接口设计、性能优化", 90)
        ), 1L);

        assertThat(saved).isEqualTo(1);
        verify(mapper, times(1)).insert(any(PersonAbilityClaim.class));
    }

    @Test
    void skipsClaimsAlreadyPersistedForTheSameResumeParse() {
        PersonAbilityClaimMapper mapper = mock(PersonAbilityClaimMapper.class);
        PersonAbilityClaim existing = new PersonAbilityClaim();
        existing.setEmpId(64L);
        existing.setSourceType("RESUME_PARSE");
        existing.setSourceRefId(75L);
        existing.setNormalizedAbilityName("Java后端开发");
        existing.setStatus("ACTIVE");
        when(mapper.selectList(any())).thenReturn(List.of(existing));
        AbilityEvidenceCollectionServiceImpl service = service(mapper);

        int saved = service.saveResumeClaims(55L, 101L, 64L,
                List.of(claim("Java 后端开发", "负责 Java 后端开发并完成接口设计", 90)), 1L);

        assertThat(saved).isZero();
        verify(mapper, times(0)).insert(any(PersonAbilityClaim.class));
    }
}
