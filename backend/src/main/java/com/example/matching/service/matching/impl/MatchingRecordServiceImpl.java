package com.example.matching.service.matching.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.example.matching.common.enums.MatchStatusEnum;
import com.example.matching.common.enums.PublishStatusEnum;
import com.example.matching.common.exception.BusinessException;
import com.example.matching.common.exception.ErrorCodeEnum;
import com.example.matching.config.RedisCacheNames;
import com.example.matching.dto.matching.MatchingExecuteDTO;
import com.example.matching.entity.employee.EmpAbility;
import com.example.matching.entity.employee.EmpEmployee;
import com.example.matching.entity.matching.MatchingApprovalFlow;
import com.example.matching.entity.matching.MatchingFeedbackDataset;
import com.example.matching.entity.matching.MatchingRecord;
import com.example.matching.entity.post.PostPost;
import com.example.matching.mapper.matching.MatchingApprovalFlowMapper;
import com.example.matching.mapper.matching.MatchingFeedbackDatasetMapper;
import com.example.matching.mapper.matching.MatchingRecordMapper;
import com.example.matching.service.matching.*;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.cache.annotation.Caching;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;

@Slf4j
@Service
@RequiredArgsConstructor
public class MatchingRecordServiceImpl extends ServiceImpl<MatchingRecordMapper, MatchingRecord> implements MatchingRecordService {

    private final MatchingExecuteService matchingExecuteService;
    private final MatchingEvidenceScoreCalculator evidenceScoreCalculator;
    private final MatchingDataQueryService dataQuery;
    private final MatchingAiAnalysisService matchingAiAnalysisService;
    private final ObjectMapper objectMapper;
    private final MatchingApprovalFlowMapper matchingApprovalFlowMapper;
    private final MatchingFeedbackDatasetMapper feedbackDatasetMapper;
    private final com.example.matching.service.notification.SysNotificationService notificationService;

    @Override
    public Map<String, Long> dashboardSummary() {
        return baseMapper.selectDashboardSummary();
    }

    @Override
    @Caching(evict = {
            @CacheEvict(cacheNames = RedisCacheNames.MATCHING_RECORD_PAGE, allEntries = true),
            @CacheEvict(cacheNames = RedisCacheNames.MATCHING_RECORD_DETAIL, allEntries = true),
            @CacheEvict(cacheNames = RedisCacheNames.DASHBOARD_STATS, allEntries = true)
    })
    public List<MatchingRecord> executeMatching(MatchingExecuteDTO dto) {
        return matchingExecuteService.execute(dto).records();
    }

    @Override
    @Transactional
    @Caching(evict = {
            @CacheEvict(cacheNames = RedisCacheNames.MATCHING_RECORD_PAGE, allEntries = true),
            @CacheEvict(cacheNames = RedisCacheNames.MATCHING_RECORD_DETAIL, allEntries = true),
            @CacheEvict(cacheNames = RedisCacheNames.MATCHING_AI_REPORT, key = "#id"),
            @CacheEvict(cacheNames = RedisCacheNames.DASHBOARD_STATS, allEntries = true)
    })
    public void modifyResult(Long id, MatchingRecord update) {
        MatchingRecord record = getById(id);
        if (record == null) {
            throw new BusinessException(ErrorCodeEnum.MATCHING_RECORD_NOT_FOUND);
        }
        if (record.getIsLocked() != null && record.getIsLocked() == 1) {
            throw new BusinessException(ErrorCodeEnum.MATCHING_ALREADY_LOCKED);
        }

        BigDecimal originalAiScore = record.getAiMatchScore();
        Integer originalStatus = record.getMatchStatus();

        if (update.getFinalMatchScore() != null) {
            record.setFinalMatchScore(update.getFinalMatchScore());
        }
        if (update.getMatchStatus() != null) {
            record.setMatchStatus(update.getMatchStatus());
        }
        if (update.getManualRemark() != null) {
            record.setManualRemark(update.getManualRemark());
        }
        if (!updateById(record)) {
            throw new BusinessException(ErrorCodeEnum.MATCHING_CONCURRENT_MODIFICATION);
        }

        createFeedbackRecord(record, originalAiScore, originalStatus, update);
    }

