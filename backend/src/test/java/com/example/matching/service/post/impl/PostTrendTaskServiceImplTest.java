package com.example.matching.service.post.impl;

import com.example.matching.common.enums.TaskStatusEnum;
import com.example.matching.common.exception.BusinessException;
import com.example.matching.dto.post.PostTrendAnalysisSummary;
import com.example.matching.entity.post.PostTrendTask;
import com.example.matching.event.PostTrendAnalysisQueuedEvent;
import com.example.matching.mapper.post.PostTrendCandidateMapper;
import com.example.matching.mapper.post.PostTrendTaskMapper;
import com.example.matching.vo.post.PostTrendProgressVO;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.context.ApplicationEventPublisher;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 岗位趋势解析任务生命周期单测。
 * <p>
 * 核心回归点：**取消必须能压住执行线程的收尾写入**。
 * 解析包含多次 LLM 调用，用户中途取消时执行线程仍在跑；若终态更新不带
 * {@code task_status = RUNNING} 前置条件，任务会被重新写成 COMPLETED，
 * 取消变成「界面上显示已取消、后台却真的建了岗位」。
 */
@DisplayName("岗位趋势解析任务（S2）")
class PostTrendTaskServiceImplTest {

    private final PostTrendTaskMapper taskMapper = mock(PostTrendTaskMapper.class);
    private final PostTrendCandidateMapper candidateMapper = mock(PostTrendCandidateMapper.class);
    private final ApplicationEventPublisher eventPublisher = mock(ApplicationEventPublisher.class);

    private final PostTrendTaskServiceImpl service =
            new PostTrendTaskServiceImpl(taskMapper, candidateMapper, eventPublisher, new ObjectMapper());

    // ---------------------------------------------------------------- 创建

    @Test
    @DisplayName("未选材料时拒绝创建，并给出可执行提示")
    void createTaskRejectsEmptyDocumentList() {
        assertThatThrownBy(() -> service.createTask(List.of(), List.of(), null, 1L))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("至少一份权威材料");
        verify(taskMapper, never()).insert(any(PostTrendTask.class));
        verify(eventPublisher, never()).publishEvent(any(Object.class));
    }

    @Test
    @DisplayName("创建后落 PENDING/QUEUED 并投递执行事件")
    void createTaskPersistsPendingAndPublishesQueuedEvent() {
        stubInsertAssigningId(100L);

        PostTrendTask task = service.createTask(List.of(7L, 8L), List.of("POLICY", "REPORT"), "2026 政策解析", 42L);

        assertThat(task.getId()).isEqualTo(100L);
        assertThat(task.getTaskCode()).startsWith("TREND_");
        assertThat(task.getTaskStatus()).isEqualTo(TaskStatusEnum.PENDING.getCode());
        assertThat(task.getProgressStatus()).isEqualTo("QUEUED");
        assertThat(task.getProgressPercent()).isZero();
        assertThat(task.getOperatorId()).isEqualTo(42L);

        ArgumentCaptor<Object> captor = ArgumentCaptor.forClass(Object.class);
        verify(eventPublisher).publishEvent(captor.capture());
        assertThat(captor.getValue()).isInstanceOf(PostTrendAnalysisQueuedEvent.class);
        PostTrendAnalysisQueuedEvent event = (PostTrendAnalysisQueuedEvent) captor.getValue();
        assertThat(event.taskId()).isEqualTo(100L);
        assertThat(event.sourceDocumentIds()).containsExactly(7L, 8L);
    }

    @Test
    @DisplayName("未传任务名时使用带日期的默认名，材料类别为空不报错")
    void createTaskToleratesMissingNameAndCategories() {
        stubInsertAssigningId(101L);

        PostTrendTask task = service.createTask(List.of(7L), null, "  ", 42L);

        assertThat(task.getTaskName()).startsWith("岗位趋势解析 ");
        assertThat(task.getSourceCategories()).isEqualTo("[]");
    }

    // ---------------------------------------------------------------- 完成

    @Test
    @DisplayName("有候选 → WAIT_CONFIRM；无候选 → COMPLETED")
    void markAnalysisFinishedChoosesTerminalStatusByCandidateCount() {
        stubInsertAssigningId(100L);
        when(taskMapper.finishRunningTask(eq(100L), eq(TaskStatusEnum.RUNNING.getCode()), anyString(),
                eq("COMPLETED"), eq(100))).thenReturn(1);
        when(taskMapper.selectById(100L)).thenReturn(existingTask(100L));

        service.markAnalysisFinished(100L, new PostTrendAnalysisSummary(
                2, 5, 3, 2, 1, 4, true, "已解析 3 个候选"));

        ArgumentCaptor<String> terminal = ArgumentCaptor.forClass(String.class);
        verify(taskMapper).finishRunningTask(eq(100L), eq(TaskStatusEnum.RUNNING.getCode()),
                terminal.capture(), eq("COMPLETED"), eq(100));
        assertThat(terminal.getValue()).isEqualTo(TaskStatusEnum.WAIT_CONFIRM.getCode());
    }

