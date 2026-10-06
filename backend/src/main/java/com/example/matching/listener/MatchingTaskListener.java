package com.example.matching.listener;

import com.example.matching.config.RabbitMQConfig;
import com.example.matching.common.enums.MatchingTaskStatus;
import com.example.matching.dto.matching.MatchingExecuteDTO;
import com.example.matching.entity.matching.MatchingRecord;
import com.example.matching.entity.matching.MatchingTask;
import com.example.matching.event.MatchingTaskCompletedEvent;
import com.example.matching.event.MatchingTaskFailedEvent;
import com.example.matching.service.matching.MatchingRecordService;
import com.example.matching.service.matching.MatchingTaskService;
import com.example.matching.utils.SecurityUtils;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

/**
 * 匹配任务消费者
 * <p>
 * 消息体仅传 taskId（轻量），消费端从 DB 加载完整 MatchingTask。
 * 幂等性：已完成的 task（status != 0）直接跳过。
 * <p>
 * 执行期间由守护心跳线程周期性 touchTask 刷新 updatedTime，
 * 防止长时间匹配被 MatchingTaskZombieScanner 误判为僵尸。
 */
@Slf4j
@Component
public class MatchingTaskListener {

    private final MatchingRecordService matchingRecordService;
    private final MatchingTaskService matchingTaskService;
    private final ObjectMapper objectMapper;
    private final ApplicationEventPublisher applicationEventPublisher;
    private final long heartbeatDelayMs;

    public MatchingTaskListener(MatchingRecordService matchingRecordService,
                                MatchingTaskService matchingTaskService,
                                ObjectMapper objectMapper,
                                ApplicationEventPublisher applicationEventPublisher,
                                @org.springframework.beans.factory.annotation.Value(
                                        "${matching.task.heartbeat-delay-ms:60000}") long heartbeatDelayMs) {
        this.matchingRecordService = matchingRecordService;
        this.matchingTaskService = matchingTaskService;
        this.objectMapper = objectMapper;
        this.applicationEventPublisher = applicationEventPublisher;
        this.heartbeatDelayMs = heartbeatDelayMs;
    }

    @RabbitListener(queues = RabbitMQConfig.MATCHING_TASK_QUEUE, containerFactory = "slowRabbitListenerContainerFactory")
    public void handleMatchingTask(String taskId) {
        log.info("收到匹配任务消息: taskId={}", taskId);

        // 幂等性检查：避免重复消费
        MatchingTask task = matchingTaskService.getTaskStatus(taskId);
        if (task == null) {
            log.warn("任务不存在: taskId={}", taskId);
            return;
        }
        if (task.getStatus() == null || task.getStatus() != MatchingTaskStatus.PENDING.getCode()) {
            log.info("任务已处理，跳过: taskId={}, status={}", taskId, task.getStatus());
            return;
        }

        // RabbitListener 线程不经 JwtFilter，注入系统身份供 MyMetaObjectHandler 审计字段使用
        if (!matchingTaskService.claimTask(taskId)) {
            log.info("Task was claimed by another consumer: taskId={}", taskId);
            return;
        }

        // RabbitListener 线程不经 JwtFilter：注入任务发起人身份（MyMetaObjectHandler 审计字段
        // 及匹配记录的 createdBy 归属），发起人为空时兜底系统身份
        if (task.getCreatedBy() != null) {
            SecurityUtils.setCurrentUserId(task.getCreatedBy());
        } else {
            SecurityUtils.setSystemContext();
        }
        java.util.concurrent.ScheduledExecutorService heartbeat = null;
        try {
            MatchingExecuteDTO dto = objectMapper.readValue(task.getMatchingConfig(), MatchingExecuteDTO.class);
            dto.setTaskExecution(true);
            // 批次号与任务ID以任务快照为准（防客户端伪造 config）
            dto.setBatchNo(task.getBatchNo());
            dto.setTaskId(taskId);
            log.info("匹配配置解析完成: mode={}, pairs={}, batchNo={}", dto.normalizedMode(), dto.normalizedPairs().size(), task.getBatchNo());

            if (heartbeatDelayMs > 0) {
                heartbeat = java.util.concurrent.Executors.newSingleThreadScheduledExecutor(r -> {
                    Thread thread = new Thread(r, "matching-task-heartbeat-" + taskId);
                    thread.setDaemon(true);
                    return thread;
                });
                heartbeat.scheduleAtFixedRate(() -> {
                    try {
                        matchingTaskService.touchTask(taskId);
                    } catch (Exception e) {
                        log.warn("匹配任务心跳失败: taskId={}, error={}", taskId, e.getMessage());
                    }
                }, heartbeatDelayMs, heartbeatDelayMs, java.util.concurrent.TimeUnit.MILLISECONDS);
            }

            var results = matchingRecordService.executeMatching(dto);
            String resultMessage = String.format("匹配完成，共处理%d条记录", results.size());
            // 仅当任务确实由 RUNNING 置为 COMPLETED（未被取消）时才发布完成事件
            boolean completed = matchingTaskService.completeTask(taskId, resultMessage);
            if (!completed) {
                log.info("匹配任务已完成但状态已变更（可能被取消），跳过完成事件: taskId={}", taskId);
                return;
            }

            String batchNo = results.isEmpty() ? null : results.get(0).getBatchNo();
            applicationEventPublisher.publishEvent(new MatchingTaskCompletedEvent(taskId, batchNo));

            log.info("匹配任务执行完成: taskId={}, count={}", taskId, results.size());
        } catch (Exception e) {
            log.error("匹配任务执行失败: taskId={}, error={}", taskId, e.getMessage(), e);
            // 用户已取消的任务不重试、不发布失败事件
            MatchingTask current = matchingTaskService.getTaskStatus(taskId);
            if (current != null && current.getStatus() == MatchingTaskStatus.CANCELLED.getCode()) {
                log.info("任务已取消，跳过重试与失败事件: taskId={}", taskId);
                return;
            }
            String reason = "匹配执行失败: " + e.getMessage();
            // 瞬时故障不直接终态：先按退避重试（最多 MAX_CONSUME_RETRIES 次），耗尽才 FAILED
            boolean retryScheduled = matchingTaskService.retryTask(taskId, reason);
            if (!retryScheduled) {
                applicationEventPublisher.publishEvent(new MatchingTaskFailedEvent(taskId, reason));
            }
        } finally {
            if (heartbeat != null) {
                heartbeat.shutdownNow();
            }
            SecurityUtils.clear();
        }
    }
}
