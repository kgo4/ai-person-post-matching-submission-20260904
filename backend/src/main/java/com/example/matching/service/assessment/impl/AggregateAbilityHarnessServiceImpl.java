package com.example.matching.service.assessment.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.example.matching.common.enums.EvidenceStatusEnum;
import com.example.matching.common.enums.TagResolutionStatusEnum;
import com.example.matching.dto.assessment.HarnessBatchItemResultDTO;
import com.example.matching.dto.harness.AiHarnessClaimDTO;
import com.example.matching.dto.harness.AiHarnessDecisionDTO;
import com.example.matching.entity.ability.PersonAbilityClaim;
import com.example.matching.entity.harness.AiHarnessCheckLog;
import com.example.matching.entity.workflow.AbilityHarnessBatch;
import com.example.matching.entity.workflow.AbilityHarnessBatchItem;
import com.example.matching.entity.workflow.PersonAbilityClaimGroup;
import com.example.matching.mapper.harness.AiHarnessCheckLogMapper;
import com.example.matching.mapper.workflow.AbilityHarnessBatchItemMapper;
import com.example.matching.mapper.workflow.AbilityHarnessBatchMapper;
import com.example.matching.mapper.workflow.PersonAbilityClaimGroupMapper;
import com.example.matching.service.assessment.AggregateAbilityHarnessService;
import com.example.matching.service.assessment.AbilityEvidenceCollectionService;
import com.example.matching.service.harness.AiTrustHarnessService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 聚合 Harness 审核服务实现
 * <p>
 * 面试完成后按能力聚合证据，执行一次批量 Harness。
 * 8-15 项能力一批；业务上仍是一轮"聚合 Harness 阶段"。
 *
 * @author system
 */
@Slf4j
@Service
public class AggregateAbilityHarnessServiceImpl implements AggregateAbilityHarnessService {

    /** 每批最大能力数 */
    private static final int BATCH_SIZE = 12;

    private final PersonAbilityClaimGroupMapper claimGroupMapper;
    private final AbilityHarnessBatchMapper batchMapper;
    private final AbilityHarnessBatchItemMapper batchItemMapper;
    private final AiHarnessCheckLogMapper harnessLogMapper;
    private final AiTrustHarnessService harnessService;
    private final AbilityEvidenceCollectionService evidenceCollectionService;

    public AggregateAbilityHarnessServiceImpl(
            PersonAbilityClaimGroupMapper claimGroupMapper,
            AbilityHarnessBatchMapper batchMapper,
            AbilityHarnessBatchItemMapper batchItemMapper,
            AiHarnessCheckLogMapper harnessLogMapper,
            AiTrustHarnessService harnessService,
            AbilityEvidenceCollectionService evidenceCollectionService) {
        this.claimGroupMapper = claimGroupMapper;
        this.batchMapper = batchMapper;
        this.batchItemMapper = batchItemMapper;
        this.harnessLogMapper = harnessLogMapper;
        this.harnessService = harnessService;
        this.evidenceCollectionService = evidenceCollectionService;
    }

    @Override
    public List<HarnessBatchItemResultDTO> runAggregateHarness(Long workflowId, Long stageRunId) {
        // 读取待审核聚合组
        List<PersonAbilityClaimGroup> groups = claimGroupMapper.selectList(
                new LambdaQueryWrapper<PersonAbilityClaimGroup>()
                        .eq(PersonAbilityClaimGroup::getWorkflowId, workflowId)
                        .ne(PersonAbilityClaimGroup::getStatus, EvidenceStatusEnum.BLOCKED.getCode())
                        .orderByAsc(PersonAbilityClaimGroup::getId));
        if (groups.isEmpty()) {
            log.warn("聚合 Harness 无待审核能力组: workflowId={}", workflowId);
            return List.of();
        }

        List<HarnessBatchItemResultDTO> results = new ArrayList<>();
        // 按批次执行（业务上仍是一轮聚合阶段，仅因上下文限制拆分调用）
        List<List<PersonAbilityClaimGroup>> batches = partition(groups, BATCH_SIZE);
        for (List<PersonAbilityClaimGroup> batchGroups : batches) {
            results.addAll(executeBatch(workflowId, stageRunId, batchGroups));
        }
        log.info("聚合 Harness 完成: workflowId={}, groups={}, batches={}",
                workflowId, groups.size(), batches.size());
        return results;
    }

