package com.example.matching.service.matching.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.example.matching.common.constant.AiConstant;
import com.example.matching.common.enums.MatchingTaskStatus;
import com.example.matching.common.exception.BusinessException;
import com.example.matching.common.exception.ErrorCodeEnum;
import com.example.matching.dto.matching.CandidateScope;
import com.example.matching.dto.matching.MatchingAbilitySnapshot;
import com.example.matching.dto.matching.MatchingEmployeeProfile;
import com.example.matching.dto.matching.MatchingExecuteDTO;
import com.example.matching.dto.matching.MatchingExecuteDTO.HardCondition;
import com.example.matching.dto.matching.MatchingExecuteDTO.MatchingPair;
import com.example.matching.dto.matching.MatchingPostProfile;
import com.example.matching.dto.matching.MatchingRequirementSnapshot;
import com.example.matching.dto.matching.MatchOverride;
import com.example.matching.entity.matching.MatchingBlackWhiteList;
import com.example.matching.entity.matching.MatchingRecord;
import com.example.matching.entity.matching.MatchingTask;
import com.example.matching.event.KnowledgeGraphRebuildRequestedEvent;
import com.example.matching.mapper.matching.MatchingRecordMapper;
import com.example.matching.mapper.matching.MatchingTaskMapper;
import com.example.matching.service.matching.*;
import com.example.matching.service.matching.MatchingAlgorithmService.HardConditionResult;
import com.example.matching.service.post.PostAbilityModelService;
import com.example.matching.service.post.PostHardConditionRuleService;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.*;

@Slf4j
@Service
@RequiredArgsConstructor
public class MatchingExecuteServiceImpl implements MatchingExecuteService {

    private final MatchingAlgorithmService matchingAlgorithmService;
    private final EmployeeVectorRecallService employeeVectorRecallService;
    private final PostAbilityModelService postAbilityModelService;
    private final PostHardConditionRuleService postHardConditionRuleService;
    private final FeedbackCalibrationService feedbackCalibrationService;
    /** @deprecated 保持构造器二进制兼容；匹配执行不再调用 RAG 评分。 */
    @Deprecated
    private final RagScoreService ragScoreService;
    private final MatchingTrainingWeightProfileStore weightProfileStore;
    private final ObjectMapper objectMapper;
    private final MatchingDataQueryService dataQuery;
    private final MatchingScoreService matchingScoreService;
    private final MatchingAiAnalysisService matchingAiAnalysisService;
    private final MatchingEvidenceScoreCalculator evidenceScoreCalculator;
    private final MatchingRecordMapper matchingRecordMapper;
    private final MatchingTaskMapper matchingTaskMapper;
    private final MatchingRecordPersistenceService matchingRecordPersistenceService;
    private final MatchingAiScoringStateMachine aiScoringStateMachine;
    private final MatchEvaluator matchEvaluator;
    private final ApplicationEventPublisher eventPublisher;
    private final MatchExecutionScoringEngine scoringEngine;
    private final MatchingTaskService matchingTaskService;
    /** 候选池超过该规模时，全量匹配自动转交异步 MatchingTask，不阻塞 HTTP */
    private static final int ASYNC_TASK_THRESHOLD = 500;

