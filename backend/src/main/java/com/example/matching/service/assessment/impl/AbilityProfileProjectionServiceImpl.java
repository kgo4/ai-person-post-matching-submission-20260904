package com.example.matching.service.assessment.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.example.matching.common.enums.DecisionStatusEnum;
import com.example.matching.common.enums.EvidenceStatusEnum;
import com.example.matching.common.enums.TagResolutionStatusEnum;
import com.example.matching.entity.ability.PersonAbilityClaim;
import com.example.matching.entity.ability.PersonAbilityProfile;
import com.example.matching.entity.employee.EmpAbility;
import com.example.matching.entity.governance.GovernanceAdmissionRecord;
import com.example.matching.entity.workflow.PersonAbilityClaimGroup;
import com.example.matching.entity.workflow.PersonAbilityLevelDecision;
import com.example.matching.mapper.ability.PersonAbilityClaimMapper;
import com.example.matching.mapper.ability.PersonAbilityProfileMapper;
import com.example.matching.mapper.employee.EmpAbilityMapper;
import com.example.matching.mapper.governance.GovernanceAdmissionMapper;
import com.example.matching.mapper.workflow.PersonAbilityClaimGroupMapper;
import com.example.matching.mapper.workflow.PersonAbilityLevelDecisionMapper;
import com.example.matching.service.assessment.AbilityEvidenceCollectionService;
import com.example.matching.service.assessment.AbilityProfileProjectionService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Locale;

/**
 * 能力画像投影服务实现
 * <p>
 * 将 AUTO_CONFIRMED / HUMAN_CONFIRMED 决策投影为 EmpAbility 与正式 PersonAbilityProfile。
 * 待确立能力仅提供视图，不写入正式表。
 *
 * @author system
 */
@Slf4j
@Service
public class AbilityProfileProjectionServiceImpl implements AbilityProfileProjectionService {

    private final PersonAbilityLevelDecisionMapper decisionMapper;
    private final PersonAbilityClaimGroupMapper claimGroupMapper;
    private final PersonAbilityClaimMapper claimMapper;
    private final PersonAbilityProfileMapper profileMapper;
    private final EmpAbilityMapper empAbilityMapper;
    private final GovernanceAdmissionMapper admissionMapper;
    private final AbilityEvidenceCollectionService evidenceCollectionService;
    public AbilityProfileProjectionServiceImpl(
            PersonAbilityLevelDecisionMapper decisionMapper,
            PersonAbilityClaimGroupMapper claimGroupMapper,
            PersonAbilityClaimMapper claimMapper,
            PersonAbilityProfileMapper profileMapper,
            EmpAbilityMapper empAbilityMapper,
            GovernanceAdmissionMapper admissionMapper,
            AbilityEvidenceCollectionService evidenceCollectionService) {
        this.decisionMapper = decisionMapper;
        this.claimGroupMapper = claimGroupMapper;
        this.claimMapper = claimMapper;
        this.profileMapper = profileMapper;
        this.empAbilityMapper = empAbilityMapper;
        this.admissionMapper = admissionMapper;
        this.evidenceCollectionService = evidenceCollectionService;
    }

    @Override
    @Transactional
    public int projectConfirmed(Long workflowId, Long operatorId) {
        List<PersonAbilityLevelDecision> decisions = listProjectableDecisions(workflowId);
        int projected = 0;
        for (PersonAbilityLevelDecision decision : decisions) {
            PersonAbilityClaimGroup group = claimGroupMapper.selectById(decision.getClaimGroupId());
            if (group == null) {
                log.warn("投影跳过：聚合组不存在 claimGroupId={}", decision.getClaimGroupId());
                continue;
            }
            // 创建 PASS 准入记录（满足 emp_ability 写守卫 + 审计）
            GovernanceAdmissionRecord admission = createPassAdmission(workflowId, group, decision, operatorId);
            // 写 EmpAbility（正式投影）
            upsertEmpAbility(group, decision, admission.getId(), operatorId);
            // upsert 正式画像
            upsertProfile(group, decision);
            projected++;
        }
        log.info("正式画像投影完成: workflowId={}, projected={}", workflowId, projected);
        return projected;
    }

