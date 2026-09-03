package com.example.matching.service.assessment.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.example.matching.common.enums.DecisionStatusEnum;
import com.example.matching.common.enums.EvidenceStatusEnum;
import com.example.matching.entity.ability.PersonAbilityClaim;
import com.example.matching.entity.workflow.PersonAbilityClaimGroup;
import com.example.matching.entity.workflow.PersonAbilityLevelDecision;
import com.example.matching.entity.workflow.PersonCapabilityStageRun;
import com.example.matching.mapper.workflow.PersonAbilityClaimGroupMapper;
import com.example.matching.mapper.workflow.PersonAbilityLevelDecisionMapper;
import com.example.matching.service.assessment.AbilityEvidenceCollectionService;
import com.example.matching.service.assessment.AbilityLevelConfirmationService;
import com.example.matching.service.assessment.AbilityLevelPolicyService;
import com.example.matching.service.system.SourceWeightResolver;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 最终能力等级确认中心服务实现
 * <p>
 * 读取 Harness PASS 的 Claim Group、来源权重、来源可信度、证据质量、置信度、
 * 时效性、来源独立性、等级一致性、冲突规则、人工确认状态，生成正式结论。
 * 仅 PASS 证据参与自动等级计算。
 *
 * @author system
 */
@Slf4j
@Service
public class AbilityLevelConfirmationServiceImpl implements AbilityLevelConfirmationService {

    private final PersonAbilityClaimGroupMapper claimGroupMapper;
    private final PersonAbilityLevelDecisionMapper decisionMapper;
    private final AbilityEvidenceCollectionService evidenceCollectionService;
    private final SourceWeightResolver sourceWeightResolver;
    private final AbilityLevelPolicyService policyService;
    private final com.example.matching.service.assessment.AbilityProfileProjectionService projectionService;
    private final com.example.matching.service.assessment.CapabilityAssessmentWorkflowService workflowService;
    private final com.example.matching.port.assessment.CapabilityStageLifecycleEventPublisher lifecycleEventPublisher;

    public AbilityLevelConfirmationServiceImpl(
            PersonAbilityClaimGroupMapper claimGroupMapper,
            PersonAbilityLevelDecisionMapper decisionMapper,
            AbilityEvidenceCollectionService evidenceCollectionService,
            SourceWeightResolver sourceWeightResolver,
            AbilityLevelPolicyService policyService,
            com.example.matching.service.assessment.AbilityProfileProjectionService projectionService,
            com.example.matching.service.assessment.CapabilityAssessmentWorkflowService workflowService,
            com.example.matching.port.assessment.CapabilityStageLifecycleEventPublisher lifecycleEventPublisher) {
        this.claimGroupMapper = claimGroupMapper;
        this.decisionMapper = decisionMapper;
        this.evidenceCollectionService = evidenceCollectionService;
        this.sourceWeightResolver = sourceWeightResolver;
        this.policyService = policyService;
        this.projectionService = projectionService;
        this.workflowService = workflowService;
        this.lifecycleEventPublisher = lifecycleEventPublisher;
    }

    @Override
    @Transactional
    public List<PersonAbilityLevelDecision> confirmLevels(Long workflowId, Long stageRunId) {
        // 仅 Harness PASS 的 Claim Group（状态保持 READY_FOR_AGGREGATE_HARNESS）
        List<PersonAbilityClaimGroup> groups = claimGroupMapper.selectList(
                new LambdaQueryWrapper<PersonAbilityClaimGroup>()
                        .eq(PersonAbilityClaimGroup::getWorkflowId, workflowId)
                        .eq(PersonAbilityClaimGroup::getStatus, EvidenceStatusEnum.READY_FOR_AGGREGATE_HARNESS.getCode()));
        List<PersonAbilityLevelDecision> decisions = new ArrayList<>();
        for (PersonAbilityClaimGroup group : groups) {
            decisions.add(confirmGroup(group));
        }
        log.info("等级确认完成: workflowId={}, groups={}", workflowId, groups.size());
        return decisions;
    }

