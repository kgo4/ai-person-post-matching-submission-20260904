package com.example.matching.service.learning.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.example.matching.entity.contest.ContestEvidenceItem;
import com.example.matching.entity.employee.EmpAbility;
import com.example.matching.entity.learning.LearningPathPlan;
import com.example.matching.entity.learning.LearningPathStep;
import com.example.matching.entity.learning.LearningProjectSubmission;
import com.example.matching.entity.learning.LearningProjectTask;
import com.example.matching.mapper.contest.ContestEvidenceItemMapper;
import com.example.matching.mapper.employee.EmpAbilityMapper;
import com.example.matching.mapper.learning.LearningPathPlanMapper;
import com.example.matching.service.learning.LearningEvidenceBridgeService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * 学习证据桥接服务实现
 * <p>
 * 将审核通过的项目提交转化为能力证据。
 *
 * @author system
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class LearningEvidenceBridgeServiceImpl implements LearningEvidenceBridgeService {

    private final ContestEvidenceItemMapper evidenceItemMapper;
    private final EmpAbilityMapper empAbilityMapper;
    private final LearningPathPlanMapper planMapper;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long createEvidenceForApprovedSubmission(LearningProjectSubmission submission,
                                                      LearningProjectTask task,
                                                      LearningPathStep step,
                                                      Integer confidence,
                                                      Integer credibility) {
        // 只读查找已有员工能力记录用于证据绑定；能力等级写入一律走治理链路
        // （LearningProjectApprovedListener -> CapabilityClosureService.onLearningOutcomeConfirmed ->
        //  GovernedAdmission），禁止此处直接 update/insert emp_ability（治理旁路，无 Harness 校验）。
        Long empAbilityId = findExistingEmpAbility(submission.getEmpId(), step);

        // 2. 创建证据
        ContestEvidenceItem evidence = new ContestEvidenceItem();

        // 生成证据编码
        evidence.setEvidenceCode("LEARNING-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase());

        // 来源信息
        evidence.setSourceType("LEARNING_PROJECT");
        evidence.setSourceRefId(submission.getId());
        evidence.setSourceTitle(task != null ? task.getTaskTitle() : "学习项目任务");
        evidence.setSourceText(buildSourceText(submission, task));

        // 目标信息 - 绑定到真实的 EmpAbility.id（不存在时留空，由治理链路创建后补绑）
        evidence.setTargetType("EMP_ABILITY");
        evidence.setTargetRefId(empAbilityId);

        // 能力信息
        evidence.setAbilityName(step != null ? step.getAbilityName() : null);
        evidence.setTagId(step != null ? step.getAbilityTagId() : null);

        // 评分（根据审核结果动态计算）
        evidence.setConfidenceScore(new BigDecimal(confidence != null ? confidence : 70));
        evidence.setCredibilityScore(new BigDecimal(credibility != null ? credibility : 65));

        // 状态
        evidence.setEvidenceStatus("VERIFIED");
        evidence.setIsDeleted(0);

        evidenceItemMapper.insert(evidence);

        log.info("学习项目证据已创建: evidenceId={}, submissionId={}, empAbilityId={}, abilityName={}",
                evidence.getId(), submission.getId(), empAbilityId,
                step != null ? step.getAbilityName() : "unknown");

        return evidence.getId();
    }

    /**
     * 只读查找已有员工能力记录（供证据绑定），不创建、不修改能力等级。
     */
    private Long findExistingEmpAbility(Long empId, LearningPathStep step) {
        if (empId == null || step == null || step.getAbilityTagId() == null) {
            return null;
        }

        LambdaQueryWrapper<EmpAbility> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(EmpAbility::getEmpId, empId)
                .eq(EmpAbility::getTagId, step.getAbilityTagId())
                .eq(EmpAbility::getIsDeleted, 0)
                .orderByDesc(EmpAbility::getMasteryLevel)
                .last("LIMIT 1");
        EmpAbility existing = empAbilityMapper.selectOne(wrapper);
        return existing != null ? existing.getId() : null;
    }

    private String buildSourceText(LearningProjectSubmission submission, LearningProjectTask task) {
        StringBuilder sb = new StringBuilder();

        if (task != null) {
            sb.append("项目任务：").append(task.getTaskTitle()).append("\n");
            if (task.getProjectName() != null) {
                sb.append("项目名称：").append(task.getProjectName()).append("\n");
            }
        }

        if (submission.getSubmissionText() != null && !submission.getSubmissionText().isEmpty()) {
            sb.append("提交说明：").append(submission.getSubmissionText()).append("\n");
        }
        if (submission.getRepoUrl() != null && !submission.getRepoUrl().isEmpty()) {
            sb.append("仓库地址：").append(submission.getRepoUrl()).append("\n");
        }
        if (submission.getDemoUrl() != null && !submission.getDemoUrl().isEmpty()) {
            sb.append("演示地址：").append(submission.getDemoUrl()).append("\n");
        }
        if (submission.getReportUrl() != null && !submission.getReportUrl().isEmpty()) {
            sb.append("报告地址：").append(submission.getReportUrl()).append("\n");
        }

        return sb.toString();
    }
}
