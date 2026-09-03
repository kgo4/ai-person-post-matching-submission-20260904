package com.example.matching.service.kg.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.example.matching.entity.kg.KgGraphChangeSet;
import com.example.matching.common.enums.TaskStatusEnum;
import com.example.matching.event.GraphChangeSetQueuedEvent;
import com.example.matching.mapper.kg.KgGraphChangeSetMapper;
import com.example.matching.mapper.kg.KgGraphBuildTaskMapper;
import com.example.matching.entity.kg.KgGraphBuildTask;
import com.example.matching.service.kg.GraphChangeSetService;
import com.example.matching.service.kg.KnowledgeGraphIncrementalService;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.Set;

@Slf4j
@Service
@RequiredArgsConstructor
public class GraphChangeSetServiceImpl implements GraphChangeSetService {

    private static final int MAX_RETRY_COUNT = 3;
    private static final String LOCK_NAME = "kg-graph-change-republish";
    private static final String EXECUTION_LOCK_NAME = "kg-graph-change-execute";

    private final KgGraphChangeSetMapper changeSetMapper;
    private final KgGraphBuildTaskMapper graphBuildTaskMapper;
    private final KnowledgeGraphIncrementalService incrementalService;
    private final ApplicationEventPublisher eventPublisher;
    private final ObjectMapper objectMapper;
    private final com.example.matching.service.common.DistributedLockService distributedLockService;
    private final com.example.matching.schedule.SchedulerMetrics schedulerMetrics;

    @org.springframework.beans.factory.annotation.Autowired(required = false)
    private com.example.matching.schedule.ScheduledTaskRunner taskRunner;

    @Override
    @Transactional
    public KgGraphChangeSet requestChange(String sourceType, String entityType, Long entityId,
                                          String operationType, Map<String, Object> payload, Long createdBy) {
        if (sourceType == null || sourceType.isBlank() || entityType == null || entityType.isBlank() || entityId == null) {
            throw new IllegalArgumentException("Graph change source, entity type and entity id are required");
        }
        if (!Set.of("UPSERT", "DELETE", "DISABLE").contains(operationType)) {
            throw new IllegalArgumentException("Unsupported graph change operation: " + operationType);
        }
        KgGraphChangeSet active = changeSetMapper.selectOne(Wrappers.<KgGraphChangeSet>lambdaQuery()
                .eq(KgGraphChangeSet::getSourceType, sourceType)
                .eq(KgGraphChangeSet::getEntityType, entityType)
                .eq(KgGraphChangeSet::getEntityId, entityId)
                .eq(KgGraphChangeSet::getOperationType, operationType)
                .in(KgGraphChangeSet::getProcessStatus, TaskStatusEnum.PENDING.getCode(), TaskStatusEnum.RUNNING.getCode(), TaskStatusEnum.RETRYING.getCode())
                .last("LIMIT 1"));
        if (active != null) {
            return active;
        }

        KgGraphChangeSet changeSet = new KgGraphChangeSet();
        changeSet.setChangeCode("KGC_" + UUID.randomUUID().toString().replace("-", "").substring(0, 16).toUpperCase());
        changeSet.setSourceType(sourceType);
        changeSet.setEntityType(entityType);
        changeSet.setEntityId(entityId);
        changeSet.setOperationType(operationType);
        changeSet.setPayloadJson(writeJson(payload));
        changeSet.setProcessStatus(TaskStatusEnum.PENDING.getCode());
        changeSet.setRetryCount(0);
        changeSet.setAffectedNodeCount(0);
        changeSet.setAffectedEdgeCount(0);
        changeSet.setCreatedBy(createdBy);
        changeSetMapper.insert(changeSet);
        eventPublisher.publishEvent(new GraphChangeSetQueuedEvent(changeSet.getChangeCode()));
        return changeSet;
    }

