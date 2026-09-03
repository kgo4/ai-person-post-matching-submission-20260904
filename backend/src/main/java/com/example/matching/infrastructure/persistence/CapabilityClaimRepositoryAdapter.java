package com.example.matching.infrastructure.persistence;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.example.matching.application.agent.AbilityClaimCandidate;
import com.example.matching.application.agent.CapabilityClaimRepository;
import com.example.matching.application.agent.GovernanceDecision;
import com.example.matching.common.exception.BusinessException;
import com.example.matching.entity.ability.PersonAbilityClaim;
import com.example.matching.mapper.ability.PersonAbilityClaimMapper;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Repository;

import java.util.List;

@Slf4j
@Repository
@RequiredArgsConstructor
public class CapabilityClaimRepositoryAdapter implements CapabilityClaimRepository {

    private final PersonAbilityClaimMapper claimMapper;
    private final ObjectMapper objectMapper;

    @Override
    public Long admitClaim(AbilityClaimCandidate claim, GovernanceDecision decision) {
        return insert(claim, "READY_FOR_FUSION");
    }

    @Override
    public Long admitPendingClaim(AbilityClaimCandidate claim, GovernanceDecision decision) {
        return insert(claim, "PENDING_REVIEW");
    }

    @Override
    public List<AbilityClaimCandidate> loadAdmittedClaims(Long employeeId) {
        return claimMapper.selectList(Wrappers.<PersonAbilityClaim>lambdaQuery()
                        .eq(PersonAbilityClaim::getEmpId, employeeId)
                        .in(PersonAbilityClaim::getStatus, "READY_FOR_FUSION", "FUSED")
                        .eq(PersonAbilityClaim::getIsDeleted, 0))
                .stream().map(this::toCandidate).toList();
    }

    @Override
    public boolean existsByDeduplicationKey(Long employeeId,
                                             com.example.matching.application.agent.ClaimSource source,
                                             Long sourceRefId,
                                             String normalizedName) {
        Long count = claimMapper.selectCount(Wrappers.<PersonAbilityClaim>lambdaQuery()
                .eq(PersonAbilityClaim::getEmpId, employeeId)
                .eq(PersonAbilityClaim::getSourceType, source.name())
                .eq(PersonAbilityClaim::getSourceRefId, sourceRefId)
                .eq(PersonAbilityClaim::getNormalizedAbilityName, normalizedName)
                .eq(PersonAbilityClaim::getIsDeleted, 0));
        return count != null && count > 0;
    }

    private Long insert(AbilityClaimCandidate claim, String status) {
        PersonAbilityClaim entity = new PersonAbilityClaim();
        entity.setEmpId(claim.employeeId());
        entity.setTagId(claim.abilityTagId());
        entity.setAbilityName(claim.abilityName());
        entity.setNormalizedAbilityName(claim.normalizedAbilityName());
        entity.setClaimedLevel(claim.claimedLevel());
        entity.setSourceType(claim.source().name());
        entity.setSourceRefId(claim.sourceRefId());
        entity.setSourceWeight(claim.sourceWeight());
        entity.setEvidenceText(claim.evidence() == null ? null : claim.evidence().evidenceText());
        entity.setConfidenceScore(claim.confidence());
        entity.setFreshnessScore(claim.freshness());
        entity.setAuthorityScore(claim.authority());
        entity.setStatus(status);
        try {
            entity.setSourceRefsJson(objectMapper.writeValueAsString(
                    claim.evidence() == null ? List.of() : claim.evidence().sourceReferences()));
        } catch (JsonProcessingException e) {
            log.error("Failed to serialize sourceRefs for claim: empId={}, abilityName={}",
                    claim.employeeId(), claim.abilityName(), e);
            entity.setStatus("PENDING_REVIEW");
            entity.setSourceRefsJson("[]");
        }
        claimMapper.insert(entity);
        return entity.getId();
    }

    private AbilityClaimCandidate toCandidate(PersonAbilityClaim entity) {
        return new AbilityClaimCandidate(entity.getEmpId(), entity.getTagId(), entity.getAbilityName(),
                entity.getNormalizedAbilityName(), entity.getClaimedLevel(),
                com.example.matching.application.agent.ClaimSource.fromString(entity.getSourceType()),
                entity.getSourceRefId(), com.example.matching.application.agent.EvidenceBundle.empty(),
                entity.getConfidenceScore(), entity.getFreshnessScore(), entity.getAuthorityScore(), null);
    }
}