    private void createFeedbackRecord(MatchingRecord record, BigDecimal originalAiScore,
                                      Integer originalStatus, MatchingRecord update) {
        // 人工备注是对匹配结论的补充说明，应同步为校准样本的反馈说明。
        boolean calibrationRelevant = update.getFinalMatchScore() != null
                || update.getMatchStatus() != null
                || update.getFeedbackReasons() != null
                || update.getFeedbackComment() != null
                || update.getManualRemark() != null;
        if (!calibrationRelevant) {
            log.info("未提供人工校准信息，不更新校准样本: matchingRecordId={}", record.getId());
            return;
        }

        // 幂等：每个 matching_record_id 至多一条当前有效校准样本（uk_matching_feedback_record）
        MatchingFeedbackDataset existing = feedbackDatasetMapper.selectOne(
                Wrappers.<MatchingFeedbackDataset>lambdaQuery()
                        .eq(MatchingFeedbackDataset::getMatchingRecordId, record.getId())
                        .last("LIMIT 1"));

        MatchingFeedbackDataset feedback = existing != null ? existing : new MatchingFeedbackDataset();
        feedback.setMatchingRecordId(record.getId());
        feedback.setEmpId(record.getEmpId());
        feedback.setPostId(record.getPostId());
        feedback.setAiMatchScore(originalAiScore);
        feedback.setFinalMatchScore(record.getFinalMatchScore());
        feedback.setFinalMatchStatus(record.getMatchStatus());
        feedback.setFeedbackTime(LocalDateTime.now());

        if (update.getFeedbackReasons() != null) feedback.setFeedbackReasons(update.getFeedbackReasons());
        if (update.getFeedbackComment() != null) {
            feedback.setFeedbackComment(update.getFeedbackComment());
        } else if (update.getManualRemark() != null) {
            feedback.setFeedbackComment(update.getManualRemark());
        }

        if (record.getFinalMatchScore() != null && originalAiScore != null) {
            double diff = Math.abs(record.getFinalMatchScore().doubleValue() - originalAiScore.doubleValue());
            if (diff < 5 && Objects.equals(record.getMatchStatus(), originalStatus)) {
                feedback.setAdoptionStatus(1);
            } else if (diff < 15) {
                feedback.setAdoptionStatus(2);
            } else {
                feedback.setAdoptionStatus(3);
            }
        } else {
            feedback.setAdoptionStatus(2);
        }
        feedback.setCalibrationSource("MANUAL_FEEDBACK");
        feedback.setCalibrationTemplateVersion("v1");
        feedback.setExportEnabled(feedback.getExportEnabled() == null ? 0 : feedback.getExportEnabled());

        if (existing != null) {
            feedbackDatasetMapper.updateById(feedback);
        } else {
            feedbackDatasetMapper.insert(feedback);
        }
    }

    @Override
    @Caching(evict = {
            @CacheEvict(cacheNames = RedisCacheNames.MATCHING_RECORD_PAGE, allEntries = true),
            @CacheEvict(cacheNames = RedisCacheNames.MATCHING_RECORD_DETAIL, allEntries = true),
            @CacheEvict(cacheNames = RedisCacheNames.DASHBOARD_STATS, allEntries = true)
    })
    public void lockResult(Long id) {
        MatchingRecord record = getById(id);
        if (record == null) throw new BusinessException(ErrorCodeEnum.MATCHING_RECORD_NOT_FOUND);
        record.setIsLocked(1);
        record.setLockedTime(LocalDateTime.now());
        updateById(record);
    }

    @Override
    @Caching(evict = {
            @CacheEvict(cacheNames = RedisCacheNames.MATCHING_RECORD_PAGE, allEntries = true),
            @CacheEvict(cacheNames = RedisCacheNames.MATCHING_RECORD_DETAIL, allEntries = true),
            @CacheEvict(cacheNames = RedisCacheNames.DASHBOARD_STATS, allEntries = true)
    })
    public void unlockResult(Long id) {
        MatchingRecord record = getById(id);
        if (record == null) throw new BusinessException(ErrorCodeEnum.MATCHING_RECORD_NOT_FOUND);
        record.setIsLocked(0);
        record.setLockedBy(null);
        record.setLockedTime(null);
        updateById(record);
    }