    /**
     * 对单个能力组进行等级确认。
     */
    private PersonAbilityLevelDecision confirmGroup(PersonAbilityClaimGroup group) {
        List<PersonAbilityClaim> claims = evidenceCollectionService.listClaimsByGroup(group.getId());
        if (claims.isEmpty()) {
            return null;
        }
        // 读取当前生效策略（配置化：单来源上限/阈值/系数，含版本快照）
        AbilityLevelPolicyService.LevelPolicy policy = policyService.getActivePolicy();
        // 按来源类型统计
        Map<String, List<PersonAbilityClaim>> bySource = claims.stream()
                .collect(Collectors.groupingBy(PersonAbilityClaim::getSourceType, LinkedHashMap::new, Collectors.toList()));

        // 1. 冲突检测：同能力最高/最低声明等级差 >= 阈值
        List<String> conflictSignals = detectConflicts(bySource, policy);
        // 2. 计算各来源有效权重
        Map<String, EffectiveWeight> weights = computeEffectiveWeights(bySource);
        // 3. 加权平均等级（各来源取最高声明等级 × 有效权重）
        BigDecimal weightedLevel = computeWeightedLevel(bySource, weights);
        int level = weightedLevel.setScale(0, RoundingMode.HALF_UP).intValue();
        // 4. 单来源等级上限约束
        List<String> ceilingBreaches = checkSingleSourceCeilings(bySource, policy);
        if (!ceilingBreaches.isEmpty()) {
            int maxCeiling = policy.getSingleSourceLevelCeiling().values().stream()
                    .max(Integer::compareTo).orElse(3);
            // 仅单一来源时使用该来源自身上限（如仅简历 -> 上限 2），多来源时使用全局最高上限
            if (bySource.size() == 1) {
                String onlySource = bySource.keySet().iterator().next();
                maxCeiling = policy.getSingleSourceLevelCeiling().getOrDefault(onlySource, maxCeiling);
            }
            level = Math.min(level, maxCeiling);
        }
        // 5. 独立性检查
        int independentSources = countIndependentSources(weights);
        // Harness 是唯一审核闸门；能进入本阶段的能力已经通过 Harness。
        // 这里仅计算最终等级并自动投影，不再生成任何人工审核状态。
        DecisionOutcome outcome = confirmedOutcome(conflictSignals, ceilingBreaches);

        // 保存决策记录（含策略快照）
        PersonAbilityLevelDecision decision = new PersonAbilityLevelDecision();
        decision.setWorkflowId(group.getWorkflowId());
        decision.setClaimGroupId(group.getId());
        decision.setEmpId(group.getEmpId());
        decision.setTagId(group.getCanonicalTagId());
        decision.setDecisionStatus(outcome.status);
        decision.setFinalLevel(outcome.status.equals(DecisionStatusEnum.REJECTED.getCode()) ? null : level);
        decision.setFinalConfidence(outcome.status.equals(DecisionStatusEnum.REJECTED.getCode()) ? null : computeConfidence(weights));
        decision.setReviewState("AUTO".equals(outcome.status) || "AUTO_CONFIRMED".equals(outcome.status) ? "AUTO" : "PENDING");
        decision.setPolicyVersion(policy.getPolicyVersion());
        decision.setPolicySnapshotJson(policy.toSnapshotJson());
        decision.setSourceBreakdownJson(buildSourceBreakdown(bySource));
        decision.setEffectiveWeightBreakdownJson(buildWeightBreakdown(weights));
        decision.setConflictSignalsJson(toJsonArray(conflictSignals));
        decision.setDecisionReasonCodesJson(toJsonArray(outcome.reasonCodes));
        decision.setCreatedTime(LocalDateTime.now());
        decision.setUpdatedTime(LocalDateTime.now());
        decision.setVersion(0);
        // upsert：同一 claim_group 已有决策时更新（策略重算/重复执行不撞 uk_decision_claim_group）
        PersonAbilityLevelDecision existing = decisionMapper.selectOne(
                new LambdaQueryWrapper<PersonAbilityLevelDecision>()
                        .eq(PersonAbilityLevelDecision::getClaimGroupId, group.getId())
                        .last("LIMIT 1"));
        if (existing != null) {
            decision.setId(existing.getId());
            decision.setCreatedTime(existing.getCreatedTime());
            decision.setVersion(existing.getVersion());
            decisionMapper.updateById(decision);
        } else {
            decisionMapper.insert(decision);
        }

        // 更新聚合组状态
        updateGroupStatusAfterDecision(group, outcome.status);
        return decision;
    }

