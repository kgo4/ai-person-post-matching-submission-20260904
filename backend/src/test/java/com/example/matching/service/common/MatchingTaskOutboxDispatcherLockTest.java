package com.example.matching.service.common;

import com.example.matching.entity.matching.MatchingTaskOutbox;
import com.example.matching.mapper.matching.MatchingTaskOutboxMapper;
import com.example.matching.schedule.SchedulerMetrics;
import com.example.matching.service.matching.MatchingTaskOutboxDispatcher;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.rabbit.core.RabbitTemplate;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class MatchingTaskOutboxDispatcherLockTest {

    @BeforeAll
    static void initMybatisPlusLambdaCache() {
        com.baomidou.mybatisplus.core.metadata.TableInfoHelper.initTableInfo(
                new org.apache.ibatis.builder.MapperBuilderAssistant(
                        new com.baomidou.mybatisplus.core.MybatisConfiguration(), ""),
                MatchingTaskOutbox.class);
    }

    private MatchingTaskOutboxMapper outboxMapper;
    private DistributedLockService distributedLockService;
    private MatchingTaskOutboxDispatcher dispatcher;

    @BeforeEach
    void setUp() {
        outboxMapper = mock(MatchingTaskOutboxMapper.class);
        distributedLockService = mock(DistributedLockService.class);
        dispatcher = new MatchingTaskOutboxDispatcher(
                outboxMapper,
                mock(RabbitTemplate.class),
                mock(com.example.matching.service.matching.MatchingTaskService.class),
                distributedLockService,
                mock(SchedulerMetrics.class),
                mock(io.micrometer.core.instrument.MeterRegistry.class));
    }

    @Test
    void lockNotAcquiredSkipsOutboxQuery() {
        when(distributedLockService.tryAcquire("matching-task-outbox-dispatch"))
                .thenReturn(null);

        dispatcher.dispatchPendingMessages();

        verify(outboxMapper, never()).selectList(any());
    }

    @Test
    void lockAcquiredQueriesAndReleasesLock() {
        DistributedLockService.LockHandle handle = mock(DistributedLockService.LockHandle.class);
        when(distributedLockService.tryAcquire("matching-task-outbox-dispatch"))
                .thenReturn(handle);
        when(outboxMapper.selectList(any())).thenReturn(List.of());

        dispatcher.dispatchPendingMessages();

        verify(outboxMapper).selectList(any());
        verify(handle).close();
    }
}