    @Test
    @DisplayName("零候选落到 COMPLETED 而不是 WAIT_CONFIRM，避免出现空审核台")
    void zeroCandidatesGoesToCompleted() {
        when(taskMapper.finishRunningTask(eq(200L), eq(TaskStatusEnum.RUNNING.getCode()), anyString(),
                eq("COMPLETED"), eq(100))).thenReturn(1);
        when(taskMapper.selectById(200L)).thenReturn(existingTask(200L));

        service.markAnalysisFinished(200L, new PostTrendAnalysisSummary(
                1, 0, 0, 0, 0, 0, true, "材料中未识别出趋势岗位"));

        ArgumentCaptor<String> terminal = ArgumentCaptor.forClass(String.class);
        verify(taskMapper).finishRunningTask(eq(200L), eq(TaskStatusEnum.RUNNING.getCode()),
                terminal.capture(), eq("COMPLETED"), eq(100));
        assertThat(terminal.getValue()).isEqualTo(TaskStatusEnum.COMPLETED.getCode());
    }

    @Test
    @DisplayName("任务已被取消时，解析结果不得覆盖终态")
    void markAnalysisFinishedDoesNotOverwriteCancelledTask() {
        when(taskMapper.finishRunningTask(eq(300L), eq(TaskStatusEnum.RUNNING.getCode()), anyString(),
                anyString(), anyInt())).thenReturn(0);

        service.markAnalysisFinished(300L, new PostTrendAnalysisSummary(
                1, 3, 2, 1, 1, 0, true, "ok"));

        verify(taskMapper, never()).selectById(any());
        verify(taskMapper, never()).updateById(any(PostTrendTask.class));
    }

    @Test
    @DisplayName("summary 为 null 时降级为空结果并写入可读诊断")
    void nullSummaryDegradesToEmptyResultWithDiagnostics() {
        when(taskMapper.finishRunningTask(eq(400L), eq(TaskStatusEnum.RUNNING.getCode()), anyString(),
                eq("COMPLETED"), eq(100))).thenReturn(1);
        PostTrendTask task = existingTask(400L);
        when(taskMapper.selectById(400L)).thenReturn(task);

        service.markAnalysisFinished(400L, null);

        assertThat(task.getDiagnostics()).isNotBlank();
        assertThat(task.getCandidateCount()).isZero();
        verify(taskMapper).updateById(task);
    }

    @Test
    @DisplayName("完成时回写候选计数与统计 JSON")
    void markAnalysisFinishedWritesCountsAndSummary() {
        when(taskMapper.finishRunningTask(eq(500L), eq(TaskStatusEnum.RUNNING.getCode()), anyString(),
                anyString(), anyInt())).thenReturn(1);
        PostTrendTask task = existingTask(500L);
        when(taskMapper.selectById(500L)).thenReturn(task);

        service.markAnalysisFinished(500L, new PostTrendAnalysisSummary(
                3, 9, 4, 3, 1, 5, false, "相似度检索不可用，候选一律待复核"));

        assertThat(task.getCandidateCount()).isEqualTo(4);
        assertThat(task.getNewPostCount()).isEqualTo(3);
        assertThat(task.getChangeCount()).isEqualTo(1);
        assertThat(task.getResultSummary()).contains("indexedDocumentCount");
        assertThat(task.getResultSummary()).contains("\"similarityServiceAvailable\":false");
    }

    // ---------------------------------------------------------------- 失败

    @Test
    @DisplayName("失败只作用于 RUNNING，且超长异常信息被截断")
    void markFailedTruncatesMessageAndKeepsCasGuard() {
        String longMessage = "x".repeat(5000);
        when(taskMapper.failRunningTask(eq(600L), eq(TaskStatusEnum.RUNNING.getCode()),
                eq(TaskStatusEnum.FAILED.getCode()), anyString())).thenReturn(1);

        service.markFailed(600L, longMessage);

        ArgumentCaptor<String> message = ArgumentCaptor.forClass(String.class);
        verify(taskMapper).failRunningTask(eq(600L), eq(TaskStatusEnum.RUNNING.getCode()),
                eq(TaskStatusEnum.FAILED.getCode()), message.capture());
        assertThat(message.getValue()).hasSize(2000);
    }

    @Test
    @DisplayName("空失败原因降级为通用文案")
    void markFailedFallsBackToGenericMessage() {
        when(taskMapper.failRunningTask(eq(601L), eq(TaskStatusEnum.RUNNING.getCode()),
                eq(TaskStatusEnum.FAILED.getCode()), anyString())).thenReturn(1);

        service.markFailed(601L, "   ");

        verify(taskMapper).failRunningTask(eq(601L), eq(TaskStatusEnum.RUNNING.getCode()),
                eq(TaskStatusEnum.FAILED.getCode()), eq("解析失败，请重试"));
    }

    // ---------------------------------------------------------------- 取消

