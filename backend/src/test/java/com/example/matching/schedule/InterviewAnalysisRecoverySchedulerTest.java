package com.example.matching.schedule;

import com.example.matching.entity.employee.EmpVideoInterviewSession;
import com.example.matching.entity.interview.InterviewConversationState;
import com.example.matching.mapper.employee.EmpVideoInterviewSessionMapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class InterviewAnalysisRecoverySchedulerTest {

    @BeforeAll
    static void initMybatisPlusLambdaCache() {
        TableInfoHelper.initTableInfo(
                new org.apache.ibatis.builder.MapperBuilderAssistant(
                        new com.baomidou.mybatisplus.core.MybatisConfiguration(), ""),
                EmpVideoInterviewSession.class);
    }

    @Test
    void recoversFinishedSessionWhosePreviousAnalysisFailed() {
        EmpVideoInterviewSessionMapper sessionMapper = mock(EmpVideoInterviewSessionMapper.class);
        ApplicationEventPublisher eventPublisher = mock(ApplicationEventPublisher.class);
        SchedulerMetrics schedulerMetrics = mock(SchedulerMetrics.class);
        InterviewAnalysisRecoveryScheduler scheduler =
                new InterviewAnalysisRecoveryScheduler(sessionMapper, eventPublisher, schedulerMetrics);

        EmpVideoInterviewSession failedSession = new EmpVideoInterviewSession();
        failedSession.setId(71L);
        failedSession.setStatus(3);
        failedSession.setUpdatedTime(LocalDateTime.now().minusMinutes(20));
        failedSession.setAnalysisFailedReason("LLM timeout");
        failedSession.setAnalysisRetryCount(0);

        // 三次扫描：stale ANALYZING（空）、failed FINISHED、stuck EVALUATING（空）
        when(sessionMapper.selectList(any())).thenReturn(List.of(), List.of(failedSession), List.of());
        when(sessionMapper.update(any(), any())).thenReturn(1);

        scheduler.recoverStalledAnalysis();

        verify(sessionMapper, times(3)).selectList(any());
        verify(eventPublisher).publishEvent(any(com.example.matching.event.InterviewFinishedEvent.class));
    }

    @Test
    void recoversStuckEvaluatingSessionToAnswerableState() {
        EmpVideoInterviewSessionMapper sessionMapper = mock(EmpVideoInterviewSessionMapper.class);
        ApplicationEventPublisher eventPublisher = mock(ApplicationEventPublisher.class);
        SchedulerMetrics schedulerMetrics = mock(SchedulerMetrics.class);
        InterviewAnalysisRecoveryScheduler scheduler =
                new InterviewAnalysisRecoveryScheduler(sessionMapper, eventPublisher, schedulerMetrics);

        EmpVideoInterviewSession stuck = new EmpVideoInterviewSession();
        stuck.setId(72L);
        stuck.setStatus(2);
        stuck.setConversationState(InterviewConversationState.EVALUATING_ANSWER.name());
        stuck.setUpdatedTime(LocalDateTime.now().minusMinutes(20));
        stuck.setAnalysisRetryCount(0);

        when(sessionMapper.selectList(any())).thenReturn(List.of(), List.of(), List.of(stuck));
        when(sessionMapper.update(any(), any())).thenReturn(1);

        scheduler.recoverStalledAnalysis();

        // 优先恢复：调用条件更新（status=2 + EVALUATING_ANSWER）并通知员工重新作答
        verify(sessionMapper).update(any(), any());
        verify(eventPublisher).publishEvent(any(com.example.matching.event.InterviewWsEvent.class));
    }

    @Test
    void stuckEvaluatingExhaustedRetriesMarksFailedSoEmployeeCanRestart() {
        EmpVideoInterviewSessionMapper sessionMapper = mock(EmpVideoInterviewSessionMapper.class);
        ApplicationEventPublisher eventPublisher = mock(ApplicationEventPublisher.class);
        SchedulerMetrics schedulerMetrics = mock(SchedulerMetrics.class);
        InterviewAnalysisRecoveryScheduler scheduler =
                new InterviewAnalysisRecoveryScheduler(sessionMapper, eventPublisher, schedulerMetrics);

        EmpVideoInterviewSession stuck = new EmpVideoInterviewSession();
        stuck.setId(73L);
        stuck.setStatus(2);
        stuck.setConversationState(InterviewConversationState.EVALUATING_ANSWER.name());
        stuck.setUpdatedTime(LocalDateTime.now().minusMinutes(20));
        stuck.setAnalysisRetryCount(3); // 已达重试上限

        when(sessionMapper.selectList(any())).thenReturn(List.of(), List.of(), List.of(stuck));
        when(sessionMapper.update(any(), any())).thenReturn(1);

        scheduler.recoverStalledAnalysis();

        // 置 FAILED 终态（status=7）：findActiveSession 不再把该会话视为 active，员工可创建新面试
        verify(sessionMapper).update(any(), any());
        verify(eventPublisher, org.mockito.Mockito.never()).publishEvent(any(com.example.matching.event.InterviewWsEvent.class));
        verify(schedulerMetrics).recordFailure("interview_evaluating_recovery");
    }
}
