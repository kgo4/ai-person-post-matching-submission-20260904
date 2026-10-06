package com.example.matching.service.matching.impl;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.example.matching.common.enums.MatchingTaskStatus;
import com.example.matching.entity.matching.MatchingTask;
import com.example.matching.mapper.matching.MatchingTaskMapper;
import com.example.matching.mapper.matching.MatchingTaskOutboxMapper;
import com.example.matching.mapper.closure.MatchingRematchValidationMapper;
import com.example.matching.service.matching.MatchingRecordService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.ObjectProvider;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.Duration;
import java.util.Collection;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@DisplayName("MatchingTask cancel & page & complete CAS")
class MatchingTaskCancelAndPageTest {

    private MatchingTaskMapper mapper;
    private MatchingTaskServiceImpl service;

    @BeforeAll
    static void initMybatisPlusLambdaCache() {
        TableInfoHelper.initTableInfo(
                new MapperBuilderAssistant(new MybatisConfiguration(), ""),
                MatchingTask.class);
    }

    @BeforeEach
    @SuppressWarnings({"rawtypes", "unchecked"})
    void setUp() {
        mapper = mock(MatchingTaskMapper.class);
        MatchingTaskOutboxMapper outboxMapper = mock(MatchingTaskOutboxMapper.class);
        ObjectProvider<MatchingRecordService> recordServiceProvider = mock(ObjectProvider.class);
        MatchingRematchValidationMapper rematchMapper = mock(MatchingRematchValidationMapper.class);
        service = new MatchingTaskServiceImpl(outboxMapper, new ObjectMapper(), recordServiceProvider, rematchMapper);
        org.springframework.test.util.ReflectionTestUtils.setField(service, "baseMapper", mapper);
        when(mapper.update(isNull(), any(Wrapper.class))).thenReturn(1);
    }

    @SuppressWarnings("unchecked")
    private com.baomidou.mybatisplus.core.conditions.AbstractWrapper<MatchingTask, ?, ?> lastUpdateWrapper() {
        ArgumentCaptor<Wrapper<MatchingTask>> captor = ArgumentCaptor.forClass(Wrapper.class);
        verify(mapper).update(isNull(), captor.capture());
        return (com.baomidou.mybatisplus.core.conditions.AbstractWrapper<MatchingTask, ?, ?>) captor.getValue();
    }

    @SuppressWarnings("unchecked")
    private com.baomidou.mybatisplus.core.conditions.AbstractWrapper<MatchingTask, ?, ?> lastPageWrapper() {
        ArgumentCaptor<Wrapper<MatchingTask>> captor = ArgumentCaptor.forClass(Wrapper.class);
        verify(mapper).selectPage(any(Page.class), captor.capture());
        return (com.baomidou.mybatisplus.core.conditions.AbstractWrapper<MatchingTask, ?, ?>) captor.getValue();
    }

    private Collection<Object> params(com.baomidou.mybatisplus.core.conditions.AbstractWrapper<?, ?, ?> w) {
        return w.getParamNameValuePairs().values();
    }

    @Test
    @DisplayName("取消任务只命中 PENDING/RUNNING，置为 CANCELLED")
    void cancelTaskTargetsPendingAndRunningOnly() {
        boolean cancelled = service.cancelTask("task-1");

        assertThat(cancelled).isTrue();
        var wrapper = lastUpdateWrapper();
        assertThat(wrapper.getSqlSegment()).contains("task_id = ");
        assertThat(wrapper.getSqlSegment()).contains("status IN");
        Collection<Object> whereParams = params(wrapper);
        assertThat(whereParams).contains(0, 1);      // PENDING/RUNNING
        assertThat(wrapper.getSqlSet()).contains("status=");
        assertThat(params(wrapper)).contains(4);     // CANCELLED
    }

    @Test
    @DisplayName("completeTask 使用 CAS：仅 RUNNING 可置 COMPLETED")
    void completeTaskIsConditionalCas() {
        MatchingTask running = new MatchingTask();
        running.setTaskId("task-1");
        running.setStatus(MatchingTaskStatus.RUNNING.getCode());
        running.setTotalCount(10);
        when(mapper.selectOne(any(Wrapper.class), org.mockito.ArgumentMatchers.anyBoolean())).thenReturn(running);

        boolean completed = service.completeTask("task-1", "匹配完成");

        assertThat(completed).isTrue();
        var wrapper = lastUpdateWrapper();
        assertThat(wrapper.getSqlSegment()).contains("status = ");
        assertThat(params(wrapper)).contains(1);     // 仅 RUNNING
        assertThat(wrapper.getSqlSet()).contains("status=");
        assertThat(params(wrapper)).contains(2);     // 置 COMPLETED
        assertThat(wrapper.getSqlSet()).contains("progress=");
    }

    @Test
    @DisplayName("completeTask 对已取消任务返回 false（更新 0 行）")
    void completeTaskSkipsCancelledTask() {
        MatchingTask cancelled = new MatchingTask();
        cancelled.setTaskId("task-1");
        cancelled.setStatus(MatchingTaskStatus.CANCELLED.getCode());
        cancelled.setTotalCount(10);
        when(mapper.selectOne(any(Wrapper.class), org.mockito.ArgumentMatchers.anyBoolean())).thenReturn(cancelled);
        when(mapper.update(isNull(), any(Wrapper.class))).thenReturn(0);

        boolean completed = service.completeTask("task-1", "匹配完成");

        assertThat(completed).isFalse();
    }

