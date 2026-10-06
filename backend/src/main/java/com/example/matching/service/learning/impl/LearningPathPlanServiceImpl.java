package com.example.matching.service.learning.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.example.matching.common.exception.BusinessException;
import com.example.matching.common.exception.ErrorCodeEnum;
import com.example.matching.agent.dto.LearningPathAgentRequest;
import com.example.matching.agent.dto.LearningPathAgentResult;
import com.example.matching.agent.service.LearningPathAgentService;
import com.example.matching.common.util.AbilityNameNormalizer;
import com.example.matching.dto.learning.*;
import com.example.matching.entity.learning.*;
import com.example.matching.mapper.learning.*;
import com.example.matching.port.matching.MatchingQueryPort;
import com.example.matching.port.matching.MatchingQueryPort.MatchingRecordDTO;
import com.example.matching.port.post.PostQueryPort;
import com.example.matching.port.post.PostQueryPort.PostAbilityDTO;
import com.example.matching.port.tag.TagQueryPort;
import com.example.matching.port.tag.TagQueryPort.TagDTO;
import com.example.matching.port.talent.TalentQueryPort;
import com.example.matching.port.talent.TalentQueryPort.EmployeeAbilityDTO;
import com.example.matching.service.learning.LearningPathPlanService;
import com.example.matching.service.learning.support.LearningResourceMatcher;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 学习路径计划服务实现
 * <p>
 * 支持两种模式：
 * <ul>
 *   <li>确定性规则模式（默认）：基于匹配记录中的能力差距，纯规则驱动生成。</li>
 *   <li>AI 增强模式（useAi=true）：调用 Agent 流水线（LLM + 知识图谱 + RAG），
 *       LLM 不可用时自动降级为确定性规则。</li>
 * </ul>
 *
 * @author system
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class LearningPathPlanServiceImpl implements LearningPathPlanService {

    private final LearningPathPlanMapper planMapper;
    private final LearningPathStepMapper stepMapper;
    private final LearningProjectTaskMapper projectTaskMapper;
    private final LearningProjectSubmissionMapper submissionMapper;
    private final LearningProgressLogMapper progressLogMapper;
    private final MatchingQueryPort matchingQueryPort;
    private final PostQueryPort postQueryPort;
    private final TalentQueryPort talentQueryPort;
    private final TagQueryPort tagQueryPort;
    private final LearningPathAgentService learningPathAgentService;
    private final LearningAssessmentItemMapper assessmentItemMapper;
    private final LearningResourceMapper resourceMapper;

    /** Coolearn开源项目URL */
    private static final String COOLEARN_PROJECT_URL = "https://github.com/HnZzMwh/Coolearn";

    @Override
    @Transactional(rollbackFor = Exception.class)
    public LearningPathPlanVO generateFromMatchingRecord(LearningPathGenerateRequest request) {
        Long matchingRecordId = request.getMatchingRecordId();

        // 1. 检查是否已有计划
        if (!Boolean.TRUE.equals(request.getForceRegenerate())) {
            LearningPathPlanVO existing = getByMatchingRecord(matchingRecordId);
            if (existing != null) {
                return existing;
            }
        }

        // 2. 加载匹配记录
        MatchingRecordDTO record = matchingQueryPort.getById(matchingRecordId);
        if (record == null) {
            throw new BusinessException(ErrorCodeEnum.MATCHING_RECORD_NOT_FOUND);
        }

        Long empId = record.empId();
        Long postId = record.postId();

        boolean useAi = Boolean.TRUE.equals(request.getUseAi());

        // 3. 创建学习计划
        LearningPathPlan plan = new LearningPathPlan();
        plan.setEmpId(empId);
        plan.setPostId(postId);
        plan.setMatchingRecordId(matchingRecordId);
        plan.setPlanTitle(buildPlanTitle(record, null));
        plan.setPlanStatus("ACTIVE");
        plan.setCurrentScore(record.finalMatchScore() != null ? record.finalMatchScore() : record.aiMatchScore());
        plan.setTargetScore(request.getTargetScore() != null ? request.getTargetScore() :
                calculateTargetScore(plan.getCurrentScore()));
        plan.setGeneratedByAi(useAi ? 1 : 0);
        plan.setIsDeleted(0);
        planMapper.insert(plan);

        List<LearningPathStep> steps;
        boolean agentUsed = false;

        if (useAi) {
            // === AI 增强模式：调用 Agent 流水线（LLM + KG + RAG），失败自动降级 ===
            LearningPathAgentResult agentResult = callAgentPipeline(request, record);
            steps = convertAgentStepsToEntities(plan.getId(), agentResult);
            agentUsed = true;

            if (steps.isEmpty()) {
                log.warn("AI 学习路径未产生有效步骤，降级为规则计划: empId={}, postId={}", empId, postId);
                steps = buildDeterministicSteps(plan.getId(), empId, postId);
                agentUsed = false;
                plan.setGeneratedByAi(0);
                plan.setAiSummary("[规则降级] AI 未返回可核验学习步骤，已按岗位能力差距生成计划。");
            }

            // 持久化 AI 生成的摘要
            if (agentUsed && agentResult.getSummary() != null) {
                plan.setAiSummary(agentResult.getSummary());
                if (agentResult.getFallbackUsed() != null && agentResult.getFallbackUsed()) {
                    plan.setAiSummary("[降级方案] " + agentResult.getSummary());
                }
            }
            planMapper.updateById(plan);

            // 批量插入步骤
            for (LearningPathStep step : steps) {
                stepMapper.insert(step);
            }

            // 创建 AI 生成的项目任务
            if (agentUsed && agentResult.getProjectTasks() != null) {
                for (int i = 0; i < agentResult.getProjectTasks().size() && i < steps.size(); i++) {
                    LearningPathAgentResult.ProjectTaskSuggestion pt = agentResult.getProjectTasks().get(i);
                    LearningPathStep matchedStep = steps.get(i);
                    LearningProjectTask task = createProjectTaskFromAgent(plan.getId(), matchedStep, pt);
                    projectTaskMapper.insert(task);
                }
            }

            // 创建 AI 生成的评估题目
            if (agentUsed && agentResult.getAssessments() != null) {
                for (LearningPathAgentResult.AssessmentSuggestion as : agentResult.getAssessments()) {
                    LearningAssessmentItem item = createAssessmentFromAgent(plan.getId(), steps, as);
                    if (item != null) {
                        assessmentItemMapper.insert(item);
                    }
                }
            }
            if (!agentUsed && Boolean.TRUE.equals(request.getIncludeProjectTasks())) {
                for (LearningPathStep step : steps) {
                    if ("HIGH".equals(step.getPriority()) || "MEDIUM".equals(step.getPriority())) {
                        projectTaskMapper.insert(createProjectTask(plan.getId(), step));
                    }
                }
            }
        } else {
            // === 确定性规则模式：基于差距计算生成 ===
            // 加载岗位能力要求
            steps = buildDeterministicSteps(plan.getId(), empId, postId);

            for (LearningPathStep step : steps) {
                stepMapper.insert(step);
            }

            if (Boolean.TRUE.equals(request.getIncludeProjectTasks())) {
                for (LearningPathStep step : steps) {
                    if ("HIGH".equals(step.getPriority()) || "MEDIUM".equals(step.getPriority())) {
                        LearningProjectTask task = createProjectTask(plan.getId(), step);
                        projectTaskMapper.insert(task);
                    }
                }
            }
        }

        // 9. 记录进度日志
        LearningProgressLog progressLog = new LearningProgressLog();
        progressLog.setPlanId(plan.getId());
        progressLog.setEmpId(empId);
        progressLog.setActionType("PLAN_CREATED");
        progressLog.setActionDesc("学习路径计划已生成" + (agentUsed ? "（AI 增强）" : "（规则引擎）") + "，共" + steps.size() + "个步骤");
        progressLogMapper.insert(progressLog);

        log.info("生成学习路径计划: planId={}, empId={}, postId={}, steps={}, aiMode={}",
                plan.getId(), empId, postId, steps.size(), useAi);

        return getPlan(plan.getId());
    }

    @Override
    public LearningPathPlanVO getPlan(Long id) {
        LearningPathPlan plan = planMapper.selectById(id);
        if (plan == null || plan.getIsDeleted() == 1) {
            throw new BusinessException(10702, "学习路径计划不存在: " + id);
        }
        return assemblePlanVO(plan);
    }

    @Override
    public LearningPathPlanVO getByMatchingRecord(Long matchingRecordId) {
        LambdaQueryWrapper<LearningPathPlan> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(LearningPathPlan::getMatchingRecordId, matchingRecordId)
                .eq(LearningPathPlan::getIsDeleted, 0)
                .orderByDesc(LearningPathPlan::getCreatedTime)
                .last("LIMIT 1");
        LearningPathPlan plan = planMapper.selectOne(wrapper);
        if (plan == null) {
            return null;
        }
        return assemblePlanVO(plan);
    }

    @Override
    public IPage<LearningPathPlanVO> pagePlans(Page<LearningPathPlan> page, Long empId, Long postId, String status) {
        LambdaQueryWrapper<LearningPathPlan> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(LearningPathPlan::getIsDeleted, 0);
        if (empId != null) {
            wrapper.eq(LearningPathPlan::getEmpId, empId);
        }
        if (postId != null) {
            wrapper.eq(LearningPathPlan::getPostId, postId);
        }
        if (status != null && !status.isEmpty()) {
            wrapper.eq(LearningPathPlan::getPlanStatus, status);
        }
        wrapper.orderByDesc(LearningPathPlan::getCreatedTime);

        IPage<LearningPathPlan> planPage = planMapper.selectPage(page, wrapper);
        return planPage.convert(this::assemblePlanVO);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateStepStatus(Long stepId, String status) {
        LearningPathStep step = stepMapper.selectById(stepId);
        if (step == null || step.getIsDeleted() == 1) {
            throw new BusinessException(10703, "学习步骤不存在: " + stepId);
        }

        step.setStatus(status);
        stepMapper.updateById(step);

        // 记录进度日志
        LearningProgressLog progressLog = new LearningProgressLog();
        progressLog.setPlanId(step.getPlanId());
        progressLog.setStepId(stepId);
        progressLog.setEmpId(getPlanEmpId(step.getPlanId()));
        progressLog.setActionType("STEP_STATUS_CHANGED");
        progressLog.setActionDesc("步骤状态变更为: " + status);
        progressLogMapper.insert(progressLog);

        // 检查是否所有步骤都完成，如果是则更新计划状态
        checkAndUpdatePlanStatus(step.getPlanId());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int refreshResourceBindings(Long planId) {
        List<LearningPathStep> steps = stepMapper.selectList(new LambdaQueryWrapper<LearningPathStep>()
                .eq(LearningPathStep::getPlanId, planId)
                .eq(LearningPathStep::getIsDeleted, 0));
        if (steps.isEmpty()) {
            return 0;
        }
        List<LearningResource> allResources = loadAllEnabledResources();
        int updated = 0;
        for (LearningPathStep step : steps) {
            List<LearningResource> matched = LearningResourceMatcher.matchAndSort(
                    allResources, step.getAbilityName(), step.getAbilityTagId(),
                    step.getCurrentLevel() != null ? step.getCurrentLevel() : 0,
                    step.getTargetLevel() != null ? step.getTargetLevel() : 3);
            LambdaUpdateWrapper<LearningPathStep> uw = Wrappers.<LearningPathStep>lambdaUpdate()
                    .eq(LearningPathStep::getId, step.getId());
            if (matched.isEmpty()) {
                uw.set(LearningPathStep::getResourceId, null)
                        .set(LearningPathStep::getResourceTitle, null)
                        .set(LearningPathStep::getResourceUrl, null)
                        .set(LearningPathStep::getResourceType, null)
                        .set(LearningPathStep::getResourceCount, 0);
            } else {
                LearningResource first = matched.get(0);
                uw.set(LearningPathStep::getResourceId, first.getId())
                        .set(LearningPathStep::getResourceTitle, first.getTitle())
                        .set(LearningPathStep::getResourceUrl, first.getUrl())
                        .set(LearningPathStep::getResourceType, first.getResourceType())
                        .set(LearningPathStep::getResourceCount, matched.size());
            }
            stepMapper.update(null, uw);
            updated++;
        }
        log.info("学习路径资源回填完成: planId={}, updatedSteps={}", planId, updated);
        return updated;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int refreshAllResourceBindings() {
        List<LearningPathStep> planIds = stepMapper.selectList(new LambdaQueryWrapper<LearningPathStep>()
                .eq(LearningPathStep::getIsDeleted, 0)
                .select(LearningPathStep::getPlanId)
                .groupBy(LearningPathStep::getPlanId));
        int total = 0;
        for (LearningPathStep p : planIds) {
            total += refreshResourceBindings(p.getPlanId());
        }
        return total;
    }

    // ==================== 私有辅助方法 ====================

    private LearningPathStep createStepFromGap(Long planId, String abilityName, Long tagId, TagDTO tag,
                                                int currentLevel, int targetLevel, boolean isCore, int sortOrder,
                                                List<LearningResource> allResources) {
        LearningPathStep step = new LearningPathStep();
        step.setPlanId(planId);
        step.setAbilityTagId(tagId);
        step.setAbilityName(abilityName != null && !abilityName.isBlank()
                ? abilityName : (tag != null ? tag.tagName() : "未命名能力"));
        step.setCurrentLevel(currentLevel);
        step.setTargetLevel(targetLevel);
        step.setGapType(currentLevel == 0 ? "MISSING" : "LEVEL_GAP");
        step.setPriority(calculatePriority(currentLevel, targetLevel, isCore));
        step.setStepTitle(buildStepTitle(step));
        step.setStepDescription(buildStepDescription(step, tag));
        step.setEstimatedHours(calculateEstimatedHours(currentLevel, targetLevel));
        step.setStatus("PENDING");
        step.setSortOrder(sortOrder);
        step.setIsDeleted(0);
        // 按能力名称绑定真实资源；无资源时保留差距（resourceCount=0），不阻断。
        List<LearningResource> matched = LearningResourceMatcher.matchAndSort(
                allResources, abilityName, tagId, currentLevel, targetLevel);
        applyResourceToStep(step, matched);
        return step;
    }

    /**
     * 将资源绑定到学习步骤（主资源 + 资源总数）。
     */
    private void applyResourceToStep(LearningPathStep step, List<LearningResource> matched) {
        step.setResourceCount(matched != null ? matched.size() : 0);
        if (matched != null && !matched.isEmpty()) {
            LearningResource first = matched.get(0);
            step.setResourceId(first.getId());
            step.setResourceTitle(first.getTitle());
            step.setResourceUrl(first.getUrl());
            step.setResourceType(first.getResourceType());
        }
    }

    /**
     * 加载所有启用的学习资源（学习路径生成时统一召回）。
     */
    private List<LearningResource> loadAllEnabledResources() {
        LambdaQueryWrapper<LearningResource> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(LearningResource::getStatus, 1);
        return resourceMapper.selectList(wrapper);
    }

    private String calculatePriority(int currentLevel, int targetLevel, boolean isCore) {
        int gap = targetLevel - currentLevel;
        if (isCore && gap >= 2) return "HIGH";
        if (gap >= 2) return "HIGH";
        if (gap >= 1) return "MEDIUM";
        if (isCore) return "MEDIUM";
        return "LOW";
    }

    private String buildStepTitle(LearningPathStep step) {
        return "提升「" + step.getAbilityName() + "」从 L" + step.getCurrentLevel() + " 到 L" + step.getTargetLevel();
    }

    private String buildStepDescription(LearningPathStep step, TagDTO tag) {
        StringBuilder desc = new StringBuilder();
        desc.append("目标能力：").append(step.getAbilityName()).append("\n");
        desc.append("当前等级：L").append(step.getCurrentLevel()).append("\n");
        desc.append("目标等级：L").append(step.getTargetLevel()).append("\n");
        if (tag != null && tag.description() != null) {
            desc.append("能力说明：").append(tag.description()).append("\n");
        }
        desc.append("建议：通过学习资源和项目实践，系统提升该能力至目标等级。");
        return desc.toString();
    }

    private int calculateEstimatedHours(int currentLevel, int targetLevel) {
        int gap = targetLevel - currentLevel;
        return Math.max(8, gap * 16);
    }

    private LearningProjectTask createProjectTask(Long planId, LearningPathStep step) {
        LearningProjectTask task = new LearningProjectTask();
        task.setPlanId(planId);
        task.setStepId(step.getId());
        task.setAbilityTagId(step.getAbilityTagId());
        task.setProjectName("Coolearn-inspired open-source adaptation");
        task.setProjectUrl(COOLEARN_PROJECT_URL);
        task.setTaskTitle("基于「" + step.getAbilityName() + "」的岗位能力改造实践");
        task.setTaskBackground("参考 Coolearn 的学习路线、项目实践和能力评估思路，将通用学习平台能力改造成当前人岗匹配系统的业务功能。");
        task.setTaskRequirements(buildTaskRequirements(step));
        task.setAcceptanceCriteria(buildAcceptanceCriteria(step));
        task.setDifficultyLevel(step.getPriority());
        task.setExpectedOutput("Git repository URL, implementation note, screenshots or report");
        task.setStatus("PENDING");
        task.setIsDeleted(0);
        return task;
    }

    private String buildTaskRequirements(LearningPathStep step) {
        return "1. 明确「" + step.getAbilityName() + "」在目标岗位中的使用场景\n" +
                "2. 在当前系统中补充一个可运行或可说明的改造点\n" +
                "3. 输出实现说明、关键截图或仓库链接\n" +
                "4. 标注成果对应的能力证据";
    }

    private String buildAcceptanceCriteria(LearningPathStep step) {
        return "1. 成果能解释其支持的能力标签「" + step.getAbilityName() + "」\n" +
                "2. 至少包含一条可追溯材料链接或说明\n" +
                "3. 审核通过后可进入证据中心";
    }

    private String buildPlanTitle(MatchingRecordDTO record, Map<Long, TagDTO> tagMap) {
        String postName = record.postName() != null ? record.postName() : "岗位#" + record.postId();
        return "人岗匹配学习路径 - " + postName;
    }

    private BigDecimal calculateTargetScore(BigDecimal currentScore) {
        if (currentScore == null) return new BigDecimal("80.00");
        BigDecimal target = currentScore.add(new BigDecimal("15"));
        return target.min(new BigDecimal("100"));
    }

    private LearningPathPlanVO assemblePlanVO(LearningPathPlan plan) {
        LearningPathPlanVO vo = new LearningPathPlanVO();
        vo.setId(plan.getId());
        vo.setEmpId(plan.getEmpId());
        vo.setPostId(plan.getPostId());
        vo.setMatchingRecordId(plan.getMatchingRecordId());
        vo.setPlanTitle(plan.getPlanTitle());
        vo.setPlanStatus(plan.getPlanStatus());
        vo.setCurrentScore(plan.getCurrentScore());
        vo.setTargetScore(plan.getTargetScore());
        vo.setAiSummary(plan.getAiSummary());
        vo.setGeneratedByAi(plan.getGeneratedByAi());
        vo.setCreatedTime(plan.getCreatedTime());
        vo.setUpdatedTime(plan.getUpdatedTime());

        // 加载步骤
        LambdaQueryWrapper<LearningPathStep> stepWrapper = new LambdaQueryWrapper<>();
        stepWrapper.eq(LearningPathStep::getPlanId, plan.getId())
                .eq(LearningPathStep::getIsDeleted, 0)
                .orderByAsc(LearningPathStep::getSortOrder);
        List<LearningPathStep> steps = stepMapper.selectList(stepWrapper);

        List<LearningPathStepVO> stepVOs = steps.stream()
                .map(this::assembleStepVO)
                .collect(Collectors.toList());
        vo.setSteps(stepVOs);

        // 统计
        vo.setTotalStepCount(steps.size());
        vo.setCompletedStepCount((int) steps.stream()
                .filter(s -> "COMPLETED".equals(s.getStatus()))
                .count());

        // 项目任务统计
        LambdaQueryWrapper<LearningProjectTask> taskWrapper = new LambdaQueryWrapper<>();
        taskWrapper.eq(LearningProjectTask::getPlanId, plan.getId())
                .eq(LearningProjectTask::getIsDeleted, 0);
        List<LearningProjectTask> tasks = projectTaskMapper.selectList(taskWrapper);
        vo.setProjectTaskCount(tasks.size());

        // 待审核提交统计
        LambdaQueryWrapper<LearningProjectSubmission> subWrapper = new LambdaQueryWrapper<>();
        subWrapper.eq(LearningProjectSubmission::getPlanId, plan.getId())
                .eq(LearningProjectSubmission::getReviewStatus, "PENDING")
                .eq(LearningProjectSubmission::getIsDeleted, 0);
        long pendingCount = submissionMapper.selectCount(subWrapper);
        vo.setPendingSubmissionCount((int) pendingCount);

        return vo;
    }

    private LearningPathStepVO assembleStepVO(LearningPathStep step) {
        LearningPathStepVO vo = new LearningPathStepVO();
        vo.setId(step.getId());
        vo.setPlanId(step.getPlanId());
        vo.setAbilityTagId(step.getAbilityTagId());
        vo.setAbilityName(step.getAbilityName());
        vo.setCurrentLevel(step.getCurrentLevel());
        vo.setTargetLevel(step.getTargetLevel());
        vo.setGapType(step.getGapType());
        vo.setPriority(step.getPriority());
        vo.setStepTitle(step.getStepTitle());
        vo.setStepDescription(step.getStepDescription());
        vo.setEstimatedHours(step.getEstimatedHours());
        vo.setStatus(step.getStatus());
        vo.setEvidenceStatus(step.getEvidenceStatus());
        vo.setSortOrder(step.getSortOrder());
        vo.setCreatedTime(step.getCreatedTime());
        vo.setResourceId(step.getResourceId());
        vo.setResourceTitle(step.getResourceTitle());
        vo.setResourceUrl(step.getResourceUrl());
        vo.setResourceType(step.getResourceType());
        vo.setResourceCount(step.getResourceCount());

        // 加载项目任务
        LambdaQueryWrapper<LearningProjectTask> taskWrapper = new LambdaQueryWrapper<>();
        taskWrapper.eq(LearningProjectTask::getStepId, step.getId())
                .eq(LearningProjectTask::getIsDeleted, 0);
        List<LearningProjectTask> tasks = projectTaskMapper.selectList(taskWrapper);
        List<LearningProjectTaskVO> taskVOs = tasks.stream()
                .map(this::assembleTaskVO)
                .collect(Collectors.toList());
        vo.setProjectTasks(taskVOs);

        return vo;
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

    private Long getPlanEmpId(Long planId) {
        LearningPathPlan plan = planMapper.selectById(planId);
        return plan != null ? plan.getEmpId() : null;
    }

    private void checkAndUpdatePlanStatus(Long planId) {
        LambdaQueryWrapper<LearningPathStep> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(LearningPathStep::getPlanId, planId)
                .eq(LearningPathStep::getIsDeleted, 0)
                .ne(LearningPathStep::getStatus, "COMPLETED");
        long incompleteCount = stepMapper.selectCount(wrapper);

        if (incompleteCount == 0) {
            LearningPathPlan plan = planMapper.selectById(planId);
            if (plan != null) {
                plan.setPlanStatus("COMPLETED");
                planMapper.updateById(plan);
            }
        }
    }

    // ==================== AI Agent 流水线 ====================

    /**
     * 调用 Agent 流水线生成学习路径建议（LLM + 知识图谱 + RAG）。
     * LLM 不可用时自动降级为确定性规则。
     */
    private LearningPathAgentResult callAgentPipeline(LearningPathGenerateRequest request, MatchingRecordDTO record) {
        LearningPathAgentRequest agentRequest = new LearningPathAgentRequest();
        agentRequest.setMatchingRecordId(request.getMatchingRecordId());
        agentRequest.setIncludeProjectTasks(request.getIncludeProjectTasks());
        agentRequest.setIncludeAssessments(true);
        if (request.getTargetScore() != null) {
            agentRequest.setTargetScore(request.getTargetScore().intValue());
        }

        try {
            LearningPathAgentResult result = learningPathAgentService.preview(agentRequest);
            log.info("Agent 流水线完成: matchingRecordId={}, steps={}, tasks={}, assessments={}, fallback={}",
                    request.getMatchingRecordId(),
                    result.getSteps() != null ? result.getSteps().size() : 0,
                    result.getProjectTasks() != null ? result.getProjectTasks().size() : 0,
                    result.getAssessments() != null ? result.getAssessments().size() : 0,
                    result.getFallbackUsed());
            return result;
        } catch (Exception e) {
            log.error("Agent 流水线调用异常，回退到空结果: {}", e.getMessage(), e);
            LearningPathAgentResult emptyResult = new LearningPathAgentResult();
            emptyResult.setFallbackUsed(true);
            emptyResult.setSummary("AI 服务暂时不可用，请稍后重试或使用规则模式生成。");
            emptyResult.setSteps(List.of());
            emptyResult.setProjectTasks(List.of());
            emptyResult.setAssessments(List.of());
            return emptyResult;
        }
    }

    /**
     * 将 Agent 生成的步骤建议转换为 LearningPathStep 实体
     */
    private List<LearningPathStep> convertAgentStepsToEntities(Long planId, LearningPathAgentResult agentResult) {
        List<LearningPathStep> steps = new ArrayList<>();
        if (agentResult.getSteps() == null || agentResult.getSteps().isEmpty()) {
            return steps;
        }

        List<LearningResource> allResources = loadAllEnabledResources();
        int sortOrder = 0;
        for (LearningPathAgentResult.LearningStepSuggestion suggestion : agentResult.getSteps()) {
            LearningPathStep step = new LearningPathStep();
            step.setPlanId(planId);
            step.setAbilityTagId(suggestion.getAbilityTagId());
            step.setAbilityName(suggestion.getAbilityName() != null ? suggestion.getAbilityName() : "未知能力");
            step.setCurrentLevel(suggestion.getCurrentLevel() != null ? suggestion.getCurrentLevel() : 0);
            step.setTargetLevel(suggestion.getTargetLevel() != null ? suggestion.getTargetLevel() : 3);
            step.setGapType((suggestion.getCurrentLevel() != null && suggestion.getCurrentLevel() == 0) ? "MISSING" : "LEVEL_GAP");
            step.setPriority(suggestion.getPriority() != null ? suggestion.getPriority() : "MEDIUM");
            step.setStepTitle(suggestion.getTitle() != null ? suggestion.getTitle() : buildStepTitle(step));
            step.setStepDescription(suggestion.getDescription() != null ? suggestion.getDescription() : buildFallbackStepDesc(step));
            step.setEstimatedHours(suggestion.getEstimatedHours() != null ? suggestion.getEstimatedHours() : 16);
            step.setStatus("PENDING");
            step.setSortOrder(sortOrder++);
            step.setIsDeleted(0);

            // AI 只负责排序/动作/原因，资源必须来自 learning_resource 表：
            // 1. AI 返回的 resourceId 必须能在匹配资源中校验通过，否则丢弃；
            // 2. 校验失败或未返回时，回退到服务端按 abilityName 匹配的资源；
            // 3. 无资源时保留能力差距（resourceCount=0），不生成假链接。
            List<LearningResource> matched = LearningResourceMatcher.matchAndSort(
                    allResources, step.getAbilityName(), suggestion.getAbilityTagId(),
                    step.getCurrentLevel(), step.getTargetLevel());
            LearningResource chosen = null;
            if (suggestion.getResourceId() != null) {
                for (LearningResource r : matched) {
                    if (r.getId().equals(suggestion.getResourceId())) {
                        chosen = r;
                        break;
                    }
                }
            }
            if (chosen == null && !matched.isEmpty()) {
                chosen = matched.get(0);
            }
            applyResourceToStep(step, chosen != null ? List.of(chosen) : List.of());
            step.setResourceCount(matched.size());
            steps.add(step);
        }
        return steps;
    }

    private List<LearningPathStep> buildDeterministicSteps(Long planId, Long empId, Long postId) {
        List<PostAbilityDTO> postRequirements = postQueryPort.listRequirementsByPostId(postId);
        if (postRequirements.isEmpty()) {
            throw new BusinessException(10606, "岗位能力模型不完整，无法生成学习路径: " + postId);
        }
        Map<String, Integer> empAbilityMap = talentQueryPort.listAbilitiesByEmpId(empId).stream()
                .filter(a -> a.abilityName() != null && !a.abilityName().isBlank())
                .collect(Collectors.groupingBy(a -> normalizeAbilityName(a.abilityName()),
                        Collectors.collectingAndThen(
                                Collectors.maxBy(Comparator.comparingInt(a -> a.masteryLevel() != null ? a.masteryLevel() : 0)),
                                opt -> opt.map(EmployeeAbilityDTO::masteryLevel).orElse(0))));
        Map<Long, TagDTO> tagMap = buildTagMap(postRequirements.stream()
                .map(PostAbilityDTO::tagId).collect(Collectors.toSet()));
        // 统一加载启用资源，能力差距按 abilityName 绑定真实资源（tagId 仅辅助）。
        List<LearningResource> allResources = loadAllEnabledResources();
        List<LearningPathStep> steps = new ArrayList<>();
        int sortOrder = 0;
        for (PostAbilityDTO requirement : postRequirements) {
            Long tagId = requirement.tagId();
            String abilityName = requirement.abilityName();
            int currentLevel = empAbilityMap.getOrDefault(normalizeAbilityName(abilityName), 0);
            int targetLevel = requirement.minRequiredLevel() != null ? requirement.minRequiredLevel() : 3;
            boolean isCore = requirement.isCore() != null && requirement.isCore() == 1;
            if (currentLevel < targetLevel || isCore) {
                steps.add(createStepFromGap(planId, abilityName, tagId, tagMap.get(tagId), currentLevel,
                        targetLevel, isCore, sortOrder++, allResources));
            }
        }
        if (steps.isEmpty()) {
            throw new BusinessException(400, "当前人员已满足岗位能力要求，无需生成学习路径");
        }
        return steps;
    }

    private String normalizeAbilityName(String abilityName) {
        return AbilityNameNormalizer.normalize(abilityName);
    }

    /**
     * 从 Agent 建议创建项目任务实体
     */
    private LearningProjectTask createProjectTaskFromAgent(Long planId, LearningPathStep step,
                                                            LearningPathAgentResult.ProjectTaskSuggestion suggestion) {
        LearningProjectTask task = new LearningProjectTask();
        task.setPlanId(planId);
        task.setStepId(step.getId());
        task.setAbilityTagId(suggestion.getAbilityTagId() != null ? suggestion.getAbilityTagId() : step.getAbilityTagId());
        task.setProjectName(suggestion.getProjectName() != null ? suggestion.getProjectName() : "AI 推荐项目");
        task.setProjectUrl(suggestion.getProjectUrl());
        task.setTaskTitle(suggestion.getTitle() != null ? suggestion.getTitle() : "基于「" + step.getAbilityName() + "」的实践任务");
        task.setTaskBackground("AI 根据岗位能力差距自动生成的实践项目。");
        task.setTaskRequirements(suggestion.getRequirements());
        task.setAcceptanceCriteria(suggestion.getAcceptanceCriteria());
        task.setDifficultyLevel(mapAgentDifficulty(suggestion.getDifficulty()));
        task.setExpectedOutput(suggestion.getExpectedOutput());
        task.setStatus("PENDING");
        task.setIsDeleted(0);
        return task;
    }

    /**
     * 从 Agent 建议创建评估题目实体
     */
    private LearningAssessmentItem createAssessmentFromAgent(Long planId, List<LearningPathStep> steps,
                                                              LearningPathAgentResult.AssessmentSuggestion suggestion) {
        LearningAssessmentItem item = new LearningAssessmentItem();
        item.setPlanId(planId);

        // 尝试匹配到对应的步骤
        if (suggestion.getAbilityTagId() != null) {
            for (LearningPathStep step : steps) {
                if (suggestion.getAbilityTagId().equals(step.getAbilityTagId())) {
                    item.setStepId(step.getId());
                    break;
                }
            }
        }
        item.setAbilityTagId(suggestion.getAbilityTagId());
        item.setQuestionType(suggestion.getQuestionType() != null ? suggestion.getQuestionType() : "INTERVIEW");
        item.setQuestionText(suggestion.getQuestionText());
        item.setReferenceAnswer(suggestion.getReferenceAnswer());
        item.setDifficultyLevel(suggestion.getDifficulty() != null ? suggestion.getDifficulty() : "MEDIUM");
        item.setSource("AI_GENERATED");
        item.setIsDeleted(0);
        return item;
    }

    private String buildFallbackStepDesc(LearningPathStep step) {
        return "目标能力：" + step.getAbilityName() + "\n"
                + "当前等级：L" + step.getCurrentLevel() + "\n"
                + "目标等级：L" + step.getTargetLevel() + "\n"
                + "建议：通过学习资源和项目实践，系统提升该能力至目标等级。";
    }

    private String mapAgentDifficulty(String agentDifficulty) {
        if (agentDifficulty == null) return "MEDIUM";
        return switch (agentDifficulty.toUpperCase()) {
            case "EASY" -> "LOW";
            case "HARD" -> "HIGH";
            default -> "MEDIUM";
        };
    }

    private Map<Long, TagDTO> buildTagMap(Set<Long> tagIds) {
        Map<Long, TagDTO> tagMap = new HashMap<>();
        Set<Long> validTagIds = tagIds == null ? Set.of() : tagIds.stream()
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        if (!validTagIds.isEmpty()) {
            List<TagDTO> tags = tagQueryPort.batchGetTags(new ArrayList<>(validTagIds));
            tagMap = tags.stream().collect(Collectors.toMap(TagDTO::id, t -> t, (a, b) -> a));
        }
        return tagMap;
    }
}
