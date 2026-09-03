package com.example.matching.service.matching;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.example.matching.config.RabbitMQConfig;
import com.example.matching.config.ReturnedMessageHandler;
import com.example.matching.entity.matching.MatchingTaskOutbox;
import com.example.matching.mapper.matching.MatchingTaskOutboxMapper;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;

@Slf4j
@Component
@RequiredArgsConstructor
public class MatchingTaskOutboxDispatcher implements ReturnedMessageHandler {

    private static final int BATCH_SIZE = 50;
    private static final int MAX_ATTEMPTS = 10;
    private static final long MAX_BACKOFF_SECONDS = 300;
    private static final String LOCK_NAME = "matching-task-outbox-dispatch";
    private static final long RETURNED_IDS_MAX_SIZE = 10_000;
    private static final Duration RETURNED_IDS_TTL = Duration.ofMinutes(10);

    private final MatchingTaskOutboxMapper outboxMapper;
    private final RabbitTemplate rabbitTemplate;
    private final MatchingTaskService matchingTaskService;
    private final com.example.matching.service.common.DistributedLockService distributedLockService;
    private final com.example.matching.schedule.SchedulerMetrics schedulerMetrics;
    private final MeterRegistry meterRegistry;

    @org.springframework.beans.factory.annotation.Autowired(required = false)
    private com.example.matching.service.system.SysOperationLogService sysOperationLogService;

    @org.springframework.beans.factory.annotation.Autowired(required = false)
    private com.example.matching.schedule.ScheduledTaskRunner taskRunner;

    private final AtomicLong expiredReturnedIds = new AtomicLong();

    /** 有界返回消息追踪：最多 10K 条、10 分钟过期，防止不可达消息的 correlationId 无限累积。 */
    private final Cache<String, Boolean> returnedIds = Caffeine.newBuilder()
            .maximumSize(RETURNED_IDS_MAX_SIZE)
            .expireAfterWrite(RETURNED_IDS_TTL)
            .removalListener((key, value, cause) -> {
                if (cause.wasEvicted()) {
                    expiredReturnedIds.incrementAndGet();
                }
            })
            .build();

    @Override
    public void onMessageReturned(String correlationId) {
        markReturned(correlationId);
    }

    public void markReturned(String correlationId) {
        if (correlationId != null) {
            returnedIds.put(correlationId, Boolean.TRUE);
        }
    }

    public boolean wasReturned(String correlationId) {
        if (correlationId == null) {
            return false;
        }
        Boolean present = returnedIds.getIfPresent(correlationId);
        if (present != null) {
            returnedIds.invalidate(correlationId);
            return true;
        }
        return false;
    }

    public long returnedIdCount() {
        return returnedIds.estimatedSize();
    }

    public long expiredReturnedIdCount() {
        return expiredReturnedIds.get();
    }

    @Scheduled(fixedDelay = 5000)
    public void dispatchPendingMessages() {
        if (taskRunner != null) {
            taskRunner.run("matching_task_outbox_dispatch", this::dispatchPendingMessagesInternal);
        } else {
            dispatchPendingMessagesInternal();
        }
    }

    private void dispatchPendingMessagesInternal() {
        var lock = distributedLockService.tryAcquire(LOCK_NAME);
        if (lock == null) {
            log.debug("Matching task outbox dispatch skipped: lock held by another instance");
            return;
        }
        try {
            outboxMapper.update(null, Wrappers.<MatchingTaskOutbox>lambdaUpdate()
                    .eq(MatchingTaskOutbox::getStatus, "SENDING")
                    .lt(MatchingTaskOutbox::getUpdatedTime, LocalDateTime.now().minusMinutes(5))
                    .set(MatchingTaskOutbox::getStatus, "PENDING"));
            List<MatchingTaskOutbox> pendingMessages = outboxMapper.selectList(
                    Wrappers.<MatchingTaskOutbox>lambdaQuery()
                            .eq(MatchingTaskOutbox::getStatus, "PENDING")
                            .and(query -> query.isNull(MatchingTaskOutbox::getNextRetryTime)
                                    .or().le(MatchingTaskOutbox::getNextRetryTime, LocalDateTime.now()))
                            .orderByAsc(MatchingTaskOutbox::getCreatedTime)
                            .last("LIMIT " + BATCH_SIZE));
            pendingMessages.forEach(this::dispatch);
        } catch (Exception e) {
            log.error("Matching task outbox dispatch batch failed, pending messages may be delayed", e);
            schedulerMetrics.recordFailure("matching_task_outbox_dispatch");
        } finally {
            lock.close();
        }
    }

