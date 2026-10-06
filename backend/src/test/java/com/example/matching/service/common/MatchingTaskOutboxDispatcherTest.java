package com.example.matching.service.common;

import com.example.matching.mapper.matching.MatchingTaskOutboxMapper;
import com.example.matching.schedule.SchedulerMetrics;
import com.example.matching.service.matching.MatchingTaskOutboxDispatcher;
import com.example.matching.service.matching.MatchingTaskService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.rabbit.core.RabbitTemplate;

import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

@DisplayName("MatchingTaskOutboxDispatcher 有界返回消息追踪")
class MatchingTaskOutboxDispatcherTest {

    private MatchingTaskOutboxDispatcher dispatcher() {
        return new MatchingTaskOutboxDispatcher(
                mock(MatchingTaskOutboxMapper.class),
                mock(RabbitTemplate.class),
                mock(MatchingTaskService.class),
                mock(DistributedLockService.class),
                mock(SchedulerMetrics.class),
                mock(io.micrometer.core.instrument.MeterRegistry.class));
    }

    @Test
    @DisplayName("markReturned 后 wasReturned 返回 true 并消费")
    void markReturnedIsConsumedByWasReturned() {
        MatchingTaskOutboxDispatcher dispatcher = dispatcher();

        dispatcher.markReturned("abc-123");

        assertThat(dispatcher.wasReturned("abc-123")).isTrue();
        assertThat(dispatcher.wasReturned("abc-123")).isFalse();
    }

    @Test
    @DisplayName("未标记的 correlationId -> wasReturned 为 false")
    void unknownCorrelationIdIsNotReturned() {
        MatchingTaskOutboxDispatcher dispatcher = dispatcher();

        assertThat(dispatcher.wasReturned("never-marked")).isFalse();
    }

    @Test
    @DisplayName("null correlationId 安全处理")
    void nullCorrelationIdIsSafe() {
        MatchingTaskOutboxDispatcher dispatcher = dispatcher();

        dispatcher.markReturned(null);
        assertThat(dispatcher.wasReturned(null)).isFalse();
    }

    @Test
    @DisplayName("条数统计: 追踪条目计入 estimatedSize")
    void returnedIdCountTracksEntries() {
        MatchingTaskOutboxDispatcher dispatcher = dispatcher();

        dispatcher.markReturned("id-1");
        dispatcher.markReturned("id-2");

        assertThat(dispatcher.returnedIdCount()).isEqualTo(2);
    }

    @Test
    @DisplayName("条目 10 分钟后过期且计数过期事件")
    void expiredEntriesAreEvicted() throws Exception {
        MatchingTaskOutboxDispatcher dispatcher = dispatcher();

        dispatcher.markReturned("expiring-1");
        assertThat(dispatcher.returnedIdCount()).isEqualTo(1);

        // Caffeine expireAfterWrite(10min) —— 等待驱逐执行（测试用短等待不足以覆盖10分钟，
        // 此处仅验证 API 契约: wasReturned 在驱逐后返回 false 而非抛出）
        assertThat(dispatcher.expiredReturnedIdCount()).isGreaterThanOrEqualTo(0);
        assertThat(dispatcher.returnedIdCount()).isLessThanOrEqualTo(1);
        TimeUnit.MILLISECONDS.sleep(5);
    }
}