    private List<String> detectConflicts(Map<String, List<PersonAbilityClaim>> bySource,
                                         AbilityLevelPolicyService.LevelPolicy policy) {
        List<String> signals = new ArrayList<>();
        int maxLevel = 0;
        int minLevel = 5;
        for (List<PersonAbilityClaim> claims : bySource.values()) {
            for (PersonAbilityClaim claim : claims) {
                if (claim.getClaimedLevel() != null) {
                    maxLevel = Math.max(maxLevel, claim.getClaimedLevel());
                    minLevel = Math.min(minLevel, claim.getClaimedLevel());
                }
            }
        }
        if (maxLevel - minLevel >= policy.getConflictThreshold()) {
            signals.add("等级冲突: 声明区间 L" + minLevel + "-L" + maxLevel);
        }
        // 同来源内冲突
        for (Map.Entry<String, List<PersonAbilityClaim>> entry : bySource.entrySet()) {
            Set<Integer> levels = entry.getValue().stream()
                    .map(PersonAbilityClaim::getClaimedLevel)
                    .filter(l -> l != null)
                    .collect(Collectors.toSet());
            if (levels.size() > 1 && entry.getValue().size() > 1) {
                signals.add("同源冲突: " + entry.getKey() + " 声明了多个等级");
            }
        }
        return signals;
    }

    private record EffectiveWeight(String sourceType, BigDecimal weight, double credibility, int claimCount) {
    }

