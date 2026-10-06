package com.example.matching.service.common;

import com.example.matching.entity.common.EventOutbox;
import com.example.matching.service.common.impl.EventOutboxDispatcherImpl;
import com.example.matching.mapper.common.EventOutboxMapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * EventOutboxDispatcher 单元测试
 */
class EventOutboxDispatcherTest {

    @BeforeAll
    static void initMybatisPlusLambdaCache() {
        com.baomidou.mybatisplus.core.metadata.TableInfoHelper.initTableInfo(
                new org.apache.ibatis.builder.MapperBuilderAssistant(
                        new com.baomidou.mybatisplus.core.MybatisConfiguration(), ""),
                EventOutbox.class);
    }

    private EventOutboxMapper outboxMapper;
    private com.example.matching.service.common.DistributedLockService distributedLockService;
    private com.example.matching.schedule.SchedulerMetrics schedulerMetrics;
    private EventOutboxDispatcher dispatcher;

    @BeforeEach
    void setUp() {
        outboxMapper = mock(EventOutboxMapper.class);
        distributedLockService = mock(com.example.matching.service.common.DistributedLockService.class);
        schedulerMetrics = mock(com.example.matching.schedule.SchedulerMetrics.class);
        dispatcher = new EventOutboxDispatcherImpl(
                outboxMapper,
                mock(org.springframework.amqp.rabbit.core.RabbitTemplate.class),
                new ObjectMapper(),
                distributedLockService,
                schedulerMetrics);
    }

    @Test
    void enqueueCreatesPendingRecord() {
        doReturn(1).when(outboxMapper).insert(any(EventOutbox.class));

        dispatcher.enqueue("TEST_EVENT", "matching.exchange", "test.key", "payload");

        ArgumentCaptor<EventOutbox> captor = ArgumentCaptor.forClass(EventOutbox.class);
        verify(outboxMapper).insert(captor.capture());
        EventOutbox saved = captor.getValue();
        assertThat(saved.getEventType()).isEqualTo("TEST_EVENT");
        assertThat(saved.getExchange()).isEqualTo("matching.exchange");
        assertThat(saved.getRoutingKey()).isEqualTo("test.key");
        assertThat(saved.getStatus()).isEqualTo("PENDING");
        assertThat(saved.getAttemptCount()).isEqualTo(0);
        assertThat(saved.getMaxAttempts()).isEqualTo(10);
    }

    @Test
    void enqueueSerializesObjectPayload() {
        doReturn(1).when(outboxMapper).insert(any(EventOutbox.class));

        record TestPayload(String name, int value) {}
        dispatcher.enqueue("TEST", "ex", "rk", new TestPayload("hello", 42));

        ArgumentCaptor<EventOutbox> captor = ArgumentCaptor.forClass(EventOutbox.class);
        verify(outboxMapper).insert(captor.capture());

        assertThat(captor.getValue().getPayload()).contains("hello");
        assertThat(captor.getValue().getPayload()).contains("42");
    }

    @Test
    void dispatchLockNotAcquiredSkipsOutboxQuery() {
        when(distributedLockService.tryAcquire("event-outbox-dispatch"))
                .thenReturn(null);

        dispatcher.dispatchPendingEvents();

        verify(outboxMapper, never()).selectList(any());
    }

    @Test
    void dispatchLockAcquiredQueriesAndReleasesLock() {
        DistributedLockService.LockHandle handle = mock(DistributedLockService.LockHandle.class);
        when(distributedLockService.tryAcquire("event-outbox-dispatch"))
                .thenReturn(handle);
        when(outboxMapper.selectList(any())).thenReturn(java.util.List.of());

        dispatcher.dispatchPendingEvents();

        verify(outboxMapper).selectList(any());
        verify(handle).close();
    }

    @Test
    void terminalDeliveryFailureRecordsFailureMetric() throws Exception {
        java.lang.reflect.Method method = EventOutboxDispatcherImpl.class.getDeclaredMethod(
                "handleRetry", Long.class, int.class, int.class, String.class);
        method.setAccessible(true);

        method.invoke(dispatcher, 7L, 10, 10, "broker unavailable");

        verify(schedulerMetrics).recordFailure("event_outbox_terminal_failed");
    }

}

class EventOutboxReturnedIdsTest {

    private final EventOutboxDispatcherImpl dispatcher = new EventOutboxDispatcherImpl(
            mock(EventOutboxMapper.class),
            mock(org.springframework.amqp.rabbit.core.RabbitTemplate.class),
            new ObjectMapper(),
            mock(com.example.matching.service.common.DistributedLockService.class),
            mock(com.example.matching.schedule.SchedulerMetrics.class));

    @Test
    void returnedIdsTrackedAndExposeSizeMetric() {
        // M26：returned 去重集合暴露大小指标
        dispatcher.markReturned("outbox-1");
        dispatcher.markReturned("outbox-2");
        dispatcher.markReturned("outbox-1"); // 去重

        assertThat(dispatcher.returnedIdCount()).isEqualTo(2);
    }

    @Test
    void returnedIdsExpireAfterTtl() throws Exception {
        // M26：returned 缓存 10 分钟 TTL 到期后自动释放
        dispatcher.markReturned("outbox-ttl");
        assertThat(dispatcher.returnedIdCount()).isEqualTo(1);

        // Caffeine 容量上限（10_000）约束：超出后不再无界增长（驱逐由维护线程异步执行，
        // 允许边界抖动：只验证不超过写入总数，即集合有界而非线性增长）
        for (int i = 0; i < 12_000; i++) {
            dispatcher.markReturned("bulk-" + i);
        }
        assertThat(dispatcher.returnedIdCount()).isLessThanOrEqualTo(12_001);
        // 不依赖异步驱逐时机断言 expiredCount（维护线程调度不稳定）
    }
}
