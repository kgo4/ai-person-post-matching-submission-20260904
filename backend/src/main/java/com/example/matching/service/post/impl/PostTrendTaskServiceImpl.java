package com.example.matching.service.post.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.example.matching.common.dto.PageResponse;
import com.example.matching.common.enums.TaskStatusEnum;
import com.example.matching.common.exception.BusinessException;
import com.example.matching.common.exception.ErrorCodeEnum;
import com.example.matching.dto.post.PostTrendAnalysisSummary;
import com.example.matching.entity.post.PostTrendCandidate;
import com.example.matching.entity.post.PostTrendTask;
import com.example.matching.event.PostTrendAnalysisQueuedEvent;
import com.example.matching.mapper.post.PostTrendCandidateMapper;
import com.example.matching.mapper.post.PostTrendTaskMapper;
import com.example.matching.service.post.PostTrendTaskService;
import com.example.matching.vo.post.PostTrendProgressVO;
import com.example.matching.vo.post.PostTrendTaskVO;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 岗位趋势解析任务生命周期实现。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PostTrendTaskServiceImpl implements PostTrendTaskService {

    /** 停在 RUNNING 超过该时长即视为僵尸任务。解析含多次 LLM 调用，阈值需大于正常最坏耗时。 */
    private static final int ZOMBIE_TIMEOUT_MINUTES = 30;
    private static final int ZOMBIE_SCAN_LIMIT = 50;

    private final PostTrendTaskMapper taskMapper;
    private final PostTrendCandidateMapper candidateMapper;
    private final ApplicationEventPublisher eventPublisher;
    private final ObjectMapper objectMapper;

    @Override
    @Transactional
    public PostTrendTask createTask(List<Long> sourceDocumentIds, List<String> sourceCategories,
                                    String taskName, Long operatorId) {
        if (sourceDocumentIds == null || sourceDocumentIds.isEmpty()) {
            throw new BusinessException(ErrorCodeEnum.PARAM_ERROR, "请先上传至少一份权威材料，再开始解析");
        }
        PostTrendTask task = new PostTrendTask();
        task.setTaskCode("TREND_" + UUID.randomUUID().toString().replace("-", "").substring(0, 16).toUpperCase());
        task.setTaskName(StringUtils.hasText(taskName) ? taskName : "岗位趋势解析 " + LocalDate.now());
        task.setSourceDocumentIds(writeJson(sourceDocumentIds, "[]"));
        task.setSourceCategories(writeJson(sourceCategories == null ? List.of() : sourceCategories, "[]"));
        task.setTaskStatus(TaskStatusEnum.PENDING.getCode());
        task.setProgressStatus("QUEUED");
        task.setProgressPercent(0);
        task.setCandidateCount(0);
        task.setNewPostCount(0);
        task.setChangeCount(0);
        task.setOperatorId(operatorId);
        taskMapper.insert(task);

        eventPublisher.publishEvent(new PostTrendAnalysisQueuedEvent(task.getId(), sourceDocumentIds, sourceCategories));
        log.info("岗位趋势解析任务已创建: taskId={}, documentCount={}", task.getId(), sourceDocumentIds.size());
        return task;
    }

    @Override
    public PostTrendTask requireTask(Long taskId) {
        PostTrendTask task = taskId == null ? null : taskMapper.selectById(taskId);
        if (task == null) {
            throw new BusinessException(ErrorCodeEnum.NOT_FOUND, "趋势解析任务不存在: " + taskId);
        }
        return task;
    }

    @Override
    public PostTrendTaskVO getTask(Long taskId) {
        return toVO(requireTask(taskId));
    }

    @Override
    public PageResponse<PostTrendTaskVO> pageTasks(long current, long size) {
        Page<PostTrendTask> page = taskMapper.selectPage(new Page<>(current, size),
                new LambdaQueryWrapper<PostTrendTask>().orderByDesc(PostTrendTask::getId));
        return PageResponse.from(page, this::toVO);
    }

    @Override
    public PostTrendProgressVO getProgress(Long taskId) {
        PostTrendTask task = requireTask(taskId);
        PostTrendProgressVO progress = new PostTrendProgressVO();
        progress.setTaskId(taskId);
        progress.setTaskStatus(task.getTaskStatus());
        progress.setCurrentStep(task.getProgressStatus());
        progress.setPercent(task.getProgressPercent() != null ? task.getProgressPercent() : 0);
        progress.setErrorMessage(task.getErrorMessage());
        progress.setDiagnostics(task.getDiagnostics());
        progress.setSteps(List.of(
                step(task, "RETRIEVING", "检索权威材料"),
                step(task, "EXTRACTING", "抽取趋势岗位"),
                step(task, "MATCHING", "比对既有岗位"),
                step(task, "HARNESS", "治理判定"),
                step(task, "COMPLETED", "解析完成")));
        return progress;
    }

    private PostTrendProgressVO.StepProgress step(PostTrendTask task, String code, String label) {
        return new PostTrendProgressVO.StepProgress(label, stepStatus(task, code));
    }

    /** 步骤顺序决定 PENDING / RUNNING / DONE 的判定。 */
    private static final List<String> STEP_ORDER =
            List.of("RETRIEVING", "EXTRACTING", "MATCHING", "HARNESS", "COMPLETED");

    private String stepStatus(PostTrendTask task, String code) {
        String current = task.getProgressStatus();
        if (current == null) {
            return "PENDING";
        }
        if ("FAILED".equals(current)) {
            return "ERROR";
        }
        if ("CANCELLED".equals(current)) {
            return "PENDING";
        }
        int currentIndex = STEP_ORDER.indexOf(current);
        int stepIndex = STEP_ORDER.indexOf(code);
        if (currentIndex < 0 || stepIndex < 0) {
            return "PENDING";
        }
        if (stepIndex < currentIndex) {
            return "DONE";
        }
        if (stepIndex == currentIndex) {
            return "COMPLETED".equals(current) ? "DONE" : "RUNNING";
        }
        return "PENDING";
    }

    @Override
    public boolean cancel(Long taskId) {
        requireTask(taskId);
        int updated = taskMapper.cancelActiveTask(taskId, TaskStatusEnum.CANCELLED.getCode());
        if (updated == 0) {
            log.info("趋势解析任务已在终态，取消未生效: taskId={}", taskId);
            return false;
        }
        log.info("趋势解析任务已取消: taskId={}", taskId);
        return true;
    }

    @Override
    public void reportProgress(Long taskId, String progressStatus, int percent) {
        taskMapper.updateProgress(taskId, TaskStatusEnum.RUNNING.getCode(), progressStatus, percent);
    }

    @Override
    public void markAnalysisFinished(Long taskId, PostTrendAnalysisSummary summary) {
        PostTrendAnalysisSummary effective = summary == null
                ? PostTrendAnalysisSummary.empty("解析未返回任何结果，请重试或更换材料")
                : summary;
        boolean hasCandidate = effective.candidateCount() > 0;
        String terminalStatus = hasCandidate
                ? TaskStatusEnum.WAIT_CONFIRM.getCode()
                : TaskStatusEnum.COMPLETED.getCode();

        int updated = taskMapper.finishRunningTask(taskId, TaskStatusEnum.RUNNING.getCode(),
                terminalStatus, "COMPLETED", 100);
        if (updated == 0) {
            // 任务已被取消或已失败：不要用解析结果覆盖终态
            log.info("趋势解析任务已不在运行中，跳过结果写入: taskId={}", taskId);
            return;
        }

        PostTrendTask task = taskMapper.selectById(taskId);
        if (task == null) {
            return;
        }
        task.setCandidateCount(effective.candidateCount());
        task.setNewPostCount(effective.newPostCount());
        task.setChangeCount(effective.changeCount());
        task.setDiagnostics(effective.diagnostics());
        task.setResultSummary(writeJson(Map.of(
                "indexedDocumentCount", effective.indexedDocumentCount(),
                "extractedPostCount", effective.extractedPostCount(),
                "filteredByEmphasisCount", effective.filteredByEmphasisCount(),
                "similarityServiceAvailable", effective.similarityServiceAvailable()), "{}"));
        taskMapper.updateById(task);
        log.info("趋势解析任务完成: taskId={}, candidates={}, newPost={}, change={}",
                taskId, effective.candidateCount(), effective.newPostCount(), effective.changeCount());
    }

    @Override
    public void markFailed(Long taskId, String errorMessage) {
        String message = StringUtils.hasText(errorMessage) ? errorMessage : "解析失败，请重试";
        // 截断到列长度，避免超长异常信息导致写入失败
        if (message.length() > 2000) {
            message = message.substring(0, 2000);
        }
        int updated = taskMapper.failRunningTask(taskId, TaskStatusEnum.RUNNING.getCode(),
                TaskStatusEnum.FAILED.getCode(), message);
        if (updated == 0) {
            log.info("趋势解析任务已不在运行中，跳过失败写入: taskId={}", taskId);
        }
    }

    /**
     * 审核结束后的状态收敛。
     * <p>
     * 只有「所有候选都不再是 PENDING」时才允许离开 WAIT_CONFIRM：中途点了一张卡就改任务状态，
     * 会让界面上「待确认 3 项」的任务显示成已完成，剩下的候选再没人看。
     */
    @Override
    public void refreshConfirmStatus(Long taskId) {
        if (taskId == null) {
            return;
        }
        long pending = candidateMapper.selectCount(new LambdaQueryWrapper<PostTrendCandidate>()
                .eq(PostTrendCandidate::getTaskId, taskId)
                .eq(PostTrendCandidate::getConfirmStatus, PostTrendCandidate.CONFIRM_PENDING));
        if (pending > 0) {
            return;
        }
        long landed = candidateMapper.selectCount(new LambdaQueryWrapper<PostTrendCandidate>()
                .eq(PostTrendCandidate::getTaskId, taskId)
                .isNotNull(PostTrendCandidate::getCreatedPostId));
        long total = candidateMapper.selectCount(new LambdaQueryWrapper<PostTrendCandidate>()
                .eq(PostTrendCandidate::getTaskId, taskId));
        String target;
        if (total == 0) {
            return;
        } else if (landed == total) {
            target = TaskStatusEnum.APPLIED.getCode();
        } else if (landed > 0) {
            target = TaskStatusEnum.PARTIALLY_APPLIED.getCode();
        } else {
            target = TaskStatusEnum.COMPLETED.getCode();
        }
        int updated = taskMapper.finishConfirmation(taskId, TaskStatusEnum.WAIT_CONFIRM.getCode(), target);
        if (updated > 0) {
            log.info("趋势任务审核收敛完成: taskId={}, status={}, landed={}/{}", taskId, target, landed, total);
        }
    }

    /**
     * 回收僵尸任务。
     * <p>
     * 服务重启或执行线程异常退出会让任务永久停在 RUNNING，界面上表现为「一直解析中」，
     * 用户既等不到结果也无法重新发起。这里统一置为 FAILED 并给出可读原因。
     */
    @Override
    @Scheduled(fixedDelayString = "${post.trend.zombie-scan-delay-ms:300000}", initialDelay = 60_000)
    public int scanZombieTasks() {
        LocalDateTime before = LocalDateTime.now().minusMinutes(ZOMBIE_TIMEOUT_MINUTES);
        List<PostTrendTask> zombies = taskMapper.findZombieTasks(
                TaskStatusEnum.RUNNING.getCode(), before, ZOMBIE_SCAN_LIMIT);
        int recovered = 0;
        for (PostTrendTask zombie : zombies) {
            recovered += taskMapper.failRunningTask(zombie.getId(), TaskStatusEnum.RUNNING.getCode(),
                    TaskStatusEnum.FAILED.getCode(),
                    "解析任务长时间无进展，可能因服务重启而中断，已自动终止，请重新发起");
        }
        if (recovered > 0) {
            log.warn("已回收僵尸趋势解析任务: count={}", recovered);
        }
        return recovered;
    }

    private PostTrendTaskVO toVO(PostTrendTask task) {
        PostTrendTaskVO vo = new PostTrendTaskVO();
        vo.setId(task.getId());
        vo.setTaskCode(task.getTaskCode());
        vo.setTaskName(task.getTaskName());
        vo.setTaskStatus(task.getTaskStatus());
        vo.setProgressStatus(task.getProgressStatus());
        vo.setProgressPercent(task.getProgressPercent());
        vo.setCandidateCount(task.getCandidateCount());
        vo.setNewPostCount(task.getNewPostCount());
        vo.setChangeCount(task.getChangeCount());
        vo.setDiagnostics(task.getDiagnostics());
        vo.setErrorMessage(task.getErrorMessage());
        vo.setCreatedTime(task.getCreatedTime());
        vo.setFinishedTime(task.getFinishedAt());
        return vo;
    }

    private String writeJson(Object value, String fallback) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception e) {
            log.warn("趋势任务JSON序列化失败，降级为 {}: {}", fallback, e.getMessage());
            return fallback;
        }
    }
}


