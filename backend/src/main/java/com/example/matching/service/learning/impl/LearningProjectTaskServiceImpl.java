package com.example.matching.service.learning.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.example.matching.common.exception.BusinessException;
import com.example.matching.config.RabbitMQConfig;
import com.example.matching.event.LearningProjectApprovedEvent;
import com.example.matching.dto.learning.LearningProjectReviewDTO;
import com.example.matching.dto.learning.LearningProjectSubmitDTO;
import com.example.matching.dto.learning.LearningProjectTaskVO;
import com.example.matching.entity.learning.*;
import com.example.matching.mapper.learning.*;
import com.example.matching.service.learning.LearningEvidenceBridgeService;
import com.example.matching.service.learning.LearningEvidenceConfidencePolicy;
import com.example.matching.service.learning.LearningProjectTaskService;
import com.example.matching.service.common.EventOutboxDispatcher;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 学习项目任务服务实现
 *
 * @author system
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class LearningProjectTaskServiceImpl implements LearningProjectTaskService {

    private final LearningProjectTaskMapper projectTaskMapper;
    private final LearningProjectSubmissionMapper submissionMapper;
    private final LearningPathStepMapper stepMapper;
    private final LearningPathPlanMapper planMapper;
    private final LearningProgressLogMapper progressLogMapper;
    private final LearningEvidenceBridgeService evidenceBridgeService;
    private final LearningEvidenceConfidencePolicy confidencePolicy = new LearningEvidenceConfidencePolicy();
    private final EventOutboxDispatcher outboxDispatcher;

    @Override
    public IPage<LearningProjectTaskVO> pageTasks(Page<LearningProjectTask> page, Long planId, Long empId, String status) {
        LambdaQueryWrapper<LearningProjectTask> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(LearningProjectTask::getIsDeleted, 0);

        if (planId != null) {
            wrapper.eq(LearningProjectTask::getPlanId, planId);
        }
        if (status != null && !status.isEmpty()) {
            wrapper.eq(LearningProjectTask::getStatus, status);
        }
        wrapper.orderByDesc(LearningProjectTask::getCreatedTime);

        IPage<LearningProjectTask> taskPage = projectTaskMapper.selectPage(page, wrapper);
        return taskPage.convert(this::assembleTaskVO);
    }

    @Override
    public LearningProjectTaskVO getTask(Long id) {
        LearningProjectTask task = projectTaskMapper.selectById(id);
        if (task == null || task.getIsDeleted() == 1) {
            throw new BusinessException(10710, "项目任务不存在: " + id);
        }
        return assembleTaskVO(task);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public LearningProjectSubmission submit(Long taskId, LearningProjectSubmitDTO dto, Long empId) {
        // 1. 验证任务存在
        LearningProjectTask task = projectTaskMapper.selectById(taskId);
        if (task == null || task.getIsDeleted() == 1) {
            throw new BusinessException(10710, "项目任务不存在: " + taskId);
        }

        // 2. 验证提交内容不为空
        if (isEmpty(dto.getRepoUrl()) && isEmpty(dto.getDemoUrl()) &&
                isEmpty(dto.getReportUrl()) && isEmpty(dto.getSubmissionText())) {
            throw new BusinessException(10711, "提交内容不能为空，至少需要提供仓库URL、演示URL、报告URL或文本说明中的一项");
        }

        // 3. 验证计划和步骤存在
        LearningPathPlan plan = planMapper.selectById(task.getPlanId());
        if (plan == null || plan.getIsDeleted() == 1) {
            throw new BusinessException(10702, "学习路径计划不存在");
        }
        LearningPathStep step = stepMapper.selectById(task.getStepId());
        if (step == null || step.getIsDeleted() == 1) {
            throw new BusinessException(10703, "学习步骤不存在");
        }

        // 4. 创建提交记录
        LearningProjectSubmission submission = new LearningProjectSubmission();
        submission.setTaskId(taskId);
        submission.setPlanId(task.getPlanId());
        submission.setStepId(task.getStepId());
        submission.setEmpId(empId != null ? empId : plan.getEmpId());
        submission.setRepoUrl(dto.getRepoUrl());
        submission.setDemoUrl(dto.getDemoUrl());
        submission.setReportUrl(dto.getReportUrl());
        submission.setSubmissionText(dto.getSubmissionText());
        submission.setReviewStatus("PENDING");
        submission.setIsDeleted(0);
        submissionMapper.insert(submission);

        // 5. 更新任务状态
        task.setStatus("SUBMITTED");
        projectTaskMapper.updateById(task);

        // 6. 记录进度日志
        LearningProgressLog progressLog = new LearningProgressLog();
        progressLog.setPlanId(task.getPlanId());
        progressLog.setStepId(task.getStepId());
        progressLog.setEmpId(submission.getEmpId());
        progressLog.setActionType("PROJECT_SUBMITTED");
        progressLog.setActionDesc("项目任务已提交: " + task.getTaskTitle());
        progressLogMapper.insert(progressLog);

        log.info("项目任务已提交: taskId={}, submissionId={}, empId={}", taskId, submission.getId(), submission.getEmpId());

        return submission;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public LearningProjectSubmission review(Long submissionId, LearningProjectReviewDTO dto, Long reviewerId) {
        // 1. 验证提交存在
        LearningProjectSubmission submission = submissionMapper.selectById(submissionId);
        if (submission == null || submission.getIsDeleted() == 1) {
            throw new BusinessException(10712, "提交记录不存在: " + submissionId);
        }

        if (!"PENDING".equals(submission.getReviewStatus())) {
            throw new BusinessException(10713, "该提交已审核，不能重复审核");
        }

        // 2. 加载关联实体
        LearningProjectTask task = projectTaskMapper.selectById(submission.getTaskId());
        LearningPathStep step = stepMapper.selectById(submission.getStepId());

        // 3. 更新审核信息
        submission.setReviewStatus(dto.getReviewStatus());
        submission.setReviewComment(dto.getReviewComment());
        submission.setReviewedBy(reviewerId);
        submission.setReviewedTime(LocalDateTime.now());
        submissionMapper.updateById(submission);

        // 4. 根据审核结果处理
        if ("APPROVED".equals(dto.getReviewStatus())) {
            handleApproved(submission, task, step, dto);
        } else if ("REJECTED".equals(dto.getReviewStatus())) {
            handleRejected(submission, task);
        }

        return submission;
    }

    private void handleApproved(LearningProjectSubmission submission, LearningProjectTask task,
                                 LearningPathStep step, LearningProjectReviewDTO dto) {
        int reviewScore = resolveReviewScore(dto);
        boolean hasRepoUrl = submission.getRepoUrl() != null && !submission.getRepoUrl().isBlank();
        boolean hasDeliverableText = submission.getSubmissionText() != null && !submission.getSubmissionText().isBlank();

        LearningEvidenceConfidencePolicy.ConfidenceResult cr = confidencePolicy.calculate(
                reviewScore, hasRepoUrl, hasDeliverableText);

        Long evidenceId = evidenceBridgeService.createEvidenceForApprovedSubmission(
                submission, task, step, cr.confidence(), cr.credibility());
        submission.setEvidenceId(evidenceId);
        submissionMapper.updateById(submission);

        // 更新任务状态
        task.setStatus("COMPLETED");
        projectTaskMapper.updateById(task);

        // 更新步骤状态
        if (step != null) {
            step.setStatus("COMPLETED");
            step.setEvidenceStatus("VERIFIED");
            stepMapper.updateById(step);
        }

        // 触发计划状态重算
        checkAndUpdatePlanStatus(submission.getPlanId());

        // 记录进度日志
        LearningProgressLog progressLog = new LearningProgressLog();
        progressLog.setPlanId(submission.getPlanId());
        progressLog.setStepId(submission.getStepId());
        progressLog.setEmpId(submission.getEmpId());
        progressLog.setActionType("EVIDENCE_CREATED");
        progressLog.setActionDesc("项目提交审核通过，证据已创建: evidenceId=" + evidenceId);
        progressLog.setEvidenceId(evidenceId);
        progressLogMapper.insert(progressLog);

        // 学习目标是计划预期，不是能力事实。只有审核人明确确认的等级才允许进入能力闭环。
        if (step != null && step.getAbilityTagId() != null && dto.getAbilityLevelAfter() != null) {
            int confirmedLevel = Math.max(1, Math.min(5, dto.getAbilityLevelAfter()));
            outboxDispatcher.enqueue("LEARNING_OUTCOME_CLOSURE", RabbitMQConfig.MATCHING_EXCHANGE,
                    "learning.outcome.closure", new LearningProjectApprovedEvent(
                            submission.getId(), submission.getEmpId(), step.getAbilityTagId(), step.getAbilityName(),
                            step.getCurrentLevel(), confirmedLevel));
        } else if (step != null && step.getAbilityTagId() != null) {
            log.info("项目审核通过但未确认能力等级，仅保留项目证据: submissionId={}, stepId={}",
                    submission.getId(), step.getId());
        }

        log.info("项目提交审核通过: submissionId={}, evidenceId={}", submission.getId(), evidenceId);
    }

    /**
     * 检查并更新计划状态
     * 如果所有步骤都完成，则将计划标记为完成
     */
    private void checkAndUpdatePlanStatus(Long planId) {
        if (planId == null) {
            return;
        }

        // 查询计划下未完成的步骤数
        LambdaQueryWrapper<LearningPathStep> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(LearningPathStep::getPlanId, planId)
                .eq(LearningPathStep::getIsDeleted, 0)
                .ne(LearningPathStep::getStatus, "COMPLETED");
        long incompleteCount = stepMapper.selectCount(wrapper);

        if (incompleteCount == 0) {
            // 所有步骤都完成，更新计划状态
            LearningPathPlan plan = planMapper.selectById(planId);
            if (plan != null && !"COMPLETED".equals(plan.getPlanStatus())) {
                plan.setPlanStatus("COMPLETED");
                planMapper.updateById(plan);
                log.info("学习路径计划已完成: planId={}", planId);
            }
        }
    }

    private void handleRejected(LearningProjectSubmission submission, LearningProjectTask task) {
        // 更新任务状态
        task.setStatus("REVISION_REQUIRED");
        projectTaskMapper.updateById(task);

        // 记录进度日志
        LearningProgressLog progressLog = new LearningProgressLog();
        progressLog.setPlanId(submission.getPlanId());
        progressLog.setStepId(submission.getStepId());
        progressLog.setEmpId(submission.getEmpId());
        progressLog.setActionType("PROJECT_REJECTED");
        progressLog.setActionDesc("项目提交被驳回: " + submission.getReviewComment());
        progressLogMapper.insert(progressLog);

        log.info("项目提交被驳回: submissionId={}", submission.getId());
    }

    private LearningProjectTaskVO assembleTaskVO(LearningProjectTask task) {
        LearningProjectTaskVO vo = new LearningProjectTaskVO();
        vo.setId(task.getId());
        vo.setPlanId(task.getPlanId());
        vo.setStepId(task.getStepId());
        vo.setAbilityTagId(task.getAbilityTagId());
        vo.setProjectName(task.getProjectName());
        vo.setProjectUrl(task.getProjectUrl());
        vo.setTaskTitle(task.getTaskTitle());
        vo.setTaskBackground(task.getTaskBackground());
        vo.setTaskRequirements(task.getTaskRequirements());
        vo.setAcceptanceCriteria(task.getAcceptanceCriteria());
        vo.setDifficultyLevel(task.getDifficultyLevel());
        vo.setExpectedOutput(task.getExpectedOutput());
        vo.setStatus(task.getStatus());
        vo.setCreatedTime(task.getCreatedTime());

        // 加载最新提交
        LambdaQueryWrapper<LearningProjectSubmission> subWrapper = new LambdaQueryWrapper<>();
        subWrapper.eq(LearningProjectSubmission::getTaskId, task.getId())
                .eq(LearningProjectSubmission::getIsDeleted, 0)
                .orderByDesc(LearningProjectSubmission::getCreatedTime)
                .last("LIMIT 1");
        LearningProjectSubmission latestSub = submissionMapper.selectOne(subWrapper);
        if (latestSub != null) {
            vo.setLatestSubmissionId(latestSub.getId());
            vo.setLatestSubmissionStatus(latestSub.getReviewStatus());
        }

        return vo;
    }

    private boolean isEmpty(String str) {
        return str == null || str.trim().isEmpty();
    }

    private int resolveReviewScore(LearningProjectReviewDTO dto) {
        if (dto.getScore() != null) {
            return dto.getScore();
        }
        if (dto.getAbilityLevelAfter() != null) {
            return Math.max(20, Math.min(100, dto.getAbilityLevelAfter() * 20));
        }
        return 0;
    }
}