    /**
     * 执行一个批次。
     * <p>
     * 注意：本方法不在数据库事务中执行 Harness 校验（外部校验/规则引擎调用
     * 不得占用长事务）；批次记录与结果保存使用各自短事务。
     */
    private List<HarnessBatchItemResultDTO> executeBatch(Long workflowId, Long stageRunId,
                                                         List<PersonAbilityClaimGroup> groups) {
        // 构建输入证据包（claimGroupId -> 证据）
        Map<Long, List<PersonAbilityClaim>> evidenceByGroup = new HashMap<>();
        Set<Long> inputGroupIds = new HashSet<>();
        for (PersonAbilityClaimGroup group : groups) {
            inputGroupIds.add(group.getId());
            List<PersonAbilityClaim> claims = evidenceCollectionService.listClaimsByGroup(group.getId());
            evidenceByGroup.put(group.getId(), claims);
        }

        // 保存批次（先占位，模型快照/请求快照在结果后补充）
        AbilityHarnessBatch batch = new AbilityHarnessBatch();
        batch.setWorkflowId(workflowId);
        batch.setBatchType("AGGREGATE");
        batch.setStatus("RUNNING");
        batch.setStartedAt(LocalDateTime.now());
        batch.setCreatedTime(LocalDateTime.now());
        batch.setUpdatedTime(LocalDateTime.now());
        batch.setVersion(0);
        batchMapper.insert(batch);

        List<HarnessBatchItemResultDTO> results = new ArrayList<>();
        try {
            // 构建本批次全部主张，一次性批量校验（业务上仍是一轮聚合审核）
            List<AiHarnessClaimDTO> batchClaims = new ArrayList<>();
            for (PersonAbilityClaimGroup group : groups) {
                batchClaims.add(buildHarnessClaim(group, evidenceByGroup.get(group.getId())));
            }
            List<AiHarnessDecisionDTO> batchDecisions = harnessService.verifyBatch(batchClaims);
            if (batchDecisions.size() != batchClaims.size()) {
                throw new IllegalStateException("聚合 Harness 批量返回数量不一致: expected="
                        + batchClaims.size() + ", actual=" + batchDecisions.size());
            }
            Map<Long, AiHarnessDecisionDTO> decisionsByGroupId = new HashMap<>();
            for (AiHarnessDecisionDTO decision : batchDecisions) {
                Long claimGroupId = decision.getClaimGroupId();
                if (claimGroupId == null || !inputGroupIds.contains(claimGroupId)
                        || decisionsByGroupId.putIfAbsent(claimGroupId, decision) != null) {
                    throw new IllegalStateException("聚合 Harness 返回了无效或重复的 claimGroupId: " + claimGroupId);
                }
            }
            if (decisionsByGroupId.size() != inputGroupIds.size()) {
                throw new IllegalStateException("聚合 Harness 批量返回缺少 claimGroupId");
            }
            for (PersonAbilityClaimGroup group : groups) {
                AiHarnessDecisionDTO decision = decisionsByGroupId.get(group.getId());
                List<PersonAbilityClaim> claims = evidenceByGroup.get(group.getId());
                HarnessBatchItemResultDTO itemResult = toItemResult(group, claims, decision);
                normalizeAssessmentReview(itemResult, claims);
                // 结构化输出校验：claimGroupId 必须在输入集合内，证据引用必须来自输入包
                validateItemResult(itemResult, inputGroupIds, claims);
                results.add(itemResult);
                saveBatchItem(batch.getId(), group.getId(), itemResult);
                updateGroupStatus(group, itemResult.getDecision());
            }
            batch.setStatus("SUCCEEDED");
            batch.setResponseSnapshotJson(toSnapshotJson(results));
        } catch (Exception e) {
            batch.setStatus("FAILED");
            log.error("聚合 Harness 批次失败: batchId={}, error={}", batch.getId(), e.getMessage(), e);
            throw e;
        } finally {
            batch.setCompletedAt(LocalDateTime.now());
            batch.setUpdatedTime(LocalDateTime.now());
            batchMapper.updateById(batch);
        }
        return results;
    }