    private GovernanceAdmissionRecord createPassAdmission(Long workflowId, PersonAbilityClaimGroup group,
                                                          PersonAbilityLevelDecision decision, Long operatorId) {
        String admissionCode = admissionCode(workflowId, group.getId());
        GovernanceAdmissionRecord existing = admissionMapper.selectOne(new LambdaQueryWrapper<GovernanceAdmissionRecord>()
                .eq(GovernanceAdmissionRecord::getAdmissionCode, admissionCode)
                .last("LIMIT 1"));
        if (existing != null) {
            return existing;
        }
        GovernanceAdmissionRecord admission = new GovernanceAdmissionRecord();
        admission.setAdmissionCode(admissionCode);
        admission.setScenario("PERSON_ABILITY_LEVEL_CONFIRMATION");
        admission.setClaimType("EMP_ABILITY");
        admission.setClaimText(group.getNormalizedAbilityName());
        admission.setSourceType("PROFILE_FUSED");
        admission.setSourceRefId(group.getId());
        admission.setMatchedTagId(group.getCanonicalTagId());
        admission.setFinalDecision("PASS");
        admission.setDecisionRule("LEVEL_CONFIRMATION_PROJECTION");
        admission.setRiskLevel("LOW");
        admission.setBusinessTargetType("EMP_ABILITY");
        admission.setApplyStatus("APPLIED");
        admission.setClaimPayloadJson("{\"workflowId\":" + workflowId
                + ",\"decisionId\":" + decision.getId() + ",\"finalLevel\":" + decision.getFinalLevel() + "}");
        admission.setCreatedTime(LocalDateTime.now());
        admissionMapper.insert(admission);
        return admission;
    }

    private String admissionCode(Long workflowId, Long claimGroupId) {
        return "GAD" + workflowId + "-" + claimGroupId;
    }

    private void upsertEmpAbility(PersonAbilityClaimGroup group, PersonAbilityLevelDecision decision,
                                  Long admissionId, Long operatorId) {
        // The workflow/assessment identifiers are provenance fields and may differ
        // across repeated evaluations. abilityName is the business identity;
        // tagId is optional enrichment and must never merge different abilities.
        String abilityName = group.getNormalizedAbilityName();
        LambdaQueryWrapper<EmpAbility> uniqueKey = new LambdaQueryWrapper<EmpAbility>()
                .eq(EmpAbility::getEmpId, group.getEmpId())
                .eq(EmpAbility::getEvaluationSource, "PROFILE_FUSED")
                .eq(EmpAbility::getAbilityName, abilityName);
        EmpAbility existing = empAbilityMapper.selectOne(uniqueKey.last("LIMIT 1"));
        if (existing == null) {
            existing = findEmpAbilityByIdentity(empAbilityMapper.selectList(new LambdaQueryWrapper<EmpAbility>()
                    .eq(EmpAbility::getEmpId, group.getEmpId())
                    .eq(EmpAbility::getEvaluationSource, "PROFILE_FUSED")), abilityName);
        }
        if (existing == null) {
            existing = empAbilityMapper.selectOne(new LambdaQueryWrapper<EmpAbility>()
                    .eq(EmpAbility::getEmpId, group.getEmpId())
                    .eq(EmpAbility::getWorkflowId, group.getWorkflowId())
                    .eq(EmpAbility::getAssessmentAbilityId, group.getAssessmentAbilityId())
                    .last("LIMIT 1"));
        }
        EmpAbility ability = existing != null ? existing : new EmpAbility();
        ability.setEmpId(group.getEmpId());
        ability.setTagId(group.getCanonicalTagId());
        ability.setAbilityName(group.getNormalizedAbilityName());
        ability.setWorkflowId(group.getWorkflowId());
        ability.setAssessmentAbilityId(group.getAssessmentAbilityId());
        ability.setEvidenceSummaryRef("workflow:" + group.getWorkflowId() + ":ability:" + group.getAssessmentAbilityId());
        ability.setHarnessDecisionId(decision.getId());
        ability.setMasteryLevel(decision.getFinalLevel());
        ability.setAbilityLevel(decision.getFinalLevel());
        ability.setEvaluationSource("PROFILE_FUSED");
        ability.setSourceWeight(BigDecimal.ONE);
        ability.setEvaluationDate(java.time.LocalDate.now());
        // workflowId remains in governance/evidence audit records. The employee-facing
        // remark must describe the business result, not expose an internal implementation id.
        ability.setRemark("能力评估最终审核已通过");
        ability.setGovernanceAdmissionId(admissionId);
        ability.setUpdatedTime(LocalDateTime.now());
        if (existing != null) {
            empAbilityMapper.updateById(ability);
        } else {
            ability.setCreatedBy(operatorId);
            ability.setCreatedTime(LocalDateTime.now());
            ability.setVersion(0);
            empAbilityMapper.insert(ability);
        }
    }

