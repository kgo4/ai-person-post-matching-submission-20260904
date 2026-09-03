package com.example.matching.service.governance.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.example.matching.agent.dto.person.PersonAbilityClaim;
import com.example.matching.agent.dto.post.PostAbilityClaim;
import com.example.matching.dto.governance.GovernanceAdmission;
import com.example.matching.dto.harness.AiHarnessClaimDTO;
import com.example.matching.dto.governance.GovernanceGrant;
import com.example.matching.dto.harness.AiHarnessDecisionDTO;
import com.example.matching.entity.employee.EmpAbility;
import com.example.matching.entity.governance.GovernanceAdmissionRecord;
import com.example.matching.entity.harness.AiHarnessCheckLog;
import com.example.matching.entity.post.PostAbilityModel;
import com.example.matching.entity.system.AbilityTag;
import com.example.matching.entity.system.AbilityTagCandidate;
import com.example.matching.mapper.ability.PersonAbilityClaimMapper;
import com.example.matching.mapper.employee.EmpAbilityMapper;
import com.example.matching.mapper.governance.GovernanceAdmissionMapper;
import com.example.matching.mapper.harness.AiHarnessCheckLogMapper;
import com.example.matching.mapper.post.PostAbilityModelMapper;
import com.example.matching.service.system.AbilityTagCandidateService;
import com.example.matching.service.system.AbilityTagService;
import com.example.matching.service.post.PostAbilityWeightNormalizer;
import java.time.Duration;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.Locale;

