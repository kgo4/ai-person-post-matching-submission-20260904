package com.example.matching.service.common.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.example.matching.config.ReturnedMessageHandler;
import com.example.matching.entity.common.EventOutbox;
import com.example.matching.entity.system.SysOperationLog;
import com.example.matching.mapper.common.EventOutboxMapper;
import com.example.matching.service.common.EventOutboxDispatcher;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * 通用事件 Outbox 调度器实现（M-09）。
 * <p>
 * payload 存储 canonical JSON，dispatch 时用原始字节构造 Message（不经过 Jackson2JsonMessageConverter 二次序列化）。
 * PUBLISHED 仅表示 broker 已接收且未被 returned；returned/nack/超时回退到 PENDING 并按退避重试。
 */
@Slf4j
@Component
public class EventOutboxDispatcherImpl implements EventOutboxDispatcher, ReturnedMessageHandler {
    private static final int BATCH_SIZE = 50;
    private static final int DEFAULT_MAX_ATTEMPTS = 10;
    private static final long MAX_BACKOFF_SECONDS = 300;
    private static final String LOCK_NAME = "event-outbox-dispatch";

    private final EventOutboxMapper outboxMapper;
    private final RabbitTemplate rabbitTemplate;
    private final ObjectMapper objectMapper;
    private final com.example.matching.service.common.DistributedLockService distributedLockService;
    private final com.example.matching.schedule.SchedulerMetrics schedulerMetrics;

    @org.springframework.beans.factory.annotation.Autowired(required = false)
    private com.example.matching.service.system.SysOperationLogService sysOperationLogService;

    @org.springframework.beans.factory.annotation.Autowired(required = false)
    private com.example.matching.schedule.ScheduledTaskRunner taskRunner;

    /** M26：Caffeine 缓存（keySet 只读，写入走 put/remove） */
    private final com.github.benmanes.caffeine.cache.Cache<String, Boolean> returnedIdCache;

    private final Set<String> returnedIds;

    /** returned 去重集合统计（M26） */
    private final java.util.concurrent.atomic.AtomicLong returnedIdExpiredCount = new java.util.concurrent.atomic.AtomicLong(0);

    @org.springframework.beans.factory.annotation.Autowired
    public EventOutboxDispatcherImpl(EventOutboxMapper outboxMapper,
                                     RabbitTemplate rabbitTemplate,
                                     ObjectMapper objectMapper,
                                     com.example.matching.service.common.DistributedLockService distributedLockService,
                                     com.example.matching.schedule.SchedulerMetrics schedulerMetrics) {
        this.outboxMapper = outboxMapper;
        this.rabbitTemplate = rabbitTemplate;
        this.objectMapper = objectMapper;
        this.distributedLockService = distributedLockService;
        this.schedulerMetrics = schedulerMetrics;
        this.returnedIdCache = createReturnedIdCache();
        this.returnedIds = returnedIdCache.asMap().keySet();
    }

    /**
     * M26：returnedIds 使用 Caffeine（最大 10000、10 分钟过期），与 Matching Outbox 一致，
     * 避免无界 ConcurrentHashMap 长期驻留导致内存泄漏。
     * <p>
     * 注意：Caffeine 的 keySet 是只读视图，写入必须通过 cache.put。
     */
    private com.github.benmanes.caffeine.cache.Cache<String, Boolean> createReturnedIdCache() {
        return com.github.benmanes.caffeine.cache.Caffeine.newBuilder()
                .maximumSize(10_000)
                .expireAfterWrite(java.time.Duration.ofMinutes(10))
                .removalListener((String key, Object value, com.github.benmanes.caffeine.cache.RemovalCause cause) -> {
                    if (cause.wasEvicted()) {
                        returnedIdExpiredCount.incrementAndGet();
                    }
                })
                .<String, Boolean>build();
    }

    /** 当前 returned 去重集合大小（指标） */
    public int returnedIdCount() {
        return returnedIds.size();
    }

    /** 因过期被淘汰的 returned 数量（指标） */
    public long returnedIdExpiredCount() {
        return returnedIdExpiredCount.get();
    }

    @Override
    public void onMessageReturned(String correlationId) {
        markReturned(correlationId);
    }

    @Override
    public void markReturned(String correlationId) {
        if (correlationId != null) {
            // Caffeine keySet 为只读视图：写入走 put
            returnedIdCache.put(correlationId, Boolean.TRUE);
        }
    }