    private void upsertProfile(PersonAbilityClaimGroup group, PersonAbilityLevelDecision decision) {
        // Repeated evaluations update the same employee ability by normalized name;
        // tagId remains optional enrichment rather than the identity.
        String abilityName = group.getNormalizedAbilityName();
        LambdaQueryWrapper<PersonAbilityProfile> uniqueKey = new LambdaQueryWrapper<PersonAbilityProfile>()
                .eq(PersonAbilityProfile::getEmpId, group.getEmpId())
                .eq(PersonAbilityProfile::getAbilityName, abilityName);
        PersonAbilityProfile existing = profileMapper.selectOne(uniqueKey.last("LIMIT 1"));
        if (existing == null) {
            existing = findProfileByIdentity(profileMapper.selectList(new LambdaQueryWrapper<PersonAbilityProfile>()
                    .eq(PersonAbilityProfile::getEmpId, group.getEmpId())), abilityName);
        }
        if (existing == null) {
            existing = profileMapper.selectOne(new LambdaQueryWrapper<PersonAbilityProfile>()
                    .eq(PersonAbilityProfile::getEmpId, group.getEmpId())
                    .eq(PersonAbilityProfile::getWorkflowId, group.getWorkflowId())
                    .eq(PersonAbilityProfile::getAssessmentAbilityId, group.getAssessmentAbilityId())
                    .last("LIMIT 1"));
        }
        PersonAbilityProfile profile = existing != null ? existing : new PersonAbilityProfile();
        profile.setEmpId(group.getEmpId());
        profile.setTagId(group.getCanonicalTagId());
        profile.setAbilityName(group.getNormalizedAbilityName());
        profile.setWorkflowId(group.getWorkflowId());
        profile.setAssessmentAbilityId(group.getAssessmentAbilityId());
        profile.setFinalLevel(decision.getFinalLevel());
        profile.setConfidenceScore(decision.getFinalConfidence() != null
                ? BigDecimal.valueOf(decision.getFinalConfidence()) : BigDecimal.valueOf(60));
        profile.setSourceBreakdownJson(decision.getSourceBreakdownJson());
        profile.setEvidenceCount(countClaims(group.getId()));
        profile.setLastEvidenceTime(LocalDateTime.now());
        profile.setReviewStatus("AUTO");
        profile.setReviewState("AUTO".equals(decision.getReviewState()) ? "AUTO" : "APPROVED");
        profile.setUpdatedTime(LocalDateTime.now());
        if (existing != null) {
            profileMapper.updateById(profile);
        } else {
            profile.setCreatedTime(LocalDateTime.now());
            profile.setVersion(0);
            profileMapper.insert(profile);
        }
    }

    private String normalizeAbilityName(String value) {
        if (value == null) return "";
        return value.replaceAll("\\s+", "").trim().toLowerCase(Locale.ROOT);
    }

    private EmpAbility findEmpAbilityByIdentity(List<EmpAbility> candidates, String abilityName) {
        if (candidates == null || candidates.isEmpty()) return null;
        String normalizedName = normalizeAbilityName(abilityName);
        return candidates.stream()
                .filter(item -> normalizedName.equals(normalizeAbilityName(item.getAbilityName())))
                .findFirst()
                .orElse(null);
    }

    private PersonAbilityProfile findProfileByIdentity(List<PersonAbilityProfile> candidates, String abilityName) {
        if (candidates == null || candidates.isEmpty()) return null;
        String normalizedName = normalizeAbilityName(abilityName);
        return candidates.stream()
                .filter(item -> normalizedName.equals(normalizeAbilityName(item.getAbilityName())))
                .findFirst()
                .orElse(null);
    }

    private int countClaims(Long claimGroupId) {
        Long count = claimMapper.selectCount(new LambdaQueryWrapper<PersonAbilityClaim>()
                .eq(PersonAbilityClaim::getClaimGroupId, claimGroupId));
        return count == null ? 0 : count.intValue();
    }