    @Override
    @Caching(evict = {
            @CacheEvict(cacheNames = RedisCacheNames.MATCHING_RECORD_PAGE, allEntries = true),
            @CacheEvict(cacheNames = RedisCacheNames.MATCHING_RECORD_DETAIL, allEntries = true),
            @CacheEvict(cacheNames = RedisCacheNames.DASHBOARD_STATS, allEntries = true)
    })
    public boolean updatePublishStatus(Long id, PublishStatusEnum publishStatus) {
        MatchingRecord record = getById(id);
        if (record == null) {
            throw new BusinessException(ErrorCodeEnum.MATCHING_RECORD_NOT_FOUND);
        }
        // 【2026-09-04 需求变更：匹配结果不再需要审批】
        // 原实现在此强制 approvalStatus=APPROVED 才允许推送，等价于要求 HR 必须先
        // 「发起审批 → 审批通过」两步，与需求方「HR 审核匹配结果后推送结果」的描述不一致，
        // 也是「匹配结果没有推送入口」的直接原因（未审批通过的记录前端根本不渲染推送项）。
        // 现口径：HR 人工修改/确认匹配结果本身即为审核动作，确认后可直接推送。
        // 审批流（matching_approval_flow 与审批任务页）保留可用，但不再作为推送的前置条件。
        int target = publishStatus.getCode();
        if (Integer.valueOf(target).equals(record.getPublishStatus())) {
            return false;
        }
        record.setPublishStatus(target);
        boolean updated = updateById(record);
        if (updated) {
            syncPublishNotification(record, target == PublishStatusEnum.PUBLISHED.getCode());
        }
        return updated;
    }

    /**
     * 让「员工的通知」与「推送状态」保持一致。
     *
     * <p>口径（2026-09-04 需求）：HR 推送匹配结果时通知**该员工本人**；
     * 未推送时员工不能有任何相关通知；撤回后通知必须同步消失
     * （否则员工会看到一条点进去空空如也的通知 —— 员工侧只读页只展示已推送记录）。</p>
     *
     * <p>通知失败绝不阻断推送：{@code SysNotificationService.send} 内部已兜底，
     * 这里再包一层是防「查岗位名」这类前置步骤出问题牵连主流程。</p>
     */
    private void syncPublishNotification(MatchingRecord record, boolean published) {
        try {
            if (published) {
                notificationService.sendMatchingPublished(
                    record.getEmpId(), record.getId(), resolvePostName(record.getPostId()));
            } else {
                notificationService.withdrawMatchingPublished(record.getId());
            }
        } catch (Exception e) {
            log.warn("匹配结果推送通知同步失败（不阻断推送）: recordId={}, published={}",
                record.getId(), published, e);
        }
    }

    /** 岗位名仅用于通知文案，取不到时交给通知服务用占位文案，不影响推送 */
    private String resolvePostName(Long postId) {
        if (postId == null) {
            return null;
        }
        return dataQuery.findPostsForMatching(List.of(postId)).stream()
            .findFirst()
            .map(com.example.matching.dto.matching.MatchingPostProfile::postName)
            .orElse(null);
    }

    @Override
    @Cacheable(cacheNames = RedisCacheNames.MATCHING_RECORD_PAGE,
               key = "'page:' + #page.current + ':' + #page.size + ':' + (#postId != null ? #postId : '') + ':' + (#empId != null ? #empId : '') + ':' + (#matchStatus != null ? #matchStatus : '')", sync = true)
    public IPage<MatchingRecord> pageRecords(IPage<MatchingRecord> page, Long postId, Long empId, Integer matchStatus) {
        LambdaQueryWrapper<MatchingRecord> wrapper = Wrappers.<MatchingRecord>lambdaQuery();
        if (postId != null) wrapper.eq(MatchingRecord::getPostId, postId);
        if (empId != null) wrapper.eq(MatchingRecord::getEmpId, empId);
        if (matchStatus != null) wrapper.eq(MatchingRecord::getMatchStatus, matchStatus);
        wrapper.orderByDesc(MatchingRecord::getAiMatchScore);

        IPage<MatchingRecord> result = page(page, wrapper);
        return enrichNames(result);
    }

