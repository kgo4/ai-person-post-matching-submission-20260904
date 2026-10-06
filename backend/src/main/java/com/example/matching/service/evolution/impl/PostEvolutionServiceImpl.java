package com.example.matching.service.evolution.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.example.matching.common.constant.SourceRefConstants;
import com.example.matching.common.enums.ChangeTypeEnum;
import com.example.matching.common.enums.TaskStatusEnum;
import com.example.matching.common.exception.BusinessException;
import com.example.matching.common.exception.ErrorCodeEnum;
import com.example.matching.dto.evolution.PostEvolutionReviewDTO;
import com.example.matching.dto.evolution.PostEvolutionTaskCreateDTO;
import com.example.matching.dto.harness.AiHarnessClaimDTO;
import com.example.matching.dto.harness.AiHarnessDecisionDTO;
import com.example.matching.dto.post.JdAbilityItemDTO;
import com.example.matching.entity.evolution.MarketJdData;
import com.example.matching.entity.evolution.PostEvolutionChangeItem;
import com.example.matching.entity.evolution.PostEvolutionEvidence;
import com.example.matching.entity.evolution.PostEvolutionTask;
import com.example.matching.entity.matching.MatchingFeedbackDataset;
import com.example.matching.entity.matching.MatchingRecord;
import com.example.matching.entity.post.PostAbilityModel;
import com.example.matching.entity.system.AbilityTag;
import com.example.matching.event.PostModelChangeEvent;
import com.example.matching.mapper.evolution.MarketJdDataMapper;
import com.example.matching.mapper.evolution.PostEvolutionChangeItemMapper;
import com.example.matching.mapper.evolution.PostEvolutionEvidenceMapper;
import com.example.matching.mapper.evolution.PostEvolutionTaskMapper;
import com.example.matching.mapper.matching.MatchingFeedbackDatasetMapper;
import com.example.matching.mapper.matching.MatchingRecordMapper;
import com.example.matching.mapper.post.PostAbilityModelMapper;
import com.example.matching.service.ability.AbilityEvidenceIngestionService;
import com.example.matching.service.closure.CapabilityClosureService;
import com.example.matching.service.evolution.EvolutionEvidenceCollector;
import com.example.matching.service.evolution.PostEvolutionService;
import com.example.matching.service.governance.GovernedAdmissionService;
import com.example.matching.service.harness.AiTrustHarnessService;
import com.example.matching.dto.post.PostCleaningResult;
import com.example.matching.dto.post.PostRawInput;
import com.example.matching.service.post.PostCapabilityGenerationService;
import com.example.matching.service.post.PostDataCleaningService;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 岗位演化服务实现（增强版）
 * <p>
 * 新增功能：
 * 1. 证据链管理 - 每个变更项都有可追溯的证据
 * 2. 多源数据融合 - 支持市场JD、匹配反馈、学习缺口等多种数据源
 * 3. 增强评分模型 - 包含趋势分、证据分、相关度等多维度评分
 *
 * @author system
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PostEvolutionServiceImpl implements PostEvolutionService {

    private final PostEvolutionTaskMapper taskMapper;
    private final PostEvolutionChangeItemMapper changeItemMapper;
    private final PostEvolutionEvidenceMapper evidenceMapper;
    private final PostAbilityModelMapper postAbilityModelMapper;
    private final AbilityEvidenceIngestionService abilityEvidenceIngestionService;
        private final PostCapabilityGenerationService postCapabilityGenerationService;
    private final ObjectMapper objectMapper;
    private final CapabilityClosureService capabilityClosureService;
    private final AiTrustHarnessService aiTrustHarnessService;
    private final MatchingFeedbackDatasetMapper feedbackDatasetMapper;
    private final MatchingRecordMapper matchingRecordMapper;
    private final MarketJdDataMapper marketJdDataMapper;
    private final EvolutionEvidenceCollector evidenceCollector;
    private final GovernedAdmissionService governedAdmissionService;
    private final com.example.matching.mapper.governance.GovernanceAdmissionMapper admissionMapper;
    private final ApplicationEventPublisher eventPublisher;
    private final PostEvolutionChangeComparator changeComparator;
    private final PostEvolutionDashboardService dashboardService;
    private final PostEvolutionScoringService scoringService;
    private final PostDataCleaningService postDataCleaningService;
    private final PlatformTransactionManager transactionManager;

    @Override
    @Transactional
    public PostEvolutionTask createTask(PostEvolutionTaskCreateDTO dto, Long userId) {
        Long activeTaskCount = taskMapper.selectCount(new LambdaQueryWrapper<PostEvolutionTask>()
                .eq(PostEvolutionTask::getPostId, dto.getPostId())
                .in(PostEvolutionTask::getTaskStatus,
                        TaskStatusEnum.PENDING.getCode(), TaskStatusEnum.RUNNING.getCode()));
        if (activeTaskCount != null && activeTaskCount > 0) {
            throw new BusinessException(ErrorCodeEnum.PARAM_ERROR, "该岗位已有进行中的演化任务");
        }
        PostEvolutionTask task = new PostEvolutionTask();
        task.setTaskCode("EVO_" + UUID.randomUUID().toString().replace("-", "").substring(0, 16).toUpperCase());
        task.setPostId(dto.getPostId());
        task.setTaskName(dto.getTaskName());
        task.setNewJdText(dto.getNewJdText());
        task.setTaskStatus(TaskStatusEnum.PENDING.getCode());
        task.setCreatedBy(userId);
        taskMapper.insert(task);
        return task;
    }

    @Override
    public PostEvolutionTask analyzeTask(Long taskId) {
        PostEvolutionTask task = markTaskRunning(taskId);

        try {
            // 1. 获取当前岗位能力模型
            LambdaQueryWrapper<PostAbilityModel> wrapper = new LambdaQueryWrapper<>();
            wrapper.eq(PostAbilityModel::getPostId, task.getPostId());
            wrapper.eq(PostAbilityModel::getIsDeleted, 0);
            List<PostAbilityModel> currentAbilities = postAbilityModelMapper.selectList(wrapper);

            // 2. 收集多源证据
            List<PostEvolutionEvidence> allEvidence = new ArrayList<>();

            // 2.1 从手动输入的JD收集证据
            if (task.getNewJdText() != null && !task.getNewJdText().isBlank()) {
                PostEvolutionEvidence jdEvidence = evidenceCollector.createJdEvidence(taskId, task.getNewJdText());
                allEvidence.add(jdEvidence);
            }

            // 2.2 从市场JD数据收集证据
            List<MarketJdData> marketJds = marketJdDataMapper.selectList(
                    new LambdaQueryWrapper<MarketJdData>()
                            .eq(MarketJdData::getMatchedPostId, task.getPostId())
                            .eq(MarketJdData::getIsDuplicate, 0)
                            .last("LIMIT 50"));
            for (MarketJdData marketJd : marketJds) {
                PostEvolutionEvidence evidence = evidenceCollector.createMarketJdEvidence(taskId, marketJd);
                allEvidence.add(evidence);
            }

            // 2.3 从匹配反馈收集证据
            List<MatchingFeedbackDataset> feedbacks = feedbackDatasetMapper.selectList(
                    new LambdaQueryWrapper<MatchingFeedbackDataset>()
                            .eq(MatchingFeedbackDataset::getPostId, task.getPostId())
                            .eq(MatchingFeedbackDataset::getAdoptionStatus, 3) // 未采纳的反馈
                            .last("LIMIT 20"));
            for (MatchingFeedbackDataset feedback : feedbacks) {
                PostEvolutionEvidence evidence = evidenceCollector.createFeedbackEvidence(taskId, feedback);
                allEvidence.add(evidence);
            }

            // 2.4 从匹配记录中的能力缺口收集证据
            List<MatchingRecord> lowScoreRecords = matchingRecordMapper.selectList(
                    new LambdaQueryWrapper<MatchingRecord>()
                            .eq(MatchingRecord::getPostId, task.getPostId())
                            .lt(MatchingRecord::getAiMatchScore, 60)
                            .last("LIMIT 20"));
            for (MatchingRecord record : lowScoreRecords) {
                PostEvolutionEvidence evidence = evidenceCollector.createMatchingGapEvidence(taskId, record);
                allEvidence.add(evidence);
            }

            // 3. 保存证据
            for (PostEvolutionEvidence evidence : allEvidence) {
                evidenceMapper.insert(evidence);
            }

            // 4. 数据清洗与治理阻断
            String cleanedPostName = "岗位 #" + task.getPostId();
            String cleanedJdText = task.getNewJdText();
            Long cleaningRecordId = null;
            String inputHash = null;

            if (task.getNewJdText() != null && !task.getNewJdText().isBlank()) {
                try {
                    inputHash = sha256(task.getNewJdText());
                } catch (Exception e) {
                    log.warn("计算输入哈希失败: {}", e.getMessage());
                    inputHash = String.valueOf(task.getNewJdText().hashCode());
                }
                task.setContextHash(inputHash);

                PostRawInput rawInput = PostRawInput.builder()
                        .postName("岗位 #" + task.getPostId())
                        .rawText(task.getNewJdText())
                        .sourceType("POST_EVOLUTION_TASK")
                        .sourceRefId(taskId)
                        .build();

                PostCleaningResult cleaningResult = postDataCleaningService.cleanAndDetect(rawInput);
                cleaningRecordId = cleaningResult.getCleaningRecordId();
                task.setContextSnapshotId(cleaningRecordId);

                if (cleaningResult.isBlocked()) {
                    log.warn("[EVOLUTION_BLOCKED] 演化任务被数据清洗阻断: taskId={}, cleaningRecordId={}, blockReason={}",
                            taskId, cleaningRecordId, cleaningResult.getBlockReason());
                    String blockMsg = "数据清洗阻断: " + cleaningResult.getBlockReason()
                            + " (cleaningRecordId=" + cleaningRecordId + ")";
                    String summary = buildBlockedSummary(cleaningRecordId, cleaningResult.getBlockReason(), inputHash);
                    markTaskFailed(taskId, blockMsg, summary);
                    task.setTaskStatus(TaskStatusEnum.FAILED.getCode());
                    task.setErrorMessage(blockMsg);
                    task.setSummaryJson(summary);
                    return task;
                }

                cleanedPostName = cleaningResult.getCleanedPostName() != null && !cleaningResult.getCleanedPostName().isBlank()
                        ? cleaningResult.getCleanedPostName() : cleanedPostName;
                cleanedJdText = cleaningResult.getCleanedText() != null && !cleaningResult.getCleanedText().isBlank()
                        ? cleaningResult.getCleanedText() : cleanedJdText;

                log.info("演化任务数据清洗通过: taskId={}, cleaningRecordId={}, qualityScore={}, contextSnapshotId={}",
                        taskId, cleaningRecordId, cleaningResult.getQualityScore(), cleaningRecordId);
            }

            // 5. 使用RAG增强的能力分析（带Harness来源上下文，使用清洗后的数据）
            List<JdAbilityItemDTO> newAbilities = postCapabilityGenerationService.analyzePostText(
                    cleanedPostName, cleanedJdText,
                    "POST_EVOLUTION_TASK", taskId,
                    List.of("source:POST_EVOLUTION_TASK:" + taskId));

            // 6. 比较当前能力和新提取能力
            List<PostEvolutionChangeItem> changeItems = compareAbilities(taskId, currentAbilities, newAbilities);

            // 7. 为每个变更项计算增强评分并关联证据
            String jdTextHash = inputHash != null ? inputHash : (task.getNewJdText() != null ? String.valueOf(task.getNewJdText().hashCode()) : "no_jd");
            List<PendingEvolutionChange> pendingChanges = new ArrayList<>();
            int blockedChanges = 0;

            for (PostEvolutionChangeItem item : changeItems) {
                item.setSourceType("POST_EVOLUTION_TASK");
                item.setSourceRef("task_" + taskId + "_jd_" + jdTextHash);
                item.setSourceDetail("来自演化任务 #" + task.getTaskCode() + " 的多源数据分析");

                // 计算增强评分
                PostEvolutionScoringService.EvolutionScore score = calculateEvolutionScore(item, allEvidence, task.getPostId());
                item.setSupportScore(score.getFinalScore());

                // 关联证据
                List<PostEvolutionEvidence> relatedEvidence = scoringService.findRelatedEvidence(item, allEvidence);
                List<PostEvolutionEvidence> completeEvidence = relatedEvidence.stream()
                        .filter(this::hasCompleteReviewEvidence)
                        .toList();
                if (completeEvidence.isEmpty()) {
                    log.warn("演化变更项缺少完整可审核证据，阻断进入人工审核: taskId={}, ability={}",
                            taskId, item.getAbilityName());
                    blockedChanges++;
                    continue;
                }
                if (!completeEvidence.isEmpty()) {
                    item.setEvidenceChunkIds(completeEvidence.stream()
                            .map(e -> String.valueOf(e.getId()))
                            .collect(Collectors.joining(",")));
                }

                // Harness验证
                AiHarnessClaimDTO claim = new AiHarnessClaimDTO();
                claim.setScenario("POST_EVOLUTION");
                claim.setClaimType("POST_EVOLUTION_CHANGE");
                claim.setChangeType(toHarnessChangeType(item));
                claim.setClaimText(buildChangeClaimText(item));
                claim.setSourceType("POST_EVOLUTION_TASK");
                claim.setSourceRefId(taskId);
                claim.setEvidenceText(task.getNewJdText() != null
                        ? (task.getNewJdText().length() > 500
                        ? task.getNewJdText().substring(0, 500) + "..." : task.getNewJdText())
                        : null);
                claim.setMatchedTagId(item.getTagId());
                claim.setSourceRefs(List.of("source:POST_EVOLUTION_TASK:" + taskId));
                if (item.getEvidenceChunkIds() != null && !item.getEvidenceChunkIds().isBlank()) {
                    for (String chunkId : item.getEvidenceChunkIds().split(",")) {
                        try {
                            claim.getRagChunkIds().add(Long.parseLong(chunkId.trim()));
                        } catch (NumberFormatException ignored) {
                        }
                    }
                }

                AiHarnessDecisionDTO decision = aiTrustHarnessService.verify(claim);

                if (AiHarnessDecisionDTO.BLOCK.equals(decision.getDecision())) {
                    log.warn("演化变更项被Harness BLOCK: taskId={}, ability={}, reason={}",
                            taskId, item.getAbilityName(), decision.getReasons());
                    blockedChanges++;
                    continue;
                }

                if (AiHarnessDecisionDTO.REVIEW.equals(decision.getDecision())) {
                    item.setConfirmStatus("PENDING");
                    log.info("演化变更项Harness REVIEW: taskId={}, ability={}, score={}",
                            taskId, item.getAbilityName(), item.getSupportScore());
                } else {
                    item.setConfirmStatus("PENDING");
                }

                pendingChanges.add(new PendingEvolutionChange(item, completeEvidence, decision));
            }

            return persistAnalysisResults(taskId, pendingChanges, allEvidence.size(), marketJds.size(),
                    feedbacks.size(), blockedChanges);
        } catch (Exception e) {
            log.error("演化分析失败: taskId={}, error={}", taskId, e.getMessage(), e);
            evidenceMapper.delete(new LambdaQueryWrapper<PostEvolutionEvidence>()
                    .eq(PostEvolutionEvidence::getTaskId, taskId));
            markTaskFailed(taskId, e.getMessage());
            throw new BusinessException(ErrorCodeEnum.INTERNAL_ERROR, "演化分析失败: " + e.getMessage());
        }
    }

    @Override
    public IPage<PostEvolutionTask> pageTasks(Page<PostEvolutionTask> page, Long postId, String taskStatus) {
        LambdaQueryWrapper<PostEvolutionTask> wrapper = new LambdaQueryWrapper<>();
        if (postId != null) {
            wrapper.eq(PostEvolutionTask::getPostId, postId);
        }
        if (taskStatus != null && !taskStatus.isBlank()) {
            wrapper.eq(PostEvolutionTask::getTaskStatus, taskStatus);
        }
        wrapper.orderByDesc(PostEvolutionTask::getCreatedTime);
        return taskMapper.selectPage(page, wrapper);
    }

    @Override
    public PostEvolutionTask getTaskById(Long taskId) {
        PostEvolutionTask task = taskMapper.selectById(taskId);
        if (task == null) {
            throw new BusinessException(ErrorCodeEnum.NOT_FOUND, "演化任务不存在: " + taskId);
        }
        return task;
    }

    @Override
    @Transactional
    public void deleteTask(Long taskId) {
        PostEvolutionTask task = taskMapper.selectById(taskId);
        if (task == null) {
            throw new BusinessException(ErrorCodeEnum.NOT_FOUND, "演化任务不存在: " + taskId);
        }
        evidenceMapper.delete(new LambdaQueryWrapper<PostEvolutionEvidence>()
                .eq(PostEvolutionEvidence::getTaskId, taskId));
        changeItemMapper.delete(new LambdaQueryWrapper<PostEvolutionChangeItem>()
                .eq(PostEvolutionChangeItem::getTaskId, taskId));
        taskMapper.deleteById(taskId);
    }

    @Override
    public IPage<PostEvolutionChangeItem> pageChangeItems(Long taskId, Page<PostEvolutionChangeItem> page) {
        LambdaQueryWrapper<PostEvolutionChangeItem> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(PostEvolutionChangeItem::getTaskId, taskId);
        wrapper.orderByDesc(PostEvolutionChangeItem::getCreatedTime);
        return changeItemMapper.selectPage(page, wrapper);
    }

    @Override
    @Transactional
    public void reviewChangeItem(Long taskId, Long itemId, PostEvolutionReviewDTO dto) {
        PostEvolutionChangeItem item = changeItemMapper.selectById(itemId);
        if (item == null || !item.getTaskId().equals(taskId)) {
            throw new BusinessException(ErrorCodeEnum.NOT_FOUND, "变更项不存在: " + itemId);
        }
        if (!"PENDING".equals(item.getConfirmStatus())) {
            throw new BusinessException(ErrorCodeEnum.PARAM_ERROR, "变更项已审核");
        }
        item.setConfirmStatus(dto.getConfirmStatus());
        item.setReviewComment(dto.getReviewComment());
        if (changeItemMapper.updateById(item) != 1) {
            throw new BusinessException(ErrorCodeEnum.PARAM_ERROR, "变更项已被并发审核，请刷新后重试");
        }
    }

    /**
     * M3/M8：应用前构造完整岗位能力模型快照并校验：
     * 单项权重 0-100、等级 1-5、总权重 95-105、必填能力正权重。
     */
    private void validateApplicableModelSnapshot(PostEvolutionTask task,
                                                  List<PostEvolutionChangeItem> approvedItems) {
        java.util.Map<Long, PostAbilityModel> snapshot = new java.util.HashMap<>();
        List<PostAbilityModel> current = postAbilityModelMapper.selectList(
                Wrappers.<PostAbilityModel>lambdaQuery()
                        .eq(PostAbilityModel::getPostId, task.getPostId())
                        .eq(PostAbilityModel::getIsDeleted, 0));
        for (PostAbilityModel m : current) {
            snapshot.put(m.getTagId(), m);
        }

        for (PostEvolutionChangeItem item : approvedItems) {
            if ("ADDED".equals(item.getChangeType())) {
                if (item.getTagId() == null) {
                    continue; // 缺少标签的变更项按原语义跳过，不进入快照校验
                }
                PostAbilityModel m = new PostAbilityModel();
                m.setTagId(item.getTagId());
                m.setWeight(item.getNewWeight());
                m.setMinRequiredLevel(item.getNewLevel() != null ? item.getNewLevel() : 2);
                m.setIsRequired(1);
                snapshot.put(item.getTagId(), m);
            } else if ("REMOVED".equals(item.getChangeType())) {
                snapshot.remove(item.getTagId());
            } else if (item.getTagId() != null && snapshot.containsKey(item.getTagId())) {
                PostAbilityModel m = snapshot.get(item.getTagId());
                if ("UPDATED_WEIGHT".equals(item.getChangeType()) && item.getNewWeight() != null) {
                    m.setWeight(item.getNewWeight());
                }
                if ("UPDATED_LEVEL".equals(item.getChangeType()) && item.getNewLevel() != null) {
                    m.setMinRequiredLevel(item.getNewLevel());
                }
            }
        }

        for (java.util.Map.Entry<Long, PostAbilityModel> entry : snapshot.entrySet()) {
            PostAbilityModel m = entry.getValue();
            if (m.getWeight() == null || m.getWeight().doubleValue() < 0 || m.getWeight().doubleValue() > 100) {
                throw new BusinessException(ErrorCodeEnum.POST_MODEL_WEIGHT_INVALID,
                        "权重超出业务范围 0-100: tagId=" + entry.getKey() + ", weight=" + m.getWeight());
            }
            if (m.getMinRequiredLevel() != null && (m.getMinRequiredLevel() < 1 || m.getMinRequiredLevel() > 5)) {
                throw new BusinessException(ErrorCodeEnum.POST_MODEL_WEIGHT_INVALID,
                        "演化等级超出业务范围 1-5: tagId=" + entry.getKey() + ", level=" + m.getMinRequiredLevel());
            }
            if (Integer.valueOf(1).equals(m.getIsRequired())
                    && (m.getWeight() == null || m.getWeight().doubleValue() <= 0)) {
                throw new BusinessException(ErrorCodeEnum.POST_MODEL_REQUIRED_WEIGHT_ZERO,
                        "必填能力权重不能为 0: tagId=" + entry.getKey());
            }
        }

        // 无有效变更项可应用（全部被跳过）时不做总权重校验，由调用方按 skipped 语义处理
        if (snapshot.isEmpty()) {
            return;
        }

        double totalWeight = snapshot.values().stream()
                .mapToDouble(m -> m.getWeight() != null ? m.getWeight().doubleValue() : 0).sum();
        if (totalWeight < 95 || totalWeight > 105) {
            throw new BusinessException(ErrorCodeEnum.POST_MODEL_WEIGHT_INVALID,
                    "应用后岗位能力总权重应处于 95-105，实际: " + String.format("%.1f", totalWeight));
        }
    }

    @Override
    @Transactional
    public int applyApprovedChanges(Long taskId) {
        PostEvolutionTask task = getTaskById(taskId);
        if (!TaskStatusEnum.WAIT_CONFIRM.getCode().equals(task.getTaskStatus())) {
            throw new BusinessException(ErrorCodeEnum.PARAM_ERROR, "任务状态不允许应用: " + task.getTaskStatus());
        }

        // 查询已审核通过的变更项
        LambdaQueryWrapper<PostEvolutionChangeItem> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(PostEvolutionChangeItem::getTaskId, taskId);
        wrapper.eq(PostEvolutionChangeItem::getConfirmStatus, "APPROVED");
        List<PostEvolutionChangeItem> approvedItems = changeItemMapper.selectList(wrapper);

        if (approvedItems.isEmpty()) {
            throw new BusinessException(ErrorCodeEnum.PARAM_ERROR, "没有已审核通过的变更项");
        }

        // M8：新增能力不允许默认 weight=0，null 时拒绝应用（无标签项按 skipped 语义处理）
        for (PostEvolutionChangeItem item : approvedItems) {
            if ("ADDED".equals(item.getChangeType()) && item.getTagId() != null && item.getNewWeight() == null) {
                throw new BusinessException(ErrorCodeEnum.POST_MODEL_WEIGHT_INVALID,
                        "新增能力必须显式设置权重（不允许默认 0）: " + item.getAbilityName());
            }
        }

        // M8：应用前构造完整岗位能力模型快照并统一校验（单项权重 0-100、总权重 95-105、必填能力正权重）
        validateApplicableModelSnapshot(task, approvedItems);

        int applied = 0;
        int skipped = 0;
        List<String> skippedReasons = new ArrayList<>();

        for (PostEvolutionChangeItem item : approvedItems) {
            if ("ADDED".equals(item.getChangeType()) && item.getTagId() == null) {
                String reason = "变更项 " + item.getId() + " 缺少能力标签，未写入岗位能力模型";
                log.warn("演化{}: taskId={}", reason, taskId);
                skipped++;
                skippedReasons.add(reason);
                continue;
            }
            // 写正式岗位能力模型前必须持有 PASS 治理准入
            Long admissionId = resolvePassAdmission(item, task);
            if (admissionId == null) {
                log.warn("演化变更项无 PASS 治理准入，跳过应用: taskId={}, itemId={}, ability={}",
                        taskId, item.getId(), item.getAbilityName());
                skipped++;
                skippedReasons.add("变更项 " + item.getId() + " 未获得 PASS 治理准入，未应用");
                continue;
            }
            switch (item.getChangeType()) {
                case "ADDED" -> {
                    // 演化不依赖标签库：tagId 可空，但能力名必须存在；按能力名防重复
                    if (item.getTagId() == null
                            && (item.getAbilityName() == null || item.getAbilityName().isBlank())) {
                        String reason = "变更项 " + item.getId() + " 缺少能力名称，未写入";
                        log.warn("岗位演化 ADDED 项缺少能力名称，跳过写入: taskId={}, itemId={}",
                                task.getId(), item.getId());
                        skipped++;
                        skippedReasons.add(reason);
                        continue;
                    }
                    if (findModelByItem(task.getPostId(), item) != null) {
                        String reason = "变更项 " + item.getId() + " 岗位已存在同能力，未重复写入";
                        skipped++;
                        skippedReasons.add(reason);
                        continue;
                    }
                    PostAbilityModel model = new PostAbilityModel();
                    model.setPostId(task.getPostId());
                    model.setTagId(item.getTagId());
                    model.setAbilityName(item.getAbilityName());
                    model.setMinRequiredLevel(item.getNewLevel() != null ? item.getNewLevel() : 2);
                    model.setWeight(item.getNewWeight() != null ? item.getNewWeight() : BigDecimal.ZERO);
                    model.setIsCore(item.getNewIsCore() != null ? item.getNewIsCore() : 0);
                    model.setIsRequired(1);
                    model.setSourceType("POST_EVOLUTION");
                    model.setGovernanceAdmissionId(admissionId);
                    postAbilityModelMapper.insert(model);
                    abilityEvidenceIngestionService.ingestPostAbilityModel(model.getId(), SourceRefConstants.SOURCE_POST_EVOLUTION);
                    applied++;
                }
                case "REMOVED" -> {
                    PostAbilityModel existing = findModelByItem(task.getPostId(), item);
                    if (existing != null) {
                        existing.setIsDeleted(1);
                        if (postAbilityModelMapper.updateById(existing) != 1) {
                            throw new BusinessException(ErrorCodeEnum.PARAM_ERROR, "岗位能力模型已被并发更新，请重新审核后再应用");
                        }
                        applied++;
                    } else {
                        String reason = "变更项" + item.getId() + " 指定的岗位能力不存在，未执行删除";
                        skipped++;
                        skippedReasons.add(reason);
                    }
                }
                case "UPDATED_LEVEL", "UPDATED_WEIGHT", "UPDATED_CORE" -> {
                    PostAbilityModel existing = findModelByItem(task.getPostId(), item);
                    if (existing != null) {
                        if ("UPDATED_LEVEL".equals(item.getChangeType()) && item.getNewLevel() != null) {
                            existing.setMinRequiredLevel(item.getNewLevel());
                        }
                        if ("UPDATED_WEIGHT".equals(item.getChangeType()) && item.getNewWeight() != null) {
                            existing.setWeight(item.getNewWeight());
                        }
                        if ("UPDATED_CORE".equals(item.getChangeType()) && item.getNewIsCore() != null) {
                            existing.setIsCore(item.getNewIsCore());
                        }
                        existing.setSourceType("POST_EVOLUTION");
                        existing.setGovernanceAdmissionId(admissionId);
                        if (postAbilityModelMapper.updateById(existing) != 1) {
                            throw new BusinessException(ErrorCodeEnum.PARAM_ERROR, "岗位能力模型已被并发更新，请重新审核后再应用");
                        }
                        abilityEvidenceIngestionService.ingestPostAbilityModel(existing.getId(), SourceRefConstants.SOURCE_POST_EVOLUTION);
                        applied++;
                    } else {
                        String reason = "变更项" + item.getId() + " 指定的岗位能力不存在，未执行更新";
                        skipped++;
                        skippedReasons.add(reason);
                    }
                }
                default -> {
                    String reason = "变更项" + item.getId() + " 的变更类型无效，未执行";
                    skipped++;
                    skippedReasons.add(reason);
                }
            }
        }

        if (skipped > 0 || applied == 0) {
            task.setTaskStatus(TaskStatusEnum.PARTIALLY_APPLIED.getCode());
            if (skippedReasons.isEmpty()) {
                skippedReasons.add("没有任何已审核变更实际写入岗位能力模型");
            }
            task.setErrorMessage(String.join("; ", skippedReasons));
        } else {
            task.setTaskStatus(TaskStatusEnum.APPLIED.getCode());
            task.setErrorMessage(null);
        }
        taskMapper.updateById(task);

        if (applied > 0) {
            try {
                eventPublisher.publishEvent(new PostModelChangeEvent(this, "MODEL_CONFIG", task.getPostId()));
                log.info("Published PostModelChangeEvent after evolution apply: postId={}, appliedChanges={}",
                        task.getPostId(), applied);
            } catch (Exception e) {
                log.warn("Failed to publish PostModelChangeEvent: postId={}, error={}",
                        task.getPostId(), e.getMessage());
            }
        }

        try {
            capabilityClosureService.onPostEvolutionApplied(taskId);
        } catch (Exception e) {
            log.warn("Post evolution closure failed: taskId={}, error={}", taskId, e.getMessage());
        }

        return applied;
    }

    /**
     * 按 tagId（优先）或 ability_name 定位岗位能力记录（演化不依赖标签库，tagId 可空）。
     */
    private PostAbilityModel findModelByItem(Long postId, PostEvolutionChangeItem item) {
        LambdaQueryWrapper<PostAbilityModel> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(PostAbilityModel::getPostId, postId);
        if (item.getTagId() != null) {
            wrapper.eq(PostAbilityModel::getTagId, item.getTagId());
        } else if (item.getAbilityName() != null && !item.getAbilityName().isBlank()) {
            wrapper.eq(PostAbilityModel::getAbilityName, item.getAbilityName());
        } else {
            return null;
        }
        return postAbilityModelMapper.selectOne(wrapper);
    }

    /**
     * 为演化变更项申请岗位能力写入的治理准入记录，返回准入ID（可能为 REVIEW/BLOCK）。
     */
    private Long grantPostAbilityAdmission(PostEvolutionChangeItem item, PostEvolutionTask task,
                                           AiHarnessDecisionDTO harnessDecision) {
        com.example.matching.agent.dto.post.PostAbilityClaim claim =
                new com.example.matching.agent.dto.post.PostAbilityClaim();
        claim.setPostId(task.getPostId());
        claim.setAbilityTagId(item.getTagId());
        claim.setAbilityName(item.getAbilityName());
        claim.setRequiredLevel(item.getNewLevel() != null ? item.getNewLevel() : 2);
        claim.setWeight(item.getNewWeight() != null ? item.getNewWeight() : BigDecimal.ZERO);
        claim.setIsCore(item.getNewIsCore() != null && item.getNewIsCore() == 1);
        claim.setIsRequired(true);
        claim.setSourceType("POST_EVOLUTION");
        claim.setSourceRefId(task.getId());
        claim.setEvidenceText(item.getEvidenceText() != null ? item.getEvidenceText()
                : (harnessDecision != null && !harnessDecision.getReasons().isEmpty()
                ? String.join("; ", harnessDecision.getReasons()) : null));
        claim.setSourceRefs(item.getSourceRefsJson() != null && !item.getSourceRefsJson().isBlank()
                ? parseJsonList(item.getSourceRefsJson())
                : List.of("source:POST_EVOLUTION_TASK:" + task.getId()));
        com.example.matching.dto.governance.GovernanceAdmission admission =
                governedAdmissionService.admitPostAbility(claim);
        return admission.getId();
    }

    /**
     * 解析变更项可用的 PASS 准入ID：优先复用已存准入，否则重新申请。
     * 非 PASS 准入不写正式事实表。
     */
    private Long resolvePassAdmission(PostEvolutionChangeItem item, PostEvolutionTask task) {
        if (item.getGovernanceAdmissionId() != null) {
            com.example.matching.entity.governance.GovernanceAdmissionRecord record =
                    admissionMapper.selectById(item.getGovernanceAdmissionId());
            if (record != null && "PASS".equals(record.getFinalDecision())) {
                return record.getId();
            }
        }
        try {
            Long id = grantPostAbilityAdmission(item, task, null);
            com.example.matching.entity.governance.GovernanceAdmissionRecord record =
                    admissionMapper.selectById(id);
            return record != null && "PASS".equals(record.getFinalDecision()) ? record.getId() : null;
        } catch (Exception e) {
            log.warn("演化变更项重新申请准入失败: itemId={}, error={}", item.getId(), e.getMessage());
            return null;
        }
    }

    @SuppressWarnings("unchecked")
    private List<String> parseJsonList(String json) {
        try {
            return objectMapper.readValue(json, List.class);
        } catch (Exception e) {
            return List.of();
        }
    }

    @Override
    public List<PostEvolutionEvidence> getTaskEvidence(Long taskId) {
        return evidenceMapper.selectList(
                new LambdaQueryWrapper<PostEvolutionEvidence>()
                        .eq(PostEvolutionEvidence::getTaskId, taskId)
                        .orderByDesc(PostEvolutionEvidence::getCreatedTime));
    }

    @Override
    public List<PostEvolutionEvidence> getItemEvidence(Long itemId) {
        return evidenceMapper.selectList(
                new LambdaQueryWrapper<PostEvolutionEvidence>()
                        .eq(PostEvolutionEvidence::getChangeItemId, itemId)
                        .orderByDesc(PostEvolutionEvidence::getCreatedTime));
    }

    // ===== 评分模型 =====

    /**
     * 计算演化评分（多维度）
     */

    private PostEvolutionScoringService.EvolutionScore calculateEvolutionScore(PostEvolutionChangeItem item,
                                                    List<PostEvolutionEvidence> allEvidence,
                                                    Long postId) {
        return scoringService.calculateEvolutionScore(item, allEvidence, postId);
    }

    private String toHarnessChangeType(PostEvolutionChangeItem item) {
        return scoringService.toHarnessChangeType(item);
    }


    // ===== 委托组件（拆分自原 1100+ 行类） =====

    public List<PostEvolutionChangeItem> compareAbilities(Long taskId, List<PostAbilityModel> currentAbilities, List<JdAbilityItemDTO> newAbilities) {
        return changeComparator.compareAbilities(taskId, currentAbilities, newAbilities);
    }

    private String buildChangeClaimText(PostEvolutionChangeItem item) {
        return changeComparator.buildChangeClaimText(item);
    }

    private boolean hasCompleteReviewEvidence(PostEvolutionEvidence evidence) {
        return evidence.getSourceType() != null && !evidence.getSourceType().isBlank()
                && evidence.getEvidenceText() != null && !evidence.getEvidenceText().isBlank()
                && evidence.getCollectedTime() != null
                && evidence.getSimilarityScore() != null
                && evidence.getTrustScore() != null
                && evidence.getSourceRef() != null && !evidence.getSourceRef().isBlank();
    }

    @Override
    public List<Map<String, Object>> getTimelineEvents(Long postId, String range, int limit) {
        return dashboardService.getTimelineEvents(postId, range, limit);
    }

    @Override
    public Map<String, Object> getDashboardStats(String range) {
        return dashboardService.getDashboardStats(range);
    }

    @Override
    public Map<String, Object> getEvolutionTrends(String range) {
        return dashboardService.getEvolutionTrends(range);
    }

    @Override
    public Map<String, Object> getEvolutionGraph(Long postId, String timePoint) {
        return dashboardService.getEvolutionGraph(postId, timePoint);
    }
    // ===== 仪表盘方法 =====

    private String sha256(String input) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(input.getBytes(StandardCharsets.UTF_8));
            StringBuilder hexString = new StringBuilder();
            for (byte b : hash) {
                String hex = Integer.toHexString(0xff & b);
                if (hex.length() == 1) hexString.append('0');
                hexString.append(hex);
            }
            return hexString.toString();
        } catch (Exception e) {
            throw new RuntimeException("SHA-256计算失败", e);
        }
    }

    private String buildBlockedSummary(Long cleaningRecordId, String blockReason, String inputHash) {
        try {
            Map<String, Object> blocked = new HashMap<>();
            blocked.put("status", "BLOCKED");
            blocked.put("cleaningRecordId", cleaningRecordId);
            blocked.put("blockReason", blockReason);
            blocked.put("inputHash", inputHash);
            return objectMapper.writeValueAsString(blocked);
        } catch (Exception e) {
            return "{\"status\":\"BLOCKED\",\"cleaningRecordId\":" + cleaningRecordId
                    + ",\"blockReason\":\"" + (blockReason != null ? blockReason : "unknown") + "\"}";
        }
    }

    private PostEvolutionTask persistAnalysisResults(Long taskId,
                                                      List<PendingEvolutionChange> pendingChanges,
                                                      int evidenceCount,
                                                      int marketJdCount,
                                                      int feedbackCount,
                                                      int blockedChanges) {
        return requiresNewTransaction().execute(status -> {
            PostEvolutionTask task = taskMapper.selectById(taskId);
            if (task == null) {
                throw new BusinessException(ErrorCodeEnum.NOT_FOUND, "演化任务不存在: " + taskId);
            }
            if (!TaskStatusEnum.RUNNING.getCode().equals(task.getTaskStatus())) {
                throw new BusinessException(ErrorCodeEnum.PARAM_ERROR, "任务状态不允许写入分析结果: " + task.getTaskStatus());
            }

            for (PendingEvolutionChange pendingChange : pendingChanges) {
                PostEvolutionChangeItem item = pendingChange.item();
                try {
                    item.setGovernanceAdmissionId(
                            grantPostAbilityAdmission(item, task, pendingChange.harnessDecision()));
                } catch (Exception e) {
                    log.warn("演化变更项治理准入失败: taskId={}, ability={}, error={}",
                            taskId, item.getAbilityName(), e.getMessage());
                }

                changeItemMapper.insert(item);
                for (PostEvolutionEvidence evidence : pendingChange.relatedEvidence()) {
                    evidence.setChangeItemId(item.getId());
                    evidenceMapper.updateById(evidence);
                }
            }

            Map<String, Object> summary = new HashMap<>();
            summary.put("totalChanges", pendingChanges.size());
            summary.put("blockedChanges", blockedChanges);
            summary.put("evidenceCount", evidenceCount);
            summary.put("marketJdCount", marketJdCount);
            summary.put("feedbackCount", feedbackCount);
            summary.put("added", pendingChanges.stream().filter(change ->
                    ChangeTypeEnum.ADDED.getCode().equals(change.item().getChangeType())).count());
            summary.put("removed", pendingChanges.stream().filter(change ->
                    ChangeTypeEnum.REMOVED.getCode().equals(change.item().getChangeType())).count());
            summary.put("updated", pendingChanges.stream().filter(change ->
                    change.item().getChangeType().startsWith("UPDATED_")).count());
            try {
                task.setSummaryJson(objectMapper.writeValueAsString(summary));
            } catch (Exception e) {
                throw new BusinessException(ErrorCodeEnum.INTERNAL_ERROR, "演化分析汇总序列化失败: " + e.getMessage());
            }
            if (pendingChanges.isEmpty()) {
                task.setTaskStatus(TaskStatusEnum.APPLIED.getCode());
                task.setErrorMessage(null);
                log.info("岗位演化未发现可确认变更，任务直接结束: taskId={}", taskId);
            } else {
                task.setTaskStatus(TaskStatusEnum.WAIT_CONFIRM.getCode());
            }
            taskMapper.updateById(task);
            return task;
        });
    }

    private TransactionTemplate requiresNewTransaction() {
        TransactionTemplate template = new TransactionTemplate(transactionManager);
        template.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        return template;
    }

    private record PendingEvolutionChange(PostEvolutionChangeItem item,
                                          List<PostEvolutionEvidence> relatedEvidence,
                                          AiHarnessDecisionDTO harnessDecision) {
    }

    private PostEvolutionTask markTaskRunning(Long taskId) {
        return requiresNewTransaction().execute(status -> {
            PostEvolutionTask task = taskMapper.selectById(taskId);
            if (task == null) {
                throw new BusinessException(ErrorCodeEnum.NOT_FOUND, "演化任务不存在: " + taskId);
            }
            if (!TaskStatusEnum.PENDING.getCode().equals(task.getTaskStatus())
                    && !TaskStatusEnum.FAILED.getCode().equals(task.getTaskStatus())) {
                throw new BusinessException(ErrorCodeEnum.PARAM_ERROR, "任务状态不允许分析: " + task.getTaskStatus());
            }
            task.setTaskStatus(TaskStatusEnum.RUNNING.getCode());
            task.setErrorMessage(null);
            taskMapper.updateById(task);
            return task;
        });
    }

    private void markTaskFailed(Long taskId, String errorMessage) {
        markTaskFailed(taskId, errorMessage, null);
    }

    private void markTaskFailed(Long taskId, String errorMessage, String summaryJson) {
        requiresNewTransaction().executeWithoutResult(status -> {
            PostEvolutionTask task = taskMapper.selectById(taskId);
            if (task == null) return;
            task.setTaskStatus(TaskStatusEnum.FAILED.getCode());
            String truncated = errorMessage != null && errorMessage.length() > 2000
                    ? errorMessage.substring(0, 2000) : errorMessage;
            task.setErrorMessage(truncated);
            if (summaryJson != null) {
                task.setSummaryJson(summaryJson);
            }
            taskMapper.updateById(task);
        });
    }

}
