package com.example.matching.service.common;

import com.example.matching.entity.matching.MatchingTaskOutbox;
import com.example.matching.mapper.matching.MatchingTaskOutboxMapper;
import com.example.matching.service.matching.MatchingTaskOutboxDispatcher;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * MatchingTaskOutboxDispatcher 单元测试
 */
class MatchingTaskOutboxDispatcherMaxRetryTest {

    @BeforeAll
    static void initMybatisPlusLambdaCache() {
        com.baomidou.mybatisplus.core.metadata.TableInfoHelper.initTableInfo(
                new org.apache.ibatis.builder.MapperBuilderAssistant(
                        new com.baomidou.mybatisplus.core.MybatisConfiguration(), ""),
                MatchingTaskOutbox.class);
    }

    private MatchingTaskOutboxMapper outboxMapper;
    private MatchingTaskOutboxDispatcher dispatcher;
    private com.example.matching.schedule.SchedulerMetrics schedulerMetrics;

    @BeforeEach
    void setUp() {
        outboxMapper = mock(MatchingTaskOutboxMapper.class);
        schedulerMetrics = mock(com.example.matching.schedule.SchedulerMetrics.class);
        dispatcher = new MatchingTaskOutboxDispatcher(
                outboxMapper,
                mock(org.springframework.amqp.rabbit.core.RabbitTemplate.class),
                mock(com.example.matching.service.matching.MatchingTaskService.class),
                mock(com.example.matching.service.common.DistributedLockService.class),
                schedulerMetrics,
                mock(io.micrometer.core.instrument.MeterRegistry.class));
    }

    @Test
    void statusSummaryGroupsCorrectly() {
        MatchingTaskOutbox pending = new MatchingTaskOutbox();
        pending.setStatus("PENDING");
        MatchingTaskOutbox published = new MatchingTaskOutbox();
        published.setStatus("PUBLISHED");

        when(outboxMapper.selectList(any())).thenReturn(List.of(pending, published, published));

        var summary = dispatcher.statusSummary();

        assertThat(summary.get("PENDING")).isEqualTo(1L);
        assertThat(summary.get("PUBLISHED")).isEqualTo(2L);
    }

    @Test
    void terminalDeliveryFailureRecordsFailureMetric() throws Exception {
        java.lang.reflect.Method method = MatchingTaskOutboxDispatcher.class.getDeclaredMethod(
                "handleRetry", Long.class, int.class, String.class);
        method.setAccessible(true);

        method.invoke(dispatcher, 7L, 10, "broker unavailable");

        verify(schedulerMetrics).recordFailure("matching_task_outbox_terminal_failed");
    }

}
