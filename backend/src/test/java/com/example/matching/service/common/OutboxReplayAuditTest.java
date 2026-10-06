package com.example.matching.service.common;

import com.example.matching.entity.system.SysOperationLog;
import com.example.matching.mapper.common.EventOutboxMapper;
import com.example.matching.mapper.matching.MatchingTaskOutboxMapper;
import com.example.matching.service.common.impl.EventOutboxDispatcherImpl;
import com.example.matching.service.matching.MatchingTaskOutboxDispatcher;
import com.example.matching.service.system.SysOperationLogService;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 工作包6：outbox FAILED 终态字段补充与管理员重放审计
 */
class OutboxReplayAuditTest {

    @BeforeAll
    static void initMybatisPlusLambdaCache() {
        com.baomidou.mybatisplus.core.metadata.TableInfoHelper.initTableInfo(
                new org.apache.ibatis.builder.MapperBuilderAssistant(
                        new com.baomidou.mybatisplus.core.MybatisConfiguration(), ""),
                com.example.matching.entity.common.EventOutbox.class);
        com.baomidou.mybatisplus.core.metadata.TableInfoHelper.initTableInfo(
                new org.apache.ibatis.builder.MapperBuilderAssistant(
                        new com.baomidou.mybatisplus.core.MybatisConfiguration(), ""),
                com.example.matching.entity.matching.MatchingTaskOutbox.class);
    }

    private EventOutboxMapper eventOutboxMapper;
    private EventOutboxDispatcher eventDispatcher;
    private MatchingTaskOutboxMapper taskOutboxMapper;
    private MatchingTaskOutboxDispatcher taskDispatcher;
    private SysOperationLogService sysOperationLogService;

    @BeforeEach
    void setUp() {
        eventOutboxMapper = mock(EventOutboxMapper.class);
        taskOutboxMapper = mock(MatchingTaskOutboxMapper.class);
        sysOperationLogService = mock(SysOperationLogService.class);
        eventDispatcher = new EventOutboxDispatcherImpl(
                eventOutboxMapper,
                mock(org.springframework.amqp.rabbit.core.RabbitTemplate.class),
                new com.fasterxml.jackson.databind.ObjectMapper(),
                mock(DistributedLockService.class),
                mock(com.example.matching.schedule.SchedulerMetrics.class));
        org.springframework.test.util.ReflectionTestUtils.setField(eventDispatcher,
                "sysOperationLogService", sysOperationLogService);
        taskDispatcher = new MatchingTaskOutboxDispatcher(
                taskOutboxMapper,
                mock(org.springframework.amqp.rabbit.core.RabbitTemplate.class),
                mock(com.example.matching.service.matching.MatchingTaskService.class),
                mock(DistributedLockService.class),
                mock(com.example.matching.schedule.SchedulerMetrics.class),
                mock(io.micrometer.core.instrument.MeterRegistry.class));
        org.springframework.test.util.ReflectionTestUtils.setField(taskDispatcher,
                "sysOperationLogService", sysOperationLogService);
    }

    @Test
    void eventOutboxReplaySucceedsAndWritesAudit() {
        when(eventOutboxMapper.update(eq(null), any())).thenReturn(1);

        boolean result = eventDispatcher.replay(42L);

        assertThat(result).isTrue();
        verify(sysOperationLogService).save(any(SysOperationLog.class));
    }

    @Test
    void eventOutboxReplaySkippedWhenStatusNotAllowed() {
        when(eventOutboxMapper.update(eq(null), any())).thenReturn(0);

        boolean result = eventDispatcher.replay(42L);

        assertThat(result).isFalse();
        verify(sysOperationLogService, never()).save(any(SysOperationLog.class));
    }

    @Test
    void matchingTaskOutboxReplaySucceedsAndWritesAudit() {
        when(taskOutboxMapper.update(eq(null), any())).thenReturn(1);

        boolean result = taskDispatcher.replay(7L);

        assertThat(result).isTrue();
        verify(sysOperationLogService).save(any(SysOperationLog.class));
    }

    @Test
    void matchingTaskOutboxReplaySkippedWhenStatusNotAllowed() {
        when(taskOutboxMapper.update(eq(null), any())).thenReturn(0);

        boolean result = taskDispatcher.replay(7L);

        assertThat(result).isFalse();
        verify(sysOperationLogService, never()).save(any(SysOperationLog.class));
    }
}