    @Override
    public MatchingExecuteResult execute(MatchingExecuteDTO dto) {
        List<MatchingPair> pairs = dto.normalizedPairs();
        String mode = dto.normalizedMode();
        if (dto.getMode() != null || dto.getPairs() != null) {
            if (pairs.isEmpty()) {
                throw new BusinessException(ErrorCodeEnum.PARAM_ERROR);
            }
            List<MatchingRecord> records = executeByPairs(dto, pairs, mode);
            return MatchingExecuteResult.sync(records, CandidateScope.EXPLICIT_EMPLOYEES,
                    pairs.size(), 0, false);
        }

        Long postId = dto.getPostId();
        MatchingPostProfile post = dataQuery.findPostForMatching(postId);
        if (post == null) {
            throw new BusinessException(ErrorCodeEnum.POST_NOT_FOUND);
        }

        List<MatchingRequirementSnapshot> requirements = requirePostRequirements(postId);

        String postModelVersion = null;
        if (!requirements.isEmpty() && requirements.get(0).modelVersion() != null) {
            postModelVersion = requirements.get(0).modelVersion();
        }

        List<MatchingBlackWhiteList> bwList = dataQuery.listBlackWhiteListByPostId(postId);

        List<Long> empIds = dto.getEmpIds();
        boolean manualEmployeeScope = empIds != null && !empIds.isEmpty();
        Map<Long, BigDecimal> vectorScoreMap = employeeVectorRecallService.recallEmployeesForPost(post);

        long totalActiveCount = dataQuery.countAllActiveEmployees();
        CandidateScope scope = dto.getCandidateScope() != null ? dto.getCandidateScope() : CandidateScope.ALL_ACTIVE;

        List<MatchingEmployeeProfile> allEmployees;
        boolean truncated = false;
        if (manualEmployeeScope || scope == CandidateScope.EXPLICIT_EMPLOYEES) {
            // 显式员工列表：候选即指定员工
            scope = CandidateScope.EXPLICIT_EMPLOYEES;
            allEmployees = dataQuery.findEmployeesForMatching(empIds);
        } else if (scope == CandidateScope.VECTOR_RECALL) {
            // 明确的性能模式：候选只取向量召回（受 topK 限制，响应标记 truncated）
            if (vectorScoreMap != null && !vectorScoreMap.isEmpty()) {
                empIds = new ArrayList<>(vectorScoreMap.keySet());
                truncated = empIds.size() < totalActiveCount;
            } else {
                empIds = List.of();
            }
            allEmployees = dataQuery.findActiveEmployeesForMatching(empIds);
        } else {
            // ALL_ACTIVE（默认）：分页全量加载，不允许 LIMIT 硬截断；
            // Milvus 分数仅对召回员工有值，未召回员工按缺失语义处理（见 M6）
            allEmployees = dataQuery.findAllActiveEmployeesForMatching();
            empIds = mergeEmployeeIds(allEmployees, vectorScoreMap);
        }

        int candidateCount = allEmployees.size();
        log.info("匹配候选池构建完成: scope={}, candidateCount={}, totalActiveCount={}, truncated={}, postId={}",
                scope, candidateCount, totalActiveCount, truncated, postId);

        // 大规模全量任务进入异步 MatchingTask，不允许阻塞 HTTP；任务消费端执行时不再转交
        if (scope == CandidateScope.ALL_ACTIVE && !dto.isTaskExecution()
                && candidateCount > ASYNC_TASK_THRESHOLD) {
            String taskId = matchingTaskService.submitTask(dto);
            log.info("候选池超过阈值，转交异步匹配任务: postId={}, candidateCount={}, taskId={}",
                    postId, candidateCount, taskId);
            return MatchingExecuteResult.async(taskId, scope, candidateCount, totalActiveCount, truncated);
        }

        Map<Long, MatchingEmployeeProfile> empMap = new HashMap<>();
        for (MatchingEmployeeProfile employee : allEmployees) {
            empMap.put(employee.empId(), employee);
        }

        Map<Long, String> tagNameMap = buildTagNameMap(requirements);

        Map<Long, Map<String, Object>> resumeBasicInfoMap = dataQuery.batchLoadResumeBasicInfo(empIds);
        Map<Long, List<MatchingAbilitySnapshot>> abilitiesMap = dataQuery.batchLoadAbilitySnapshots(empIds);

        // 正式能力资格校验（方案要求）：无正式能力不允许参与匹配。
        // 显式指定员工（EXPLICIT_EMPLOYEES/manual）任一无正式能力 → 拒绝整个请求；
        // 全量/召回候选池 → 无正式能力员工剔除（不产生匹配记录），统计被排除数。
        boolean strictExplicit = scope == CandidateScope.EXPLICIT_EMPLOYEES;
        int excludedCount = 0;
        List<Long> ineligible = resolveFormalAbilityIneligible(empIds, abilitiesMap, strictExplicit);
        if (!ineligible.isEmpty()) {
            Set<Long> excludedIds = new HashSet<>(ineligible);
            empIds = empIds.stream().filter(id -> !excludedIds.contains(id)).toList();
            allEmployees = allEmployees.stream()
                    .filter(e -> !excludedIds.contains(e.empId())).toList();
            empMap = new HashMap<>();
            for (MatchingEmployeeProfile employee : allEmployees) {
                empMap.put(employee.empId(), employee);
            }
            candidateCount = allEmployees.size();
            excludedCount = ineligible.size();
            log.info("匹配候选池资格过滤完成: scope={}, 保留={}, 排除无正式能力={}, postId={}",
                    scope, candidateCount, excludedCount, postId);
        }

        BigDecimal modelQualityScore = postAbilityModelService.calculateQualityScore(postId);
        BigDecimal feedbackCalibration = feedbackCalibrationService.calculateCalibration(postId);

        // 批次号：异步任务消费端复用任务快照中的 batchNo（删除任务可连带删除记录）；同步执行时新生成
        String batchNo = (dto.getBatchNo() != null && !dto.getBatchNo().isBlank())
                ? dto.getBatchNo()
                : UUID.randomUUID().toString().replace("-", "").substring(0, 16);
        List<HardCondition> hardConditions = resolveHardConditions(dto);
        boolean enableAi = Boolean.TRUE.equals(dto.getEnableAiMatching());
        boolean forceAi = Boolean.TRUE.equals(dto.getForceAiMatching());
        int aiTopN = dto.getAiTopN() != null ? dto.getAiTopN() : 5;
        int aiThreshold = dto.getAiThreshold() != null ? dto.getAiThreshold() : weightProfileStore.currentProfile().getAiTriggerThreshold();

        List<MatchingRecord> allRecords = new ArrayList<>();

        for (Long empId : empIds) {
            if (isTaskAborted(dto)) {
                log.warn("异步任务已被取消/删除，中断匹配执行: taskId={}", dto.getTaskId());
                break;
            }
            MatchingEmployeeProfile employee = empMap.get(empId);
            if (employee == null) {
                continue;
            }

            List<MatchingAbilitySnapshot> abilities = abilitiesMap.getOrDefault(empId, List.of());
            allRecords.add(scoringEngine.buildScoredRecord(new MatchExecutionScoringEngine.MatchContext(
                    batchNo, employee, post, postModelVersion, modelQualityScore, feedbackCalibration,
                    hardConditions, resumeBasicInfoMap.get(empId), abilities, requirements, bwList,
                    vectorScoreMap, tagNameMap, true,
                    weightProfileStore.currentProfile())));
        }

        initializeAiScoringStatuses(allRecords, enableAi, forceAi, aiThreshold);
        saveRecords(allRecords);

        if (enableAi) {
            try {
                matchingAiAnalysisService.runAiScoring(allRecords, abilitiesMap, vectorScoreMap, modelQualityScore, feedbackCalibration,
                        empMap, post, requirements, tagNameMap, aiTopN, aiThreshold, forceAi,
                        matchingAlgorithmService, matchingScoreService, weightProfileStore);
            } catch (RuntimeException e) {
                markPendingAiScoringFailed(allRecords, e);
            }
        }

        rebuildGraph();

        return MatchingExecuteResult.sync(allRecords, scope, candidateCount, totalActiveCount, truncated,
                excludedCount);
    }