    @Override
    public List<Map<String, Object>> getProvisionalView(Long empId) {
        List<Map<String, Object>> view = new ArrayList<>();
        List<PersonAbilityClaimGroup> groups = listProvisionalGroups(empId);
        for (PersonAbilityClaimGroup group : groups) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("claimGroupId", group.getId());
            item.put("abilityName", group.getNormalizedAbilityName());
            item.put("tagResolutionStatus", group.getTagResolutionStatus());
            item.put("evidenceStatus", group.getStatus());
            List<PersonAbilityClaim> claims = evidenceCollectionService.listClaimsByGroup(group.getId());
            item.put("evidenceCount", claims.size());
            item.put("sourceTypes", claims.stream().map(PersonAbilityClaim::getSourceType).distinct().toList());
            item.put("claimedLevel", claims.stream()
                    .map(PersonAbilityClaim::getClaimedLevel)
                    .filter(l -> l != null)
                    .max(Integer::compareTo)
                    .orElse(null));
            item.put("finalLevel", null); // 未确立
            item.put("harnessStatus", group.getStatus());
            item.put("riskLabel", riskLabel(group));
            item.put("evidenceSummary", claims.stream()
                    .map(c -> "[" + c.getSourceType() + "] " + c.getEvidenceText())
                    .toList());
            item.put("nextAction", nextAction(group));
            view.add(item);
        }
        return view;
    }

    private String riskLabel(PersonAbilityClaimGroup group) {
        String status = group.getStatus();
        if (EvidenceStatusEnum.PENDING_MANUAL_REVIEW.getCode().equals(status)) {
            return "Harness 待审核";
        }
        if (TagResolutionStatusEnum.TAG_CANDIDATE_PENDING.getCode().equals(group.getTagResolutionStatus())) {
            return "标签待治理";
        }
        List<PersonAbilityClaim> claims = evidenceCollectionService.listClaimsByGroup(group.getId());
        long sources = claims.stream().map(PersonAbilityClaim::getSourceType).distinct().count();
        if (sources <= 1) {
            return "单来源";
        }
        return "等待测试/面试";
    }

    private String nextAction(PersonAbilityClaimGroup group) {
        String status = group.getStatus();
        if (EvidenceStatusEnum.PENDING_MANUAL_REVIEW.getCode().equals(status)) {
            return "人工复核";
        }
        if (TagResolutionStatusEnum.TAG_CANDIDATE_PENDING.getCode().equals(group.getTagResolutionStatus())) {
            return "标签治理";
        }
        if (EvidenceStatusEnum.COLLECTED.getCode().equals(status)) {
            return "继续后续评估阶段";
        }
        return "等待聚合审核";
    }

    @Override
    public List<PersonAbilityProfile> getConfirmedProfile(Long empId) {
        return profileMapper.selectList(new LambdaQueryWrapper<PersonAbilityProfile>()
                .eq(PersonAbilityProfile::getEmpId, empId)
                .orderByDesc(PersonAbilityProfile::getFinalLevel));
    }

    @Override
    public List<PersonAbilityClaimGroup> listProvisionalGroups(Long empId) {
        return claimGroupMapper.selectList(new LambdaQueryWrapper<PersonAbilityClaimGroup>()
                .eq(PersonAbilityClaimGroup::getEmpId, empId)
                .in(PersonAbilityClaimGroup::getStatus,
                        EvidenceStatusEnum.COLLECTED.getCode(),
                        EvidenceStatusEnum.READY_FOR_AGGREGATE_HARNESS.getCode(),
                        EvidenceStatusEnum.PENDING_MANUAL_REVIEW.getCode())
                .orderByDesc(PersonAbilityClaimGroup::getId));
    }

    @Override
    public List<PersonAbilityLevelDecision> listProjectableDecisions(Long workflowId) {
        return decisionMapper.selectList(new LambdaQueryWrapper<PersonAbilityLevelDecision>()
                .eq(PersonAbilityLevelDecision::getWorkflowId, workflowId)
                .in(PersonAbilityLevelDecision::getDecisionStatus,
                        DecisionStatusEnum.AUTO_CONFIRMED.getCode(),
                        DecisionStatusEnum.HUMAN_CONFIRMED.getCode()));
    }
}
