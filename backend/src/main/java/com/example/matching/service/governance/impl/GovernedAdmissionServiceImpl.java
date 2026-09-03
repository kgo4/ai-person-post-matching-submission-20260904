package com.example.matching.service.governance.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.example.matching.dto.governance.GovernanceAdmission;
import com.example.matching.dto.governance.GovernanceGrant;
import com.example.matching.dto.harness.AiHarnessClaimDTO;
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
import com.example.matching.service.governance.GovernedAdmissionService;
import com.example.matching.service.harness.AiTrustHarnessService;
import com.example.matching.service.system.AbilityTagCandidateService;
import com.example.matching.service.system.AbilityTagService;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Slf4j
@Service
public class GovernedAdmissionServiceImpl implements GovernedAdmissionService {

    static final String PENDING_HARNESS_REVIEW = "PENDING_HARNESS_REVIEW";
    static final String READY_FOR_FUSION = "READY_FOR_FUSION";
    static final String FUSED = "FUSED";
    static final String RETRYABLE = "RETRYABLE";
    static final String RETRY_EXHAUSTED = "RETRY_EXHAUSTED";

    /** 首次重试延迟 5 分钟，指数退避翻倍，上限 60 分钟 */
    static final Duration RETRY_INITIAL_DELAY = Duration.ofMinutes(5);
    static final Duration RETRY_MAX_DELAY = Duration.ofMinutes(60);
    static final int MAX_RETRIES = 10;

    private final AiTrustHarnessService harnessService;
    private final GovernanceAdmissionMapper admissionMapper;
    private final GovernedAdmissionEntityBuilder builder;
    private final ObjectMapper objectMapper;