    /**
     * 将事件写入 outbox（在业务事务内调用）。payload 存储 canonical JSON 字符串。
     */
    @Override
    @Transactional
    public void enqueue(String eventType, String exchange, String routingKey, Object payload) {
        EventOutbox record = new EventOutbox();
        record.setEventType(eventType);
        record.setExchange(exchange);
        record.setRoutingKey(routingKey);
        try {
            record.setPayload(objectMapper.writeValueAsString(payload));
        } catch (Exception e) {
            throw new IllegalStateException("Failed to serialize event payload: " + eventType, e);
        }
        record.setStatus("PENDING");
        record.setAttemptCount(0);
        record.setMaxAttempts(DEFAULT_MAX_ATTEMPTS);
        record.setCreatedTime(LocalDateTime.now());
        outboxMapper.insert(record);
    }

    @Scheduled(fixedDelay = 5000)
    @Override
    public void dispatchPendingEvents() {
        if (taskRunner != null) {
            taskRunner.run("event_outbox_dispatch", this::dispatchPendingEventsInternal);
        } else {
            dispatchPendingEventsInternal();
        }
    }

    private void dispatchPendingEventsInternal() {
        var lock = distributedLockService.tryAcquire(LOCK_NAME);
        if (lock == null) {
            log.debug("Event outbox dispatch skipped: lock held by another instance");
            return;
        }
        try {
            outboxMapper.update(null, Wrappers.<EventOutbox>lambdaUpdate()
                    .eq(EventOutbox::getStatus, "SENDING")
                    .lt(EventOutbox::getUpdatedTime, LocalDateTime.now().minusMinutes(5))
                    .set(EventOutbox::getStatus, "PENDING"));

            List<EventOutbox> pending = outboxMapper.selectList(
                    Wrappers.<EventOutbox>lambdaQuery()
                            .eq(EventOutbox::getStatus, "PENDING")
                            .and(q -> q.isNull(EventOutbox::getNextRetryTime)
                                    .or().le(EventOutbox::getNextRetryTime, LocalDateTime.now()))
                            .orderByAsc(EventOutbox::getCreatedTime)
                            .last("LIMIT " + BATCH_SIZE));

            pending.forEach(this::dispatch);
        } catch (Exception e) {
            log.error("Event outbox dispatch batch failed, pending messages may be delayed", e);
            schedulerMetrics.recordFailure("event_outbox_dispatch");
        } finally {
            lock.close();
        }
    }

    @Override
    public Map<String, Long> statusSummary() {
        return outboxMapper.selectList(Wrappers.<EventOutbox>lambdaQuery())
                .stream()
                .collect(Collectors.groupingBy(EventOutbox::getStatus, Collectors.counting()));
    }

    /**
     * 管理员重放：将指定 outbox 记录重新投递。记录审计日志。
     */
    @Override
    public boolean replay(Long outboxId) {
        log.info("管理员重放 outbox: id={}", outboxId);
        boolean updated = outboxMapper.update(null, Wrappers.<EventOutbox>lambdaUpdate()
                .eq(EventOutbox::getId, outboxId)
                .ne(EventOutbox::getStatus, "SENDING")
                .set(EventOutbox::getStatus, "PENDING")
                .set(EventOutbox::getAttemptCount, 0)
                .set(EventOutbox::getNextRetryTime, LocalDateTime.now())
                .set(EventOutbox::getErrorMessage, null)
                .set(EventOutbox::getLastFailedTime, null)) > 0;
        if (updated) {
            writeReplayAudit(outboxId);
        }
        return updated;
    }

    private void writeReplayAudit(Long outboxId) {
        try {
            SysOperationLog audit = new SysOperationLog();
            audit.setUserId(com.example.matching.utils.SecurityUtils.getCurrentUserId());
            audit.setRealName(com.example.matching.utils.SecurityUtils.getCurrentUsername());
            audit.setOperationModule("OUTBOX");
            audit.setOperationType("UPDATE");
            audit.setOperationDesc("事件Outbox消息人工重放: outboxId=" + outboxId);
            audit.setRequestUrl("/api/matching/outbox/replay");
            audit.setOperationTime(LocalDateTime.now());
            sysOperationLogService.save(audit);
        } catch (Exception e) {
            log.warn("Outbox 重放审计日志写入失败: outboxId={}", outboxId, e);
        }
    }