    /**
     * 构建单个能力的 Harness 主张。
     */
    private AiHarnessClaimDTO buildHarnessClaim(PersonAbilityClaimGroup group, List<PersonAbilityClaim> claims) {
        AiHarnessClaimDTO claim = new AiHarnessClaimDTO();
        claim.setClaimGroupId(group.getId());
        claim.setScenario("PERSON_ABILITY_AGGREGATE");
        claim.setClaimType("EMP_ABILITY");
        claim.setChangeType("ADD_ABILITY");
        claim.setClaimText(group.getNormalizedAbilityName());
        claim.setSourceType("AGGREGATE_HARNESS");
        claim.setSourceRefId(group.getId());
        claim.setMatchedTagId(group.getCanonicalTagId());
        // 证据文本：聚合所有来源的证据
        String evidenceText = claims.stream()
                .map(c -> "[" + c.getSourceType() + "|L" + c.getClaimedLevel() + "] " + c.getEvidenceText())
                .collect(Collectors.joining("\n"));
        claim.setEvidenceText(evidenceText);
        List<String> sourceRefs = new ArrayList<>();
        for (PersonAbilityClaim c : claims) {
            if (c.getSourceRefsJson() != null && !c.getSourceRefsJson().isBlank()) {
                sourceRefs.addAll(parseRefs(c.getSourceRefsJson()));
            }
        }
        claim.setSourceRefs(sourceRefs);
        return claim;
    }

    /**
     * 将批量校验决策转换为逐项结果。
     */
    private HarnessBatchItemResultDTO toItemResult(PersonAbilityClaimGroup group, List<PersonAbilityClaim> claims,
                                                   AiHarnessDecisionDTO decision) {
        HarnessBatchItemResultDTO result = new HarnessBatchItemResultDTO();
        result.setClaimGroupId(group.getId());
        // A capability can be new to the global taxonomy. Harness evaluates the
        // evidence first; taxonomy promotion is a later governance decision.
        result.setDecision(normalizeDecision(decision.getDecision()));
        result.setAbilitySupported(decision.isPass());
        result.setSupportedLevelCeiling(decision.isPass() ? maxClaimedLevel(claims) : null);
        result.setRiskLevel(decision.getRiskLevel());
        result.setReasonCodes(decision.getReasons());
        result.setEvidenceRefs(decision.getAcceptedSourceRefs().isEmpty()
                ? collectAllRefs(claims) : decision.getAcceptedSourceRefs());
        return result;
    }

    private List<String> collectAllRefs(List<PersonAbilityClaim> claims) {
        List<String> refs = new ArrayList<>();
        for (PersonAbilityClaim claim : claims) {
            if (claim.getSourceRefsJson() != null) {
                refs.addAll(parseRefs(claim.getSourceRefsJson()));
            }
        }
        return refs;
    }

    private String normalizeDecision(String raw) {
        if ("PASS".equals(raw) || "REVIEW".equals(raw) || "BLOCK".equals(raw)) {
            return raw;
        }
        return "REVIEW";
    }

    /** Harness PASS is allowed to advance only after both AI test and interview
     * have supplied valid, traceable evidence. Otherwise it remains REVIEW. */
    private void normalizeAssessmentReview(HarnessBatchItemResultDTO result, List<PersonAbilityClaim> claims) {
        if (!"PASS".equals(result.getDecision())) return;
        boolean hasTest = hasValidVerification(claims, "AI_TEST");
        boolean hasInterview = hasValidVerification(claims, "AI_INTERVIEW");
        if (hasTest && hasInterview) return;
        result.setDecision("REVIEW");
        result.setAbilitySupported(false);
        List<String> reasons = result.getReasonCodes() == null ? new ArrayList<>() : new ArrayList<>(result.getReasonCodes());
        if (!hasTest) reasons.add("REVIEW_MISSING_TEST_VERIFICATION");
        if (!hasInterview) reasons.add("REVIEW_MISSING_INTERVIEW_VERIFICATION");
        result.setReasonCodes(reasons.stream().distinct().toList());
        result.setSupportedLevelCeiling(null);
    }