    public GovernedAdmissionServiceImpl(
            AiTrustHarnessService harnessService,
            GovernanceAdmissionMapper admissionMapper,
            GovernedAdmissionEntityBuilder builder,
            ObjectMapper objectMapper) {
        this.harnessService = harnessService;
        this.admissionMapper = admissionMapper;
        this.builder = builder;
        this.objectMapper = objectMapper;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public GovernanceAdmission admitPersonAbility(
            com.example.matching.agent.dto.person.PersonAbilityClaim claim) {
        GovernanceAdmission admission = builder.initAdmission("PERSON_ABILITY", claim.getAbilityName());

        AiHarnessClaimDTO harnessClaim = builder.buildPersonHarnessClaim(claim);
        AiHarnessDecisionDTO decision;
        try {
            decision = harnessService.verify(harnessClaim);
        } catch (Exception e) {
            log.error("Harness verify failed for person ability: {}", claim.getAbilityName(), e);
            admission.setFinalDecision(GovernanceGrant.RETRY.name());
            admission.setDecisionRule("HARNESS_ERROR");
            admission.setReasonJson(builder.json(Map.of("error", e.getMessage())));
            admission.setClaimPayloadJson(builder.json(claim));
            admission.setRetryCount(0);
            admission.setNextRetryTime(LocalDateTime.now().plus(RETRY_INITIAL_DELAY));
            admission.setApplyStatus(RETRYABLE);
            builder.persistAdmission(admission);
            return admission;
        }

        GovernanceGrant grant = grantFrom(decision);
        builder.enrichAdmission(admission, decision, grant);
        admission.setHarnessCheckCode(decision.getCheckCode());

        Long harnessLogId = builder.findHarnessLogId(decision.getCheckCode());

        switch (grant) {
            case PASS -> {
                // Task9：PASS 写入前必须已有正式 abilityTagId；缺失时转入 REVIEW，绝不插入空 tagId
                if (claim.getAbilityTagId() == null) {
                    log.warn("治理准入 PASS 但缺少正式 abilityTagId，转入 REVIEW: abilityName={}",
                            claim.getAbilityName());
                    admission.setFinalDecision(GovernanceGrant.REVIEW.name());
                    admission.setDecisionRule("MISSING_FORMAL_TAG_ID");
                    admission.setReasonJson(builder.json(java.util.Map.of(
                            "error", "PASS requires formal abilityTagId")));
                    admission.setApplyStatus(PENDING_HARNESS_REVIEW);
                    builder.persistAdmission(admission);
                    return admission;
                }
                com.example.matching.entity.ability.PersonAbilityClaim entity =
                        builder.findOrCreatePersonClaimEntity(claim, decision, harnessLogId);
                builder.persistAdmission(admission);
                Long abilityId = builder.writeEmpAbility(entity, admission.getId());
                admission.setBusinessTargetId(abilityId);
                admission.setBusinessTargetType("EMP_ABILITY");
                admission.setApplyStatus(FUSED);
                builder.updateAdmissionTarget(admission);
            }
            case REVIEW -> {
                com.example.matching.entity.ability.PersonAbilityClaim entity =
                        builder.toPersonClaimEntity(claim, decision, harnessLogId);
                entity.setStatus(PENDING_HARNESS_REVIEW);
                builder.personClaimMapper.insert(entity);
                admission.setBusinessTargetId(entity.getId());
                admission.setBusinessTargetType("PERSON_ABILITY_CLAIM");
                admission.setApplyStatus(PENDING_HARNESS_REVIEW);
                builder.persistAdmission(admission);
            }
            case BLOCK -> {
                admission.setApplyStatus("BLOCKED");
                builder.persistAdmission(admission);
            }
            case RETRY -> {
                admission.setApplyStatus(RETRYABLE);
                admission.setClaimPayloadJson(builder.json(claim));
                admission.setRetryCount(0);
                admission.setNextRetryTime(LocalDateTime.now().plus(RETRY_INITIAL_DELAY));
                builder.persistAdmission(admission);
            }
        }

        log.info("Governed admission for person ability {}: {}",
                claim.getAbilityName(), admission.getFinalDecision());
        return admission;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public GovernanceAdmission deferPersonAbilityRetry(
            com.example.matching.agent.dto.person.PersonAbilityClaim claim, String reason) {
        GovernanceAdmission admission = builder.initAdmission("PERSON_ABILITY", claim.getAbilityName());
        admission.setFinalDecision(GovernanceGrant.RETRY.name());
        admission.setRiskLevel("HIGH");
        admission.setDecisionRule("APPLICATION_RETRY");
        admission.setReasonJson(builder.json(Map.of(
                "reason", reason != null && !reason.isBlank() ? reason : "Governance retry requested")));
        admission.setClaimPayloadJson(builder.json(claim));
        admission.setRetryCount(0);
        admission.setNextRetryTime(LocalDateTime.now().plus(RETRY_INITIAL_DELAY));
        admission.setApplyStatus(RETRYABLE);
        builder.persistAdmission(admission);
        return admission;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public GovernanceAdmission admitPostAbility(
            com.example.matching.agent.dto.post.PostAbilityClaim claim) {
        GovernanceAdmission admission = builder.initAdmission("POST_ABILITY", claim.getAbilityName());

        // A reasoning string is not evidence. Invalid claims are audit-blocked here
        // before Harness, candidate creation, or any formal post-model write.
        if (!hasRequiredPostSourceEvidence(claim)) {
            admission.setFinalDecision(GovernanceGrant.BLOCK.name());
            admission.setDecisionRule("MISSING_SOURCE_EVIDENCE");
            admission.setReasonJson(builder.json(Map.of(
                    "error", "Post ability requires non-empty source evidence and source references")));
            admission.setApplyStatus("BLOCKED");
            builder.persistAdmission(admission);
            log.warn("岗位能力缺少原文证据，已在 Harness 前拒绝: postId={}, ability={}",
                    claim.getPostId(), claim.getAbilityName());
            return admission;
        }

        AiHarnessClaimDTO harnessClaim = builder.buildPostHarnessClaim(claim);
        AiHarnessDecisionDTO decision;
        try {
            decision = harnessService.verify(harnessClaim);
        } catch (Exception e) {
            log.error("Harness verify failed for post ability: {}", claim.getAbilityName(), e);
            admission.setFinalDecision(GovernanceGrant.RETRY.name());
            admission.setDecisionRule("HARNESS_ERROR");
            admission.setClaimPayloadJson(builder.json(claim));
            admission.setRetryCount(0);
            admission.setNextRetryTime(LocalDateTime.now().plus(RETRY_INITIAL_DELAY));
            admission.setApplyStatus(RETRYABLE);
            builder.persistAdmission(admission);
            return admission;
        }

        GovernanceGrant grant = grantFrom(decision);
        builder.enrichAdmission(admission, decision, grant);
        admission.setHarnessCheckCode(decision.getCheckCode());

        switch (grant) {
            case PASS -> {
                // Task9：PASS 写入前必须已有正式 abilityTagId；缺失时转入 REVIEW，绝不插入空 tagId
                if (claim.getAbilityTagId() == null) {
                    log.warn("治理准入 PASS 但缺少正式 abilityTagId，转入 REVIEW: abilityName={}",
                            claim.getAbilityName());
                    admission.setFinalDecision(GovernanceGrant.REVIEW.name());
                    admission.setDecisionRule("MISSING_FORMAL_TAG_ID");
                    admission.setReasonJson(builder.json(java.util.Map.of(
                            "error", "PASS requires formal abilityTagId")));
                    admission.setApplyStatus(PENDING_HARNESS_REVIEW);
                    builder.persistAdmission(admission);
                    return admission;
                }
                builder.persistAdmission(admission);
                Long modelId = builder.upsertPostAbilityModel(claim, admission.getId());
                admission.setBusinessTargetId(modelId);
                admission.setBusinessTargetType("POST_ABILITY_MODEL");
                admission.setApplyStatus(FUSED);
                builder.updateAdmissionTarget(admission);
            }
            case REVIEW -> {
                admission.setApplyStatus(PENDING_HARNESS_REVIEW);
                admission.setBusinessTargetType("POST_ABILITY_CANDIDATE");
                builder.writePostAbilityCandidate(claim, decision);
                builder.persistAdmission(admission);
            }
            case BLOCK -> {
                admission.setApplyStatus("BLOCKED");
                builder.persistAdmission(admission);
            }
            case RETRY -> {
                admission.setApplyStatus(RETRYABLE);
                admission.setClaimPayloadJson(builder.json(claim));
                admission.setRetryCount(0);
                admission.setNextRetryTime(LocalDateTime.now().plus(RETRY_INITIAL_DELAY));
                builder.persistAdmission(admission);
            }
        }

        log.info("Governed admission for post ability {}: {}",
                claim.getAbilityName(), admission.getFinalDecision());
        return admission;
    }

    public static GovernanceGrant grantFrom(AiHarnessDecisionDTO decision) {
        if (decision.isPass()) return GovernanceGrant.PASS;
        if (decision.isReview()) return GovernanceGrant.REVIEW;
        if (decision.isRetry()) return GovernanceGrant.RETRY;
        return GovernanceGrant.BLOCK;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public GovernanceAdmission retryDueAdmission(Long recordId) {
        GovernanceAdmissionRecord record = admissionMapper.selectById(recordId);
        if (record == null) {
            return null;
        }
        if (!RETRYABLE.equals(record.getApplyStatus())) {
            return null;
        }
        LocalDateTime now = LocalDateTime.now();
        if (record.getNextRetryTime() != null && record.getNextRetryTime().isAfter(now)) {
            return null;
        }
        int retryCount = record.getRetryCount() != null ? record.getRetryCount() : 0;
        if (retryCount >= MAX_RETRIES) {
            record.setApplyStatus(RETRY_EXHAUSTED);
            admissionMapper.updateById(record);
            log.warn("治理准入重试次数耗尽，标记终止: record={}, scenario={}", record.getId(), record.getScenario());
            return null;
        }

        GovernanceAdmission admission = fromRecord(record);
        try {
            if ("PERSON_ABILITY".equals(record.getScenario())) {
                com.example.matching.agent.dto.person.PersonAbilityClaim claim =
                        objectMapper.readValue(record.getClaimPayloadJson(),
                                com.example.matching.agent.dto.person.PersonAbilityClaim.class);
                AiHarnessDecisionDTO decision = harnessService.verify(builder.buildPersonHarnessClaim(claim));
                applyRetryOutcome(admission, record, decision,
                        () -> {
                            Long harnessLogId = builder.findHarnessLogId(decision.getCheckCode());
                            com.example.matching.entity.ability.PersonAbilityClaim entity =
                                    builder.findOrCreatePersonClaimEntity(claim, decision, harnessLogId);
                            return builder.writeEmpAbility(entity, admission.getId());
                        },
                        () -> {
                            Long harnessLogId = builder.findHarnessLogId(decision.getCheckCode());
                            com.example.matching.entity.ability.PersonAbilityClaim entity =
                                    builder.toPersonClaimEntity(claim, decision, harnessLogId);
                            entity.setStatus(PENDING_HARNESS_REVIEW);
                            builder.personClaimMapper.insert(entity);
                            return entity.getId();
                        },
                        "EMP_ABILITY");
            } else if ("POST_ABILITY".equals(record.getScenario())) {
                com.example.matching.agent.dto.post.PostAbilityClaim claim =
                        objectMapper.readValue(record.getClaimPayloadJson(),
                                com.example.matching.agent.dto.post.PostAbilityClaim.class);
                if (!hasRequiredPostSourceEvidence(claim)) {
                    record.setFinalDecision(GovernanceGrant.BLOCK.name());
                    record.setDecisionRule("MISSING_SOURCE_EVIDENCE");
                    record.setApplyStatus("BLOCKED");
                    admissionMapper.updateById(record);
                    admission.setFinalDecision(GovernanceGrant.BLOCK.name());
                    admission.setDecisionRule("MISSING_SOURCE_EVIDENCE");
                    admission.setApplyStatus("BLOCKED");
                    return admission;
                }
                AiHarnessDecisionDTO decision = harnessService.verify(builder.buildPostHarnessClaim(claim));
                applyRetryOutcome(admission, record, decision,
                        () -> builder.upsertPostAbilityModel(claim, admission.getId()),
                        () -> {
                            builder.writePostAbilityCandidate(claim, decision);
                            return null;
                        },
                        "POST_ABILITY_MODEL");
            } else {
                record.setApplyStatus(RETRY_EXHAUSTED);
                admissionMapper.updateById(record);
                log.warn("治理准入重试跳过未知场景: record={}, scenario={}", record.getId(), record.getScenario());
                return null;
            }
        } catch (Exception e) {
            log.error("治理准入重试执行失败: record={}", record.getId(), e);
            retryCount = (record.getRetryCount() != null ? record.getRetryCount() : 0) + 1;
            record.setRetryCount(retryCount);
            record.setNextRetryTime(now.plus(nextBackoff(retryCount)));
            admissionMapper.updateById(record);
            return null;
        }
        return admission;
    }

    private boolean hasRequiredPostSourceEvidence(
            com.example.matching.agent.dto.post.PostAbilityClaim claim) {
        return claim != null && claim.isValid();
    }

    /**
     * 将重试决策应用到原准入记录（原地更新，不产生新记录）。
     * <p>PASS 融合前先把记录置为 PASS 并落库，满足 emp_ability/post_ability_model
     * 的数据库 trigger（governance_admission_id 必须引用 final_decision='PASS' 的记录）。
     */
    private void applyRetryOutcome(GovernanceAdmission admission,
                                   GovernanceAdmissionRecord record,
                                   AiHarnessDecisionDTO decision,
                                   java.util.function.Supplier<Long> fusionAction,
                                   java.util.function.Supplier<Long> reviewAction,
                                   String passTargetType) {
        GovernanceGrant grant = grantFrom(decision);
        record.setHarnessCheckCode(decision.getCheckCode());
        record.setLegacySupportScore(decision.getLegacySupportScore());
        record.setHarnessSupportScore(decision.getSupportScore());
        record.setFinalSupportScore(decision.getSupportScore());
        record.setLegacyDecision(decision.getLegacyDecision());
        record.setHarnessDecision(decision.getDecision());
        record.setFinalDecision(grant.name());
        record.setRiskLevel(decision.getRiskLevel());
        record.setIsSelfEvidence(decision.isSelfEvidence() ? 1 : 0);
        record.setReasonJson(builder.json(Map.of("reasons", decision.getReasons() != null ? decision.getReasons() : List.of())));
        record.setAcceptedSourceRefsJson(builder.json(decision.getAcceptedSourceRefs()));
        record.setInvalidSourceRefsJson(builder.json(decision.getInvalidSourceRefs()));
        record.setMissingEvidenceJson(builder.json(decision.getMissingEvidence()));
        record.setTraceId(decision.getTraceId());
        record.setDecisionRule(decision.getDecisionRule());

        switch (grant) {
            case PASS -> {
                record.setApplyStatus(FUSED);
                record.setBusinessTargetType(passTargetType);
                admissionMapper.updateById(record);
                Long targetId = fusionAction.get();
                record.setBusinessTargetId(targetId);
                admissionMapper.updateById(record);
            }
            case REVIEW -> {
                Long targetId = reviewAction.get();
                record.setApplyStatus(PENDING_HARNESS_REVIEW);
                record.setBusinessTargetType("POST_ABILITY".equals(record.getScenario())
                        ? "POST_ABILITY_CANDIDATE" : "PERSON_ABILITY_CLAIM");
                record.setBusinessTargetId(targetId);
                admissionMapper.updateById(record);
            }
            case BLOCK -> {
                record.setApplyStatus("BLOCKED");
                admissionMapper.updateById(record);
            }
            case RETRY -> {
                int retryCount = (record.getRetryCount() != null ? record.getRetryCount() : 0) + 1;
                record.setRetryCount(retryCount);
                record.setNextRetryTime(LocalDateTime.now().plus(nextBackoff(retryCount)));
                admissionMapper.updateById(record);
                log.info("治理准入重试仍为 RETRY: record={}, retryCount={}", record.getId(), retryCount);
            }
        }
        admission.setId(record.getId());
        admission.setFinalDecision(record.getFinalDecision());
        admission.setApplyStatus(record.getApplyStatus());
        admission.setBusinessTargetType(record.getBusinessTargetType());
        admission.setBusinessTargetId(record.getBusinessTargetId());
        admission.setRetryCount(record.getRetryCount());
        admission.setNextRetryTime(record.getNextRetryTime());
    }

    private Duration nextBackoff(int retryCount) {
        long minutes = RETRY_INITIAL_DELAY.toMinutes() * (1L << Math.min(retryCount, 4));
        return Duration.ofMinutes(Math.min(minutes, RETRY_MAX_DELAY.toMinutes()));
    }

    private GovernanceAdmission fromRecord(GovernanceAdmissionRecord record) {
        GovernanceAdmission admission = new GovernanceAdmission();
        admission.setId(record.getId());
        admission.setAdmissionCode(record.getAdmissionCode());
        admission.setScenario(record.getScenario());
        admission.setClaimText(record.getClaimText());
        admission.setApplyStatus(record.getApplyStatus());
        admission.setFinalDecision(record.getFinalDecision());
        admission.setRetryCount(record.getRetryCount());
        admission.setNextRetryTime(record.getNextRetryTime());
        return admission;
    }

    // ===== private helpers =====


}