    private List<MatchingRecord> executeByPairs(MatchingExecuteDTO dto,
                                                List<MatchingPair> pairs,
                                                String mode) {
        validateMode(mode, pairs);

        Set<Long> empIds = new LinkedHashSet<>();
        Set<Long> postIds = new LinkedHashSet<>();
        for (MatchingPair pair : pairs) {
            if (pair.getEmpId() == null || pair.getPostId() == null) {
                throw new BusinessException(ErrorCodeEnum.PARAM_ERROR);
            }
            empIds.add(pair.getEmpId());
            postIds.add(pair.getPostId());
        }

        List<MatchingEmployeeProfile> employees = dataQuery.findActiveEmployeesForMatching(new ArrayList<>(empIds));
        Map<Long, MatchingEmployeeProfile> empMap = new HashMap<>();
        for (MatchingEmployeeProfile employee : employees) {
            empMap.put(employee.empId(), employee);
        }

        Map<Long, Map<String, Object>> resumeBasicInfoMap = dataQuery.batchLoadResumeBasicInfo(new ArrayList<>(empIds));
        Map<Long, List<MatchingAbilitySnapshot>> abilitiesMap = dataQuery.batchLoadAbilitySnapshots(new ArrayList<>(empIds));

        // 正式能力资格校验：pairs 为显式指定员工，任一员工无正式能力 → 拒绝整个请求（后端兜底）
        resolveFormalAbilityIneligible(empIds, abilitiesMap, true);

        // 批次号：异步任务消费端复用任务快照中的 batchNo；同步执行时新生成
        String batchNo = (dto.getBatchNo() != null && !dto.getBatchNo().isBlank())
                ? dto.getBatchNo()
                : UUID.randomUUID().toString().replace("-", "").substring(0, 16);
        boolean enableAi = Boolean.TRUE.equals(dto.getEnableAiMatching());
        boolean forceAi = Boolean.TRUE.equals(dto.getForceAiMatching());
        int aiTopN = dto.getAiTopN() != null ? dto.getAiTopN() : 5;
        int aiThreshold = dto.getAiThreshold() != null ? dto.getAiThreshold() : weightProfileStore.currentProfile().getAiTriggerThreshold();

        Map<Long, MatchingPostProfile> postMap = new HashMap<>();
        Map<Long, List<MatchingRequirementSnapshot>> requirementsByPost = new HashMap<>();
        Map<Long, List<MatchingBlackWhiteList>> bwListByPost = new HashMap<>();
        Map<Long, Map<Long, BigDecimal>> vectorScoreByPost = new HashMap<>();
        Map<Long, String> postModelVersionByPost = new HashMap<>();
        Map<Long, BigDecimal> qualityByPost = new HashMap<>();
        Map<Long, BigDecimal> feedbackByPost = new HashMap<>();
        Map<Long, Map<Long, String>> tagNameMapByPost = new HashMap<>();

        for (MatchingPostProfile post : dataQuery.findPostsForMatching(new ArrayList<>(postIds))) {
            postMap.put(post.postId(), post);
        }

        for (Long postId : postIds) {
            MatchingPostProfile post = postMap.get(postId);
            if (post == null) {
                throw new BusinessException(ErrorCodeEnum.POST_NOT_FOUND);
            }

            List<MatchingRequirementSnapshot> requirements = requirePostRequirements(postId);
            requirementsByPost.put(postId, requirements);

            String postModelVersion = null;
            if (!requirements.isEmpty() && requirements.get(0).modelVersion() != null) {
                postModelVersion = requirements.get(0).modelVersion();
            }
            postModelVersionByPost.put(postId, postModelVersion);

            bwListByPost.put(postId, dataQuery.listBlackWhiteListByPostId(postId));

            vectorScoreByPost.put(postId, employeeVectorRecallService.recallEmployeesForPost(post));
            qualityByPost.put(postId, postAbilityModelService.calculateQualityScore(postId));
            feedbackByPost.put(postId, feedbackCalibrationService.calculateCalibration(postId));
            tagNameMapByPost.put(postId, buildTagNameMap(requirements));
        }

        List<MatchingRecord> allRecords = new ArrayList<>();
        Map<Long, List<MatchingPair>> pairsByPost = new LinkedHashMap<>();
        for (MatchingPair pair : pairs) {
            pairsByPost.computeIfAbsent(pair.getPostId(), key -> new ArrayList<>()).add(pair);
        }

        for (Map.Entry<Long, List<MatchingPair>> entry : pairsByPost.entrySet()) {
            Long postId = entry.getKey();
            if (isTaskAborted(dto)) {
                log.warn("异步任务已被取消/删除，中断匹配执行: taskId={}", dto.getTaskId());
                break;
            }
            MatchingPostProfile post = postMap.get(postId);
            List<MatchingRequirementSnapshot> requirements = requirementsByPost.getOrDefault(postId, List.of());
            List<MatchingBlackWhiteList> bwList = bwListByPost.getOrDefault(postId, List.of());
            Map<Long, BigDecimal> vectorScoreMap = vectorScoreByPost.getOrDefault(postId, Map.of());
            BigDecimal modelQualityScore = qualityByPost.getOrDefault(postId, BigDecimal.ZERO);
            BigDecimal feedbackCalibration = feedbackByPost.getOrDefault(postId, BigDecimal.ZERO);
            String postModelVersion = postModelVersionByPost.get(postId);
            Map<Long, String> tagNameMap = tagNameMapByPost.getOrDefault(postId, Map.of());
            List<HardCondition> hardConditions = resolveHardConditions(dto, postId);

            for (MatchingPair pair : entry.getValue()) {
                Long empId = pair.getEmpId();
                MatchingEmployeeProfile employee = empMap.get(empId);
                if (employee == null) {
                    throw new BusinessException(ErrorCodeEnum.EMPLOYEE_NOT_FOUND);
                }

                List<MatchingAbilitySnapshot> abilities = abilitiesMap.getOrDefault(empId, List.of());
                allRecords.add(scoringEngine.buildScoredRecord(new MatchExecutionScoringEngine.MatchContext(
                        batchNo, employee, post, postModelVersion, modelQualityScore, feedbackCalibration,
                        hardConditions, resumeBasicInfoMap.get(empId), abilities, requirements, bwList,
                        vectorScoreMap, tagNameMap, false,
                    weightProfileStore.currentProfile())));
            }
        }

        initializeAiScoringStatuses(allRecords, enableAi, forceAi, aiThreshold);
        saveRecords(allRecords);

        if (enableAi) {
            for (Map.Entry<Long, List<MatchingPair>> entry : pairsByPost.entrySet()) {
                Long postId = entry.getKey();
                if (isTaskAborted(dto)) {
                    log.warn("异步任务已被取消/删除，中断 AI 评分阶段: taskId={}", dto.getTaskId());
                    break;
                }
                MatchingPostProfile post = postMap.get(postId);
                List<MatchingRequirementSnapshot> requirements = requirementsByPost.getOrDefault(postId, List.of());
                Map<Long, String> tagNameMap = tagNameMapByPost.getOrDefault(postId, Map.of());
                Map<Long, BigDecimal> vectorScoreMap = vectorScoreByPost.getOrDefault(postId, Map.of());
                BigDecimal modelQualityScore = qualityByPost.getOrDefault(postId, BigDecimal.ZERO);
                BigDecimal feedbackCalibration = feedbackByPost.getOrDefault(postId, BigDecimal.ZERO);

                List<MatchingRecord> postRecords = allRecords.stream()
                        .filter(r -> postId.equals(r.getPostId()))
                        .toList();

                try {
                    matchingAiAnalysisService.runAiScoring(postRecords, abilitiesMap, vectorScoreMap, modelQualityScore, feedbackCalibration,
                            empMap, post, requirements, tagNameMap, aiTopN, aiThreshold, forceAi,
                            matchingAlgorithmService, matchingScoreService, weightProfileStore);
                } catch (RuntimeException e) {
                    markPendingAiScoringFailed(postRecords, e);
                }
            }
        }

        rebuildGraph();

        return allRecords;
    }
    private List<HardCondition> resolveHardConditions(MatchingExecuteDTO dto, Long postId) {
        if (dto.getHardConditions() != null && !dto.getHardConditions().isEmpty() && dto.normalizedPostIds().size() <= 1) {
            return dto.getHardConditions();
        }
        return postHardConditionRuleService.toHardConditions(postId);
    }

