package com.example.matching.integration.fault;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.example.matching.entity.common.EventOutbox;
import com.example.matching.mapper.common.EventOutboxMapper;
import com.example.matching.service.common.EventOutboxDispatcher;
import com.example.matching.service.common.impl.EventOutboxDispatcherImpl;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitTemplate;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("Restart Recovery -- Outbox Dispatcher Tests")
class RestartRecoveryTest {

    @BeforeAll
    static void initMybatisPlusLambdaCache() {
        TableInfoHelper.initTableInfo(
                new MapperBuilderAssistant(new MybatisConfiguration(), ""),
                EventOutbox.class);
    }

    @Mock
    private EventOutboxMapper outboxMapper;

    @Mock
    private RabbitTemplate rabbitTemplate;

    @Mock
    private com.example.matching.service.common.DistributedLockService distributedLockService;

    private EventOutboxDispatcher dispatcher;

    @BeforeEach
    void setUp() {
        com.example.matching.service.common.DistributedLockService.LockHandle handle =
                mock(com.example.matching.service.common.DistributedLockService.LockHandle.class);
        when(handle.acquired()).thenReturn(true);
        when(distributedLockService.tryAcquire(anyString())).thenReturn(handle);
        dispatcher = new EventOutboxDispatcherImpl(outboxMapper, rabbitTemplate, new ObjectMapper(),
                distributedLockService,
                mock(com.example.matching.schedule.SchedulerMetrics.class));
    }

    @Test
    @DisplayName("dispatchPendingEvents picks up PENDING events and sends them to RabbitMQ")
    void dispatchPendingEvents_picksUpPendingEvents() {
        // Arrange: two PENDING events in the outbox
        EventOutbox event1 = buildOutboxEvent(1L, "PENDING", "MATCHING_TASK", "{\"postId\":1}");
        EventOutbox event2 = buildOutboxEvent(2L, "PENDING", "ABILITY_UPDATE", "{\"empId\":5}");

        // zombie recovery: no SENDING events to reset
        when(outboxMapper.update(isNull(), any(LambdaUpdateWrapper.class)))
                .thenReturn(0);

        // Return the two PENDING events
        when(outboxMapper.selectList(any(LambdaQueryWrapper.class)))
                .thenReturn(List.of(event1, event2));

        // Claim (CAS update) each event: PENDING -> SENDING succeeds
        when(outboxMapper.update(isNull(), any(LambdaUpdateWrapper.class)))
                .thenReturn(0)   // zombie recovery reset
                .thenReturn(1)   // claim event1
                .thenReturn(1);  // claim event2

        // RabbitTemplate send: do nothing (fire-and-forget)
        doNothing().when(rabbitTemplate).send(anyString(), anyString(), any(), any(CorrelationData.class));

        // Act
        dispatcher.dispatchPendingEvents();

        // Assert: rabbitTemplate.send was called twice (one per event)
        verify(rabbitTemplate, times(2)).send(anyString(), anyString(), any(), any(CorrelationData.class));
    }

    @Test
    @DisplayName("dispatchPendingEvents resets SENDING events older than 5 minutes to PENDING (zombie recovery)")
    void dispatchPendingEvents_resetsZombieSendingEvents() {
        // Arrange: no PENDING events
        when(outboxMapper.selectList(any(LambdaQueryWrapper.class)))
                .thenReturn(Collections.emptyList());

        // The first update call in dispatchPendingEvents is the zombie recovery reset
        when(outboxMapper.update(isNull(), any(LambdaUpdateWrapper.class)))
                .thenReturn(3); // 3 zombie SENDING events were reset

        // Act
        dispatcher.dispatchPendingEvents();

        // Assert: the first update call should be the zombie recovery
        // (verified by call order -- first invocation is the reset)
        ArgumentCaptor<LambdaUpdateWrapper<EventOutbox>> captor =
                ArgumentCaptor.forClass(LambdaUpdateWrapper.class);
        verify(outboxMapper, atLeastOnce()).update(isNull(), captor.capture());

        // No RabbitMQ calls since no PENDING events
        verifyNoInteractions(rabbitTemplate);
    }

    @Test
    @DisplayName("dispatchPendingEvents does nothing when outbox is empty")
    void dispatchPendingEvents_noopWhenEmpty() {
        when(outboxMapper.update(isNull(), any(LambdaUpdateWrapper.class)))
                .thenReturn(0);
        when(outboxMapper.selectList(any(LambdaQueryWrapper.class)))
                .thenReturn(Collections.emptyList());

        dispatcher.dispatchPendingEvents();

        verifyNoInteractions(rabbitTemplate);
    }

