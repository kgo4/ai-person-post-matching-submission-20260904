package com.example.matching.service.learning;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.example.matching.common.exception.BusinessException;
import com.example.matching.common.exception.ErrorCodeEnum;
import com.example.matching.dto.learning.LearningOutcomeContextResponse;
import com.example.matching.dto.learning.LearningPathPlanVO;
import com.example.matching.dto.learning.LearningPathStepVO;
import com.example.matching.entity.employee.EmpEmployee;
import com.example.matching.entity.learning.EmpLearningOutcomeSubmission;
import com.example.matching.entity.learning.LearningAssessmentItem;
import com.example.matching.entity.learning.LearningProjectSubmission;
import com.example.matching.entity.learning.LearningProjectTask;
import com.example.matching.mapper.employee.EmpEmployeeMapper;
import com.example.matching.mapper.learning.EmpLearningOutcomeSubmissionMapper;
import com.example.matching.mapper.learning.LearningProjectSubmissionMapper;
import com.example.matching.mapper.learning.LearningProjectTaskMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * 「提交单 → 学习路径与学习情况」只读查询（HR 复核用）。
 *
 * <p><b>为什么单独成一个只读服务，而不是塞进 {@link EmpLearningOutcomeReviewService}</b>：
 * <ul>
 *   <li>复核服务是写链路（通过/驳回、回写能力证据），本类只读，职责不同；</li>
 *   <li>给复核服务加构造参数会连带改它的手工装配单测
 *       （{@code EmpLearningOutcomeReviewServiceTest}），改动面本可避免；</li>
 *   <li>本类只依赖 Mapper 与查询型 Service，单测成本低。</li>
 * </ul>
 *
 * <p>🔴 <b>可见性闸门</b>：入口只能是「提交单 ID」。HR 默认看不到员工的学习路径，
 * 员工提交能力提升申请后，HR 才能借由这条提交单看到该员工在该匹配下的学习路径进展
 * 与该项能力的学习记录。<b>不得</b>在本类（或任何 HR 侧接口）上再加
 * 「按 empId / matchingRecordId 直接查学习路径」的方法，那等于绕过复核去看员工过程数据。
 *
 * @author system
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class LearningOutcomeContextQueryService {

    private final EmpLearningOutcomeSubmissionMapper submissionMapper;
    private final EmpEmployeeMapper empEmployeeMapper;
    private final LearningPathPlanService learningPathPlanService;
    private final LearningAssessmentService learningAssessmentService;
    private final LearningProjectSubmissionMapper projectSubmissionMapper;
    private final LearningProjectTaskMapper projectTaskMapper;

    /**
     * 查询某条提交单对应的学习路径与学习情况。
     *
     * @param submissionId 提交单ID（唯一入口）
     * @throws BusinessException id 为空（400）或提交单不存在（404）
     */
    public LearningOutcomeContextResponse getContext(Long submissionId) {
        if (submissionId == null) {
            throw new BusinessException(ErrorCodeEnum.PARAM_ERROR, "请指定要查看的学习成果提交单");
        }
        EmpLearningOutcomeSubmission submission = submissionMapper.selectById(submissionId);
        if (submission == null) {
            throw new BusinessException(ErrorCodeEnum.NOT_FOUND, "该学习成果提交单不存在或已被删除");
        }

        LearningPathPlanVO plan = resolvePlan(submission.getMatchingRecordId());
        List<LearningPathStepVO> stepVOs = plan == null || plan.getSteps() == null
                ? List.of() : plan.getSteps();

        Long appliedStepId = resolveAppliedStepId(stepVOs, submission);
        List<LearningOutcomeContextResponse.StepBrief> steps = new ArrayList<>(stepVOs.size());
        for (LearningPathStepVO step : stepVOs) {
            if (step == null) {
                continue;
            }
            steps.add(new LearningOutcomeContextResponse.StepBrief(
                    step.getId(),
                    step.getAbilityName(),
                    step.getCurrentLevel(),
                    step.getTargetLevel(),
                    step.getGapType(),
                    step.getPriority(),
                    step.getStatus(),
                    step.getEvidenceStatus(),
                    step.getResourceCount(),
                    Objects.equals(step.getId(), appliedStepId)));
        }

        return new LearningOutcomeContextResponse(
                submission.getId(),
                submission.getEmpId(),
                resolveEmpName(submission.getEmpId()),
                submission.getMatchingRecordId(),
                plan == null ? null : plan.getPostName(),
                submission.getAbilityName(),
                submission.getBeforeLevel(),
                submission.getConfirmedLevel(),
                plan == null ? null : plan.getId(),
                plan == null ? null : plan.getPlanTitle(),
                plan == null ? null : plan.getPlanStatus(),
                plan == null ? null : plan.getTotalStepCount(),
                plan == null ? null : plan.getCompletedStepCount(),
                appliedStepId,
                steps,
                // 只回本次申请那一步的评估题：HR 要判断的是「这项能力验证过没有」，
                // 把整份计划的题目都丢过来只会淹掉关键的几条。
                appliedStepId == null ? List.of() : loadAssessments(plan.getId(), appliedStepId),
                // 项目材料是 HR 判断能力提升的主要佐证 —— 它已不再单独复核，
                // 只在这里（复核能力提升申请时）露出。
                loadProjectMaterials(plan == null ? null : plan.getId(), submission.getEmpId(),
                        appliedStepId, stepVOs));
    }

    /**
     * 取该员工在这份学习计划下提交过的项目材料。
     *
     * <p>取<b>整个计划</b>而非只取本次申请那一步：员工可能为达成同一项能力做了多个练习，
     * 只给一步会让 HR 漏看佐证；同时用 {@code belongsToAppliedStep} 标出哪条直接对应本次申请，
     * 前端据此把关键的排在前面（{@link Comparator} 稳定排序，保留提交时间倒序）。</p>
     */
    private List<LearningOutcomeContextResponse.ProjectMaterialBrief> loadProjectMaterials(
            Long planId, Long empId, Long appliedStepId, List<LearningPathStepVO> steps) {
        if (planId == null || empId == null) {
            return List.of();
        }
        List<LearningProjectSubmission> submissions;
        try {
            submissions = projectSubmissionMapper.selectList(
                    new LambdaQueryWrapper<LearningProjectSubmission>()
                            .eq(LearningProjectSubmission::getPlanId, planId)
                            .eq(LearningProjectSubmission::getEmpId, empId)
                            .orderByDesc(LearningProjectSubmission::getCreatedTime));
        } catch (Exception e) {
            // 与计划/评估题一致地降级：材料查询失败不该让复核页整页打不开。
            log.warn("查询项目材料失败，按「暂无材料」处理: planId={}, empId={}", planId, empId, e);
            return List.of();
        }
        if (submissions == null || submissions.isEmpty()) {
            return List.of();
        }

        Map<Long, String> taskTitles = loadTaskTitles(submissions);
        Map<Long, String> stepAbilityNames = new HashMap<>();
        for (LearningPathStepVO step : steps) {
            if (step != null && step.getId() != null) {
                stepAbilityNames.put(step.getId(), step.getAbilityName());
            }
        }

        List<LearningOutcomeContextResponse.ProjectMaterialBrief> materials = new ArrayList<>(submissions.size());
        for (LearningProjectSubmission item : submissions) {
            if (item == null) {
                continue;
            }
            materials.add(new LearningOutcomeContextResponse.ProjectMaterialBrief(
                    item.getId(),
                    taskTitles.get(item.getTaskId()),
                    item.getStepId(),
                    stepAbilityNames.get(item.getStepId()),
                    item.getRepoUrl(),
                    item.getDemoUrl(),
                    item.getReportUrl(),
                    item.getSubmissionText(),
                    item.getCreatedTime(),
                    Objects.equals(item.getStepId(), appliedStepId)));
        }
        // 属于本次申请步骤的排前面；List.sort 稳定，同组内仍是时间倒序。
        materials.sort(Comparator.comparing(
                m -> Boolean.TRUE.equals(m.belongsToAppliedStep()) ? 0 : 1));
        return materials;
    }

    /** 批量取项目任务标题，避免逐条查库。 */
    private Map<Long, String> loadTaskTitles(List<LearningProjectSubmission> submissions) {
        List<Long> taskIds = submissions.stream()
                .filter(Objects::nonNull)
                .map(LearningProjectSubmission::getTaskId)
                .filter(Objects::nonNull)
                .distinct()
                .toList();
        if (taskIds.isEmpty()) {
            return Map.of();
        }
        Map<Long, String> titles = new HashMap<>();
        try {
            List<LearningProjectTask> tasks = projectTaskMapper.selectBatchIds(taskIds);
            for (LearningProjectTask task : tasks) {
                if (task != null && task.getId() != null) {
                    titles.put(task.getId(), task.getTaskTitle());
                }
            }
        } catch (Exception e) {
            log.warn("查询项目任务标题失败，材料仍照常返回（标题缺失）: taskIds={}", taskIds, e);
        }
        return titles;
    }

    /**
     * 取该匹配记录对应的学习计划。
     *
     * <p>「还没有学习计划」与「查询出错」必须区分：前者是正常状态（null 返回，
     * 前端提示「该员工尚未生成学习计划」），后者记日志后同样按无计划处理 ——
     * 复核页不应因为计划查询失败而整页打不开。</p>
     */
    private LearningPathPlanVO resolvePlan(Long matchingRecordId) {
        if (matchingRecordId == null) {
            return null;
        }
        try {
            return learningPathPlanService.getByMatchingRecord(matchingRecordId);
        } catch (Exception e) {
            log.warn("查询学习成果提交单对应的学习计划失败，按「暂无计划」处理: matchingRecordId={}", matchingRecordId, e);
            return null;
        }
    }

    /**
     * 定位本次申请的能力对应哪一步。
     *
     * <p>先按能力名精确匹配（申请单与步骤都来自同一套能力名，这是最常见且最可靠的情形），
     * 名字对不上时再按能力标签ID兜底（步骤可能只带 tagId 而名字被改写过）。
     * 两者都定位不到时返回 {@code null} —— 前端据此提示「该能力在当前计划里没有对应步骤」，
     * 而不是悄悄显示一个空列表让人以为员工没学。</p>
     */
    private Long resolveAppliedStepId(List<LearningPathStepVO> steps, EmpLearningOutcomeSubmission submission) {
        String abilityName = normalize(submission.getAbilityName());
        if (abilityName != null) {
            for (LearningPathStepVO step : steps) {
                if (step != null && abilityName.equals(normalize(step.getAbilityName()))) {
                    return step.getId();
                }
            }
        }
        if (submission.getTagId() != null) {
            for (LearningPathStepVO step : steps) {
                if (step != null && submission.getTagId().equals(step.getAbilityTagId())) {
                    return step.getId();
                }
            }
        }
        return null;
    }

    private List<LearningOutcomeContextResponse.AssessmentBrief> loadAssessments(Long planId, Long stepId) {
        List<LearningAssessmentItem> items;
        try {
            items = learningAssessmentService.getAssessmentsByPlan(planId);
        } catch (Exception e) {
            log.warn("查询评估题失败，按「暂无验证记录」处理: planId={}, stepId={}", planId, stepId, e);
            return List.of();
        }
        if (items == null) {
            return List.of();
        }
        List<LearningOutcomeContextResponse.AssessmentBrief> briefs = new ArrayList<>();
        for (LearningAssessmentItem item : items) {
            if (item == null || !Objects.equals(item.getStepId(), stepId)) {
                continue;
            }
            briefs.add(new LearningOutcomeContextResponse.AssessmentBrief(
                    item.getId(),
                    item.getQuestionType(),
                    item.getDifficultyLevel(),
                    item.getAssessmentStatus(),
                    item.getScore(),
                    item.getAnswerText(),
                    item.getScoringFeedback(),
                    item.getAnsweredTime()));
        }
        return briefs;
    }

    private String resolveEmpName(Long empId) {
        if (empId == null) {
            return null;
        }
        try {
            EmpEmployee employee = empEmployeeMapper.selectById(empId);
            return employee == null ? null : employee.getRealName();
        } catch (Exception e) {
            log.warn("查询员工姓名失败: empId={}", empId, e);
            return null;
        }
    }

    private static String normalize(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