    private List<Long> mergeEmployeeIds(List<MatchingEmployeeProfile> employees, Map<Long, BigDecimal> vectorScoreMap) {
        LinkedHashSet<Long> ids = new LinkedHashSet<>();
        if (vectorScoreMap != null) {
            ids.addAll(vectorScoreMap.keySet());
        }
        if (employees != null) {
            for (MatchingEmployeeProfile employee : employees) {
                ids.add(employee.empId());
            }
        }
        return new ArrayList<>(ids);
    }

    /**
     * 从岗位要求快照构建标签名称映射（M-12：不再依赖 Entity 查询）
     */
    private static Map<Long, String> buildTagNameMap(List<MatchingRequirementSnapshot> requirements) {
        Map<Long, String> tagNameMap = new HashMap<>();
        if (requirements != null) {
            for (MatchingRequirementSnapshot req : requirements) {
                if (req.tagId() != null && req.abilityName() != null) {
                    tagNameMap.putIfAbsent(req.tagId(), req.abilityName());
                }
            }
        }
        return tagNameMap;
    }

    private List<HardCondition> resolveHardConditions(MatchingExecuteDTO dto) {
        if (dto.getHardConditions() != null && !dto.getHardConditions().isEmpty()) {
            return dto.getHardConditions();
        }
        return postHardConditionRuleService.toHardConditions(dto.getPostId());
    }

