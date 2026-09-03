package com.example.matching.service.matching;

import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.example.matching.common.constant.AiConstant;
import com.example.matching.entity.matching.MatchingRecord;
import com.example.matching.mapper.matching.MatchingRecordMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Slf4j
@Component
@RequiredArgsConstructor
public class MatchingAiScoringStateMachine {

    private final MatchingRecordMapper matchingRecordMapper;

    @Transactional
    public boolean claimForProcessing(Long recordId) {
        // 已锁定记录不进入 AI 评分（人工结果优先）
        LambdaUpdateWrapper<MatchingRecord> wrapper = new LambdaUpdateWrapper<>();
        wrapper.eq(MatchingRecord::getId, recordId)
                .eq(MatchingRecord::getAiScoringStatus, AiConstant.AI_SCORING_PENDING)
                .eq(MatchingRecord::getIsLocked, 0);
        MatchingRecord update = new MatchingRecord();
        update.setAiScoringStatus(AiConstant.AI_SCORING_PROCESSING);
        update.setAiScoringLastAttemptAt(LocalDateTime.now());
        int rows = matchingRecordMapper.update(update, wrapper);
        return rows > 0;
    }

    @Transactional
    public boolean completeIfProcessing(Long recordId, MatchingAiScoringResult result) {
        // 双保险：评分期间若记录被 HR 锁定/人工修改，AI 结果不得覆盖人工结果
        MatchingRecord current = matchingRecordMapper.selectById(recordId);
        if (current != null && current.getIsLocked() != null && current.getIsLocked() == 1) {
            log.info("AI scoring skipped for locked record: recordId={}", recordId);
            skipIfProcessing(recordId, "AI scoring skipped: record locked by reviewer");
            return false;
        }
        LambdaUpdateWrapper<MatchingRecord> wrapper = new LambdaUpdateWrapper<>();
        wrapper.eq(MatchingRecord::getId, recordId)
                .eq(MatchingRecord::getAiScoringStatus, AiConstant.AI_SCORING_PROCESSING)
                .eq(MatchingRecord::getIsLocked, 0);
        MatchingRecord update = new MatchingRecord();
        update.setAiScoringStatus(AiConstant.AI_SCORING_COMPLETED);
        update.setAiScoringFailReason(null);
        update.setAiScoringNextRetryAt(null);
        if (result.llmScore() != null) update.setLlmScore(result.llmScore());
        if (result.finalScore() != null) update.setAiMatchScore(result.finalScore());
        if (result.evidenceScore() != null) update.setEvidenceCredibilityScore(result.evidenceScore());
        if (result.rankScore() != null) update.setRankScore(result.rankScore());
        if (result.qualityAdjustment() != null) update.setQualityAdjustment(result.qualityAdjustment());
        if (result.feedbackAdjustment() != null) update.setFeedbackAdjustment(result.feedbackAdjustment());
        if (result.calibrationAdjustment() != null) update.setCalibrationAdjustment(result.calibrationAdjustment());
        if (result.scoreBreakdownJson() != null) update.setScoreBreakdownJson(result.scoreBreakdownJson());
        update.setAiAnalysisReport(result.aiAnalysisReport());
        update.setQuantitativeReport(result.quantitativeReport());
        update.setMatchStatus(result.matchStatus());
        update.setScreeningLevel(3);
        int rows = matchingRecordMapper.update(update, wrapper);
        return rows > 0;
    }

    @Transactional
    public boolean failIfProcessing(Long recordId, String reason, int attemptCount) {
        int nextAttempt = attemptCount + 1;
        boolean retryable = nextAttempt < AiConstant.AI_SCORING_MAX_RETRIES;

        LambdaUpdateWrapper<MatchingRecord> wrapper = new LambdaUpdateWrapper<>();
        wrapper.eq(MatchingRecord::getId, recordId)
                .eq(MatchingRecord::getAiScoringStatus, AiConstant.AI_SCORING_PROCESSING);

        MatchingRecord update = new MatchingRecord();
        update.setAiScoringAttemptCount(nextAttempt);
        update.setAiScoringFailReason(reason);

        update.setAiScoringStatus(retryable
                ? AiConstant.AI_SCORING_PENDING
                : AiConstant.AI_SCORING_FAILED);
        long delayMs = Math.min(AiConstant.AI_SCORING_MAX_RETRY_DELAY_MS,
                AiConstant.AI_SCORING_RETRY_DELAY_MS * (1L << Math.min(nextAttempt - 1, 4)));
        update.setAiScoringNextRetryAt(retryable
                ? LocalDateTime.now().plusNanos(delayMs * 1_000_000)
                : null);

        matchingRecordMapper.update(update, wrapper);
        return retryable;
    }

    @Transactional
    public boolean skipIfPending(Long recordId) {
        LambdaUpdateWrapper<MatchingRecord> wrapper = new LambdaUpdateWrapper<>();
        wrapper.eq(MatchingRecord::getId, recordId)
                .eq(MatchingRecord::getAiScoringStatus, AiConstant.AI_SCORING_PENDING);
        MatchingRecord update = new MatchingRecord();
        update.setAiScoringStatus(AiConstant.AI_SCORING_SKIPPED);
        update.setAiScoringNextRetryAt(null);
        return matchingRecordMapper.update(update, wrapper) > 0;
    }

    /**
     * PROCESSING -> SKIPPED：用于记录被锁定等人工接管场景，避免恢复调度将其误判为失败重试。
     */
    @Transactional
    public boolean skipIfProcessing(Long recordId, String reason) {
        LambdaUpdateWrapper<MatchingRecord> wrapper = new LambdaUpdateWrapper<>();
        wrapper.eq(MatchingRecord::getId, recordId)
                .eq(MatchingRecord::getAiScoringStatus, AiConstant.AI_SCORING_PROCESSING);
        MatchingRecord update = new MatchingRecord();
        update.setAiScoringStatus(AiConstant.AI_SCORING_SKIPPED);
        update.setAiScoringFailReason(reason);
        update.setAiScoringNextRetryAt(null);
        return matchingRecordMapper.update(update, wrapper) > 0;
    }

    @Transactional
    public void failPending(Long recordId, String reason, int attemptCount) {
        LambdaUpdateWrapper<MatchingRecord> wrapper = new LambdaUpdateWrapper<>();
        wrapper.eq(MatchingRecord::getId, recordId)
                .eq(MatchingRecord::getAiScoringStatus, AiConstant.AI_SCORING_PENDING);
        MatchingRecord update = new MatchingRecord();
        update.setAiScoringStatus(AiConstant.AI_SCORING_FAILED);
        update.setAiScoringAttemptCount(attemptCount + 1);
        update.setAiScoringFailReason(reason);
        update.setAiScoringNextRetryAt(null);
        matchingRecordMapper.update(update, wrapper);
    }
}
