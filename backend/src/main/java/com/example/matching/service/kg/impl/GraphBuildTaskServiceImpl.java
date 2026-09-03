package com.example.matching.service.kg.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.example.matching.common.enums.TaskStatusEnum;
import com.example.matching.dto.kg.GraphBuildResultDTO;
import com.example.matching.dto.kg.GraphBuildTaskStatusDTO;
import com.example.matching.entity.kg.KgGraphBuildTask;
import com.example.matching.event.GraphBuildQueuedEvent;
import com.example.matching.mapper.common.JobLockMapper;
import com.example.matching.mapper.kg.KgGraphBuildTaskMapper;
import com.example.matching.service.kg.GraphBuildTaskService;
import com.example.matching.service.kg.KnowledgeGraphBuildService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class GraphBuildTaskServiceImpl implements GraphBuildTaskService {

    private final KgGraphBuildTaskMapper taskMapper;
    private final KnowledgeGraphBuildService knowledgeGraphBuildService;
    private final ObjectMapper objectMapper;
    private final ApplicationEventPublisher eventPublisher;
    private final JobLockMapper jobLockMapper;
    private final com.example.matching.service.common.EventOutboxDispatcher outboxDispatcher;

    @org.springframework.beans.factory.annotation.Autowired(required = false)
    private com.example.matching.schedule.ScheduledTaskRunner taskRunner;

    @org.springframework.beans.factory.annotation.Autowired(required = false)
    private com.example.matching.schedule.SchedulerMetrics schedulerMetrics;

    private static final String LOCK_NAME = "FULL_GRAPH_REBUILD";
    private static final long LOCK_TTL_MINUTES = 30;
    private static final int MAX_RETRY_COUNT = 3;
    private static final long ZOMBIE_TIMEOUT_MINUTES = 30;

    @Override
    @Transactional
    public GraphBuildTaskStatusDTO requestFullRebuild(Long requestedBy) {
        // CAS 跨实例锁：防止并发请求创建重复全量构建任务
        String lockedBy = "instance-" + ProcessHandle.current().pid() + "-" + Thread.currentThread().getId();
        String expiresAt = LocalDateTime.now().plusMinutes(LOCK_TTL_MINUTES).toString();

        int acquired = jobLockMapper.acquireLock(LOCK_NAME, lockedBy, expiresAt);
        if (acquired == 0) {
            log.info("全量构建锁已被其他实例持有: lockName={}", LOCK_NAME);
            // 返回当前活跃任务状态
            KgGraphBuildTask activeTask = taskMapper.selectOne(Wrappers.<KgGraphBuildTask>lambdaQuery()
                    .in(KgGraphBuildTask::getTaskStatus, TaskStatusEnum.PENDING.getCode(), TaskStatusEnum.RUNNING.getCode())
                    .orderByDesc(KgGraphBuildTask::getCreatedTime)
                    .last("LIMIT 1"));
            if (activeTask != null) return toStatus(activeTask);
            // 锁已过期但任务已完成，允许继续
            jobLockMapper.releaseLock(LOCK_NAME, lockedBy);
            acquired = jobLockMapper.acquireLock(LOCK_NAME, lockedBy, expiresAt);
            if (acquired == 0) {
                log.warn("无法获取全量构建锁，跳过");
                return null;
            }
        }

        try {
            KgGraphBuildTask activeTask = taskMapper.selectOne(Wrappers.<KgGraphBuildTask>lambdaQuery()
                    .in(KgGraphBuildTask::getTaskStatus, TaskStatusEnum.PENDING.getCode(), TaskStatusEnum.RUNNING.getCode())
                    .orderByDesc(KgGraphBuildTask::getCreatedTime)
                    .last("LIMIT 1"));
            if (activeTask != null) {
                return toStatus(activeTask);
            }

            KgGraphBuildTask task = new KgGraphBuildTask();
            task.setTaskCode("KGB_" + UUID.randomUUID().toString().replace("-", "").substring(0, 16).toUpperCase());
            task.setTaskStatus(TaskStatusEnum.PENDING.getCode());
            task.setRequestedBy(requestedBy);
            taskMapper.insert(task);
            eventPublisher.publishEvent(new GraphBuildQueuedEvent(task.getTaskCode()));
            return toStatus(task);
        } finally {
            jobLockMapper.releaseLock(LOCK_NAME, lockedBy);
        }
    }

    @Override
    public GraphBuildTaskStatusDTO getTaskStatus(String taskCode) {
        KgGraphBuildTask task = taskMapper.selectOne(Wrappers.<KgGraphBuildTask>lambdaQuery()
                .eq(KgGraphBuildTask::getTaskCode, taskCode));
        return task == null ? null : toStatus(task);
    }

    @Override
    public void executeQueuedTask(String taskCode) {
        // CAS 抢占：PENDING 或 RETRYING → RUNNING
        int claimed = taskMapper.update(null, Wrappers.<KgGraphBuildTask>lambdaUpdate()
                .eq(KgGraphBuildTask::getTaskCode, taskCode)
                .in(KgGraphBuildTask::getTaskStatus,
                        TaskStatusEnum.PENDING.getCode(), TaskStatusEnum.RETRYING.getCode())
                .set(KgGraphBuildTask::getTaskStatus, TaskStatusEnum.RUNNING.getCode())
                .set(KgGraphBuildTask::getStartedTime, LocalDateTime.now()));
        if (claimed == 0) {
            log.info("Skipping graph build task that is not pending/retrying: taskCode={}", taskCode);
            return;
        }

        try {
            GraphBuildResultDTO result = knowledgeGraphBuildService.rebuildFullGraph();
            taskMapper.update(null, Wrappers.<KgGraphBuildTask>lambdaUpdate()
                    .eq(KgGraphBuildTask::getTaskCode, taskCode)
                    .set(KgGraphBuildTask::getTaskStatus, result.isSuccess() ? TaskStatusEnum.SUCCEEDED.getCode() : TaskStatusEnum.FAILED.getCode())
                    .set(KgGraphBuildTask::getResultJson, writeJson(result))
                    .set(KgGraphBuildTask::getErrorMessage, result.isSuccess() ? null : result.getMessage())
                    .set(KgGraphBuildTask::getCompletedTime, LocalDateTime.now()));
        } catch (Exception exception) {
            log.error("Graph build task failed: taskCode={}", taskCode, exception);
            // 获取当前重试次数
            KgGraphBuildTask current = taskMapper.selectOne(Wrappers.<KgGraphBuildTask>lambdaQuery()
                    .eq(KgGraphBuildTask::getTaskCode, taskCode).last("LIMIT 1"));
            int retryCount = current != null && current.getRetryCount() != null ? current.getRetryCount() + 1 : 1;
            String status = retryCount >= MAX_RETRY_COUNT
                    ? TaskStatusEnum.FAILED.getCode()
                    : TaskStatusEnum.RETRYING.getCode();

            taskMapper.update(null, Wrappers.<KgGraphBuildTask>lambdaUpdate()
                    .eq(KgGraphBuildTask::getTaskCode, taskCode)
                    .set(KgGraphBuildTask::getTaskStatus, status)
                    .set(KgGraphBuildTask::getRetryCount, retryCount)
                    .set(KgGraphBuildTask::getErrorMessage, truncate(exception.getMessage()))
                    .set(KgGraphBuildTask::getCompletedTime, LocalDateTime.now()));

            if (retryCount < MAX_RETRY_COUNT) {
                log.info("Graph build task will retry: taskCode={}, attempt={}", taskCode, retryCount);
                enqueueRetry(taskCode);
            } else {
                log.error("Graph build task max retries reached: taskCode={}", taskCode);
            }
        }
    }

    /**
     * 启动时和定时扫描：回收超时 RUNNING 任务，重新投递（M29：接入 ScheduledTaskRunner + 分布式锁，
     * CAS 更新只允许影响 1 行时投递重试，防止多实例重复投递）。
     */
    @org.springframework.scheduling.annotation.Scheduled(fixedDelay = 60000)
    public void recoverZombieTasks() {
        if (taskRunner != null) {
            taskRunner.run("kg_graph_build_zombie_scan", this::recoverZombieTasksInternal);
        } else {
            recoverZombieTasksInternal();
        }
    }

    private void recoverZombieTasksInternal() {
        try {
            LocalDateTime threshold = LocalDateTime.now().minusMinutes(ZOMBIE_TIMEOUT_MINUTES);
            List<KgGraphBuildTask> zombies = taskMapper.selectList(Wrappers.<KgGraphBuildTask>lambdaQuery()
                    .eq(KgGraphBuildTask::getTaskStatus, TaskStatusEnum.RUNNING.getCode())
                    .lt(KgGraphBuildTask::getStartedTime, threshold));

            for (KgGraphBuildTask zombie : zombies) {
                int retryCount = zombie.getRetryCount() != null ? zombie.getRetryCount() + 1 : 1;
                String status = retryCount >= MAX_RETRY_COUNT
                        ? TaskStatusEnum.FAILED.getCode()
                        : TaskStatusEnum.RETRYING.getCode();

                int rows = taskMapper.update(null, Wrappers.<KgGraphBuildTask>lambdaUpdate()
                        .eq(KgGraphBuildTask::getTaskCode, zombie.getTaskCode())
                        .eq(KgGraphBuildTask::getTaskStatus, TaskStatusEnum.RUNNING.getCode())
                        .set(KgGraphBuildTask::getTaskStatus, status)
                        .set(KgGraphBuildTask::getRetryCount, retryCount)
                        .set(KgGraphBuildTask::getErrorMessage, "Zombie task recovered after timeout"));

                if (rows != 1) {
                    // 其他实例已回收该任务：跳过，避免重复投递
                    log.debug("Zombie task already claimed by another instance, skip: taskCode={}", zombie.getTaskCode());
                    continue;
                }

                if (retryCount < MAX_RETRY_COUNT) {
                    log.warn("Recovered zombie graph build task: taskCode={}, attempt={}", zombie.getTaskCode(), retryCount);
                    enqueueRetry(zombie.getTaskCode());
                } else {
                    log.error("Zombie graph build task max retries reached: taskCode={}", zombie.getTaskCode());
                }
            }
        } catch (Exception e) {
            log.error("Graph build zombie scan failed", e);
            if (schedulerMetrics != null) {
                schedulerMetrics.recordFailure("kg_graph_build_zombie_scan");
            }
        }
    }

    /**
     * 直接写入 Outbox，不依赖 @TransactionalEventListener。
     * EventOutboxDispatcher.enqueue 在其代理上开启短事务。
     */
    protected void enqueueRetry(String taskCode) {
        outboxDispatcher.enqueue("KG_GRAPH_BUILD",
                com.example.matching.config.RabbitMQConfig.MATCHING_EXCHANGE,
                "kg.graph.build.execute",
                new GraphBuildQueuedEvent(taskCode));
    }

    private String truncate(String value) {
        if (value == null) return "Unknown error";
        return value.length() > 2000 ? value.substring(0, 2000) : value;
    }

    private GraphBuildTaskStatusDTO toStatus(KgGraphBuildTask task) {
        GraphBuildTaskStatusDTO status = new GraphBuildTaskStatusDTO();
        status.setTaskCode(task.getTaskCode());
        status.setTaskStatus(task.getTaskStatus());
        status.setErrorMessage(task.getErrorMessage());
        status.setCreatedTime(task.getCreatedTime());
        status.setStartedTime(task.getStartedTime());
        status.setCompletedTime(task.getCompletedTime());
        if (task.getResultJson() != null) {
            try {
                status.setResult(objectMapper.readValue(task.getResultJson(), GraphBuildResultDTO.class));
            } catch (JsonProcessingException exception) {
                log.warn("Unable to read graph build result: taskCode={}", task.getTaskCode(), exception);
            }
        }
        return status;
    }

    private String writeJson(GraphBuildResultDTO result) {
        try {
            return objectMapper.writeValueAsString(result);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Unable to serialize graph build result", exception);
        }
    }
}
