package com.example.matching.service.matching.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.example.matching.dto.matching.MatchingExecuteDTO;
import com.example.matching.dto.matching.MatchingExecuteDTO.MatchingPair;
import com.example.matching.common.enums.MatchingTaskStatus;
import org.springframework.beans.factory.ObjectProvider;
import com.example.matching.entity.closure.MatchingRematchValidation;
import com.example.matching.entity.matching.MatchingTask;
import com.example.matching.entity.matching.MatchingTaskOutbox;
import com.example.matching.mapper.closure.MatchingRematchValidationMapper;
import com.example.matching.mapper.matching.MatchingTaskMapper;
import com.example.matching.mapper.matching.MatchingTaskOutboxMapper;
import com.example.matching.service.matching.MatchingRecordService;
import com.example.matching.service.matching.MatchingTaskService;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * 匹配任务服务实现类
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MatchingTaskServiceImpl extends ServiceImpl<MatchingTaskMapper, MatchingTask>
        implements MatchingTaskService {

    private final MatchingTaskOutboxMapper outboxMapper;
    private final ObjectMapper objectMapper;
    private final ObjectProvider<MatchingRecordService> matchingRecordServiceProvider;
    private final MatchingRematchValidationMapper rematchValidationMapper;

    @Override
    @Transactional
    public String submitTask(MatchingExecuteDTO dto) {
        String taskId = UUID.randomUUID().toString().replace("-", "");
        var pairs = dto.normalizedPairs();

        // 生成批次号并写入任务与 DTO：消费端 execute 复用同一批次，删除任务可连带删除该批次记录
        String batchNo = UUID.randomUUID().toString().replace("-", "").substring(0, 16);
        dto.setBatchNo(batchNo);

        MatchingTask task = new MatchingTask();
        task.setTaskId(taskId);
        task.setBatchNo(batchNo);
        task.setPostId(pairs.size() == 1 ? pairs.get(0).getPostId() : dto.normalizedPostIds().stream().findFirst().orElse(null));
        task.setStatus(MatchingTaskStatus.PENDING.getCode());
        task.setProgress(0);
        task.setTotalCount(pairs.size());
        task.setProcessedCount(0);

        if (!pairs.isEmpty()) {
            try {
                task.setEmpIds(objectMapper.writeValueAsString(pairs.stream().map(MatchingPair::getEmpId).distinct().toList()));
            } catch (Exception e) {
                log.warn("序列化员工ID列表失败: {}", e.getMessage());
            }
        }

        try {
            task.setMatchingConfig(objectMapper.writeValueAsString(dto));
        } catch (Exception e) {
            log.warn("序列化匹配配置失败: {}", e.getMessage());
        }

        save(task);

        MatchingTaskOutbox outbox = new MatchingTaskOutbox();
        outbox.setTaskId(taskId);
        outbox.setRoutingKey("matching.task.execute");
        // payload 存储合法 JSON string（带引号），发送时作为 application/json 原始字节
        outbox.setPayload("\"" + taskId + "\"");
        outbox.setStatus("PENDING");
        outbox.setAttemptCount(0);
        outboxMapper.insert(outbox);
        log.info("匹配任务及可靠投递消息已创建 taskId={}", taskId);

        return taskId;
    }

    @Override
    public MatchingTask getTaskStatus(String taskId) {
        return getOne(Wrappers.<MatchingTask>lambdaQuery()
                .eq(MatchingTask::getTaskId, taskId));
    }

    @Override
    @Transactional
    public boolean claimTask(String taskId) {
        return lambdaUpdate()
                .eq(MatchingTask::getTaskId, taskId)
                .eq(MatchingTask::getStatus, MatchingTaskStatus.PENDING.getCode())
                .set(MatchingTask::getStatus, MatchingTaskStatus.RUNNING.getCode())
                .set(MatchingTask::getProgress, 0)
                .update();
    }

    @Override
    @Transactional
    public void updateProgress(String taskId, int processedCount, int totalCount) {
        MatchingTask task = getTaskStatus(taskId);
        if (task == null) {
            log.warn("任务不存在: taskId={}", taskId);
            return;
        }

        task.setProcessedCount(processedCount);
        task.setTotalCount(totalCount);
        task.setProgress(totalCount > 0 ? (int) ((double) processedCount / totalCount * 100) : 0);
        updateById(task);
    }

    @Override
    @Transactional
    public boolean completeTask(String taskId, String resultMessage) {
        MatchingTask task = getTaskStatus(taskId);
        if (task == null) {
            log.warn("任务不存在: taskId={}", taskId);
            return false;
        }
        // CAS：仅 RUNNING 可置 COMPLETED——用户已取消（CANCELLED）或已失败（FAILED）的任务
        // 消费线程跑完后也无法覆盖其终态，避免取消后结果"复活"
        boolean updated = lambdaUpdate()
                .eq(MatchingTask::getTaskId, taskId)
                .eq(MatchingTask::getStatus, MatchingTaskStatus.RUNNING.getCode())
                .set(MatchingTask::getStatus, MatchingTaskStatus.COMPLETED.getCode())
                .set(MatchingTask::getProgress, 100)
                .set(MatchingTask::getProcessedCount, task.getTotalCount())
                .set(MatchingTask::getResultMessage, resultMessage)
                .update();
        if (updated) {
            log.info("匹配任务完成: taskId={}, message={}", taskId, resultMessage);
        } else {
            log.info("匹配任务完成被跳过（非 RUNNING 状态）: taskId={}", taskId);
        }
        return updated;
    }

    @Override
    @Transactional
    public boolean deleteTask(String taskId) {
        MatchingTask task = getTaskStatus(taskId);
        if (task == null) {
            log.warn("任务不存在，无法删除: taskId={}", taskId);
            return false;
        }
        // 进行中任务先取消（CAS 置 CANCELLED，消费线程无法复活），再物理删除
        if (task.getStatus() == MatchingTaskStatus.PENDING.getCode()
                || task.getStatus() == MatchingTaskStatus.RUNNING.getCode()) {
            cancelTask(taskId);
        }
        // 清理任务子表
        outboxMapper.delete(Wrappers.<MatchingTaskOutbox>lambdaQuery()
                .eq(MatchingTaskOutbox::getTaskId, taskId));
        rematchValidationMapper.delete(Wrappers.<MatchingRematchValidation>lambdaQuery()
                .eq(MatchingRematchValidation::getTaskId, taskId));
        // 连带删除该任务批次产生的匹配记录（含审批流/反馈数据集子表）
        // ObjectProvider 延迟解析：MatchingRecordService → MatchingExecuteService → MatchingTaskService 存在循环，
        // 构造期注入会构成 bean 环，改为运行时按需获取（deleteTask 调用时环上各 bean 均已就绪）
        if (task.getBatchNo() != null && !task.getBatchNo().isBlank()) {
            MatchingRecordService recordService = matchingRecordServiceProvider.getIfAvailable();
            if (recordService != null) {
                int deleted = recordService.deleteByBatchNo(task.getBatchNo());
                log.info("删除任务连带清理匹配记录: taskId={}, batchNo={}, records={}", taskId, task.getBatchNo(), deleted);
            }
        } else {
            // V116 之前的旧任务无 batch_no：无法定位其匹配记录，仅删任务本身
            log.warn("任务无批次号（旧数据），跳过匹配记录清理: taskId={}", taskId);
        }
        // 物理删除任务（matching_task 无 @TableLogic）
        boolean removed = remove(Wrappers.<MatchingTask>lambdaQuery()
                .eq(MatchingTask::getTaskId, taskId));
        log.info("匹配任务已删除: taskId={}, removed={}", taskId, removed);
        return removed;
    }

    @Override
    @Transactional
    public boolean cancelTask(String taskId) {
        return lambdaUpdate()
                .eq(MatchingTask::getTaskId, taskId)
                .in(MatchingTask::getStatus,
                        MatchingTaskStatus.PENDING.getCode(),
                        MatchingTaskStatus.RUNNING.getCode())
                .set(MatchingTask::getStatus, MatchingTaskStatus.CANCELLED.getCode())
                .set(MatchingTask::getResultMessage, "任务已取消")
                .update();
    }

    @Override
    public com.baomidou.mybatisplus.core.metadata.IPage<MatchingTask> pageTasks(long current, long size, Integer status) {
        var wrapper = Wrappers.<MatchingTask>lambdaQuery()
                .eq(status != null, MatchingTask::getStatus, status)
                .orderByDesc(MatchingTask::getCreatedTime);
        return page(new com.baomidou.mybatisplus.extension.plugins.pagination.Page<>(current, size), wrapper);
    }

    @Override
    @Transactional
    public void failTask(String taskId, String errorMessage) {
        MatchingTask task = getTaskStatus(taskId);
        if (task == null) {
            log.warn("任务不存在: taskId={}", taskId);
            return;
        }
        // CAS：仅 RUNNING 可置 FAILED——已取消（CANCELLED）任务不被覆盖（与 completeTask 一致）
        boolean updated = lambdaUpdate()
                .eq(MatchingTask::getTaskId, taskId)
                .eq(MatchingTask::getStatus, MatchingTaskStatus.RUNNING.getCode())
                .set(MatchingTask::getStatus, MatchingTaskStatus.FAILED.getCode())
                .set(MatchingTask::getErrorMessage, errorMessage)
                .update();
        if (updated) {
            log.error("匹配任务失败: taskId={}, error={}", taskId, errorMessage);
        } else {
            log.warn("匹配任务失败被跳过（非 RUNNING 状态，可能已取消）: taskId={}", taskId);
        }
    }

    @Override
    @Transactional
    public boolean retryTask(String taskId, String errorMessage) {
        MatchingTask task = getTaskStatus(taskId);
        if (task == null) {
            log.warn("任务不存在，无法重试: taskId={}", taskId);
            return false;
        }

        int attempt = (task.getRetryCount() != null ? task.getRetryCount() : 0) + 1;
        // CAS 抢占：仅 RUNNING 且未达上限的任务可进入重试调度，防并发重投
        // 注意 retry_count 可能为 NULL（首次失败），需显式处理 NULL 比较
        boolean scheduled = lambdaUpdate()
                .eq(MatchingTask::getTaskId, taskId)
                .eq(MatchingTask::getStatus, MatchingTaskStatus.RUNNING.getCode())
                .and(w -> w.isNull(MatchingTask::getRetryCount)
                        .or().lt(MatchingTask::getRetryCount, MatchingTaskService.MAX_CONSUME_RETRIES))
                .set(MatchingTask::getStatus, MatchingTaskStatus.PENDING.getCode())
                .set(MatchingTask::getRetryCount, attempt)
                .set(MatchingTask::getNextRetryTime, java.time.LocalDateTime.now().plusSeconds(
                        Math.min(300L, 15L * (1L << Math.min(attempt - 1, 4)))))
                .set(MatchingTask::getErrorMessage, errorMessage)
                .update();
        if (scheduled) {
            // 重新入 outbox 驱动重投（分发器 5s 轮询 + 发送侧退避重试）
            MatchingTaskOutbox outbox = new MatchingTaskOutbox();
            outbox.setTaskId(taskId);
            outbox.setRoutingKey("matching.task.execute");
            outbox.setPayload("\"" + taskId + "\"");
            outbox.setStatus("PENDING");
            outbox.setAttemptCount(0);
            outboxMapper.insert(outbox);
            log.warn("匹配任务消费失败，已调度重试并重投: taskId={}, attempt={}, error={}", taskId, attempt, errorMessage);
            return true;
        }

        // 达到重试上限：终态失败（原 failTask 语义）
        lambdaUpdate()
                .eq(MatchingTask::getTaskId, taskId)
                .eq(MatchingTask::getStatus, MatchingTaskStatus.RUNNING.getCode())
                .set(MatchingTask::getStatus, MatchingTaskStatus.FAILED.getCode())
                .set(MatchingTask::getRetryCount, attempt)
                .set(MatchingTask::getErrorMessage, errorMessage)
                .update();
        log.error("匹配任务重试耗尽，置为失败终态: taskId={}, attempts={}, error={}", taskId, attempt, errorMessage);
        return false;
    }

    @Override
    @Transactional
    public int recoverZombieTasks(Duration olderThan) {
        LocalDateTime cutoff = LocalDateTime.now().minus(olderThan);
        int updated = getBaseMapper().update(null,
                new com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper<MatchingTask>()
                        .eq(MatchingTask::getStatus, MatchingTaskStatus.RUNNING.getCode())
                        .le(MatchingTask::getUpdatedTime, cutoff)
                        .set(MatchingTask::getStatus, MatchingTaskStatus.FAILED.getCode())
                        .set(MatchingTask::getErrorMessage,
                                "Zombie task recovered: no status change within " + olderThan.toMinutes() + " minutes"));
        if (updated > 0) {
            log.warn("僵尸匹配任务已恢复: count={}, cutoff={}", updated, cutoff);
        }
        return updated;
    }

    @Override
    @Transactional
    public void touchTask(String taskId) {
        lambdaUpdate()
                .eq(MatchingTask::getTaskId, taskId)
                .eq(MatchingTask::getStatus, MatchingTaskStatus.RUNNING.getCode())
                .setSql("updated_time = NOW()")
                .update();
    }

    @Override
    @Transactional
    public boolean markDispatchFailed(String taskId, String errorMessage) {
        return lambdaUpdate()
                .eq(MatchingTask::getTaskId, taskId)
                .eq(MatchingTask::getStatus, MatchingTaskStatus.PENDING.getCode())
                .set(MatchingTask::getStatus, MatchingTaskStatus.FAILED.getCode())
                .set(MatchingTask::getErrorMessage, errorMessage)
                .set(MatchingTask::getUpdatedTime, LocalDateTime.now())
                .update();
    }

    @Override
    @Transactional
    public boolean requeueAfterDispatchFailure(String taskId) {
        return lambdaUpdate()
                .eq(MatchingTask::getTaskId, taskId)
                .in(MatchingTask::getStatus, MatchingTaskStatus.PENDING.getCode(), MatchingTaskStatus.FAILED.getCode())
                .set(MatchingTask::getStatus, MatchingTaskStatus.PENDING.getCode())
                .set(MatchingTask::getErrorMessage, null)
                .set(MatchingTask::getNextRetryTime, null)
                .set(MatchingTask::getUpdatedTime, LocalDateTime.now())
                .update();
    }
}