    @Override
    public IPage<MatchingRecord> pagePublishedRecords(IPage<MatchingRecord> page, Long empId) {
        return pagePublishedRecords(page, empId, null, null);
    }

    @Override
    public IPage<MatchingRecord> pagePublishedRecords(IPage<MatchingRecord> page, Long empId, Long postId, Integer matchStatus) {
        LambdaQueryWrapper<MatchingRecord> wrapper = Wrappers.<MatchingRecord>lambdaQuery()
                .eq(MatchingRecord::getEmpId, empId)
                // 员工侧可见闸门 = 仅看「已推送」。
                //
                // 【2026-09-04 修复】原实现要求 approvalStatus=APPROVED AND publishStatus=PUBLISHED，
                // 但同一轮需求已明确「匹配结果不再需要审批，HR 人工确认即为审核动作，可直接推送」，
                // 后端 updatePublishStatus 也同步移除了审批前置校验。这造成口径撕裂：
                // HR 点推送能把 publish_status 置 1，approval_status 仍是 0（未发起审批），
                // 于是记录永远不会出现在员工侧 —— 表现为「推送后员工看不到、学习路径也选不到」。
                // 现口径：推送是员工侧可见的唯一开关；审批流保留可用，但不参与可见性判定。
                .eq(MatchingRecord::getPublishStatus, PublishStatusEnum.PUBLISHED.getCode())
                .eq(MatchingRecord::getIsDeleted, 0);
        // 岗位与匹配状态只用于在本人数据内收窄，不影响归属范围
        if (postId != null) wrapper.eq(MatchingRecord::getPostId, postId);
        if (matchStatus != null) wrapper.eq(MatchingRecord::getMatchStatus, matchStatus);
        wrapper.orderByDesc(MatchingRecord::getUpdatedTime);
        return enrichNames(page(page, wrapper));
    }

    @Override
    public List<MatchingRecord> listPassedPosts(Long empId) {
        if (empId == null) {
            return List.of();
        }
        List<MatchingRecord> records = list(passedRecordsWrapper(empId)
                .orderByDesc(MatchingRecord::getUpdatedTime));
        enrichNames(records);
        // 同一岗位可能存在多条历史记录（重新匹配），只保留最新一条：records 已按 updatedTime 倒序
        Map<Long, MatchingRecord> latestByPost = new LinkedHashMap<>();
        for (MatchingRecord record : records) {
            latestByPost.putIfAbsent(record.getPostId(), record);
        }
        return new ArrayList<>(latestByPost.values());
    }

    @Override
    public List<MatchingRecord> listAllPassedRecords(int limit) {
        int safeLimit = Math.min(Math.max(limit, 1), 500);
        List<MatchingRecord> records = list(passedRecordsWrapper(null)
                .orderByDesc(MatchingRecord::getUpdatedTime)
                .last("LIMIT " + safeLimit));
        return enrichNames(records);
    }

    @Override
    public List<MatchingRecord> listRecentRecords(int limit) {
        int safeLimit = Math.min(Math.max(limit, 1), 500);
        // 不限匹配状态、不限是否已推送：HR 的邀约动作不应被匹配结论挡在门外
        //（匹配通过只作为「推荐」信号，见 CommunicationInterviewService#listCandidates）。
        List<MatchingRecord> records = list(Wrappers.<MatchingRecord>lambdaQuery()
                .eq(MatchingRecord::getIsDeleted, 0)
                .orderByDesc(MatchingRecord::getUpdatedTime)
                .last("LIMIT " + safeLimit));
        return enrichNames(records);
    }