    private boolean hasValidVerification(List<PersonAbilityClaim> claims, String sourceType) {
        return claims != null && claims.stream().anyMatch(claim ->
                sourceType.equals(claim.getSourceType()) && "ACTIVE".equals(claim.getStatus())
                        && claim.getClaimedLevel() != null && claim.getClaimedLevel() >= 1 && claim.getClaimedLevel() <= 5
                        && claim.getEvidenceText() != null && !claim.getEvidenceText().isBlank()
                        && claim.getSourceRefsJson() != null && !claim.getSourceRefsJson().isBlank()
                        && !EvidenceStatusEnum.BLOCKED.getCode().equals(claim.getEvidenceStatus())
                        && !EvidenceStatusEnum.UNCLASSIFIED_OBSERVATION.getCode().equals(claim.getEvidenceStatus()));
    }

    private int maxClaimedLevel(List<PersonAbilityClaim> claims) {
        return claims.stream()
                .map(PersonAbilityClaim::getClaimedLevel)
                .filter(l -> l != null)
                .max(Integer::compareTo)
                .orElse(1);
    }

    /**
     * 结构化输出校验：未知 claimGroupId 或未知证据引用时整个批次失败。
     */
    private void validateItemResult(HarnessBatchItemResultDTO item, Set<Long> inputGroupIds,
                                    List<PersonAbilityClaim> claims) {
        if (item.getClaimGroupId() == null || !inputGroupIds.contains(item.getClaimGroupId())) {
            throw new IllegalStateException("聚合 Harness 返回未知 claimGroupId: " + item.getClaimGroupId());
        }
        Set<String> knownRefs = new HashSet<>();
        for (PersonAbilityClaim claim : claims) {
            if (claim.getSourceRefsJson() != null) {
                knownRefs.addAll(parseRefs(claim.getSourceRefsJson()));
            }
        }
        if (item.getEvidenceRefs() != null) {
            for (String ref : item.getEvidenceRefs()) {
                if (!knownRefs.contains(ref)) {
                    throw new IllegalStateException(
                            "聚合 Harness 使用未在输入包中的证据引用: " + ref + " (claimGroupId=" + item.getClaimGroupId() + ")");
                }
            }
        }
    }

    private List<String> parseRefs(String refsJson) {
        List<String> refs = new ArrayList<>();
        String cleaned = refsJson.replaceAll("^\\[|\\]$", "");
        for (String ref : cleaned.split(",")) {
            String trimmed = ref.trim().replace("\"", "");
            if (!trimmed.isEmpty()) {
                refs.add(trimmed);
            }
        }
        return refs;
    }

    private void saveBatchItem(Long batchId, Long claimGroupId, HarnessBatchItemResultDTO result) {
        AbilityHarnessBatchItem item = new AbilityHarnessBatchItem();
        item.setBatchId(batchId);
        item.setClaimGroupId(claimGroupId);
        item.setDecision(result.getDecision());
        item.setAbilitySupported(result.getAbilitySupported() != null && result.getAbilitySupported() ? 1 : 0);
        item.setSupportedLevelCeiling(result.getSupportedLevelCeiling());
        item.setRiskLevel(result.getRiskLevel());
        item.setReasonCodesJson(toJsonArray(result.getReasonCodes()));
        item.setEvidenceRefsJson(toJsonArray(result.getEvidenceRefs()));
        // 关联既有 AiHarnessCheckLog（按 trace/checkCode 匹配，保证审计中心可用）
        Long harnessLogId = findHarnessLogId(claimGroupId);
        item.setHarnessLogId(harnessLogId);
        item.setCreatedTime(LocalDateTime.now());
        item.setUpdatedTime(LocalDateTime.now());
        item.setVersion(0);
        batchItemMapper.insert(item);
        // The aggregate Harness audit row is created by the shared Harness
        // service before the final personnel qualification is known. Keep its
        // review state aligned with the normalized batch result: only REVIEW
        // remains actionable; PASS is audit-only and never enters the queue.
        if (harnessLogId != null) {
            AiHarnessCheckLog log = harnessLogMapper.selectById(harnessLogId);
            if (log != null) {
                log.setReviewStatus("REVIEW".equals(result.getDecision()) ? "PENDING" :
                        "PASS".equals(result.getDecision()) ? "AUTO_PASSED" : "PENDING");
                log.setDecision(result.getDecision());
                harnessLogMapper.updateById(log);
            }
        }
    }

