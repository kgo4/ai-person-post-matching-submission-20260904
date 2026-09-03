package com.example.matching.schedule;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.example.matching.entity.employee.EmpVideoInterviewSession;
import com.example.matching.event.InterviewWsEvent;
import com.example.matching.event.CapabilityStageLifecycleEvent;
import com.example.matching.mapper.employee.EmpVideoInterviewSessionMapper;
import com.example.matching.port.assessment.CapabilityStageLifecycleEventPublisher;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 面试后 AI 分析僵尸恢复扫描器
 * <p>
 * 面试结束事件驱动的异步分析（InterviewPostAnalysisListener）可能因 LLM 抖动/异常中断，
 * 会话停留在 ANALYZING(4) 或分析失败后回退到 FINISHED(3) 而报告永久缺失。本调度器每 5 分钟扫描超过 15 分钟的此类会话：
 * <ul>
 *   <li>重试次数未达上限：置回 FINISHED(3) 并重新发布面试完成事件驱动重跑</li>
 *   <li>重试次数已达上限：置 FAILED(7) 终态并记录失败原因（可人工重试）</li>
 * </ul>
 */
@Slf4j
@Component
public class InterviewAnalysisRecoveryScheduler {

    /** ANALYZING 超过该时长视为僵尸（LLM 长分析一般分钟级，15 分钟兜底） */
    private static final long STALE_MINUTES = 15;

    /** 最大自动重试次数 */
    private static final int MAX_RETRY_COUNT = 3;

    private final EmpVideoInterviewSessionMapper sessionMapper;
    private final ApplicationEventPublisher eventPublisher;
    private final CapabilityStageLifecycleEventPublisher lifecycleEventPublisher;

    @org.springframework.beans.factory.annotation.Autowired
    public InterviewAnalysisRecoveryScheduler(
            EmpVideoInterviewSessionMapper sessionMapper,
            ApplicationEventPublisher eventPublisher,
            SchedulerMetrics schedulerMetrics,
            CapabilityStageLifecycleEventPublisher lifecycleEventPublisher) {
        this.sessionMapper = sessionMapper;
        this.eventPublisher = eventPublisher;
        this.schedulerMetrics = schedulerMetrics;
        this.lifecycleEventPublisher = lifecycleEventPublisher;
    }

    /** Compatibility constructor for non-workflow scheduler fixtures. */
    public InterviewAnalysisRecoveryScheduler(
            EmpVideoInterviewSessionMapper sessionMapper,
            ApplicationEventPublisher eventPublisher,
            SchedulerMetrics schedulerMetrics) {
        this(sessionMapper, eventPublisher, schedulerMetrics, null);
    }

    @org.springframework.beans.factory.annotation.Autowired(required = false)
    private ScheduledTaskRunner taskRunner;

    @Scheduled(fixedDelay = 300_000, initialDelay = 60_000)
    public void recoverStalledAnalysis() {
        if (taskRunner != null) {
            taskRunner.run("interview_analysis_recovery", this::recoverStalledAnalysisInternal);
        } else {
            recoverStalledAnalysisInternal();
        }
    }

    private void recoverStalledAnalysisInternal() {
        LocalDateTime before = LocalDateTime.now().minusMinutes(STALE_MINUTES);
        List<EmpVideoInterviewSession> stalled = sessionMapper.selectList(
                Wrappers.<EmpVideoInterviewSession>lambdaQuery()
                        .eq(EmpVideoInterviewSession::getStatus, 4) // ANALYZING
                        .le(EmpVideoInterviewSession::getUpdatedTime, before)
                        .last("LIMIT 100"));
        List<EmpVideoInterviewSession> failedFinished = sessionMapper.selectList(
                Wrappers.<EmpVideoInterviewSession>lambdaQuery()
                        .eq(EmpVideoInterviewSession::getStatus, 3) // FINISHED
                        .isNotNull(EmpVideoInterviewSession::getAnalysisFailedReason)
                        .le(EmpVideoInterviewSession::getUpdatedTime, before)
                        .last("LIMIT 100"));
        // M15：卡死评估会话（status=2 + EVALUATING_ANSWER + 超时）
        List<EmpVideoInterviewSession> stuckEvaluating = sessionMapper.selectList(
                Wrappers.<EmpVideoInterviewSession>lambdaQuery()
                        .eq(EmpVideoInterviewSession::getStatus, 2) // IN_PROGRESS
                        .eq(EmpVideoInterviewSession::getConversationState,
                                com.example.matching.entity.interview.InterviewConversationState.EVALUATING_ANSWER.name())
                        .le(EmpVideoInterviewSession::getUpdatedTime, before)
                        .last("LIMIT 100"));
        stalled = new java.util.ArrayList<>(stalled);
        stalled.addAll(failedFinished);
        for (EmpVideoInterviewSession session : stalled) {
            recover(session);
        }
        for (EmpVideoInterviewSession session : stuckEvaluating) {
            recoverStuckEvaluating(session);
        }
        if (!stalled.isEmpty() || !stuckEvaluating.isEmpty()) {
            log.warn("面试后分析/评估僵尸扫描完成: recoveredOrFailed={}, stuckEvaluating={}",
                    stalled.size(), stuckEvaluating.size());
        }
    }