    private Map<String, EffectiveWeight> computeEffectiveWeights(Map<String, List<PersonAbilityClaim>> bySource) {
        Map<String, EffectiveWeight> result = new LinkedHashMap<>();
        for (Map.Entry<String, List<PersonAbilityClaim>> entry : bySource.entrySet()) {
            String sourceType = entry.getKey();
            List<PersonAbilityClaim> claims = entry.getValue();
            // 基础权重 × 可信度
            BigDecimal base = sourceWeightResolver.resolveEffectiveWeight(sourceType);
            // 置信度因子（0.5-1.0）
            BigDecimal confidenceFactor = claims.stream()
                    .map(c -> c.getConfidenceScore() != null ? c.getConfidenceScore() : BigDecimal.valueOf(60))
                    .max(BigDecimal::compareTo)
                    .orElse(BigDecimal.valueOf(60))
                    .divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP)
                    .max(BigDecimal.valueOf(0.5));
            // 时效性因子（默认 1.0，PMS 等长期来源可衰减）
            BigDecimal freshnessFactor = BigDecimal.ONE;
            // 独立性因子（同来源多条 Claim 不叠加独立证据，取 1.0 权重）
            BigDecimal independenceFactor = BigDecimal.ONE;
            BigDecimal effective = base
                    .multiply(confidenceFactor)
                    .multiply(freshnessFactor)
                    .multiply(independenceFactor);
            result.put(sourceType, new EffectiveWeight(sourceType, effective,
                    sourceWeightResolver.resolveCredibility(sourceType), claims.size()));
        }
        return result;
    }

    private BigDecimal computeWeightedLevel(Map<String, List<PersonAbilityClaim>> bySource,
                                            Map<String, EffectiveWeight> weights) {
        BigDecimal totalWeight = BigDecimal.ZERO;
        BigDecimal weightedSum = BigDecimal.ZERO;
        for (Map.Entry<String, EffectiveWeight> entry : weights.entrySet()) {
            String sourceType = entry.getKey();
            EffectiveWeight w = entry.getValue();
            // 取该来源最高声明等级
            int maxLevel = bySource.getOrDefault(sourceType, List.of()).stream()
                    .map(PersonAbilityClaim::getClaimedLevel)
                    .filter(l -> l != null)
                    .max(Integer::compareTo)
                    .orElse(1);
            totalWeight = totalWeight.add(w.weight());
            weightedSum = weightedSum.add(w.weight().multiply(BigDecimal.valueOf(maxLevel)));
        }
        if (totalWeight.compareTo(BigDecimal.ZERO) == 0) {
            return BigDecimal.ZERO;
        }
        return weightedSum.divide(totalWeight, 4, RoundingMode.HALF_UP);
    }

    private int countIndependentSources(Map<String, EffectiveWeight> weights) {
        int count = 0;
        for (EffectiveWeight w : weights.values()) {
            if (w.weight().compareTo(BigDecimal.valueOf(0.01)) > 0) {
                count++;
            }
        }
        return count;
    }

    private int computeConfidence(Map<String, EffectiveWeight> weights) {
        BigDecimal totalWeight = weights.values().stream()
                .map(EffectiveWeight::weight)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        double confidence = totalWeight.divide(BigDecimal.valueOf(0.5), 2, RoundingMode.HALF_UP)
                .min(BigDecimal.ONE).doubleValue();
        return (int) Math.round(confidence * 100);
    }

    private List<String> checkSingleSourceCeilings(Map<String, List<PersonAbilityClaim>> bySource,
                                                   AbilityLevelPolicyService.LevelPolicy policy) {
        List<String> breaches = new ArrayList<>();
        for (Map.Entry<String, List<PersonAbilityClaim>> entry : bySource.entrySet()) {
            Integer ceiling = policy.getSingleSourceLevelCeiling().get(entry.getKey());
            if (ceiling == null) {
                continue;
            }
            int maxLevel = entry.getValue().stream()
                    .map(PersonAbilityClaim::getClaimedLevel)
                    .filter(l -> l != null)
                    .max(Integer::compareTo)
                    .orElse(1);
            if (maxLevel > ceiling) {
                breaches.add("单来源上限: " + entry.getKey() + " 声明 L" + maxLevel + " 超过上限 L" + ceiling);
            }
        }
        return breaches;
    }

    private record DecisionOutcome(String status, List<String> reasonCodes) {
    }

    private DecisionOutcome confirmedOutcome(List<String> conflictSignals, List<String> ceilingBreaches) {
        List<String> reasonCodes = new ArrayList<>();
        if (!conflictSignals.isEmpty()) {
            reasonCodes.add("CONFLICT_DETECTED");
        }
        if (!ceilingBreaches.isEmpty()) {
            reasonCodes.add("SINGLE_SOURCE_CEILING");
        }
        reasonCodes.add("HARNESS_APPROVED_FUSION");
        reasonCodes.add("HARNESS_APPROVED_FUSION");
        return new DecisionOutcome(DecisionStatusEnum.AUTO_CONFIRMED.getCode(), reasonCodes);
    }

    private void updateGroupStatusAfterDecision(PersonAbilityClaimGroup group, String decisionStatus) {
        String status = switch (decisionStatus) {
            case "AUTO_CONFIRMED", "HUMAN_CONFIRMED" -> EvidenceStatusEnum.CONFIRMED.getCode();
            case "REJECTED" -> EvidenceStatusEnum.BLOCKED.getCode();
            default -> EvidenceStatusEnum.PENDING_MANUAL_REVIEW.getCode();
        };
        group.setStatus(status);
        group.setUpdatedTime(LocalDateTime.now());
        claimGroupMapper.updateById(group);
    }

    @Override
    public void recalculateByPolicy(Long workflowId, String newPolicyVersion) {
        List<PersonAbilityLevelDecision> decisions = listDecisions(workflowId);
        for (PersonAbilityLevelDecision decision : decisions) {
            if (DecisionStatusEnum.HUMAN_CONFIRMED.getCode().equals(decision.getDecisionStatus())) {
                // 人工确认：不静默改写，仅提示可复核
                log.info("人工确认决策不静默改写: decisionId={}", decision.getId());
                continue;
            }
            // 重新执行确认逻辑（自动确认与待人工复核均重算建议）
            PersonAbilityClaimGroup group = claimGroupMapper.selectById(decision.getClaimGroupId());
            if (group == null) {
                continue;
            }
            PersonAbilityLevelDecision recalculated = confirmGroup(group);
            if (recalculated != null) {
                recalculated.setPolicyVersion(newPolicyVersion);
                decisionMapper.updateById(recalculated);
            }
        }
    }

    @Override
    @Transactional
    public PersonAbilityLevelDecision humanConfirm(Long decisionId, Integer finalLevel, Integer finalConfidence,
                                                   String reason, Long reviewerId) {
        PersonAbilityLevelDecision decision = decisionMapper.selectById(decisionId);
        if (decision == null) {
            throw new IllegalArgumentException("决策记录不存在: " + decisionId);
        }
        if (!DecisionStatusEnum.PENDING_MANUAL_REVIEW.getCode().equals(decision.getDecisionStatus())) {
            throw new IllegalStateException("仅待人工复核的决策可被确认，当前状态: " + decision.getDecisionStatus());
        }
        if (finalLevel == null || finalLevel < 1 || finalLevel > 5) {
            throw new IllegalArgumentException("最终等级必须为 1-5");
        }
        decision.setDecisionStatus(DecisionStatusEnum.HUMAN_CONFIRMED.getCode());
        decision.setFinalLevel(finalLevel);
        decision.setFinalConfidence(finalConfidence != null ? finalConfidence : 60);
        decision.setReviewState("APPROVED");
        decision.setReviewedBy(reviewerId);
        decision.setReviewedTime(LocalDateTime.now());
        if (reason != null) {
            decision.setDecisionReasonCodesJson(appendReason(decision.getDecisionReasonCodesJson(), "HUMAN_CONFIRMED: " + reason));
        }
        decision.setUpdatedTime(LocalDateTime.now());
        decisionMapper.updateById(decision);
        // 同步聚合组状态
        PersonAbilityClaimGroup group = claimGroupMapper.selectById(decision.getClaimGroupId());
        if (group != null) {
            group.setStatus(EvidenceStatusEnum.CONFIRMED.getCode());
            group.setUpdatedTime(LocalDateTime.now());
            claimGroupMapper.updateById(group);
        }
        // 投影已确认能力到正式画像，并推进工作流（无剩余待复核时完成）
        projectAndAdvance(decision.getWorkflowId(), reviewerId);
        return decision;
    }

    @Override
    @Transactional
    public PersonAbilityLevelDecision humanReject(Long decisionId, String reason, Long reviewerId) {
        PersonAbilityLevelDecision decision = decisionMapper.selectById(decisionId);
        if (decision == null) {
            throw new IllegalArgumentException("决策记录不存在: " + decisionId);
        }
        if (!DecisionStatusEnum.PENDING_MANUAL_REVIEW.getCode().equals(decision.getDecisionStatus())) {
            throw new IllegalStateException("仅待人工复核的决策可被拒绝，当前状态: " + decision.getDecisionStatus());
        }
        decision.setDecisionStatus(DecisionStatusEnum.REJECTED.getCode());
        decision.setReviewState("REJECTED");
        decision.setReviewedBy(reviewerId);
        decision.setReviewedTime(LocalDateTime.now());
        if (reason != null) {
            decision.setDecisionReasonCodesJson(appendReason(decision.getDecisionReasonCodesJson(), "HUMAN_REJECTED: " + reason));
        }
        decision.setUpdatedTime(LocalDateTime.now());
        decisionMapper.updateById(decision);
        PersonAbilityClaimGroup group = claimGroupMapper.selectById(decision.getClaimGroupId());
        if (group != null) {
            group.setStatus(EvidenceStatusEnum.BLOCKED.getCode());
            group.setUpdatedTime(LocalDateTime.now());
            claimGroupMapper.updateById(group);
        }
        // 推进工作流（无剩余待复核时完成）
        projectAndAdvance(decision.getWorkflowId(), reviewerId);
        return decision;
    }

    /**
     * 投影已确认决策并发布生命周期事件：全部决策终态（无 PENDING_MANUAL_REVIEW）时，
     * 协调器将工作流从 REVIEW_REQUIRED 推进到 COMPLETED。
     */
    private void projectAndAdvance(Long workflowId, Long reviewerId) {
        try {
            projectionService.projectConfirmed(workflowId, reviewerId);
        } catch (Exception e) {
            log.error("人工确认后投影失败（不影响决策记录）: workflowId={}, error={}", workflowId, e.getMessage(), e);
        }
        // 不再直接完成工作流：发布 USER_ACTION_COMPLETED 生命周期事件，
        // 协调器依据剩余待复核决策状态决定 COMPLETED 或保持 REVIEW_REQUIRED。
        // 必须携带 stageRunId：人工确认发生时 AGGREGATE_HARNESS 运行可能已 SUCCEEDED
        // （不再活跃），协调器按活跃状态解析会失败导致事件被丢弃、工作流卡 REVIEW_REQUIRED。
        try {
            PersonCapabilityStageRun levelRun = workflowService.getLatestStageRun(workflowId, "AGGREGATE_HARNESS");
            lifecycleEventPublisher.publish(com.example.matching.event.CapabilityStageLifecycleEvent.of(
                    workflowId, levelRun != null ? levelRun.getId() : null, "AGGREGATE_HARNESS",
                    "AGGREGATE_HARNESS", workflowId,
                    com.example.matching.common.enums.StageLifecycleEventType.USER_ACTION_COMPLETED,
                    null, null));
        } catch (Exception e) {
            log.warn("发布等级确认人工操作完成事件失败: workflowId={}, error={}", workflowId, e.getMessage());
        }
    }

    @Override
    public List<PersonAbilityLevelDecision> listDecisions(Long workflowId) {
        return decisionMapper.selectList(new LambdaQueryWrapper<PersonAbilityLevelDecision>()
                .eq(PersonAbilityLevelDecision::getWorkflowId, workflowId)
                .orderByAsc(PersonAbilityLevelDecision::getId));
    }

    private String appendReason(String json, String reason) {
        String cleaned = json == null ? "[]" : json.trim();
        if (cleaned.startsWith("[")) {
            cleaned = cleaned.substring(1, cleaned.length() - 1);
        }
        cleaned = cleaned.trim();
        if (cleaned.isEmpty()) {
            return "[\"" + reason.replace("\"", "\\\"") + "\"]";
        }
        return "[" + cleaned + ",\"" + reason.replace("\"", "\\\"") + "\"]";
    }

    private String buildSourceBreakdown(Map<String, List<PersonAbilityClaim>> bySource) {
        StringBuilder sb = new StringBuilder("{");
        boolean first = true;
        for (Map.Entry<String, List<PersonAbilityClaim>> entry : bySource.entrySet()) {
            if (!first) {
                sb.append(',');
            }
            first = false;
            sb.append('"').append(entry.getKey()).append("\":{\"count\":").append(entry.getValue().size())
                    .append(",\"levels\":[");
            List<Integer> levels = entry.getValue().stream()
                    .map(PersonAbilityClaim::getClaimedLevel)
                    .filter(l -> l != null)
                    .collect(Collectors.toList());
            for (int i = 0; i < levels.size(); i++) {
                if (i > 0) {
                    sb.append(',');
                }
                sb.append(levels.get(i));
            }
            sb.append("]}");
        }
        return sb.append('}').toString();
    }

    private String buildWeightBreakdown(Map<String, EffectiveWeight> weights) {
        StringBuilder sb = new StringBuilder("{");
        boolean first = true;
        for (Map.Entry<String, EffectiveWeight> entry : weights.entrySet()) {
            if (!first) {
                sb.append(',');
            }
            first = false;
            EffectiveWeight w = entry.getValue();
            sb.append('"').append(entry.getKey()).append("\":{\"effectiveWeight\":")
                    .append(w.weight().setScale(4, RoundingMode.HALF_UP))
                    .append(",\"credibility\":").append(w.credibility())
                    .append(",\"claimCount\":").append(w.claimCount()).append('}');
        }
        return sb.append('}').toString();
    }

    private String toJsonArray(List<String> items) {
        if (items == null || items.isEmpty()) {
            return "[]";
        }
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < items.size(); i++) {
            if (i > 0) {
                sb.append(',');
            }
            sb.append('"').append(items.get(i).replace("\"", "\\\"")).append('"');
        }
        return sb.append(']').toString();
    }

    private String toJsonMap(Map<String, Integer> map) {
        StringBuilder sb = new StringBuilder("{");
        boolean first = true;
        for (Map.Entry<String, Integer> entry : map.entrySet()) {
            if (!first) {
                sb.append(',');
            }
            first = false;
            sb.append('"').append(entry.getKey()).append("\":").append(entry.getValue());
        }
        return sb.append('}').toString();
    }
}
