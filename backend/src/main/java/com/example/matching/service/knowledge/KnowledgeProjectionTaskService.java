package com.example.matching.service.knowledge;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.example.matching.entity.common.KnowledgeProjectionTask;
import com.example.matching.mapper.common.KnowledgeProjectionTaskMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class KnowledgeProjectionTaskService {

    private static final String COL_PROJECTION = "projection";
    private static final String COL_AGGREGATE_TYPE = "aggregate_type";
    private static final String COL_AGGREGATE_ID = "aggregate_id";
    private static final String COL_TARGET_REVISION = "target_revision";
    private static final String COL_OPERATION = "operation";
    private static final String COL_PAYLOAD_HASH = "payload_hash";
    private static final String COL_STATUS = "status";
    private static final String COL_ATTEMPT_COUNT = "attempt_count";
    private static final String COL_MAX_ATTEMPTS = "max_attempts";
    private static final String COL_NEXT_RETRY_TIME = "next_retry_time";
    private static final String COL_LEASE_UNTIL = "lease_until";
    private static final String COL_ERROR_MESSAGE = "error_message";
    private static final String COL_COMPLETED_TIME = "completed_time";
    private static final String COL_CREATED_TIME = "created_time";
    private static final String COL_UPDATED_TIME = "updated_time";

    private final KnowledgeProjectionTaskMapper mapper;

    @Transactional
    public void enqueue(KnowledgeProjectionTask.Projection projection,
                        String aggregateType,
                        long aggregateId,
                        long targetRevision,
                        KnowledgeProjectionTask.Operation operation,
                        String payloadHash) {
        KnowledgeProjectionTask task = new KnowledgeProjectionTask();
        task.setProjection(projection.name());
        task.setAggregateType(aggregateType);
        task.setAggregateId(aggregateId);
        task.setTargetRevision(targetRevision);
        task.setOperation(operation.name());
        task.setPayloadHash(payloadHash);
        task.setStatus(KnowledgeProjectionTask.Status.PENDING.name());
        task.setAttemptCount(0);
        task.setMaxAttempts(10);
        task.setCreatedTime(LocalDateTime.now());
        task.setUpdatedTime(LocalDateTime.now());

        try {
            mapper.insert(task);
        } catch (DuplicateKeyException e) {
            // A projection is version-idempotent, but a prior terminal task must
            // be reusable when an external projection recovered (Milvus/Neo4j).
            // Do not touch PENDING/PROCESSING rows: another worker may own them.
            UpdateWrapper<KnowledgeProjectionTask> revive = new UpdateWrapper<>();
            revive.eq(COL_PROJECTION, projection.name())
                    .eq(COL_AGGREGATE_TYPE, aggregateType)
                    .eq(COL_AGGREGATE_ID, aggregateId)
                    .eq(COL_TARGET_REVISION, targetRevision)
                    .in(COL_STATUS, KnowledgeProjectionTask.Status.SUCCEEDED.name(),
                            KnowledgeProjectionTask.Status.FAILED.name())
                    .set(COL_STATUS, KnowledgeProjectionTask.Status.PENDING.name())
                    .set(COL_ATTEMPT_COUNT, 0)
                    .set(COL_NEXT_RETRY_TIME, null)
                    .set(COL_LEASE_UNTIL, null)
                    .set(COL_ERROR_MESSAGE, null)
                    .set(COL_COMPLETED_TIME, null)
                    .set(COL_UPDATED_TIME, LocalDateTime.now());
            int revived = mapper.update(null, revive);
            log.debug(revived == 1
                            ? "Projection task revived for compensation: projection={}, agg={}, rev={}"
                            : "Projection task is already active: projection={}, agg={}, rev={}",
                    projection, aggregateId, targetRevision);
        }
    }

    @Transactional
    public List<KnowledgeProjectionTask> claimNextBatch(KnowledgeProjectionTask.Projection projection, int limit) {
        LocalDateTime now = LocalDateTime.now();

        QueryWrapper<KnowledgeProjectionTask> query = new QueryWrapper<>();
        query.eq(COL_PROJECTION, projection.name())
                .eq(COL_STATUS, KnowledgeProjectionTask.Status.PENDING.name())
                .and(w -> w.isNull(COL_NEXT_RETRY_TIME).or().le(COL_NEXT_RETRY_TIME, now))
                .and(w -> w.isNull(COL_LEASE_UNTIL).or().lt(COL_LEASE_UNTIL, now))
                .orderByAsc(COL_TARGET_REVISION)
                .orderByAsc("id")
                .last("LIMIT " + limit);

        List<KnowledgeProjectionTask> candidates = mapper.selectList(query);
        if (candidates.isEmpty()) {
            return List.of();
        }

        LocalDateTime leaseUntil = now.plusMinutes(5);
        List<KnowledgeProjectionTask> claimed = new ArrayList<>(candidates.size());
        for (KnowledgeProjectionTask candidate : candidates) {
            UpdateWrapper<KnowledgeProjectionTask> update = new UpdateWrapper<>();
            update.eq("id", candidate.getId())
                    .eq(COL_STATUS, KnowledgeProjectionTask.Status.PENDING.name())
                    .and(w -> w.isNull(COL_LEASE_UNTIL).or().lt(COL_LEASE_UNTIL, now))
                    .set(COL_STATUS, KnowledgeProjectionTask.Status.PROCESSING.name())
                    .set(COL_LEASE_UNTIL, leaseUntil)
                    .set(COL_UPDATED_TIME, now);
            if (mapper.update(null, update) == 1) {
                candidate.setStatus(KnowledgeProjectionTask.Status.PROCESSING.name());
                candidate.setLeaseUntil(leaseUntil);
                claimed.add(candidate);
            }
        }
        return claimed;
    }

    @Transactional
    public void markSucceeded(Long taskId) {
        LocalDateTime now = LocalDateTime.now();
        UpdateWrapper<KnowledgeProjectionTask> update = new UpdateWrapper<>();
        update.eq("id", taskId)
                .set(COL_STATUS, KnowledgeProjectionTask.Status.SUCCEEDED.name())
                .set(COL_COMPLETED_TIME, now)
                .set(COL_LEASE_UNTIL, null)
                .set(COL_UPDATED_TIME, now);
        mapper.update(null, update);
    }

    @Transactional
    public void markFailed(Long taskId, String errorMessage) {
        KnowledgeProjectionTask task = mapper.selectById(taskId);
        if (task == null) {
            return;
        }
        int attempts = (task.getAttemptCount() != null ? task.getAttemptCount() : 0) + 1;
        LocalDateTime now = LocalDateTime.now();

        UpdateWrapper<KnowledgeProjectionTask> update = new UpdateWrapper<>();
        update.eq("id", taskId)
                .set(COL_ATTEMPT_COUNT, attempts)
                .set(COL_ERROR_MESSAGE, truncate(errorMessage, 2000));

        if (attempts >= task.getMaxAttempts()) {
            update.set(COL_STATUS, KnowledgeProjectionTask.Status.FAILED.name())
                    .set(COL_LEASE_UNTIL, null)
                    .set(COL_COMPLETED_TIME, now);
        } else {
            long delaySeconds = Math.min((long) Math.pow(2, Math.min(attempts, 6)), 3600L);
            update.set(COL_STATUS, KnowledgeProjectionTask.Status.PENDING.name())
                    .set(COL_NEXT_RETRY_TIME, now.plusSeconds(delaySeconds))
                    .set(COL_LEASE_UNTIL, null);
        }
        update.set(COL_UPDATED_TIME, now);
        mapper.update(null, update);
    }

    private String truncate(String value, int maxLength) {
        if (value == null) return null;
        return value.length() <= maxLength ? value : value.substring(0, maxLength);
    }
}