    /**
     * 「已通过」统一口径：已推送给员工 + 未删除 + 匹配状态为强适配/适配（empId 为空则不限人员）。
     *
     * <p>同样不再要求 approvalStatus=APPROVED —— 推送即代表 HR 已确认，见
     * {@link #pagePublishedRecords} 的口径说明。
     */
    private LambdaQueryWrapper<MatchingRecord> passedRecordsWrapper(Long empId) {
        LambdaQueryWrapper<MatchingRecord> wrapper = Wrappers.<MatchingRecord>lambdaQuery()
                .eq(MatchingRecord::getPublishStatus, PublishStatusEnum.PUBLISHED.getCode())
                .eq(MatchingRecord::getIsDeleted, 0)
                .in(MatchingRecord::getMatchStatus,
                        MatchStatusEnum.STRONG_MATCH.getCode(), MatchStatusEnum.MATCH.getCode());
        if (empId != null) {
            wrapper.eq(MatchingRecord::getEmpId, empId);
        }
        return wrapper;
    }

    @Override
    public IPage<MatchingRecord> pageRecordsByCreator(IPage<MatchingRecord> page, Long postId,
                                                      Integer matchStatus, Long createdBy) {
        LambdaQueryWrapper<MatchingRecord> wrapper = Wrappers.<MatchingRecord>lambdaQuery();
        if (postId != null) wrapper.eq(MatchingRecord::getPostId, postId);
        if (matchStatus != null) wrapper.eq(MatchingRecord::getMatchStatus, matchStatus);
        if (createdBy != null) wrapper.eq(MatchingRecord::getCreatedBy, createdBy);
        wrapper.eq(MatchingRecord::getIsDeleted, 0);
        wrapper.orderByDesc(MatchingRecord::getCreatedTime);

        IPage<MatchingRecord> result = page(page, wrapper);
        return enrichNames(result);
    }

    private IPage<MatchingRecord> enrichNames(IPage<MatchingRecord> result) {
        enrichNames(result.getRecords());
        return result;
    }

    /** 补全人员 / 岗位名称（记录内的冗余字段可能为空，统一以主数据为准） */
    private List<MatchingRecord> enrichNames(List<MatchingRecord> records) {
        if (records == null || records.isEmpty()) {
            return records;
        }
        List<Long> empIds = records.stream().map(MatchingRecord::getEmpId).distinct().toList();
        List<Long> postIds = records.stream().map(MatchingRecord::getPostId).distinct().toList();

        Map<Long, String> empNameMap = new HashMap<>();
        for (com.example.matching.dto.matching.MatchingEmployeeProfile profile :
                dataQuery.findEmployeesForMatching(empIds)) {
            empNameMap.put(profile.empId(), profile.realName());
        }

        Map<Long, String> postNameMap = new HashMap<>();
        for (com.example.matching.dto.matching.MatchingPostProfile profile :
                dataQuery.findPostsForMatching(postIds)) {
            postNameMap.put(profile.postId(), profile.postName());
        }

        for (MatchingRecord record : records) {
            record.setEmpName(empNameMap.getOrDefault(record.getEmpId(), "Employee#" + record.getEmpId()));
            record.setPostName(postNameMap.getOrDefault(record.getPostId(), "Post#" + record.getPostId()));
        }
        return records;
    }

    @Override
    public String generateReport(Long id) {
        MatchingRecord record = getById(id);
        if (record == null) throw new BusinessException(ErrorCodeEnum.MATCHING_RECORD_NOT_FOUND);
        return record.getQuantitativeReport();
    }

    @Override
    @Cacheable(cacheNames = RedisCacheNames.MATCHING_AI_REPORT, key = "#id", sync = true)
    public String generateAiReport(Long id) {
        return matchingAiAnalysisService.generateAiReport(id);
    }

