package com.example.matching.schedule;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.example.matching.entity.system.AbilityTagMergeTask;
import com.example.matching.mapper.system.AbilityTagMergeTaskMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import java.time.LocalDateTime;
import java.util.*;

@Slf4j
@Component
@RequiredArgsConstructor
public class MergeScheduleManager {
    private final AbilityTagMergeTaskMapper taskMapper;
    private final TagMergeScheduler tagMergeScheduler;

    /** Compatibility entry point for internal callers without an authenticated operator. */
    public Map<String, Object> schedule(LocalDateTime scheduledTime, double threshold) {
        return schedule(scheduledTime, threshold, 0L);
    }

    public Map<String, Object> schedule(LocalDateTime scheduledTime, double threshold, Long operatorId) {
        if (scheduledTime == null || !scheduledTime.isAfter(LocalDateTime.now())) throw new IllegalArgumentException("scheduled time must be in the future");
        if (Double.isNaN(threshold) || threshold < 0.5d || threshold > 1d) throw new IllegalArgumentException("merge threshold must be between 0.5 and 1.0");
        AbilityTagMergeTask task = new AbilityTagMergeTask();
        task.setTaskCode("TAG_MERGE_" + UUID.randomUUID().toString().replace("-", "").substring(0, 16));
        task.setThreshold(threshold); task.setScheduledTime(scheduledTime); task.setStatus("PENDING");
        task.setCreatedBy(operatorId == null ? 0L : operatorId);
        taskMapper.insert(task);
        return taskView(task);
    }

    public boolean cancel(String taskCode) { return taskMapper.cancelPendingTask(taskCode) > 0; }

    public List<Map<String, Object>> listPending() {
        return taskMapper.selectList(Wrappers.<AbilityTagMergeTask>lambdaQuery().eq(AbilityTagMergeTask::getStatus, "PENDING").orderByAsc(AbilityTagMergeTask::getScheduledTime))
                .stream().map(this::taskView).toList();
    }

    public List<Map<String, Object>> listRecentTerminalTasks(Long operatorId) {
        return taskMapper.selectRecentTerminalTasks(operatorId == null ? 0L : operatorId, 10)
                .stream().map(this::taskView).toList();
    }

    @Scheduled(fixedDelay = 30_000, initialDelay = 15_000)
    public void runDueTasks() {
        for (AbilityTagMergeTask task : taskMapper.selectDueTasks(LocalDateTime.now())) {
            if (taskMapper.claimPendingTask(task.getId()) == 0) continue;
            try {
                Map<String, Object> result = tagMergeScheduler.executeMerge(task.getThreshold());
                taskMapper.markCompleted(task.getId(), result.toString());
                log.info("定时标签归并完成: taskCode={}, result={}", task.getTaskCode(), result);
            } catch (Exception ex) {
                taskMapper.markFailed(task.getId(), safeMessage(ex));
                log.error("定时标签归并失败: taskCode={}", task.getTaskCode(), ex);
            }
        }
    }

    private Map<String, Object> taskView(AbilityTagMergeTask task) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("taskId", task.getTaskCode()); result.put("scheduledTime", task.getScheduledTime().toString()); result.put("threshold", task.getThreshold());
        result.put("status", task.getStatus()); result.put("completedTime", task.getCompletedTime() == null ? null : task.getCompletedTime().toString());
        result.put("resultSummary", task.getResultSummary()); result.put("errorMessage", task.getErrorMessage());
        return result;
    }

    private String safeMessage(Exception ex) {
        String message = ex.getMessage() == null ? ex.getClass().getSimpleName() : ex.getMessage();
        return message.length() <= 1000 ? message : message.substring(0, 1000);
    }
}