    private void dispatch(EventOutbox message) {
        int claimed = outboxMapper.update(null, Wrappers.<EventOutbox>lambdaUpdate()
                .eq(EventOutbox::getId, message.getId())
                .eq(EventOutbox::getStatus, "PENDING")
                .set(EventOutbox::getStatus, "SENDING"));
        if (claimed == 0) return;

        int maxAttempts = message.getMaxAttempts() != null ? message.getMaxAttempts() : DEFAULT_MAX_ATTEMPTS;
        int attempt = message.getAttemptCount() != null ? message.getAttemptCount() + 1 : 1;
        String outboxId = String.valueOf(message.getId());

        try {
            // 用原始 JSON 字节构造 Message，避免 Jackson2JsonMessageConverter 二次序列化
            MessageProperties props = new MessageProperties();
            props.setContentType(MessageProperties.CONTENT_TYPE_JSON);
            props.setContentEncoding("UTF-8");
            props.setCorrelationId(outboxId);
            String traceId = com.example.matching.common.trace.TraceContext.getOrNull();
            if (traceId != null) {
                props.setHeader("traceId", traceId);
            }
            byte[] body = message.getPayload().getBytes(StandardCharsets.UTF_8);
            Message amqpMessage = new Message(body, props);

            CorrelationData correlationData = new CorrelationData(outboxId);
            correlationData.getFuture().whenComplete((confirm, throwable) -> {
                boolean wasReturned = returnedIdCache.asMap().remove(outboxId) != null;

                if (wasReturned) {
                    String error = "Message returned by broker (unroutable): exchange=" + message.getExchange()
                            + ", routingKey=" + message.getRoutingKey();
                    handleRetry(message.getId(), attempt, maxAttempts, error);
                    return;
                }

                if (throwable == null && confirm != null && confirm.isAck()) {
                    outboxMapper.update(null, Wrappers.<EventOutbox>lambdaUpdate()
                            .eq(EventOutbox::getId, message.getId())
                            .eq(EventOutbox::getStatus, "SENDING")
                            .set(EventOutbox::getStatus, "PUBLISHED")
                            .set(EventOutbox::getPublishedTime, LocalDateTime.now())
                            .set(EventOutbox::getErrorMessage, null));
                    return;
                }

                String error = throwable != null ? throwable.getMessage()
                        : confirm != null ? confirm.getReason() : "Missing publisher confirm";
                handleRetry(message.getId(), attempt, maxAttempts, error);
            });

            rabbitTemplate.send(message.getExchange(), message.getRoutingKey(), amqpMessage, correlationData);
        } catch (Exception exception) {
            handleRetry(message.getId(), attempt, maxAttempts, exception.getMessage());
            log.warn("Event outbox delivery failed: eventType={}, attempt={}", message.getEventType(), attempt, exception);
        }
    }

    private void handleRetry(Long id, int attempt, int maxAttempts, String error) {
        if (attempt >= maxAttempts) {
            outboxMapper.update(null, Wrappers.<EventOutbox>lambdaUpdate()
                    .eq(EventOutbox::getId, id)
                    .eq(EventOutbox::getStatus, "SENDING")
                    .set(EventOutbox::getStatus, "FAILED")
                    .set(EventOutbox::getAttemptCount, attempt)
                    .set(EventOutbox::getErrorMessage, error)
                    .set(EventOutbox::getLastFailedTime, LocalDateTime.now())
                    .set(EventOutbox::getUpdatedTime, LocalDateTime.now()));
            log.error("Event outbox max attempts reached, marked FAILED: id={}, attempt={}", id, attempt);
            schedulerMetrics.recordFailure("event_outbox_terminal_failed");
        } else {
            long backoff = Math.min(MAX_BACKOFF_SECONDS, attempt * 10L);
            outboxMapper.update(null, Wrappers.<EventOutbox>lambdaUpdate()
                    .eq(EventOutbox::getId, id)
                    .eq(EventOutbox::getStatus, "SENDING")
                    .set(EventOutbox::getStatus, "PENDING")
                    .set(EventOutbox::getAttemptCount, attempt)
                    .set(EventOutbox::getNextRetryTime, LocalDateTime.now().plusSeconds(backoff))
                    .set(EventOutbox::getErrorMessage, error));
        }
    }
}