    /**
     * M15：卡死评估会话恢复。
     * <p>
     * 优先恢复到可重新作答状态（EVALUATING_ANSWER -> ANSWERING_PRESET）；
     * 恢复失败或重试耗尽则置 FAILED 终态，确保资格检查（findActiveSession）
     * 不再永久阻塞员工创建新面试。
     */
    private void recoverStuckEvaluating(EmpVideoInterviewSession session) {
        Long sessionId = session.getId();
        int retryCount = session.getAnalysisRetryCount() != null ? session.getAnalysisRetryCount() : 0;

        if (retryCount < MAX_RETRY_COUNT) {
            int rows = sessionMapper.update(null, Wrappers.<EmpVideoInterviewSession>lambdaUpdate()
                    .eq(EmpVideoInterviewSession::getId, sessionId)
                    .eq(EmpVideoInterviewSession::getStatus, 2)
                    .eq(EmpVideoInterviewSession::getConversationState,
                            com.example.matching.entity.interview.InterviewConversationState.EVALUATING_ANSWER.name())
                    .set(EmpVideoInterviewSession::getConversationState,
                            com.example.matching.entity.interview.InterviewConversationState.ANSWERING_PRESET.name())
                    .set(EmpVideoInterviewSession::getAnalysisRetryCount, retryCount + 1));
            if (rows == 1) {
                log.warn("卡死评估会话已恢复到可重新作答: sessionId={}, retry={}", sessionId, retryCount + 1);
                eventPublisher.publishEvent(InterviewWsEvent.sendMessage(String.valueOf(sessionId), "SEND_MESSAGE",
                        "检测到回答评估超时，请重新作答当前问题。"));
            }
            return;
        }

        // 恢复失败/重试耗尽：置 FAILED 终态，确保资格检查不再永久阻塞员工
        int rows = sessionMapper.update(null, Wrappers.<EmpVideoInterviewSession>lambdaUpdate()
                .eq(EmpVideoInterviewSession::getId, sessionId)
                .eq(EmpVideoInterviewSession::getStatus, 2)
                .set(EmpVideoInterviewSession::getStatus, 7) // STATUS_FAILED
                .set(EmpVideoInterviewSession::getErrorMessage, "面试回答评估卡死，恢复失败"));
        if (rows == 1) {
            log.error("卡死评估会话恢复失败，置 FAILED 终态: sessionId={}, retries={}", sessionId, retryCount);
            schedulerMetrics.recordFailure("interview_evaluating_recovery");
        }
    }

    private void recover(EmpVideoInterviewSession session) {
        int retryCount = session.getAnalysisRetryCount() != null ? session.getAnalysisRetryCount() : 0;
        Long sessionId = session.getId();

        if (retryCount >= MAX_RETRY_COUNT) {
            // 重试耗尽：置 FAILED(7) 终态，由人工通过 REST analyze() 重试
            int rows = sessionMapper.update(null, Wrappers.<EmpVideoInterviewSession>lambdaUpdate()
                    .eq(EmpVideoInterviewSession::getId, sessionId)
                    .in(EmpVideoInterviewSession::getStatus, 3, 4)
                    .set(EmpVideoInterviewSession::getStatus, 7) // STATUS_FAILED
                    .set(EmpVideoInterviewSession::getErrorMessage, "面试后分析重试耗尽")
                    .set(EmpVideoInterviewSession::getAnalysisFailedReason,
                            session.getAnalysisFailedReason() != null
                                    ? session.getAnalysisFailedReason() : "面试后分析重试耗尽"));
            if (rows == 1) {
                log.error("面试后分析重试耗尽，置 FAILED 终态: sessionId={}, retries={}", sessionId, retryCount);
            schedulerMetrics.recordFailure("interview_analysis_recovery");
            publishFinalFailure(session);
            }
            return;
        }

        // 未达上限：ANALYZING(4) -> FINISHED(3) 并重新发布事件重跑
        int rows = sessionMapper.update(null, Wrappers.<EmpVideoInterviewSession>lambdaUpdate()
                .eq(EmpVideoInterviewSession::getId, sessionId)
                .in(EmpVideoInterviewSession::getStatus, 3, 4)
                .set(EmpVideoInterviewSession::getStatus, 3) // STATUS_FINISHED
                .set(EmpVideoInterviewSession::getAnalysisRetryCount, retryCount + 1)
                .set(EmpVideoInterviewSession::getErrorMessage, null));
        if (rows == 1) {
            log.warn("面试后分析僵尸恢复，重新调度分析: sessionId={}, retry={}", sessionId, retryCount + 1);
            eventPublisher.publishEvent(new com.example.matching.event.InterviewFinishedEvent(sessionId));
        }
    }

    private void publishFinalFailure(EmpVideoInterviewSession session) {
        if (lifecycleEventPublisher == null || session == null || session.getWorkflowId() == null) {
            return;
        }
        lifecycleEventPublisher.publish(CapabilityStageLifecycleEvent.failedFinal(
                session.getWorkflowId(), null, "AI_INTERVIEW", "AI_INTERVIEW", session.getId(),
                "AI_INTERVIEW_ANALYSIS_ZOMBIE", "面试后分析重试已耗尽"));
    }

    private final com.example.matching.schedule.SchedulerMetrics schedulerMetrics;
}