    private void validateMode(String mode, List<MatchingPair> pairs) {
        if (mode == null || mode.isBlank()) {
            throw new BusinessException(ErrorCodeEnum.PARAM_ERROR);
        }
        switch (mode) {
            case "SINGLE_EVAL" -> {
                if (pairs.size() != 1) throw new BusinessException(ErrorCodeEnum.PARAM_ERROR);
            }
            case "EMP_TO_POST" -> {
                Long empId = pairs.get(0).getEmpId();
                for (MatchingPair pair : pairs) {
                    if (!empId.equals(pair.getEmpId())) throw new BusinessException(ErrorCodeEnum.PARAM_ERROR);
                }
            }
            case "POST_TO_EMP" -> {
                Long postId = pairs.get(0).getPostId();
                for (MatchingPair pair : pairs) {
                    if (!postId.equals(pair.getPostId())) throw new BusinessException(ErrorCodeEnum.PARAM_ERROR);
                }
            }
            default -> throw new BusinessException(ErrorCodeEnum.PARAM_ERROR);
        }
    }

    private void saveRecords(List<MatchingRecord> records) {
        matchingRecordPersistenceService.saveAll(records);
    }

    private List<MatchingRequirementSnapshot> requirePostRequirements(Long postId) {
        List<MatchingRequirementSnapshot> requirements = dataQuery.findPostRequirements(postId);
        if (requirements == null || requirements.isEmpty()) {
            throw BusinessException.of(
                            ErrorCodeEnum.POST_MODEL_INCOMPLETE,
                            "岗位 " + postId + " 的能力模型不完整，无法执行正式匹配")
                    .entity("POST", postId)
                    .operation("executeMatching")
                    .build();
        }
        return requirements;
    }

