package com.example.matching.service.ability.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.example.matching.dto.harness.AiHarnessClaimDTO;
import com.example.matching.dto.harness.AiHarnessDecisionDTO;
import com.example.matching.common.enums.AbilitySourceType;
import com.example.matching.entity.ability.PersonAbilityClaim;
import com.example.matching.entity.employee.EmpAbility;
import com.example.matching.entity.governance.GovernanceAdmissionRecord;
import com.example.matching.entity.harness.AiHarnessCheckLog;
import com.example.matching.event.AbilityChangeEvent;
import com.example.matching.mapper.ability.PersonAbilityClaimMapper;
import com.example.matching.mapper.employee.EmpAbilityMapper;
import com.example.matching.mapper.governance.GovernanceAdmissionMapper;
import com.example.matching.mapper.harness.AiHarnessCheckLogMapper;
import com.example.matching.service.ability.PersonAbilityClaimAdmissionService;
import com.example.matching.service.ability.PersonAbilityProfileAgent;
import com.example.matching.service.ability.AbilityEvidenceIngestionService;
import com.example.matching.service.harness.AiTrustHarnessService;
import com.example.matching.service.system.AbilityTagService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class PersonAbilityClaimAdmissionServiceImpl implements PersonAbilityClaimAdmissionService {

    static final String PENDING_HARNESS_REVIEW = "PENDING_HARNESS_REVIEW";
    static final String READY_FOR_FUSION = "READY_FOR_FUSION";
    static final String FUSED = "FUSED";
    static final String REJECTED = "REJECTED";

    private final PersonAbilityClaimMapper claimMapper;
    private final EmpAbilityMapper empAbilityMapper;
    private final AiHarnessCheckLogMapper harnessLogMapper;
    private final GovernanceAdmissionMapper admissionMapper;
    private final AiTrustHarnessService harnessService;
    private final AbilityTagService abilityTagService;
    private final PersonAbilityProfileAgent profileAgent;
    private final AbilityEvidenceIngestionService abilityEvidenceIngestionService;
    private final ApplicationEventPublisher eventPublisher;

    @Override
    @Deprecated
    @Transactional(rollbackFor = Exception.class)
    public PersonAbilityClaim admit(com.example.matching.agent.dto.person.PersonAbilityClaim source,
                                    AiHarnessDecisionDTO decision) {
        return admitInternal(source, decision, true);
    }

    @Override
    @Deprecated
    @Transactional(rollbackFor = Exception.class)
    public PersonAbilityClaim admitWithoutSideEffects(com.example.matching.agent.dto.person.PersonAbilityClaim source,
                                                       AiHarnessDecisionDTO decision) {
        return admitInternal(source, decision, false);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void completeBatchForEmployee(Long empId) {
        if (empId == null) {
            return;
        }
        profileAgent.refreshProfile(empId);
        eventPublisher.publishEvent(new AbilityChangeEvent(this, "EMP_ABILITY", empId));
    }

    private PersonAbilityClaim admitInternal(com.example.matching.agent.dto.person.PersonAbilityClaim source,
                                              AiHarnessDecisionDTO decision,
                                              boolean refreshProfileAndPublishEvent) {
        if (source == null || decision == null || AiHarnessDecisionDTO.BLOCK.equals(decision.getDecision())) {
            return null;
        }
        PersonAbilityClaim existing = findBySource(source);
        if (existing != null) {
            if (READY_FOR_FUSION.equals(existing.getStatus())) {
                fuse(existing, refreshProfileAndPublishEvent, null);
            }
            return existing;
        }

        PersonAbilityClaim claim = toEntity(source, decision);
        if (AiHarnessDecisionDTO.REVIEW.equals(decision.getDecision())) {
            claim.setStatus(PENDING_HARNESS_REVIEW);
            claimMapper.insert(claim);
            return claim;
        }

        claim.setStatus(READY_FOR_FUSION);
        claimMapper.insert(claim);
        fuse(claim, refreshProfileAndPublishEvent, null);
        return claim;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean acceptReview(Long harnessLogId) {
        PersonAbilityClaim claim = findByHarnessLogId(harnessLogId);
        if (claim == null || !PENDING_HARNESS_REVIEW.equals(claim.getStatus())) {
            return false;
        }
        // 人工明确复核后重新准入：仅当 Harness 重新验证为 PASS 时才允许写入正式事实表
        AiHarnessDecisionDTO decision;
        try {
            decision = harnessService.verify(buildReverifyClaim(claim));
        } catch (Exception e) {
            log.warn("人工采纳复审失败: harnessLogId={}, error={}", harnessLogId, e.getMessage());
            return false;
        }
        if (!AiHarnessDecisionDTO.PASS.equals(decision.getDecision())) {
            log.warn("人工采纳复审未通过，拒绝写入正式事实表: harnessLogId={}, decision={}",
                    harnessLogId, decision.getDecision());
            return false;
        }

        GovernanceAdmissionRecord admission = new GovernanceAdmissionRecord();
        admission.setAdmissionCode("GAD_" + UUID.randomUUID().toString().replace("-", "").substring(0, 12).toUpperCase());
        admission.setScenario("PERSON_ABILITY");
        admission.setClaimType("EMP_ABILITY");
        admission.setClaimText(claim.getAbilityName());
        admission.setSourceType(claim.getSourceType());
        admission.setSourceRefId(claim.getSourceRefId());
        admission.setEvidenceText(claim.getEvidenceText());
        admission.setMatchedTagId(claim.getTagId());
        admission.setHarnessDecision(decision.getDecision());
        admission.setFinalDecision("PASS");
        admission.setHarnessSupportScore(decision.getSupportScore());
        admission.setFinalSupportScore(decision.getSupportScore());
        admission.setDecisionRule("MANUAL_REVIEW_ACCEPT");
        admission.setHarnessCheckCode(decision.getCheckCode());
        admission.setRiskLevel(decision.getRiskLevel());
        admission.setApplyStatus("FUSED");
        admission.setCreatedTime(LocalDateTime.now());
        admissionMapper.insert(admission);

        claim.setStatus(READY_FOR_FUSION);
        claimMapper.updateById(claim);
        fuse(claim, admission.getId());
        return true;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean rejectReview(Long harnessLogId) {
        PersonAbilityClaim claim = findByHarnessLogId(harnessLogId);
        if (claim == null || !PENDING_HARNESS_REVIEW.equals(claim.getStatus())) {
            return false;
        }
        claim.setStatus(REJECTED);
        claimMapper.updateById(claim);
        return true;
    }

    private void fuse(PersonAbilityClaim claim) {
        fuse(claim, true, null);
    }

    private void fuse(PersonAbilityClaim claim, Long governanceAdmissionId) {
        fuse(claim, true, governanceAdmissionId);
    }

    private void fuse(PersonAbilityClaim claim, boolean refreshProfileAndPublishEvent, Long governanceAdmissionId) {
        Long abilityId = upsertEmpAbility(claim, governanceAdmissionId);
        if (abilityId != null) {
            abilityEvidenceIngestionService.ingestEmployeeAbility(abilityId, "EMP_ABILITY");
        }
        if (refreshProfileAndPublishEvent) {
            profileAgent.refreshProfile(claim.getEmpId());
            eventPublisher.publishEvent(new AbilityChangeEvent(this, "EMP_ABILITY", claim.getEmpId()));
        }
        claim.setStatus(FUSED);
        claimMapper.updateById(claim);
    }

    private void resolveFormalTag(PersonAbilityClaim claim) {
        // Personnel claims never create or require taxonomy tags. Tags belong
        // to the role/market discovery domain and remain optional enrichment.
    }

    private Long upsertEmpAbility(PersonAbilityClaim claim, Long governanceAdmissionId) {
        String formalSource = formalSource(claim.getSourceType());
        EmpAbility existing = empAbilityMapper.selectOne(new LambdaQueryWrapper<EmpAbility>()
                .eq(EmpAbility::getEmpId, claim.getEmpId())
                .eq(EmpAbility::getAbilityName, claim.getAbilityName())
                .eq(EmpAbility::getEvaluationSource, formalSource)
                .eq(EmpAbility::getIsDeleted, 0)
                .last("LIMIT 1"));
        if (existing == null && (AbilitySourceType.AI_TEST.equals(claim.getSourceType())
                || AbilitySourceType.AI_INTERVIEW.equals(claim.getSourceType()))) {
            return null;
        }
        if (existing != null && (AbilitySourceType.AI_TEST.equals(claim.getSourceType())
                || AbilitySourceType.AI_INTERVIEW.equals(claim.getSourceType()))) {
            return existing.getId();
        }
        if (existing == null) {
            EmpAbility ability = new EmpAbility();
            ability.setEmpId(claim.getEmpId());
            ability.setTagId(claim.getTagId());
            ability.setAbilityName(claim.getAbilityName());
            ability.setMasteryLevel(claim.getClaimedLevel());
            ability.setAbilityLevel(claim.getClaimedLevel());
            ability.setEvaluationSource(formalSource);
            ability.setSourceWeight(sourceWeight(claim.getConfidenceScore()));
            ability.setEvaluationDate(LocalDate.now());
            ability.setRemark(claim.getEvidenceText());
            ability.setIsDeleted(0);
            ability.setVersion(0);
            ability.setGovernanceAdmissionId(governanceAdmissionId);
            try {
                empAbilityMapper.insert(ability);
                return ability.getId();
            } catch (DuplicateKeyException e) {
                existing = empAbilityMapper.selectOne(new LambdaQueryWrapper<EmpAbility>()
                        .eq(EmpAbility::getEmpId, claim.getEmpId())
                        .eq(EmpAbility::getAbilityName, claim.getAbilityName())
                        .eq(EmpAbility::getEvaluationSource, formalSource)
                        .eq(EmpAbility::getIsDeleted, 0)
                        .last("LIMIT 1"));
                if (existing == null) {
                    throw e;
                }
            }
        }
        existing.setMasteryLevel(claim.getClaimedLevel());
        existing.setAbilityName(claim.getAbilityName());
        existing.setAbilityLevel(claim.getClaimedLevel());
        existing.setSourceWeight(sourceWeight(claim.getConfidenceScore()));
        existing.setEvaluationDate(LocalDate.now());
        existing.setRemark(claim.getEvidenceText());
        existing.setGovernanceAdmissionId(governanceAdmissionId);
        empAbilityMapper.updateById(existing);
        return existing.getId();
    }

    private String formalSource(String claimSource) {
        if (AbilitySourceType.AI_PROJECT.equals(claimSource)) {
            return AbilitySourceType.AI_PROJECT;
        }
        if (AbilitySourceType.MANUAL.equals(claimSource)) {
            return AbilitySourceType.MANUAL;
        }
        return AbilitySourceType.ASSESSMENT_WORKFLOW;
    }

    private AiHarnessClaimDTO buildReverifyClaim(PersonAbilityClaim claim) {
        AiHarnessClaimDTO dto = new AiHarnessClaimDTO();
        dto.setScenario("PERSON_ABILITY");
        dto.setClaimType("EMP_ABILITY");
        dto.setClaimText(claim.getAbilityName());
        dto.setSourceType(claim.getSourceType());
        dto.setSourceRefId(claim.getSourceRefId());
        dto.setEvidenceText(claim.getEvidenceText());
        dto.setMatchedTagId(claim.getTagId());
        if (claim.getSourceRefsJson() != null && !claim.getSourceRefsJson().isBlank()
                && !"[]".equals(claim.getSourceRefsJson())) {
            dto.setSourceRefs(parseSourceRefs(claim.getSourceRefsJson()));
        }
        return dto;
    }

    @SuppressWarnings("unchecked")
    private List<String> parseSourceRefs(String json) {
        try {
            return new com.fasterxml.jackson.databind.ObjectMapper().readValue(json, List.class);
        } catch (Exception e) {
            return List.of();
        }
    }

    private PersonAbilityClaim toEntity(com.example.matching.agent.dto.person.PersonAbilityClaim source,
                                         AiHarnessDecisionDTO decision) {
        PersonAbilityClaim claim = new PersonAbilityClaim();
        claim.setEmpId(source.getEmpId());
        claim.setTagId(source.getAbilityTagId());
        claim.setAbilityName(source.getAbilityName());
        claim.setNormalizedAbilityName(normalize(source));
        claim.setClaimedLevel(source.getMasteryLevel());
        claim.setSourceType(source.getSourceType());
        claim.setSourceRefId(source.getSourceRefId());
        claim.setEvidenceText(source.getEvidenceText());
        claim.setConfidenceScore(source.getConfidenceScore());
        claim.setSourceWeight(sourceWeight(source.getConfidenceScore()));
        claim.setSourceRefsJson(source.getSourceRefs() == null ? "[]" : source.getSourceRefs().toString());
        claim.setHarnessDecision(decision.getDecision());
        claim.setHarnessLogId(findHarnessLogId(decision.getCheckCode()));
        return claim;
    }

    private PersonAbilityClaim findBySource(com.example.matching.agent.dto.person.PersonAbilityClaim source) {
        return claimMapper.selectOne(new LambdaQueryWrapper<PersonAbilityClaim>()
                .eq(PersonAbilityClaim::getEmpId, source.getEmpId())
                .eq(PersonAbilityClaim::getSourceType, source.getSourceType())
                .eq(PersonAbilityClaim::getSourceRefId, source.getSourceRefId())
                .eq(PersonAbilityClaim::getNormalizedAbilityName, normalize(source))
                .last("LIMIT 1"));
    }

    private PersonAbilityClaim findByHarnessLogId(Long harnessLogId) {
        if (harnessLogId == null) {
            return null;
        }
        PersonAbilityClaim linked = claimMapper.selectOne(new LambdaQueryWrapper<PersonAbilityClaim>()
                .eq(PersonAbilityClaim::getHarnessLogId, harnessLogId)
                .last("LIMIT 1"));
        if (linked != null) {
            return linked;
        }

        // Older review rows were created before the claim-to-harness FK was
        // reliably populated. Recover the pending claim from the immutable
        // harness payload so manual review remains actionable.
        AiHarnessCheckLog harnessLog = harnessLogMapper.selectById(harnessLogId);
        if (harnessLog == null || !isPersonAbilityLog(harnessLog)) {
            return null;
        }
        LambdaQueryWrapper<PersonAbilityClaim> fallback = new LambdaQueryWrapper<PersonAbilityClaim>()
                .eq(PersonAbilityClaim::getStatus, PENDING_HARNESS_REVIEW)
                .eq(PersonAbilityClaim::getIsDeleted, 0);
        if (harnessLog.getBusinessTargetId() != null
                && "EMP_ABILITY".equals(harnessLog.getBusinessTargetType())) {
            fallback.eq(PersonAbilityClaim::getEmpId, harnessLog.getBusinessTargetId());
        }
        if (harnessLog.getSourceType() != null) {
            fallback.eq(PersonAbilityClaim::getSourceType, harnessLog.getSourceType());
        }
        if (harnessLog.getSourceRefId() != null) {
            fallback.eq(PersonAbilityClaim::getSourceRefId, harnessLog.getSourceRefId());
        }
        String normalizedName = normalize(harnessLog.getClaimText());
        if (!normalizedName.isEmpty()) {
            fallback.eq(PersonAbilityClaim::getNormalizedAbilityName, normalizedName);
        }
        PersonAbilityClaim recovered = claimMapper.selectOne(fallback.last("LIMIT 1"));
        if (recovered != null) {
            recovered.setHarnessLogId(harnessLogId);
        }
        return recovered;
    }

    private boolean isPersonAbilityLog(AiHarnessCheckLog log) {
        return "EMP_ABILITY".equals(log.getClaimType())
                || "PERSON_ABILITY".equals(log.getScenario())
                || "PMS_ANALYSIS".equals(log.getScenario())
                || "RESUME_PARSE".equals(log.getScenario());
    }

    private String normalize(String value) {
        return value == null ? "" : value.trim().toLowerCase();
    }

    private Long findHarnessLogId(String checkCode) {
        if (checkCode == null || checkCode.isBlank()) {
            return null;
        }
        AiHarnessCheckLog log = harnessLogMapper.selectOne(new LambdaQueryWrapper<AiHarnessCheckLog>()
                .eq(AiHarnessCheckLog::getCheckCode, checkCode)
                .last("LIMIT 1"));
        return log == null ? null : log.getId();
    }

    private BigDecimal sourceWeight(BigDecimal confidence) {
        return confidence == null
                ? BigDecimal.valueOf(0.8)
                : confidence.divide(BigDecimal.valueOf(100), 4, RoundingMode.HALF_UP);
    }

    private String normalize(com.example.matching.agent.dto.person.PersonAbilityClaim source) {
        if (source.getNormalizedAbilityName() != null && !source.getNormalizedAbilityName().isBlank()) {
            return source.getNormalizedAbilityName().trim().toLowerCase();
        }
        return source.getAbilityName() == null ? "" : source.getAbilityName().trim().toLowerCase();
    }
}
