package com.example.matching.service.matching.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
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
        if (result.getRecords() != null && !result.getRecords().isEmpty()) {
            List<Long> empIds = result.getRecords().stream().map(MatchingRecord::getEmpId).distinct().toList();
            List<Long> postIds = result.getRecords().stream().map(MatchingRecord::getPostId).distinct().toList();

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

            for (MatchingRecord record : result.getRecords()) {
                record.setEmpName(empNameMap.getOrDefault(record.getEmpId(), "Employee#" + record.getEmpId()));
                record.setPostName(postNameMap.getOrDefault(record.getPostId(), "Post#" + record.getPostId()));
            }
        }
        return result;
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
        return remove(Wrappers.<MatchingRecord>lambdaQuery()
                .eq(MatchingRecord::getBatchNo, batchNo)) ? recordIds.size() : 0;
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