    @Override
    @Transactional
    public void executeChange(String changeCode) {
        // Graph node/edge replacement touches shared ability nodes. Serializing
        // the incremental writer avoids MySQL deadlocks across Rabbit consumers.
        var executionLock = distributedLockService.tryAcquire(EXECUTION_LOCK_NAME);
        if (executionLock == null) {
            log.debug("Graph change execution is busy; it will be republished: changeCode={}", changeCode);
            return;
        }
        try {
        int claimed = changeSetMapper.update(null, Wrappers.<KgGraphChangeSet>lambdaUpdate()
                .eq(KgGraphChangeSet::getChangeCode, changeCode)
                .in(KgGraphChangeSet::getProcessStatus, TaskStatusEnum.PENDING.getCode(), TaskStatusEnum.RETRYING.getCode())
                .set(KgGraphChangeSet::getProcessStatus, TaskStatusEnum.RUNNING.getCode())
                .set(KgGraphChangeSet::getStartedTime, LocalDateTime.now()));
        if (claimed == 0) {
            return;
        }

        if (hasActiveFullBuild()) {
            changeSetMapper.update(null, Wrappers.<KgGraphChangeSet>lambdaUpdate()
                    .eq(KgGraphChangeSet::getChangeCode, changeCode)
                    .set(KgGraphChangeSet::getProcessStatus, TaskStatusEnum.PENDING.getCode())
                    .set(KgGraphChangeSet::getStartedTime, null));
            return;
        }

        KgGraphChangeSet changeSet = changeSetMapper.selectOne(Wrappers.<KgGraphChangeSet>lambdaQuery()
                .eq(KgGraphChangeSet::getChangeCode, changeCode));
        try {
            KnowledgeGraphIncrementalService.IncrementalGraphResult result = incrementalService.apply(changeSet);
            changeSetMapper.update(null, Wrappers.<KgGraphChangeSet>lambdaUpdate()
                    .eq(KgGraphChangeSet::getChangeCode, changeCode)
                    .set(KgGraphChangeSet::getProcessStatus, TaskStatusEnum.SUCCEEDED.getCode())
                    .set(KgGraphChangeSet::getGraphVersion, result.graphVersion())
                    .set(KgGraphChangeSet::getAffectedNodeCount, result.affectedNodeCount())
                    .set(KgGraphChangeSet::getAffectedEdgeCount, result.affectedEdgeCount())
                    .set(KgGraphChangeSet::getCompletedTime, LocalDateTime.now())
                    .set(KgGraphChangeSet::getErrorMessage, null));
        } catch (Exception exception) {
            int retryCount = changeSet.getRetryCount() == null ? 1 : changeSet.getRetryCount() + 1;
            String status = retryCount >= MAX_RETRY_COUNT ? TaskStatusEnum.FAILED.getCode() : TaskStatusEnum.RETRYING.getCode();
            if (retryCount >= MAX_RETRY_COUNT) {
                log.error("Graph change permanently failed: changeCode={}, retry={}", changeCode, retryCount, exception);
                schedulerMetrics.recordFailure("kg_graph_change_execute");
            } else {
                log.warn("Graph change failed and will retry: changeCode={}, retry={}", changeCode, retryCount, exception);
            }
            changeSetMapper.update(null, Wrappers.<KgGraphChangeSet>lambdaUpdate()
                    .eq(KgGraphChangeSet::getChangeCode, changeCode)
                    .set(KgGraphChangeSet::getProcessStatus, status)
                    .set(KgGraphChangeSet::getRetryCount, retryCount)
                    .set(KgGraphChangeSet::getErrorMessage, truncate(exception.getMessage()))
                    .set(KgGraphChangeSet::getCompletedTime, LocalDateTime.now()));
        }
        } finally {
            executionLock.close();
        }
    }

    @Override
    @Scheduled(fixedDelayString = "${kg.graph.change.republish-delay-ms:60000}")
    public void republishPendingChanges() {
        if (taskRunner != null) {
            taskRunner.run("kg_graph_change_republish", this::republishPendingChangesInternal);
        } else {
            republishPendingChangesInternal();
        }
    }