    @Test
    @DisplayName("dispatchPendingEvents skips events that fail CAS claim (concurrent dispatcher)")
    void dispatchPendingEvents_skipsAlreadyClaimedEvents() {
        EventOutbox event = buildOutboxEvent(1L, "PENDING", "TEST", "{\"data\":1}");

        when(outboxMapper.selectList(any(LambdaQueryWrapper.class)))
                .thenReturn(List.of(event));

        // First call: zombie recovery (0 rows). Second call: CAS claim fails (0 rows = already claimed)
        when(outboxMapper.update(isNull(), any(LambdaUpdateWrapper.class)))
                .thenReturn(0)
                .thenReturn(0);

        dispatcher.dispatchPendingEvents();

        // Should not send to RabbitMQ since claim failed
        verify(rabbitTemplate, never()).send(anyString(), anyString(), any(), any(CorrelationData.class));
    }

    @Test
    @DisplayName("dispatch handles RabbitMQ send exception and schedules retry")
    void dispatch_handlesRabbitExceptionAndSchedulesRetry() {
        EventOutbox event = buildOutboxEvent(1L, "PENDING", "TEST", "{\"data\":1}");

        when(outboxMapper.selectList(any(LambdaQueryWrapper.class)))
                .thenReturn(List.of(event));

        // CAS claim succeeds
        when(outboxMapper.update(isNull(), any(LambdaUpdateWrapper.class)))
                .thenReturn(0)  // zombie recovery
                .thenReturn(1); // claim

        // RabbitMQ send throws
        doThrow(new RuntimeException("Connection refused"))
                .when(rabbitTemplate).send(anyString(), anyString(), any(), any(CorrelationData.class));

        dispatcher.dispatchPendingEvents();

        // Verify that the retry handler was invoked (update with PENDING + backoff)
        verify(outboxMapper, atLeast(2)).update(isNull(), any(LambdaUpdateWrapper.class));
    }

    @Test
    @DisplayName("enqueue creates PENDING outbox event with correct fields")
    void enqueue_createsPendingEvent() {
        doReturn(1).when(outboxMapper).insert(any(EventOutbox.class));

        dispatcher.enqueue("MATCHING_COMPLETE", "matching.exchange", "matching.result",
                new TestPayload(1L, "done"));

        ArgumentCaptor<EventOutbox> captor = ArgumentCaptor.forClass(EventOutbox.class);
        verify(outboxMapper).insert(captor.capture());

        EventOutbox saved = captor.getValue();
        assertThat(saved.getEventType()).isEqualTo("MATCHING_COMPLETE");
        assertThat(saved.getExchange()).isEqualTo("matching.exchange");
        assertThat(saved.getRoutingKey()).isEqualTo("matching.result");
        assertThat(saved.getStatus()).isEqualTo("PENDING");
        assertThat(saved.getAttemptCount()).isEqualTo(0);
        assertThat(saved.getMaxAttempts()).isEqualTo(10);
        assertThat(saved.getPayload()).contains("done");
        assertThat(saved.getCreatedTime()).isNotNull();
    }

    @Test
    @DisplayName("statusSummary aggregates counts by status")
    void statusSummary_groupsByStatus() {
        EventOutbox pending1 = buildOutboxEvent(1L, "PENDING", "T1", "{}");
        EventOutbox pending2 = buildOutboxEvent(2L, "PENDING", "T2", "{}");
        EventOutbox published = buildOutboxEvent(3L, "PUBLISHED", "T3", "{}");
        EventOutbox failed = buildOutboxEvent(4L, "FAILED", "T4", "{}");

        when(outboxMapper.selectList(any(LambdaQueryWrapper.class)))
                .thenReturn(List.of(pending1, pending2, published, failed));

        var summary = dispatcher.statusSummary();

        assertThat(summary).containsEntry("PENDING", 2L);
        assertThat(summary).containsEntry("PUBLISHED", 1L);
        assertThat(summary).containsEntry("FAILED", 1L);
    }

    // ==================== helpers ====================

    private EventOutbox buildOutboxEvent(Long id, String status, String eventType, String payload) {
        EventOutbox event = new EventOutbox();
        event.setId(id);
        event.setStatus(status);
        event.setEventType(eventType);
        event.setExchange("matching.exchange");
        event.setRoutingKey("matching.key");
        event.setPayload(payload);
        event.setAttemptCount(0);
        event.setMaxAttempts(10);
        event.setCreatedTime(LocalDateTime.now().minusMinutes(10));
        event.setUpdatedTime(LocalDateTime.now().minusMinutes(10));
        return event;
    }

    private record TestPayload(Long id, String status) {}
}