    @Override
    @Transactional
    @Caching(evict = {
            @CacheEvict(cacheNames = RedisCacheNames.MATCHING_RECORD_PAGE, allEntries = true),
            @CacheEvict(cacheNames = RedisCacheNames.MATCHING_RECORD_DETAIL, allEntries = true),
            @CacheEvict(cacheNames = RedisCacheNames.MATCHING_AI_REPORT, key = "#id"),
            @CacheEvict(cacheNames = RedisCacheNames.DASHBOARD_STATS, allEntries = true)
    })
    public void deleteRecord(Long id) {
        matchingApprovalFlowMapper.delete(
                Wrappers.<MatchingApprovalFlow>lambdaQuery()
                        .eq(MatchingApprovalFlow::getMatchingRecordId, id));
        feedbackDatasetMapper.delete(
                Wrappers.<MatchingFeedbackDataset>lambdaQuery()
                        .eq(MatchingFeedbackDataset::getMatchingRecordId, id));
        // 记录被删掉后，员工那条「收到新的匹配结果」通知会指向一个不存在的岗位结果
        // （点进去是空的）→ 一并收回，保持「通知存在 ⟺ 记录已推送且未删除」。
        withdrawNotificationsQuietly(List.of(id));
        removeById(id);
    }

    @Override
    @Transactional
    @Caching(evict = {
            @CacheEvict(cacheNames = RedisCacheNames.MATCHING_RECORD_PAGE, allEntries = true),
            @CacheEvict(cacheNames = RedisCacheNames.MATCHING_RECORD_DETAIL, allEntries = true),
            @CacheEvict(cacheNames = RedisCacheNames.DASHBOARD_STATS, allEntries = true)
    })
    public int deleteByBatchNo(String batchNo) {
        if (batchNo == null || batchNo.isBlank()) {
            return 0;
        }
        // 收集该批次记录ID，级联删除子表后再逻辑删除记录本身
        List<Long> recordIds = list(Wrappers.<MatchingRecord>lambdaQuery()
                .select(MatchingRecord::getId)
                .eq(MatchingRecord::getBatchNo, batchNo))
                .stream().map(MatchingRecord::getId).toList();
        if (recordIds.isEmpty()) {
            return 0;
        }
        matchingApprovalFlowMapper.delete(
                Wrappers.<MatchingApprovalFlow>lambdaQuery()
                        .in(MatchingApprovalFlow::getMatchingRecordId, recordIds));
        feedbackDatasetMapper.delete(
                Wrappers.<MatchingFeedbackDataset>lambdaQuery()
                        .in(MatchingFeedbackDataset::getMatchingRecordId, recordIds));
        withdrawNotificationsQuietly(recordIds);
        return remove(Wrappers.<MatchingRecord>lambdaQuery()
                .eq(MatchingRecord::getBatchNo, batchNo)) ? recordIds.size() : 0;
    }

    /** 批量收回这些记录可能存在的「已推送」通知；失败只记日志，不影响删除结果 */
    private void withdrawNotificationsQuietly(List<Long> recordIds) {
        if (recordIds == null || recordIds.isEmpty()) {
            return;
        }
        for (Long recordId : recordIds) {
            try {
                notificationService.withdrawMatchingPublished(recordId);
            } catch (Exception e) {
                log.warn("删除匹配记录时收回通知失败（不阻断删除）: recordId={}", recordId, e);
            }
        }
    }

    @Override
    @Transactional
    @Caching(evict = {
            @CacheEvict(cacheNames = RedisCacheNames.MATCHING_RECORD_PAGE, allEntries = true),
            @CacheEvict(cacheNames = RedisCacheNames.MATCHING_RECORD_DETAIL, allEntries = true),
            @CacheEvict(cacheNames = RedisCacheNames.DASHBOARD_STATS, allEntries = true)
    })
    public boolean retryAiScoring(Long id) {
        // FAILED/PENDING -> PENDING（attempt=0、nextRetryAt=now），由 AI 评分恢复调度器自动重投。
        // 条件更新保证幂等：评分中/已完成/已锁定记录不可重复触发。
        int rows = baseMapper.update(null, Wrappers.<MatchingRecord>lambdaUpdate()
                .eq(MatchingRecord::getId, id)
                .in(MatchingRecord::getAiScoringStatus,
                        com.example.matching.common.constant.AiConstant.AI_SCORING_FAILED,
                        com.example.matching.common.constant.AiConstant.AI_SCORING_PENDING)
                .eq(MatchingRecord::getIsLocked, 0)
                .set(MatchingRecord::getAiScoringStatus, com.example.matching.common.constant.AiConstant.AI_SCORING_PENDING)
                .set(MatchingRecord::getAiScoringAttemptCount, 0)
                .set(MatchingRecord::getAiScoringFailReason, null)
                .set(MatchingRecord::getAiScoringNextRetryAt, LocalDateTime.now()));
        if (rows == 1) {
            log.info("AI评分已重置待重试: recordId={}", id);
        }
        return rows == 1;
    }

