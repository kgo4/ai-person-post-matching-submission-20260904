package com.example.matching.service.matching;

import com.example.matching.common.constant.AiConstant;
import com.example.matching.dto.matching.MatchingAbilitySnapshot;
import com.example.matching.dto.matching.MatchingEmployeeProfile;
import com.example.matching.dto.matching.MatchingPostProfile;
import com.example.matching.dto.matching.MatchingRequirementSnapshot;
import com.example.matching.entity.matching.MatchingRecord;
import com.example.matching.mapper.matching.MatchingRecordMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

/** Replays an already-created L3 scoring record without creating another matching record. */
@Slf4j
@Service
@RequiredArgsConstructor
public class MatchingAiScoringRetryService {

    private final MatchingRecordMapper matchingRecordMapper;
    private final MatchingAiScoringStateMachine stateMachine;
    private final MatchingDataQueryService dataQuery;
    private final MatchingAiAnalysisService matchingAiAnalysisService;
    private final MatchingAlgorithmService matchingAlgorithmService;
    private final MatchingScoreService matchingScoreService;
    private final MatchingTrainingWeightProfileStore weightProfileStore;
    private final MatchingEvidenceScoreCalculator evidenceScoreCalculator;
    private final MatchingCacheInvalidator cacheInvalidator;
    @Qualifier("aiTaskExecutor")
    private final Executor aiTaskExecutor;

    /**
     * Atomically claims an eligible record before sending it to the bounded AI executor.
     * The caller may invoke this repeatedly; only one worker can process a record at a time.
     */
    public boolean submitRetry(Long recordId) {
        MatchingRecord record = matchingRecordMapper.selectById(recordId);
        if (record == null || !AiConstant.AI_SCORING_PENDING.equals(record.getAiScoringStatus())) {
            return false;
        }
        if (!stateMachine.claimForProcessing(recordId)) {
            return false;
        }
        try {
            CompletableFuture.runAsync(() -> replayClaimedRecord(recordId), aiTaskExecutor);
            return true;
        } catch (java.util.concurrent.RejectedExecutionException e) {
            int attempts = record.getAiScoringAttemptCount() == null ? 0 : record.getAiScoringAttemptCount();
            stateMachine.failIfProcessing(recordId, "AI retry executor rejected task: " + e.getMessage(), attempts);
            log.warn("AI retry executor rejected record={}", recordId);
            return false;
        }
    }

    private void replayClaimedRecord(Long recordId) {
        MatchingRecord record = matchingRecordMapper.selectById(recordId);
        if (record == null) {
            return;
        }
        record.setAiScoringStatus(AiConstant.AI_SCORING_PROCESSING);
        try {
            MatchingEmployeeProfile employee = dataQuery.findEmployeeForMatching(record.getEmpId());
            MatchingPostProfile post = dataQuery.findPostForMatching(record.getPostId());
            if (employee == null || post == null) {
                throw new IllegalStateException("matching record references a missing employee or post");
            }

            List<MatchingAbilitySnapshot> abilities = dataQuery.batchLoadAbilitySnapshots(List.of(record.getEmpId()))
                    .getOrDefault(record.getEmpId(), List.of());
            List<MatchingRequirementSnapshot> requirements = dataQuery.findPostRequirements(record.getPostId());
            Map<Long, String> tagNameMap = new HashMap<>();
            for (MatchingRequirementSnapshot req : requirements) {
                if (req.tagId() != null && req.abilityName() != null) {
                    tagNameMap.putIfAbsent(req.tagId(), req.abilityName());
                }
            }
            Map<Long, BigDecimal> vectorScoreMap = record.getVectorScore() == null
                    ? Map.of()
                    : Map.of(record.getEmpId(), record.getVectorScore());

            matchingAiAnalysisService.scoreClaimedCandidate(record, Map.of(record.getEmpId(), abilities), vectorScoreMap,
                    nonNullScore(record.getModelQualityCoefficient()), nonNullScore(record.getFeedbackCalibration()),
                    Map.of(record.getEmpId(), employee), post, requirements, tagNameMap,
                    matchingAlgorithmService, matchingScoreService, weightProfileStore);
        } catch (Exception e) {
            int attempts = record.getAiScoringAttemptCount() == null ? 0 : record.getAiScoringAttemptCount();
            stateMachine.failIfProcessing(recordId, failureReason(e), attempts);
        } finally {
            cacheInvalidator.evictAfterAiScore(recordId);
        }
    }

    private static BigDecimal nonNullScore(BigDecimal score) {
        return score == null ? BigDecimal.ZERO : score;
    }

    private static String failureReason(Exception e) {
        String reason = e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage();
        return reason.length() <= 500 ? reason : reason.substring(0, 500);
    }
}
