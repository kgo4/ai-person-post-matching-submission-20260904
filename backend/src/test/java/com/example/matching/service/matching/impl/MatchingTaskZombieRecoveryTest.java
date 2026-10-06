package com.example.matching.service.matching.impl;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.example.matching.common.enums.MatchingTaskStatus;
import com.example.matching.entity.matching.MatchingTask;
import com.example.matching.mapper.matching.MatchingTaskMapper;
import com.example.matching.mapper.matching.MatchingTaskOutboxMapper;
import com.example.matching.service.matching.impl.MatchingTaskServiceImpl;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@DisplayName("MatchingTask zombie recovery & heartbeat (N8)")
class MatchingTaskZombieRecoveryTest {

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
        service = new MatchingTaskServiceImpl(outboxMapper, new ObjectMapper(),
                mock(org.springframework.beans.factory.ObjectProvider.class),
                mock(com.example.matching.mapper.closure.MatchingRematchValidationMapper.class));
        org.springframework.test.util.ReflectionTestUtils.setField(service, "baseMapper", mapper);
        when(mapper.update(isNull(), any(Wrapper.class))).thenReturn(1);
    }

    @Test
    @DisplayName("僵尸恢复只命中 RUNNING 且 updated_time 超期的任务")
    void recoverZombieTasksTargetsStalledRunningTasks() {
        service.recoverZombieTasks(Duration.ofMinutes(30));

        ArgumentCaptor<Wrapper<MatchingTask>> captor = ArgumentCaptor.forClass(Wrapper.class);
        verify(mapper).update(isNull(), captor.capture());
        com.baomidou.mybatisplus.core.conditions.AbstractWrapper<?, ?, ?> wrapper =
                (com.baomidou.mybatisplus.core.conditions.AbstractWrapper<?, ?, ?>) captor.getValue();
        assertThat(wrapper.getSqlSegment()).contains("status = ");
        assertThat(wrapper.getSqlSegment()).contains("updated_time <= ");
        assertThat(wrapper.getSqlSet()).contains("status=");
        assertThat(wrapper.getSqlSet()).contains("error_message=");
    }

    @Test
    @DisplayName("心跳只刷新 RUNNING 任务的 updated_time")
    void touchTaskRefreshesOnlyRunningTasks() {
        service.touchTask("task-1");

        ArgumentCaptor<Wrapper<MatchingTask>> captor = ArgumentCaptor.forClass(Wrapper.class);
        verify(mapper).update(isNull(), captor.capture());
        com.baomidou.mybatisplus.core.conditions.AbstractWrapper<?, ?, ?> wrapper =
                (com.baomidou.mybatisplus.core.conditions.AbstractWrapper<?, ?, ?>) captor.getValue();
        assertThat(wrapper.getSqlSegment()).contains("task_id = ");
        assertThat(wrapper.getSqlSegment()).contains("status = ");
        assertThat(wrapper.getSqlSet()).contains("updated_time = NOW()");
    }

    @Test
    @DisplayName("状态枚举与任务状态码一致")
    void statusCodesAlignWithEnum() {
        assertThat(MatchingTaskStatus.PENDING.getCode()).isZero();
        assertThat(MatchingTaskStatus.RUNNING.getCode()).isEqualTo(1);
        assertThat(MatchingTaskStatus.COMPLETED.getCode()).isEqualTo(2);
        assertThat(MatchingTaskStatus.FAILED.getCode()).isEqualTo(3);
        assertThat(MatchingTaskStatus.CANCELLED.getCode()).isEqualTo(4);
    }
}
