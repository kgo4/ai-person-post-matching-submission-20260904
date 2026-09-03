package com.example.matching.service.common.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.example.matching.entity.common.VectorSyncTask;
import com.example.matching.entity.employee.EmpAbility;
import com.example.matching.entity.employee.EmpEmployee;
import com.example.matching.entity.post.PostAbilityModel;
import com.example.matching.entity.post.PostPost;
import com.example.matching.event.VectorSyncCompletedEvent;
import com.example.matching.mapper.common.VectorSyncTaskMapper;
import com.example.matching.schedule.SchedulerMetrics;
import com.example.matching.mapper.employee.EmpAbilityMapper;
import com.example.matching.mapper.employee.EmpEmployeeMapper;
import com.example.matching.mapper.post.PostAbilityModelMapper;
import com.example.matching.mapper.post.PostPostMapper;
import com.example.matching.service.common.VectorRecallCacheEpoch;
import com.example.matching.service.common.VectorSyncTaskService;
import com.example.matching.vector.MilvusVectorService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * 向量同步任务服务实现（M-09）：监听器只负责入队，后台任务负责写入 Milvus。
 * <p>
 * 业务唯一键（EMPLOYEE:{id} / POST:{id}）保证同一实体仅一条待办；执行幂等，
 * 以最新业务数据覆盖旧向量；失败指数退避（10s * 2^attempt，上限 5 分钟）、
 * 最大 10 次后置 FAILED，提供人工重放与失败指标。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class VectorSyncTaskServiceImpl implements VectorSyncTaskService {

    private static final int BATCH_SIZE = 50;
    private static final int DEFAULT_MAX_ATTEMPTS = 10;
    private static final long MAX_BACKOFF_SECONDS = 300;

    private final VectorSyncTaskMapper vectorSyncTaskMapper;
    private final EmpEmployeeMapper empEmployeeMapper;
    private final EmpAbilityMapper empAbilityMapper;
    private final PostPostMapper postPostMapper;
    private final PostAbilityModelMapper postAbilityModelMapper;
    private final com.example.matching.service.common.DistributedLockService distributedLockService;
    private final SchedulerMetrics schedulerMetrics;

    @Autowired(required = false)
    private com.example.matching.schedule.ScheduledTaskRunner taskRunner;

    @Autowired(required = false)
    private MilvusVectorService milvusVectorService;

    @Autowired(required = false)
    private CacheManager cacheManager;

    @Autowired(required = false)
    private VectorRecallCacheEpoch vectorRecallCacheEpoch;

    @Autowired(required = false)
    private ApplicationEventPublisher eventPublisher;

    /**
     * 监听器调用：入队（唯一键存在则刷新为 PENDING 并重置重试），不执行向量写入。
     */
    @Override
    @Transactional
    public void enqueue(String entityType, Long entityId, Map<String, Object> extra) {
        String key = VectorSyncTaskService.businessKey(entityType, entityId);
        VectorSyncTask existing = vectorSyncTaskMapper.selectOne(Wrappers.<VectorSyncTask>lambdaQuery()
                .eq(VectorSyncTask::getBusinessKey, key)
                .in(VectorSyncTask::getStatus, "PENDING", "PROCESSING", "FAILED", "SUCCEEDED")
                .last("LIMIT 1"));
        if (existing != null) {
            if ("FAILED".equals(existing.getStatus()) || "SUCCEEDED".equals(existing.getStatus())) {
                vectorSyncTaskMapper.update(null, Wrappers.<VectorSyncTask>lambdaUpdate()
                        .eq(VectorSyncTask::getId, existing.getId())
                        .set(VectorSyncTask::getStatus, "PENDING")
                        .set(VectorSyncTask::getAttemptCount, 0)
                        .set(VectorSyncTask::getNextRetryTime, LocalDateTime.now())
                        .set(VectorSyncTask::getErrorMessage, null)
                        .set(VectorSyncTask::getUpdatedTime, LocalDateTime.now()));
            }
            return;
        }
        VectorSyncTask task = new VectorSyncTask();
        task.setBusinessKey(key);
        task.setEntityType(entityType);
        task.setEntityId(entityId);
        task.setStatus("PENDING");
        task.setAttemptCount(0);
        task.setMaxAttempts(DEFAULT_MAX_ATTEMPTS);
        task.setCreatedTime(LocalDateTime.now());
        task.setUpdatedTime(LocalDateTime.now());
        vectorSyncTaskMapper.insert(task);
    }

    @Scheduled(fixedDelay = 5000)
    @Override
    public void processPendingTasks() {
        if (taskRunner != null) {
            taskRunner.run("vector_sync_task_process", this::processPendingTasksInternal);
        } else {
            processPendingTasksInternal();
        }
    }

    private void processPendingTasksInternal() {
        var lock = distributedLockService.tryAcquire("vector-sync-task-process");
        if (lock == null) {
            log.debug("Vector sync task processing skipped: lock held by another instance");
            return;
        }
        try {
            vectorSyncTaskMapper.update(null, Wrappers.<VectorSyncTask>lambdaUpdate()
                    .eq(VectorSyncTask::getStatus, "PROCESSING")
                    .lt(VectorSyncTask::getUpdatedTime, LocalDateTime.now().minusMinutes(5))
                    .set(VectorSyncTask::getStatus, "PENDING"));

            List<VectorSyncTask> pending = vectorSyncTaskMapper.selectList(
                    Wrappers.<VectorSyncTask>lambdaQuery()
                            .eq(VectorSyncTask::getStatus, "PENDING")
                            .and(q -> q.isNull(VectorSyncTask::getNextRetryTime)
                                    .or().le(VectorSyncTask::getNextRetryTime, LocalDateTime.now()))
                            .orderByAsc(VectorSyncTask::getCreatedTime)
                            .last("LIMIT " + BATCH_SIZE));

            pending.forEach(this::processOne);
        } catch (Exception e) {
            log.error("Vector sync task batch failed, pending syncs may be delayed", e);
            schedulerMetrics.recordFailure("vector_sync_task_process");
        } finally {
            lock.close();
        }
    }

    private void processOne(VectorSyncTask task) {
        int claimed = vectorSyncTaskMapper.update(null, Wrappers.<VectorSyncTask>lambdaUpdate()
                .eq(VectorSyncTask::getId, task.getId())
                .eq(VectorSyncTask::getStatus, "PENDING")
                .set(VectorSyncTask::getStatus, "PROCESSING")
                .set(VectorSyncTask::getUpdatedTime, LocalDateTime.now()));
        if (claimed == 0) {
            return;
        }

        try {
            executeSync(task);
            vectorSyncTaskMapper.update(null, Wrappers.<VectorSyncTask>lambdaUpdate()
                    .eq(VectorSyncTask::getId, task.getId())
                    .eq(VectorSyncTask::getStatus, "PROCESSING")
                    .set(VectorSyncTask::getStatus, "SUCCEEDED")
                    .set(VectorSyncTask::getPublishedTime, LocalDateTime.now())
                    .set(VectorSyncTask::getErrorMessage, null)
                    .set(VectorSyncTask::getUpdatedTime, LocalDateTime.now()));
            if (eventPublisher != null) {
                eventPublisher.publishEvent(new VectorSyncCompletedEvent(task.getEntityType(), task.getEntityId()));
            }
            evictVectorRecall();
        } catch (Exception e) {
            handleFailure(task, e);
        }
    }

    /**
     * 幂等执行：始终以最新业务数据覆盖旧向量，可安全重放。
     * Milvus 返回 false 视为失败（抛异常），由调用方进入退避重试。
     */
    private void executeSync(VectorSyncTask task) {
        if (milvusVectorService == null) {
            throw new IllegalStateException("MilvusVectorService not available for vector sync");
        }
        if (ENTITY_EMPLOYEE.equals(task.getEntityType())) {
            EmpEmployee employee = empEmployeeMapper.selectById(task.getEntityId());
            if (employee == null) {
                throw new IllegalStateException("Employee not found for vector sync: empId=" + task.getEntityId());
            }
            List<EmpAbility> abilities = empAbilityMapper.selectList(Wrappers.<EmpAbility>lambdaQuery()
                    .eq(EmpAbility::getEmpId, task.getEntityId()));
            if (abilities == null) {
                abilities = Collections.emptyList();
            }
            boolean written = milvusVectorService.insertEmployeeVector(task.getEntityId(), employee, abilities);
            if (!written) {
                throw new IllegalStateException("Milvus insertEmployeeVector returned false: empId=" + task.getEntityId());
            }
            log.info("Vector sync succeeded for employee: empId={}", task.getEntityId());
        } else if (ENTITY_POST.equals(task.getEntityType())) {
            PostPost post = postPostMapper.selectById(task.getEntityId());
            if (post == null) {
                throw new IllegalStateException("Post not found for vector sync: postId=" + task.getEntityId());
            }
            List<PostAbilityModel> requirements = postAbilityModelMapper.selectList(Wrappers.<PostAbilityModel>lambdaQuery()
                    .eq(PostAbilityModel::getPostId, task.getEntityId()));
            if (requirements == null) {
                requirements = Collections.emptyList();
            }
            boolean written = milvusVectorService.insertPostVector(task.getEntityId(), post, requirements);
            if (!written) {
                throw new IllegalStateException("Milvus insertPostVector returned false: postId=" + task.getEntityId());
            }
            log.info("Vector sync succeeded for post: postId={}", task.getEntityId());
        } else {
            throw new IllegalStateException("Unknown vector sync entity type: " + task.getEntityType());
        }
    }

    private void handleFailure(VectorSyncTask task, Exception e) {
        int attempt = (task.getAttemptCount() == null ? 0 : task.getAttemptCount()) + 1;
        if (attempt >= task.getMaxAttempts()) {
            vectorSyncTaskMapper.update(null, Wrappers.<VectorSyncTask>lambdaUpdate()
                    .eq(VectorSyncTask::getId, task.getId())
                    .eq(VectorSyncTask::getStatus, "PROCESSING")
                    .set(VectorSyncTask::getStatus, "FAILED")
                    .set(VectorSyncTask::getAttemptCount, attempt)
                    .set(VectorSyncTask::getErrorMessage, truncate(e.getMessage()))
                    .set(VectorSyncTask::getUpdatedTime, LocalDateTime.now()));
            log.error("Vector sync permanently failed: entityType={}, entityId={}, attempt={}",
                    task.getEntityType(), task.getEntityId(), attempt, e);
            schedulerMetrics.recordFailure("vector_sync_task");
        } else {
            long backoff = Math.min(MAX_BACKOFF_SECONDS, (1L << Math.min(attempt, 5)) * 10L);
            vectorSyncTaskMapper.update(null, Wrappers.<VectorSyncTask>lambdaUpdate()
                    .eq(VectorSyncTask::getId, task.getId())
                    .eq(VectorSyncTask::getStatus, "PROCESSING")
                    .set(VectorSyncTask::getStatus, "PENDING")
                    .set(VectorSyncTask::getAttemptCount, attempt)
                    .set(VectorSyncTask::getNextRetryTime, LocalDateTime.now().plusSeconds(backoff))
                    .set(VectorSyncTask::getErrorMessage, truncate(e.getMessage()))
                    .set(VectorSyncTask::getUpdatedTime, LocalDateTime.now()));
            log.warn("Vector sync failed and will retry: entityType={}, entityId={}, attempt={}, backoff={}s",
                    task.getEntityType(), task.getEntityId(), attempt, backoff, e);
        }
    }

    /**
     * 人工重放：将 FAILED 记录重置为 PENDING。
     */
    @Override
    public boolean replay(Long taskId) {
        return vectorSyncTaskMapper.update(null, Wrappers.<VectorSyncTask>lambdaUpdate()
                .eq(VectorSyncTask::getId, taskId)
                .ne(VectorSyncTask::getStatus, "PROCESSING")
                .set(VectorSyncTask::getStatus, "PENDING")
                .set(VectorSyncTask::getAttemptCount, 0)
                .set(VectorSyncTask::getNextRetryTime, LocalDateTime.now())
                .set(VectorSyncTask::getErrorMessage, null)) > 0;
    }

    private String truncate(String message) {
        if (message == null) {
            return null;
        }
        return message.length() > 500 ? message.substring(0, 500) : message;
    }

    /**
     * 向量同步完成后失效召回缓存：优先递增 epoch（精细失效），
     * Redis 不可用时退化为全量清空并告警。
     */
    private void evictVectorRecall() {
        if (vectorRecallCacheEpoch != null && vectorRecallCacheEpoch.advance() >= 0) {
            return;
        }
        log.warn("Vector recall epoch unavailable, falling back to full cache clear");
        if (cacheManager == null) {
            return;
        }
        Cache cache = cacheManager.getCache(com.example.matching.config.RedisCacheNames.VECTOR_RECALL);
        if (cache != null) {
            cache.clear();
        }
    }
}