    // ==================== 正式能力资格校验（无正式能力不允许参与匹配） ====================

    /**
     * 匹配资格校验：只有 emp_ability 中的正式能力可参与匹配；待审核能力只用于画像展示和审计。
     */
    private List<Long> resolveFormalAbilityIneligible(Collection<Long> empIds,
                                                      Map<Long, List<MatchingAbilitySnapshot>> abilitiesMap,
                                                      boolean strictExplicit) {
        List<Long> ineligible = new ArrayList<>();
        for (Long empId : empIds) {
            if (empId == null) {
                continue;
            }
            List<MatchingAbilitySnapshot> abilities =
                    abilitiesMap == null ? null : abilitiesMap.get(empId);
            if (abilities == null || abilities.isEmpty()) {
                ineligible.add(empId);
            }
        }
        if (ineligible.isEmpty()) {
            return List.of();
        }
        if (strictExplicit) {
            throw new BusinessException(400, "员工 " + ineligible + " 无正式能力，不允许参与匹配");
        }
        log.info("匹配资格过滤：{} 名员工因无正式能力被排除（不参与匹配）: {}",
                ineligible.size(), ineligible);
        return ineligible;
    }

    private boolean isTaskAborted(MatchingExecuteDTO dto) {
        if (!dto.isTaskExecution() || dto.getTaskId() == null || dto.getTaskId().isBlank()) {
            return false;
        }
        MatchingTask task = matchingTaskMapper.selectOne(
                Wrappers.<MatchingTask>lambdaQuery().eq(MatchingTask::getTaskId, dto.getTaskId()));
        return task == null || task.getStatus() == MatchingTaskStatus.CANCELLED.getCode();
    }