    @Test
    @DisplayName("取消成功返回 true；已是终态返回 false 且不报错")
    void cancelReflectsCasOutcome() {
        when(taskMapper.selectById(700L)).thenReturn(existingTask(700L));
        when(taskMapper.cancelActiveTask(700L, TaskStatusEnum.CANCELLED.getCode())).thenReturn(1);
        assertThat(service.cancel(700L)).isTrue();

        when(taskMapper.cancelActiveTask(701L, TaskStatusEnum.CANCELLED.getCode())).thenReturn(0);
        when(taskMapper.selectById(701L)).thenReturn(existingTask(701L));
        assertThat(service.cancel(701L)).isFalse();
    }

    @Test
    @DisplayName("任务不存在时取消抛 404")
    void cancelMissingTaskThrowsNotFound() {
        when(taskMapper.selectById(999L)).thenReturn(null);

        assertThatThrownBy(() -> service.cancel(999L)).isInstanceOf(BusinessException.class)
                .hasMessageContaining("不存在");
        verify(taskMapper, never()).cancelActiveTask(any(), anyString());
    }

    // ---------------------------------------------------------------- 僵尸回收

    @Test
    @DisplayName("僵尸回收：逐个置 FAILED 并统计回收数量")
    void scanZombieTasksRecoversStalledRunningTasks() {
        PostTrendTask z1 = existingTask(801L);
        PostTrendTask z2 = existingTask(802L);
        when(taskMapper.findZombieTasks(eq(TaskStatusEnum.RUNNING.getCode()), any(LocalDateTime.class), anyInt()))
                .thenReturn(List.of(z1, z2));
        when(taskMapper.failRunningTask(any(), eq(TaskStatusEnum.RUNNING.getCode()),
                eq(TaskStatusEnum.FAILED.getCode()), anyString())).thenReturn(1);

        int recovered = service.scanZombieTasks();

        assertThat(recovered).isEqualTo(2);
        // 必须带 RUNNING 前置条件，否则会把刚完成的任务一起改坏
        verify(taskMapper, times(2)).failRunningTask(any(), eq(TaskStatusEnum.RUNNING.getCode()),
                eq(TaskStatusEnum.FAILED.getCode()), anyString());
    }

    @Test
    @DisplayName("没有僵尸任务时不写库")
    void scanZombieTasksIsNoopWhenNothingStalled() {
        when(taskMapper.findZombieTasks(anyString(), any(LocalDateTime.class), anyInt())).thenReturn(List.of());

        assertThat(service.scanZombieTasks()).isZero();
        verify(taskMapper, never()).failRunningTask(any(), anyString(), anyString(), anyString());
    }

    // ---------------------------------------------------------------- 进度

    @Test
    @DisplayName("进度步骤按阶段顺序推导 DONE / RUNNING / PENDING")
    void progressStepsFollowStageOrder() {
        PostTrendTask task = existingTask(900L);
        task.setTaskStatus(TaskStatusEnum.RUNNING.getCode());
        task.setProgressStatus("MATCHING");
        task.setProgressPercent(60);
        when(taskMapper.selectById(900L)).thenReturn(task);

        PostTrendProgressVO progress = service.getProgress(900L);

        assertThat(progress.getPercent()).isEqualTo(60);
        assertThat(progress.getSteps()).extracting(PostTrendProgressVO.StepProgress::getStatus)
                .containsExactly("DONE", "DONE", "RUNNING", "PENDING", "PENDING");
    }

    @Test
    @DisplayName("解析完成后所有步骤为 DONE")
    void progressStepsAllDoneAfterCompletion() {
        PostTrendTask task = existingTask(901L);
        task.setProgressStatus("COMPLETED");
        when(taskMapper.selectById(901L)).thenReturn(task);

        assertThat(service.getProgress(901L).getSteps())
                .extracting(PostTrendProgressVO.StepProgress::getStatus)
                .containsOnly("DONE");
    }

    @Test
    @DisplayName("失败时所有步骤标记 ERROR，取消时回到 PENDING")
    void progressStepsReflectFailureAndCancellation() {
        PostTrendTask failed = existingTask(902L);
        failed.setProgressStatus("FAILED");
        when(taskMapper.selectById(902L)).thenReturn(failed);
        assertThat(service.getProgress(902L).getSteps())
                .extracting(PostTrendProgressVO.StepProgress::getStatus)
                .containsOnly("ERROR");

        PostTrendTask cancelled = existingTask(903L);
        cancelled.setProgressStatus("CANCELLED");
        when(taskMapper.selectById(903L)).thenReturn(cancelled);
        assertThat(service.getProgress(903L).getSteps())
                .extracting(PostTrendProgressVO.StepProgress::getStatus)
                .containsOnly("PENDING");
    }

    // ---------------------------------------------------------------- helpers

    private void stubInsertAssigningId(long id) {
        when(taskMapper.insert(any(PostTrendTask.class))).thenAnswer(invocation -> {
            PostTrendTask inserted = invocation.getArgument(0);
            inserted.setId(id);
            return 1;
        });
    }

    private PostTrendTask existingTask(long id) {
        PostTrendTask task = new PostTrendTask();
        task.setId(id);
        task.setTaskStatus(TaskStatusEnum.RUNNING.getCode());
        return task;
    }
}