    @Override
    @Cacheable(cacheNames = RedisCacheNames.MATCHING_RECORD_DETAIL, key = "#id", sync = true)
    public MatchingRecord getDetailById(Long id) {
        MatchingRecord record = getById(id);
        if (record == null) return null;

        BigDecimal evidenceScore = record.getEvidenceCredibilityScore();
        if (evidenceScore == null) {
            List<com.example.matching.dto.matching.MatchingAbilitySnapshot> abilities =
                    dataQuery.batchLoadAbilitySnapshots(List.of(record.getEmpId()))
                            .getOrDefault(record.getEmpId(), List.of());
            evidenceScore = evidenceScoreCalculator.computeEvidenceScoreFromSnapshots(abilities);
            record.setEvidenceScore(evidenceScore);
        } else {
            record.setEvidenceScore(evidenceScore);
        }

        populateTransientScoresFromReport(record);

        com.example.matching.dto.matching.MatchingEmployeeProfile employee =
                dataQuery.findEmployeeForMatching(record.getEmpId());
        if (employee != null) record.setEmpName(employee.realName());
        com.example.matching.dto.matching.MatchingPostProfile post = dataQuery.findPostForMatching(record.getPostId());
        if (post != null) record.setPostName(post.postName());

        return record;
    }

    @Override
    public MatchingRecord getPublishedDetailById(Long id, Long empId) {
        MatchingRecord record = getOne(Wrappers.<MatchingRecord>lambdaQuery()
                .eq(MatchingRecord::getId, id)
                .eq(MatchingRecord::getEmpId, empId)
                // 与列表闸门保持一致：未推送的记录，员工即使拿到 recordId 也不可读。
                // 同样只看 publishStatus（推送即 HR 已确认），不再叠加审批状态，见 pagePublishedRecords。
                .eq(MatchingRecord::getPublishStatus, PublishStatusEnum.PUBLISHED.getCode())
                .eq(MatchingRecord::getIsDeleted, 0)
                .last("LIMIT 1"));
        return record == null ? null : getDetailById(record.getId());
    }

    private void populateTransientScoresFromReport(MatchingRecord record) {
        String reportJson = record.getQuantitativeReport();
        if (reportJson == null || reportJson.isBlank()) return;
        try {
            Map<String, Object> report = objectMapper.readValue(reportJson, new TypeReference<>() {});
            if (record.getProfileSemanticScore() == null) {
                Object val = report.get("profileSemanticScore");
                if (val instanceof Number n) record.setProfileSemanticScore(new BigDecimal(n.toString()));
            }
            if (record.getRankScore() == null) {
                Object val = report.get("rankScore");
                if (val instanceof Number n) record.setRankScore(new BigDecimal(n.toString()));
            }
            if (record.getQualityAdjustment() == null) {
                Object val = report.get("qualityAdjustment");
                if (val instanceof Number n) record.setQualityAdjustment(new BigDecimal(n.toString()));
            }
            if (record.getFeedbackAdjustment() == null) {
                Object val = report.get("feedbackAdjustment");
                if (val instanceof Number n) record.setFeedbackAdjustment(new BigDecimal(n.toString()));
            }
            if (record.getCalibrationAdjustment() == null) {
                Object val = report.get("calibrationAdjustment");
                if (val instanceof Number n) record.setCalibrationAdjustment(new BigDecimal(n.toString()));
            }
        } catch (Exception e) {
            log.debug("Failed to parse transient scores from report: recordId={}", record.getId());
        }
    }
}