    /**
     * 将待确立能力并入本次请求的内存能力快照（仅软评分）。
     * 服务端重新读取 Claim 状态并校验归属，不信任前端提交的等级与分数。
     * 临时能力 sourceType=PROVISIONAL、权重折减，不写任何正式表。
     *
     * @return empId -> 临时能力数量
     */

    private void markPendingAiScoringFailed(List<MatchingRecord> records, RuntimeException exception) {
        String reason = exception.getMessage() == null ? exception.getClass().getSimpleName() : exception.getMessage();
        if (reason.length() > 500) {
            reason = reason.substring(0, 500);
        }
        log.warn("AI scoring task could not be scheduled: {}", reason);
        for (MatchingRecord record : records) {
            if (AiConstant.AI_SCORING_PENDING.equals(record.getAiScoringStatus())) {
                aiScoringStateMachine.failPending(record.getId(), reason,
                        record.getAiScoringAttemptCount() == null ? 0 : record.getAiScoringAttemptCount());
            }
        }
    }

    private void initializeAiScoringStatuses(List<MatchingRecord> records, boolean enableAi, boolean forceAi, int aiThreshold) {
        for (MatchingRecord record : records) {
            // force 模式：记忆规则排除（screeningLevel=null）的记录也视为 L1 通过进入 AI；
            // 硬条件失败（=1）仍不通过（不绕过硬条件）
            boolean hardConditionPassed = forceAi
                    ? (record.getScreeningLevel() == null || record.getScreeningLevel() >= 2)
                    : (record.getScreeningLevel() != null && record.getScreeningLevel() >= 2);
            boolean scoreEligible = forceAi
                    || (record.getL2Score() != null
                    && record.getL2Score().doubleValue() >= aiThreshold);
            boolean eligible = enableAi
                    && record.getForcedByList() == null
                    && hardConditionPassed
                    && scoreEligible;
            record.setAiScoringStatus(eligible ? AiConstant.AI_SCORING_PENDING : AiConstant.AI_SCORING_SKIPPED);
            record.setAiScoringAttemptCount(eligible ? 0 : null);
            record.setAiScoringFailReason(null);
            record.setAiScoringNextRetryAt(null);
        }
    }

    private void rebuildGraph() {
        try {
            // 匹配结果可能影响图谱（MATCHED_WITH 关系），但重建成本高，
            // 异步入队由 GraphBuildTaskService 串行执行，避免阻塞匹配主流程
            eventPublisher.publishEvent(new KnowledgeGraphRebuildRequestedEvent());
        } catch (Exception e) {
            log.warn("匹配后图谱重建入队失败: {}", e.getMessage());
        }
    }

}