    private void republishPendingChangesInternal() {
        var lock = distributedLockService.tryAcquire(LOCK_NAME);
        if (lock == null) {
            return;
        }
        try {
            List<KgGraphChangeSet> pending = changeSetMapper.selectList(Wrappers.<KgGraphChangeSet>lambdaQuery()
                    .in(KgGraphChangeSet::getProcessStatus, TaskStatusEnum.PENDING.getCode(), TaskStatusEnum.RETRYING.getCode())
                    .lt(KgGraphChangeSet::getRetryCount, MAX_RETRY_COUNT)
                    .orderByAsc(KgGraphChangeSet::getCreatedTime)
                    .last("LIMIT 100"));
            if (pending.isEmpty()) {
                return;
            }
            pending.forEach(changeSet -> eventPublisher.publishEvent(new GraphChangeSetQueuedEvent(changeSet.getChangeCode())));
        } catch (Exception e) {
            log.error("Graph change republish batch failed, pending changes may be delayed", e);
            schedulerMetrics.recordFailure("kg_graph_change_republish");
        } finally {
            lock.close();
        }
    }

    @Override
    public KgGraphChangeSet getChange(String changeCode) {
        return changeSetMapper.selectOne(Wrappers.<KgGraphChangeSet>lambdaQuery()
                .eq(KgGraphChangeSet::getChangeCode, changeCode)
                .last("LIMIT 1"));
    }

    @Override
    public List<KgGraphChangeSet> listChanges(String processStatus, Integer limit) {
        int effectiveLimit = limit == null ? 50 : Math.max(1, Math.min(limit, 200));
        return changeSetMapper.selectList(Wrappers.<KgGraphChangeSet>lambdaQuery()
                .eq(processStatus != null && !processStatus.isBlank(), KgGraphChangeSet::getProcessStatus, processStatus)
                .orderByDesc(KgGraphChangeSet::getCreatedTime)
                .last("LIMIT " + effectiveLimit));
    }

    @Override
    @Scheduled(fixedDelayString = "${kg.graph.change.zombie-scan-delay-ms:120000}")
    public void recoverZombieChangeSets() {
        if (taskRunner != null) {
            taskRunner.run("kg_graph_change_zombie_scan", this::recoverZombieChangeSetsInternal);
        } else {
            recoverZombieChangeSetsInternal();
        }
    }

    private void recoverZombieChangeSetsInternal() {
        try {
            LocalDateTime cutoff = LocalDateTime.now().minusMinutes(30);
            int updated = changeSetMapper.update(null, Wrappers.<KgGraphChangeSet>lambdaUpdate()
                    .eq(KgGraphChangeSet::getProcessStatus, TaskStatusEnum.RUNNING.getCode())
                    .le(KgGraphChangeSet::getStartedTime, cutoff)
                    .set(KgGraphChangeSet::getProcessStatus, TaskStatusEnum.RETRYING.getCode())
                    .set(KgGraphChangeSet::getErrorMessage, "Zombie change set recovered: stuck in RUNNING > 30 minutes"));
            if (updated > 0) {
                log.warn("僵尸图谱变更集已恢复: count={}", updated);
            }
        } catch (Exception e) {
            log.error("Graph change zombie scan failed", e);
            schedulerMetrics.recordFailure("kg_graph_change_zombie_scan");
        }
    }

    private String writeJson(Map<String, Object> payload) {
        try {
            return objectMapper.writeValueAsString(payload == null ? Map.of() : payload);
        } catch (Exception exception) {
            throw new IllegalArgumentException("Unable to serialize graph change payload", exception);
        }
    }

    private boolean hasActiveFullBuild() {
        return graphBuildTaskMapper.selectCount(Wrappers.<KgGraphBuildTask>lambdaQuery()
                .in(KgGraphBuildTask::getTaskStatus, TaskStatusEnum.PENDING.getCode(), TaskStatusEnum.RUNNING.getCode())) > 0;
    }

    private String truncate(String value) {
        if (value == null) return "Unknown incremental graph update failure";
        return value.length() > 2000 ? value.substring(0, 2000) : value;
    }
}