/**
 * 治理准入实体构造与持久化：准入记录、Harness 声明构建、事实表写入（emp_ability/post_ability_model）。
 * <p>
 * 从 GovernedAdmissionServiceImpl（690+ 行）中拆分的持久化/实体构建组件。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class GovernedAdmissionEntityBuilder {

    final PersonAbilityClaimMapper personClaimMapper;
    private final EmpAbilityMapper empAbilityMapper;
    private final PostAbilityModelMapper postAbilityModelMapper;
    private final AiHarnessCheckLogMapper harnessLogMapper;
    private final GovernanceAdmissionMapper admissionMapper;
    private final AbilityTagService abilityTagService;
    private final AbilityTagCandidateService abilityTagCandidateService;
    private final ObjectMapper objectMapper;

    static final String PENDING_HARNESS_REVIEW = "PENDING_HARNESS_REVIEW";
    static final String READY_FOR_FUSION = "READY_FOR_FUSION";
    static final String FUSED = "FUSED";
    static final String RETRYABLE = "RETRYABLE";
    static final String RETRY_EXHAUSTED = "RETRY_EXHAUSTED";
    static final int MAX_RETRIES = 10;
    static final Duration RETRY_INITIAL_DELAY = Duration.ofMinutes(5);
    static final Duration RETRY_MAX_DELAY = Duration.ofMinutes(60);
    public GovernanceAdmission initAdmission(String scenario, String claimText) {
        GovernanceAdmission admission = new GovernanceAdmission();
        admission.setAdmissionCode("GAD_" + UUID.randomUUID().toString().replace("-", "").substring(0, 12).toUpperCase());
        admission.setScenario(scenario);
        admission.setClaimText(claimText);
        admission.setCreatedTime(LocalDateTime.now());
        return admission;
    }

    public void enrichAdmission(GovernanceAdmission admission, AiHarnessDecisionDTO decision, GovernanceGrant grant) {
        admission.setHarnessSupportScore(decision.getSupportScore());
        admission.setFinalSupportScore(decision.getSupportScore());
        admission.setLegacySupportScore(decision.getLegacySupportScore());
        admission.setLegacyDecision(decision.getLegacyDecision());
        admission.setHarnessDecision(decision.getDecision());
        admission.setFinalDecision(grant.name());
        admission.setRiskLevel(decision.getRiskLevel());
        admission.setSelfEvidence(decision.isSelfEvidence());
        admission.setMatchedTagId(decision.getMatchedTagId());
        admission.setSimilarTagId(decision.getSimilarTagId());
        admission.setReasonJson(json(Map.of("reasons", decision.getReasons() != null ? decision.getReasons() : List.of())));
        admission.setAcceptedSourceRefsJson(json(decision.getAcceptedSourceRefs()));
        admission.setInvalidSourceRefsJson(json(decision.getInvalidSourceRefs()));
        admission.setMissingEvidenceJson(json(decision.getMissingEvidence()));
        admission.setTraceId(decision.getTraceId());
        admission.setDecisionRule(decision.getDecisionRule());
    }

    public void persistAdmission(GovernanceAdmission admission) {
        GovernanceAdmissionRecord record = toRecord(admission);
        admissionMapper.insert(record);
        admission.setId(record.getId());
    }

    public void updateAdmissionTarget(GovernanceAdmission admission) {
        if (admission.getId() == null) {
            return;
        }
        GovernanceAdmissionRecord record = new GovernanceAdmissionRecord();
        record.setId(admission.getId());
        record.setBusinessTargetType(admission.getBusinessTargetType());
        record.setBusinessTargetId(admission.getBusinessTargetId());
        record.setApplyStatus(admission.getApplyStatus());
        admissionMapper.updateById(record);
    }

    public GovernanceAdmissionRecord toRecord(GovernanceAdmission admission) {
        GovernanceAdmissionRecord record = new GovernanceAdmissionRecord();
        record.setAdmissionCode(admission.getAdmissionCode());
        record.setScenario(admission.getScenario());
        record.setClaimType(admission.getClaimType());
        record.setClaimText(admission.getClaimText());
        record.setSourceType(admission.getSourceType());
        record.setSourceRefId(admission.getSourceRefId());
        record.setEvidenceText(admission.getEvidenceText());
        record.setSourceRefsJson(admission.getSourceRefsJson());
        record.setRagChunkIdsJson(admission.getRagChunkIdsJson());
        record.setMatchedTagId(admission.getMatchedTagId());
        record.setSimilarTagId(admission.getSimilarTagId());
        record.setLegacySupportScore(admission.getLegacySupportScore());
        record.setHarnessSupportScore(admission.getHarnessSupportScore());
        record.setFinalSupportScore(admission.getFinalSupportScore());
        record.setLegacyDecision(admission.getLegacyDecision());
        record.setHarnessDecision(admission.getHarnessDecision());
        record.setFinalDecision(admission.getFinalDecision());
        record.setDecisionRule(admission.getDecisionRule());
        record.setHarnessCheckCode(admission.getHarnessCheckCode());
        record.setTraceId(admission.getTraceId());
        record.setRiskLevel(admission.getRiskLevel());
        record.setIsSelfEvidence(admission.isSelfEvidence() ? 1 : 0);
        record.setReasonJson(admission.getReasonJson());
        record.setAcceptedSourceRefsJson(admission.getAcceptedSourceRefsJson());
        record.setInvalidSourceRefsJson(admission.getInvalidSourceRefsJson());
        record.setMissingEvidenceJson(admission.getMissingEvidenceJson());
        record.setBusinessTargetType(admission.getBusinessTargetType());
        record.setBusinessTargetId(admission.getBusinessTargetId());
        record.setApplyStatus(admission.getApplyStatus());
        record.setContextSnapshotId(admission.getContextSnapshotId());
        record.setContextHash(admission.getContextHash());
        record.setClaimPayloadJson(admission.getClaimPayloadJson());
        record.setRetryCount(admission.getRetryCount());
        record.setNextRetryTime(admission.getNextRetryTime());
        record.setCreatedTime(admission.getCreatedTime());
        return record;
    }

    public AiHarnessClaimDTO buildPersonHarnessClaim(
            com.example.matching.agent.dto.person.PersonAbilityClaim claim) {
        AiHarnessClaimDTO dto = new AiHarnessClaimDTO();
        dto.setScenario("PERSON_ABILITY");
        // The scenario identifies the workflow; the claim type identifies the
        // business fact that will be admitted.
        dto.setClaimType("EMP_ABILITY");
        dto.setClaimText(claim.getAbilityName());
        dto.setSourceType(claim.getSourceType());
        dto.setSourceRefId(claim.getSourceRefId());
        dto.setEvidenceText(claim.getEvidenceText());
        dto.setSourceRefs(claim.getSourceRefs());
        dto.setMatchedTagId(claim.getAbilityTagId());
        dto.setConfidence(claim.getConfidenceScore() != null ? claim.getConfidenceScore().doubleValue() : null);
        dto.setBusinessTargetType("EMP_ABILITY");
        dto.setBusinessTargetId(claim.getEmpId());
        return dto;
    }

    public AiHarnessClaimDTO buildPostHarnessClaim(
            com.example.matching.agent.dto.post.PostAbilityClaim claim) {
        AiHarnessClaimDTO dto = new AiHarnessClaimDTO();
        dto.setScenario("POST_ABILITY");
        dto.setClaimType("POST_ABILITY");
        dto.setClaimText(claim.getAbilityName());
        dto.setSourceType(claim.getSourceType());
        dto.setSourceRefId(claim.getSourceRefId());
        dto.setEvidenceText(claim.getEvidenceText());
        dto.setSourceRefs(claim.getSourceRefs());
        dto.setMatchedTagId(claim.getAbilityTagId());
        dto.setConfidence(claim.getConfidenceScore() != null ? claim.getConfidenceScore().doubleValue() : null);
        dto.setBusinessTargetType("POST_ABILITY_MODEL");
        dto.setBusinessTargetId(claim.getPostId());
        return dto;
    }

    public com.example.matching.entity.ability.PersonAbilityClaim findOrCreatePersonClaimEntity(
            com.example.matching.agent.dto.person.PersonAbilityClaim source,
            AiHarnessDecisionDTO decision,
            Long harnessLogId) {
        com.example.matching.entity.ability.PersonAbilityClaim existing = personClaimMapper.selectOne(
                new LambdaQueryWrapper<com.example.matching.entity.ability.PersonAbilityClaim>()
                        .eq(com.example.matching.entity.ability.PersonAbilityClaim::getEmpId, source.getEmpId())
                        .eq(com.example.matching.entity.ability.PersonAbilityClaim::getSourceType, source.getSourceType())
                        .eq(com.example.matching.entity.ability.PersonAbilityClaim::getSourceRefId, source.getSourceRefId())
                        .eq(com.example.matching.entity.ability.PersonAbilityClaim::getNormalizedAbilityName, normalize(source))
                        .last("LIMIT 1"));
        if (existing != null) {
            return existing;
        }
        com.example.matching.entity.ability.PersonAbilityClaim entity = toPersonClaimEntity(source, decision, harnessLogId);
        entity.setStatus(READY_FOR_FUSION);
        personClaimMapper.insert(entity);
        return entity;
    }

    public com.example.matching.entity.ability.PersonAbilityClaim toPersonClaimEntity(
            com.example.matching.agent.dto.person.PersonAbilityClaim source,
            AiHarnessDecisionDTO decision,
            Long harnessLogId) {
        com.example.matching.entity.ability.PersonAbilityClaim entity =
                new com.example.matching.entity.ability.PersonAbilityClaim();
        entity.setEmpId(source.getEmpId());
        entity.setTagId(source.getAbilityTagId());
        entity.setAbilityName(source.getAbilityName());
        entity.setNormalizedAbilityName(normalize(source));
        entity.setClaimedLevel(source.getMasteryLevel());
        entity.setSourceType(source.getSourceType());
        entity.setSourceRefId(source.getSourceRefId());
        entity.setEvidenceText(source.getEvidenceText());
        entity.setConfidenceScore(source.getConfidenceScore());
        entity.setSourceWeight(sourceWeight(source.getConfidenceScore()));
        entity.setSourceRefsJson(source.getSourceRefs() == null ? "[]" : source.getSourceRefs().toString());
        entity.setHarnessDecision(decision.getDecision());
        entity.setHarnessLogId(harnessLogId);
        return entity;
    }

    public Long writeEmpAbility(com.example.matching.entity.ability.PersonAbilityClaim claim,
                                 Long governanceAdmissionId) {
        EmpAbility existing = empAbilityMapper.selectOne(new LambdaQueryWrapper<EmpAbility>()
                .eq(EmpAbility::getEmpId, claim.getEmpId())
                .eq(claim.getTagId() != null, EmpAbility::getTagId, claim.getTagId())
                .eq(claim.getTagId() == null, EmpAbility::getAbilityName, claim.getAbilityName())
                .eq(EmpAbility::getEvaluationSource, claim.getSourceType())
                .eq(EmpAbility::getIsDeleted, 0)
                .last("LIMIT 1"));
        if (existing == null) {
            EmpAbility ability = new EmpAbility();
            ability.setEmpId(claim.getEmpId());
            ability.setTagId(claim.getTagId());
            ability.setAbilityName(claim.getAbilityName());
            ability.setMasteryLevel(claim.getClaimedLevel());
            ability.setAbilityLevel(claim.getClaimedLevel());
            ability.setEvaluationSource(claim.getSourceType());
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
                        .eq(claim.getTagId() != null, EmpAbility::getTagId, claim.getTagId())
                        .eq(claim.getTagId() == null, EmpAbility::getAbilityName, claim.getAbilityName())
                        .eq(EmpAbility::getEvaluationSource, claim.getSourceType())
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

    public Long upsertPostAbilityModel(com.example.matching.agent.dto.post.PostAbilityClaim claim,
                                        Long governanceAdmissionId) {
        requirePostSourceEvidence(claim);
        String abilityName = claim.getAbilityName().trim();
        String skillPointKey = skillPointKey(abilityName);
        PostAbilityModel existing = postAbilityModelMapper.selectOne(new LambdaQueryWrapper<PostAbilityModel>()
                .eq(PostAbilityModel::getPostId, claim.getPostId())
                .eq(PostAbilityModel::getSkillPointKey, skillPointKey)
                .eq(PostAbilityModel::getIsDeleted, 0)
                .last("LIMIT 1"));
        if (existing != null) {
            existing.setTagId(claim.getAbilityTagId());
            existing.setAbilityName(abilityName);
            existing.setTechStack(resolveTechStack(claim.getTechStack(), abilityName));
            existing.setSkillPointKey(skillPointKey);
            existing.setMinRequiredLevel(claim.getRequiredLevel() != null ? claim.getRequiredLevel() : 3);
            existing.setWeight(PostAbilityWeightNormalizer.toPercentage(claim.getWeight(), new BigDecimal("5")));
            existing.setIsCore(claim.getIsCore() != null && claim.getIsCore() ? 1 : 0);
            existing.setIsRequired(claim.getIsRequired() != null && claim.getIsRequired() ? 1 : 0);
            existing.setRemark(claim.getSourceRefs() != null && !claim.getSourceRefs().isEmpty()
                    ? claim.getSourceRefs().get(0) : null);
            existing.setGovernanceAdmissionId(governanceAdmissionId);
            existing.setSourceType(claim.getSourceType());
            postAbilityModelMapper.updateById(existing);
            return existing.getId();
        }
        PostAbilityModel model = new PostAbilityModel();
        model.setPostId(claim.getPostId());
        model.setTagId(claim.getAbilityTagId());
        model.setAbilityName(abilityName);
        model.setTechStack(resolveTechStack(claim.getTechStack(), abilityName));
        model.setSkillPointKey(skillPointKey);
        model.setMinRequiredLevel(claim.getRequiredLevel() != null ? claim.getRequiredLevel() : 3);
        model.setWeight(PostAbilityWeightNormalizer.toPercentage(claim.getWeight(), new BigDecimal("5")));
        model.setIsCore(claim.getIsCore() != null && claim.getIsCore() ? 1 : 0);
        model.setIsRequired(claim.getIsRequired() != null && claim.getIsRequired() ? 1 : 0);
        model.setRemark(claim.getSourceRefs() != null && !claim.getSourceRefs().isEmpty()
                ? claim.getSourceRefs().get(0) : null);
        model.setIsDeleted(0);
        model.setGovernanceAdmissionId(governanceAdmissionId);
        model.setSourceType(claim.getSourceType());
        postAbilityModelMapper.insert(model);
        return model.getId();
    }

    private String skillPointKey(String abilityName) {
        return abilityName.toLowerCase(Locale.ROOT).replaceAll("\\s+", " ");
    }

    private String resolveTechStack(String techStack, String abilityName) {
        if (techStack != null && !techStack.isBlank()) {
            return techStack.trim();
        }
        String text = abilityName.toLowerCase(Locale.ROOT);
        if (text.contains("spring")) return "Spring";
        if (text.contains("java")) return "Java";
        if (text.contains("mysql") || text.contains("sql") || text.contains("数据库")) return "数据存储";
        if (text.contains("redis")) return "Redis";
        if (text.contains("rabbitmq") || text.contains("kafka") || text.contains("消息")) return "消息队列";
        if (text.contains("docker") || text.contains("kubernetes") || text.contains("k8s")) return "云原生";
        return "通用工程能力";
    }

    public void writePostAbilityCandidate(com.example.matching.agent.dto.post.PostAbilityClaim claim,
                                           AiHarnessDecisionDTO decision) {
        requirePostSourceEvidence(claim);
        try {
            AbilityTagCandidate candidate = new AbilityTagCandidate();
            candidate.setCandidateName(claim.getAbilityName());
            candidate.setTagCategory("TECHNICAL");
            candidate.setSourceType(claim.getSourceType());
            candidate.setSourceRefId(claim.getSourceRefId());
            candidate.setSourcePostId(claim.getPostId());
            candidate.setEvidenceText(claim.getEvidenceText());
            candidate.setSimilarTagId(claim.getSimilarTagId());
            candidate.setStatus("PENDING");
            StringBuilder reasoning = new StringBuilder();
            reasoning.append("Governed admission REVIEW");
            if (decision.getSupportScore() != null) {
                reasoning.append("，支持分数: ").append(decision.getSupportScore());
            }
            if (decision.getReasons() != null && !decision.getReasons().isEmpty()) {
                reasoning.append("，原因: ").append(String.join("; ", decision.getReasons()));
            }
            candidate.setReasoning(reasoning.toString());
            abilityTagCandidateService.addCandidate(candidate);
        } catch (Exception e) {
            log.error("写入岗位能力候选失败", e);
        }
    }

    private void requirePostSourceEvidence(PostAbilityClaim claim) {
        if (claim == null || !claim.isValid()) {
            throw new IllegalArgumentException(
                    "Post ability requires non-empty source evidence and source references");
        }
    }

    public void resolveFormalTag(com.example.matching.entity.ability.PersonAbilityClaim claim) {
        // Personnel claims are deliberately independent from the role taxonomy.
        // A missing tag is a valid formal personnel ability, not an admission failure.
    }

    public Long findHarnessLogId(String checkCode) {
        if (checkCode == null || checkCode.isBlank()) {
            return null;
        }
        AiHarnessCheckLog log = harnessLogMapper.selectOne(new LambdaQueryWrapper<AiHarnessCheckLog>()
                .eq(AiHarnessCheckLog::getCheckCode, checkCode)
                .last("LIMIT 1"));
        return log == null ? null : log.getId();
    }

    public BigDecimal sourceWeight(BigDecimal confidence) {
        return confidence == null
                ? BigDecimal.valueOf(0.8)
                : confidence.divide(BigDecimal.valueOf(100), 4, RoundingMode.HALF_UP);
    }

    public String normalize(com.example.matching.agent.dto.person.PersonAbilityClaim source) {
        if (source.getNormalizedAbilityName() != null && !source.getNormalizedAbilityName().isBlank()) {
            return source.getNormalizedAbilityName().trim().toLowerCase();
        }
        return source.getAbilityName() == null ? "" : source.getAbilityName().trim().toLowerCase();
    }

    public String json(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception e) {
            return "{}";
        }
    }
}
