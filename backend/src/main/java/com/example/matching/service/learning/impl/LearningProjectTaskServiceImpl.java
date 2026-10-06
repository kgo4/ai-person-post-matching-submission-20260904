package com.example.matching.service.learning.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.example.matching.common.exception.BusinessException;
import com.example.matching.dto.learning.LearningProjectSubmitDTO;
import com.example.matching.dto.learning.LearningProjectTaskVO;
import com.example.matching.entity.learning.*;
import com.example.matching.mapper.learning.*;
import com.example.matching.service.learning.LearningEvidenceBridgeService;
import com.example.matching.service.learning.LearningEvidenceConfidencePolicy;
import com.example.matching.service.learning.LearningProjectTaskService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 学习项目任务服务实现
 *
 * <p>🔴 <b>本类不再有任何「复核」动作</b>。项目材料由员工提交即归档：同步生成能力证据、
 * 标记任务与步骤完成。能力等级<b>不在这里更新</b> —— 唯一入口是员工发起「能力提升申请」，
 * 由 HR 在复核时一并查看这些材料并决定等级（见 {@code EmpLearningOutcomeReviewService#approve}）。
 *
 * <p>原先存在的 {@code review} 端点会把项目复核通过直接送进能力闭环
 * （{@code LearningProjectApprovedEvent} → {@code CapabilityClosureService}），
 * 构成**第二条能写能力画像的路径**，已整条移除。详见
 * {@code LearningOutcomeAutomationContractTest} 的防回归断言。
 *
 * @author system
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class LearningProjectTaskServiceImpl implements LearningProjectTaskService {

    /**
     * 提交状态：材料已提交。
     * <p>取代原先的 {@code PENDING}（待复核）—— 已无复核环节，该状态不再产生。
     * 历史数据里的 {@code PENDING} 与 {@code APPROVED} 都按「已提交」展示。
     */
    public static final String SUBMISSION_STATUS_SUBMITTED = "SUBMITTED";

    private final LearningProjectTaskMapper projectTaskMapper;
    private final LearningProjectSubmissionMapper submissionMapper;
    private final LearningPathStepMapper stepMapper;
    private final LearningPathPlanMapper planMapper;
    private final LearningProgressLogMapper progressLogMapper;
    private final LearningEvidenceBridgeService evidenceBridgeService;
    private final LearningEvidenceConfidencePolicy confidencePolicy = new LearningEvidenceConfidencePolicy();

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

        // 4. 归档提交材料
        LearningProjectSubmission submission = new LearningProjectSubmission();
        submission.setTaskId(taskId);
        submission.setPlanId(task.getPlanId());
        submission.setStepId(task.getStepId());
        submission.setEmpId(empId != null ? empId : plan.getEmpId());
        submission.setRepoUrl(dto.getRepoUrl());
        submission.setDemoUrl(dto.getDemoUrl());
        submission.setReportUrl(dto.getReportUrl());
        submission.setSubmissionText(dto.getSubmissionText());
        submission.setReviewStatus(SUBMISSION_STATUS_SUBMITTED);
        submission.setIsDeleted(0);
        submissionMapper.insert(submission);

        // 5. 生成能力证据 —— 不再等人工复核。
        //    先作废旧证据：同一任务可能被反复提交（网络重试、或员工补充材料），
        //    不作废会留下多条分数不同、内容矛盾的「当前证据」。
        deprecateStaleEvidence(taskId);
        //    置信度按材料完整度计：材料越全分越高，但上限低于「经人工评分」的场景，
        //    「自提」与「已核验」的差距由分数表达，不由等级表达。
        LearningEvidenceConfidencePolicy.ConfidenceResult confidence = confidencePolicy.calculateByCompleteness(
                !isEmpty(submission.getRepoUrl()),
                !isEmpty(submission.getDemoUrl()),
                !isEmpty(submission.getReportUrl()),
                !isEmpty(submission.getSubmissionText()));
        Long evidenceId = evidenceBridgeService.createEvidenceForSubmission(
                submission, task, step, confidence.confidence(), confidence.credibility());
        submission.setEvidenceId(evidenceId);
        submissionMapper.updateById(submission);

        // 6. 任务完成（提交即完成：学习行为已发生，不再等 HR 判定）
        task.setStatus("COMPLETED");
        projectTaskMapper.updateById(task);

        // 7. 步骤完成 —— 「我学完了」由员工的提交行为决定；
        //    「能力等级该不该更新」是另一件事，走能力提升申请。
        step.setStatus("COMPLETED");
        step.setEvidenceStatus("VERIFIED");
        stepMapper.updateById(step);

        // 8. 计划状态重算
        checkAndUpdatePlanStatus(submission.getPlanId());

        // 9. 进度日志
        LearningProgressLog progressLog = new LearningProgressLog();
        progressLog.setPlanId(submission.getPlanId());
        progressLog.setStepId(submission.getStepId());
        progressLog.setEmpId(submission.getEmpId());
        progressLog.setActionType("EVIDENCE_CREATED");
        progressLog.setActionDesc("项目材料已提交，证据已创建: evidenceId=" + evidenceId);
        progressLog.setEvidenceId(evidenceId);
        progressLogMapper.insert(progressLog);

        log.info("项目材料已提交并生成证据: taskId={}, submissionId={}, empId={}, evidenceId={}, confidence={}",
                taskId, submission.getId(), submission.getEmpId(), evidenceId, confidence.confidence());

        return submission;
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

    /**
     * 作废同一任务下历史提交产生的能力证据。
     *
     * <p>当前这条提交刚落库（{@code evidenceId} 还是空），所以这里只会命中历史提交；
     * 反复提交不会让证据越积越多。</p>
     */
    private void deprecateStaleEvidence(Long taskId) {
        List<LearningProjectSubmission> previous = submissionMapper.selectList(
                new LambdaQueryWrapper<LearningProjectSubmission>()
                        .eq(LearningProjectSubmission::getTaskId, taskId)
                        .isNotNull(LearningProjectSubmission::getEvidenceId));
        for (LearningProjectSubmission old : previous) {
            if (old.getEvidenceId() != null) {
                evidenceBridgeService.deprecateEvidence(old.getEvidenceId());
            }
        }
    }

    private boolean isEmpty(String str) {
        return str == null || str.trim().isEmpty();
    }
}