    private Long findHarnessLogId(Long claimGroupId) {
        // 最近一条同 claimGroup 的聚合 Harness 日志
        List<AiHarnessCheckLog> logs = harnessLogMapper.selectList(
                new LambdaQueryWrapper<AiHarnessCheckLog>()
                        .eq(AiHarnessCheckLog::getSourceRefId, claimGroupId)
                        .eq(AiHarnessCheckLog::getScenario, "PERSON_ABILITY_AGGREGATE")
                        .orderByDesc(AiHarnessCheckLog::getId)
                        .last("LIMIT 1"));
        return logs.isEmpty() ? null : logs.get(0).getId();
    }

    private void updateGroupStatus(PersonAbilityClaimGroup group, String decision) {
        String status = switch (decision) {
            case "PASS" -> EvidenceStatusEnum.READY_FOR_AGGREGATE_HARNESS.getCode(); // 等待等级确认
            case "REVIEW" -> EvidenceStatusEnum.PENDING_MANUAL_REVIEW.getCode();
            default -> EvidenceStatusEnum.BLOCKED.getCode();
        };
        group.setStatus(status);
        group.setUpdatedTime(LocalDateTime.now());
        claimGroupMapper.updateById(group);
    }

    private List<List<PersonAbilityClaimGroup>> partition(List<PersonAbilityClaimGroup> groups, int size) {
        List<List<PersonAbilityClaimGroup>> batches = new ArrayList<>();
        for (int i = 0; i < groups.size(); i += size) {
            batches.add(groups.subList(i, Math.min(i + size, groups.size())));
        }
        return batches;
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

    private String toSnapshotJson(List<HarnessBatchItemResultDTO> results) {
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < results.size(); i++) {
            if (i > 0) {
                sb.append(',');
            }
            HarnessBatchItemResultDTO r = results.get(i);
            sb.append("{\"claimGroupId\":").append(r.getClaimGroupId())
                    .append(",\"decision\":\"").append(r.getDecision())
                    .append("\",\"abilitySupported\":").append(r.getAbilitySupported())
                    .append(",\"supportedLevelCeiling\":").append(r.getSupportedLevelCeiling() == null ? "null" : r.getSupportedLevelCeiling())
                    .append(",\"riskLevel\":\"").append(r.getRiskLevel() == null ? "" : r.getRiskLevel())
                    .append("\",\"reasonCodes\":").append(toJsonArray(r.getReasonCodes()))
                    .append(",\"evidenceRefs\":").append(toJsonArray(r.getEvidenceRefs()))
                    .append('}');
        }
        return sb.append(']').toString();
    }

    @Override
    public List<HarnessBatchItemResultDTO> getHarnessResults(Long workflowId) {
        List<AbilityHarnessBatch> batches = batchMapper.selectList(
                new LambdaQueryWrapper<AbilityHarnessBatch>()
                        .eq(AbilityHarnessBatch::getWorkflowId, workflowId)
                        .orderByDesc(AbilityHarnessBatch::getId));
        List<HarnessBatchItemResultDTO> results = new ArrayList<>();
        for (AbilityHarnessBatch batch : batches) {
            List<AbilityHarnessBatchItem> items = batchItemMapper.selectList(
                    new LambdaQueryWrapper<AbilityHarnessBatchItem>()
                            .eq(AbilityHarnessBatchItem::getBatchId, batch.getId()));
            for (AbilityHarnessBatchItem item : items) {
                HarnessBatchItemResultDTO dto = new HarnessBatchItemResultDTO();
                dto.setClaimGroupId(item.getClaimGroupId());
                dto.setDecision(item.getDecision());
                dto.setAbilitySupported(item.getAbilitySupported() != null && item.getAbilitySupported() == 1);
                dto.setSupportedLevelCeiling(item.getSupportedLevelCeiling());
                dto.setRiskLevel(item.getRiskLevel());
                dto.setReasonCodes(parseRefs(item.getReasonCodesJson()));
                dto.setEvidenceRefs(parseRefs(item.getEvidenceRefsJson()));
                results.add(dto);
            }
        }
        return results;
    }
}