    public Map<String, Long> statusSummary() {
        return outboxMapper.selectList(Wrappers.<MatchingTaskOutbox>lambdaQuery())
                .stream()
                .collect(Collectors.groupingBy(MatchingTaskOutbox::getStatus, Collectors.counting()));
    }

    public boolean replay(Long outboxId) {
        log.info("管理员重放 matching task outbox: id={}", outboxId);
        boolean updated = outboxMapper.update(null, Wrappers.<MatchingTaskOutbox>lambdaUpdate()
                .eq(MatchingTaskOutbox::getId, outboxId)
                .ne(MatchingTaskOutbox::getStatus, "SENDING")
                .set(MatchingTaskOutbox::getStatus, "PENDING")
                .set(MatchingTaskOutbox::getAttemptCount, 0)
                .set(MatchingTaskOutbox::getNextRetryTime, LocalDateTime.now())
                .set(MatchingTaskOutbox::getErrorMessage, null)
                .set(MatchingTaskOutbox::getLastFailedTime, null)) > 0;
        if (updated) {
            MatchingTaskOutbox outbox = outboxMapper.selectById(outboxId);
            if (outbox != null && outbox.getTaskId() != null) {
                try {
                    matchingTaskService.requeueAfterDispatchFailure(outbox.getTaskId());
                } catch (Exception e) {
                    log.error("匹配任务重新入队失败: taskId={}", outbox.getTaskId(), e);
                }
            }
            writeReplayAudit(outboxId);
        }
        return updated;
    }

    private void writeReplayAudit(Long outboxId) {
        try {
            com.example.matching.entity.system.SysOperationLog audit =
                    new com.example.matching.entity.system.SysOperationLog();
            audit.setUserId(com.example.matching.utils.SecurityUtils.getCurrentUserId());
            audit.setRealName(com.example.matching.utils.SecurityUtils.getCurrentUsername());
            audit.setOperationModule("OUTBOX");
            audit.setOperationType("UPDATE");
            audit.setOperationDesc("匹配任务Outbox消息人工重放: outboxId=" + outboxId);
            audit.setRequestUrl("/api/matching/outbox/replay");
            audit.setOperationTime(LocalDateTime.now());
            sysOperationLogService.save(audit);
        } catch (Exception e) {
            log.warn("Matching task outbox 重放审计日志写入失败: outboxId={}", outboxId, e);
        }
    }

