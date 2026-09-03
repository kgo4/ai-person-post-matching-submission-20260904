package com.example.matching.service.matching;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.example.matching.common.constant.AiConstant;
import com.example.matching.entity.matching.MatchingRecord;
import com.example.matching.mapper.matching.MatchingRecordMapper;
import com.example.matching.service.common.DistributedLockService;
import com.example.matching.schedule.SchedulerMetrics;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class AiScoringRecoveryScheduler {

    private static final String LOCK_NAME = "ai-scoring-recovery";

    private final MatchingRecordMapper matchingRecordMapper;
    private final MatchingAiScoringStateMachine stateMachine;
    private final MatchingAiScoringRetryService retryService;
    private final DistributedLockService distributedLockService;
    private final SchedulerMetrics schedulerMetrics;

    @org.springframework.beans.factory.annotation.Autowired(required = false)
    private com.example.matching.schedule.ScheduledTaskRunner taskRunner;

    @org.springframework.beans.factory.annotation.Value("${ai.scoring.recovery-timeout-minutes:30}")
    private long recoveryTimeoutMinutes;

    @Scheduled(fixedDelay = 30_000)
    public void recoverStalledRecords() {
        if (taskRunner != null) {
            taskRunner.run("ai_scoring_recovery", this::recoverStalledRecordsInternal);
        } else {
            recoverStalledRecordsInternal();
        }
    }

    private void recoverStalledRecordsInternal() {
        DistributedLockService.LockHandle lock = null;
        try {
            lock = distributedLockService.tryAcquire(LOCK_NAME);
            if (lock == null) {
                return;
            }
                long timeout = Math.max(5L, recoveryTimeoutMinutes);
                LocalDateTime now = LocalDateTime.now();

                LambdaQueryWrapper<MatchingRecord> pendingWrapper = new LambdaQueryWrapper<>();
            pendingWrapper.eq(MatchingRecord::getAiScoringStatus, AiConstant.AI_SCORING_PENDING)
                    .and(wrapper -> wrapper.isNull(MatchingRecord::getAiScoringNextRetryAt)
                            .or().le(MatchingRecord::getAiScoringNextRetryAt, now))
                    .last("LIMIT 100");
            List<MatchingRecord> pendingRecords = matchingRecordMapper.selectList(pendingWrapper);

            for (MatchingRecord record : pendingRecords) {
                if (retryService.submitRetry(record.getId())) {
                    log.info("Resubmitted pending AI scoring: record={}", record.getId());
                }
            }

            LambdaQueryWrapper<MatchingRecord> processingWrapper = new LambdaQueryWrapper<>();
            processingWrapper.eq(MatchingRecord::getAiScoringStatus, AiConstant.AI_SCORING_PROCESSING)
                    .and(wrapper -> wrapper.isNull(MatchingRecord::getAiScoringLastAttemptAt)
                            .or().le(MatchingRecord::getAiScoringLastAttemptAt, now.minusMinutes(timeout)));
            List<MatchingRecord> stalledRecords = matchingRecordMapper.selectList(processingWrapper);

            for (MatchingRecord record : stalledRecords) {
                String reason = "AI scoring timeout (last attempt: " + record.getAiScoringLastAttemptAt() + ")";
                stateMachine.failIfProcessing(record.getId(), reason,
                        record.getAiScoringAttemptCount() != null ? record.getAiScoringAttemptCount() : 0);
                log.warn("Requeued stalled AI scoring: record={}", record.getId());
            }
        } catch (Exception e) {
            log.error("AI scoring recovery run failed", e);
            schedulerMetrics.recordFailure("ai_scoring_recovery");
        } finally {
            if (lock != null) {
                lock.close();
            }
        }
    }
}