    @Test
    @DisplayName("分页查询按状态过滤并按创建时间倒序")
    void pageTasksFiltersByStatusAndOrdersByCreatedTimeDesc() {
        Page<MatchingTask> pageResult = new Page<>(1, 20);
        pageResult.setRecords(List.of(new MatchingTask()));
        pageResult.setTotal(1);
        when(mapper.selectPage(any(Page.class), any(Wrapper.class))).thenReturn(pageResult);

        IPage<MatchingTask> result = service.pageTasks(1, 20, MatchingTaskStatus.RUNNING.getCode());

        assertThat(result.getTotal()).isEqualTo(1);
        var wrapper = lastPageWrapper();
        assertThat(wrapper.getSqlSegment()).contains("status = ");
        assertThat(params(wrapper)).contains(1);
        assertThat(wrapper.getSqlSegment()).contains("ORDER BY created_time DESC");
    }

    @Test
    @DisplayName("分页查询 status 为空时不加状态过滤")
    void pageTasksWithoutStatusFilter() {
        Page<MatchingTask> pageResult = new Page<>(1, 20);
        when(mapper.selectPage(any(Page.class), any(Wrapper.class))).thenReturn(pageResult);

        service.pageTasks(1, 20, null);

        var wrapper = lastPageWrapper();
        assertThat(wrapper.getSqlSegment()).doesNotContain("status = ");
    }

    @Test
    @DisplayName("状态枚举含 CANCELLED=4")
    void statusCodesIncludeCancelled() {
        assertThat(MatchingTaskStatus.CANCELLED.getCode()).isEqualTo(4);
    }

    @Test
    @DisplayName("删除任务：进行中先取消，删子表、按批次删记录、物理删任务")
    void deleteTaskCascadesCancellationAndRecords() {
        MatchingTask running = new MatchingTask();
        running.setTaskId("task-1");
        running.setBatchNo("batch-abc");
        running.setStatus(MatchingTaskStatus.RUNNING.getCode());
        when(mapper.selectOne(any(Wrapper.class), org.mockito.ArgumentMatchers.anyBoolean())).thenReturn(running);
        MatchingRecordService recordService = mock(MatchingRecordService.class);
        ObjectProvider<MatchingRecordService> recordServiceProvider = mock(ObjectProvider.class);
        when(recordServiceProvider.getIfAvailable()).thenReturn(recordService);
        MatchingRematchValidationMapper rematchMapper = mock(MatchingRematchValidationMapper.class);
        MatchingTaskOutboxMapper outboxMapper = mock(MatchingTaskOutboxMapper.class);
        MatchingTaskServiceImpl svc = new MatchingTaskServiceImpl(outboxMapper, new ObjectMapper(), recordServiceProvider, rematchMapper);
        org.springframework.test.util.ReflectionTestUtils.setField(svc, "baseMapper", mapper);
        when(mapper.update(isNull(), any(Wrapper.class))).thenReturn(1);   // cancelTask 的 CAS
        when(mapper.delete(any(Wrapper.class))).thenReturn(1);             // 物理删任务

        boolean deleted = svc.deleteTask("task-1");

        assertThat(deleted).isTrue();
        // running 任务先被取消（status→4，仅一次 update）
        org.mockito.ArgumentCaptor<Wrapper<MatchingTask>> captor = org.mockito.ArgumentCaptor.forClass(Wrapper.class);
        verify(mapper).update(isNull(), captor.capture());
        var cancelWrapper = (com.baomidou.mybatisplus.core.conditions.AbstractWrapper<MatchingTask, ?, ?>) captor.getValue();
        assertThat(params(cancelWrapper)).contains(4); // 置 CANCELLED
        // 按批次删记录
        verify(recordService).deleteByBatchNo("batch-abc");
        // 物理删任务（含 outbox/rematch_validation 子表）
        verify(rematchMapper).delete(any(Wrapper.class));
        verify(outboxMapper).delete(any(Wrapper.class));
        verify(mapper).delete(any(Wrapper.class));
    }

    @Test
    @DisplayName("删除不存在任务返回 false")
    void deleteTaskReturnsFalseWhenMissing() {
        when(mapper.selectOne(any(Wrapper.class), org.mockito.ArgumentMatchers.anyBoolean())).thenReturn(null);
        boolean deleted = service.deleteTask("task-x");
        assertThat(deleted).isFalse();
    }

    @Test
    @DisplayName("僵尸恢复/心跳对 CANCELLED 任务无效（CAS 条件不变）")
    void zombieAndHeartbeatStillTargetRunningOnly() {
        service.recoverZombieTasks(Duration.ofMinutes(30));
        service.touchTask("task-1");

        ArgumentCaptor<Wrapper<MatchingTask>> captor = ArgumentCaptor.forClass(Wrapper.class);
        verify(mapper, org.mockito.Mockito.times(2)).update(isNull(), captor.capture());
        for (Wrapper<MatchingTask> w : captor.getAllValues()) {
            var wrapper = (com.baomidou.mybatisplus.core.conditions.AbstractWrapper<MatchingTask, ?, ?>) w;
            assertThat(wrapper.getSqlSegment()).contains("status = ");
            assertThat(wrapper.getSqlSegment()).doesNotContain("status IN");
            assertThat(params(wrapper)).doesNotContain(4); // CANCELLED 不在任何条件中
        }
    }
}