    private void dispatch(MatchingTaskOutbox message) {
        int claimed = outboxMapper.update(null, Wrappers.<MatchingTaskOutbox>lambdaUpdate()
                .eq(MatchingTaskOutbox::getId, message.getId())
                .eq(MatchingTaskOutbox::getStatus, "PENDING")
                .set(MatchingTaskOutbox::getStatus, "SENDING"));
        if (claimed == 0) return;

        int attempt = message.getAttemptCount() != null ? message.getAttemptCount() + 1 : 1;
        String outboxId = String.valueOf(message.getId());

        try {
            // 原始 JSON 字节发送，避免二次序列化
            MessageProperties props = new MessageProperties();
            props.setContentType(MessageProperties.CONTENT_TYPE_JSON);
            props.setContentEncoding("UTF-8");
            props.setCorrelationId(outboxId);
            String traceId = com.example.matching.common.trace.TraceContext.getOrNull();
            if (traceId != null) {
                props.setHeader("traceId", traceId);
            }
            byte[] body = message.getPayload() != null
                    ? message.getPayload().getBytes(StandardCharsets.UTF_8)
                    : "{}".getBytes(StandardCharsets.UTF_8);
            Message amqpMessage = new Message(body, props);

            CorrelationData correlationData = new CorrelationData(outboxId);
            correlationData.getFuture().whenComplete((confirm, throwable) -> {
                boolean wasReturned = wasReturned(outboxId);

                if (wasReturned) {
                    handleRetry(message.getId(), attempt, "Message returned (unroutable)");
                    return;
                }

                if (throwable == null && confirm != null && confirm.isAck()) {
                    outboxMapper.update(null, Wrappers.<MatchingTaskOutbox>lambdaUpdate()
                            .eq(MatchingTaskOutbox::getId, message.getId())
                            .eq(MatchingTaskOutbox::getStatus, "SENDING")
                            .set(MatchingTaskOutbox::getStatus, "PUBLISHED")
                            .set(MatchingTaskOutbox::getPublishedTime, LocalDateTime.now())
                            .set(MatchingTaskOutbox::getErrorMessage, null));
                    return;
                }

                String error = throwable != null ? throwable.getMessage()
                        : confirm != null ? confirm.getReason() : "Missing publisher confirm";
                handleRetry(message.getId(), attempt, error);
            });

            rabbitTemplate.send(RabbitMQConfig.MATCHING_EXCHANGE, message.getRoutingKey(), amqpMessage, correlationData);
        } catch (Exception exception) {
            handleRetry(message.getId(), attempt, exception.getMessage());
            log.warn("Matching task outbox delivery failed: taskId={}, attempt={}", message.getTaskId(), attempt, exception);
        }
    }

    private void handleRetry(Long id, int attempt, String error) {
        if (attempt >= MAX_ATTEMPTS) {
            outboxMapper.update(null, Wrappers.<MatchingTaskOutbox>lambdaUpdate()
                    .eq(MatchingTaskOutbox::getId, id)
                    .eq(MatchingTaskOutbox::getStatus, "SENDING")
                    .set(MatchingTaskOutbox::getStatus, "FAILED")
                    .set(MatchingTaskOutbox::getAttemptCount, attempt)
                    .set(MatchingTaskOutbox::getErrorMessage, error)
                    .set(MatchingTaskOutbox::getLastFailedTime, LocalDateTime.now())
                    .set(MatchingTaskOutbox::getUpdatedTime, LocalDateTime.now()));
            MatchingTaskOutbox failed = outboxMapper.selectById(id);
            if (failed != null && failed.getTaskId() != null) {
                try {
                    matchingTaskService.markDispatchFailed(failed.getTaskId(), error);
                } catch (Exception e) {
                    log.error("匹配任务标记分发失败时出错: taskId={}", failed.getTaskId(), e);
                }
            }
            log.error("Matching task outbox max attempts reached, marked FAILED: id={}, attempt={}", id, attempt);
            schedulerMetrics.recordFailure("matching_task_outbox_terminal_failed");
        } else {
            long backoff = Math.min(MAX_BACKOFF_SECONDS, attempt * 10L);
            outboxMapper.update(null, Wrappers.<MatchingTaskOutbox>lambdaUpdate()
                    .eq(MatchingTaskOutbox::getId, id)
                    .eq(MatchingTaskOutbox::getStatus, "SENDING")
                    .set(MatchingTaskOutbox::getStatus, "PENDING")
                    .set(MatchingTaskOutbox::getAttemptCount, attempt)
                    .set(MatchingTaskOutbox::getNextRetryTime, LocalDateTime.now().plusSeconds(backoff))
                    .set(MatchingTaskOutbox::getErrorMessage, error));
        }
    }
}
