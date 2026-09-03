package com.example.matching.schedule;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.example.matching.common.enums.StageRunStatusEnum;
import com.example.matching.common.enums.WorkflowStatusEnum;
import com.example.matching.entity.ability.PersonAbilityClaim;
import com.example.matching.entity.employee.EmpAiTest;
import com.example.matching.entity.workflow.PersonCapabilityStageRun;
import com.example.matching.entity.workflow.PersonCapabilityWorkflow;
import com.example.matching.mapper.ability.PersonAbilityClaimMapper;
import com.example.matching.mapper.employee.EmpAiTestMapper;
import com.example.matching.mapper.workflow.PersonCapabilityStageRunMapper;
import com.example.matching.mapper.workflow.PersonCapabilityWorkflowMapper;
import com.example.matching.port.assessment.CapabilityStageLifecycleEventPublisher;
import com.example.matching.event.CapabilityStageLifecycleEvent;
import com.example.matching.common.enums.StageLifecycleEventType;
import com.example.matching.service.assessment.LifecycleTransitionRules;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 能力评估工作流一致性巡检
 * <p>
 * 每分钟扫描活跃工作流，发现并修复以下不一致（只生成补偿事件，不猜测成功）：
 * <ol>
 *   <li>工作流显示处理中，但无对应 PENDING/RUNNING/WAITING_USER 阶段运行；</li>
 *   <li>阶段运行已 SUCCEEDED，工作流却仍停留在前序状态；</li>
 *   <li>工作流超过配置时长无更新时间；</li>
 *   <li>存在 FAILED_RETRYABLE 的阶段但未生成下一次重试任务。</li>
 * </ol>
 * 每次补偿写审计日志（capability_stage_lifecycle_event_log：原状态、目标状态、依据记录、处理结果）。
 *
 * @author system
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class CapabilityAssessmentWorkflowReconciler {

    /** 活跃工作流超过该时长（分钟）无更新时间视为卡死 */
    private static final long STALE_MINUTES = 30;

    private static final List<String> ACTIVE_WORKFLOW_STATUSES = List.of(
            WorkflowStatusEnum.RESUME_PARSING.getCode(),
            WorkflowStatusEnum.TEST_GENERATING.getCode(),
            WorkflowStatusEnum.TEST_EVALUATING.getCode(),
            WorkflowStatusEnum.INTERVIEW_PREPARING.getCode(),
            WorkflowStatusEnum.INTERVIEW_IN_PROGRESS.getCode(),
            WorkflowStatusEnum.INTERVIEW_ANALYZING.getCode(),
            WorkflowStatusEnum.AGGREGATE_HARNESS_RUNNING.getCode(),
            WorkflowStatusEnum.LEVEL_CONFIRMING.getCode()
    );

    private final PersonCapabilityWorkflowMapper workflowMapper;
    private final PersonCapabilityStageRunMapper stageRunMapper;
    private final CapabilityStageLifecycleEventPublisher lifecycleEventPublisher;
    private final EmpAiTestMapper empAiTestMapper;
    private final PersonAbilityClaimMapper personAbilityClaimMapper;
    private final com.example.matching.mapper.employee.EmpVideoInterviewSessionMapper interviewSessionMapper;
    private final com.example.matching.service.assessment.InterviewAssessmentEvidenceService interviewEvidenceService;
    private final ApplicationEventPublisher applicationEventPublisher;

    @org.springframework.beans.factory.annotation.Autowired(required = false)
    private ScheduledTaskRunner taskRunner;

    @Scheduled(fixedDelay = 60_000, initialDelay = 90_000)
    public void reconcileWorkflows() {
        runScheduled("capability_workflow_reconcile", this::reconcileInternal);
    }

    private void runScheduled(String taskName, Runnable task) {
        if (taskRunner != null) {
            taskRunner.run(taskName, task);
            return;
        }
        try {
            task.run();
        } catch (Exception e) {
            log.error("能力评估工作流一致性巡检失败", e);
        }
    }

    /**
     * 包可见供测试直接调用（跳过 ScheduledTaskRunner 包装）。
     */
    void reconcileInternal() {
        List<PersonCapabilityWorkflow> workflows = workflowMapper.selectList(
                new LambdaQueryWrapper<PersonCapabilityWorkflow>()
                        .in(PersonCapabilityWorkflow::getStatus, ACTIVE_WORKFLOW_STATUSES)
                        .last("LIMIT 500"));
        if (workflows.isEmpty()) {
            return;
        }
        int checked = 0;
        int compensated = 0;
        for (PersonCapabilityWorkflow workflow : workflows) {
            checked++;
            compensated += reconcileWorkflow(workflow);
        }
        log.info("能力评估工作流一致性巡检完成: 扫描={}, 补偿={}", checked, compensated);
    }

    private int reconcileWorkflow(PersonCapabilityWorkflow workflow) {
        Long workflowId = workflow.getId();
        int compensated = 0;
        List<PersonCapabilityStageRun> runs = stageRunMapper.selectList(
                new LambdaQueryWrapper<PersonCapabilityStageRun>()
                        .eq(PersonCapabilityStageRun::getWorkflowId, workflowId)
                        .orderByAsc(PersonCapabilityStageRun::getId));

        // 规则 1：工作流处理中，但无任何活跃阶段运行（任务漏投/事件漏发）
        boolean hasActiveRun = runs.stream().anyMatch(r ->
                StageRunStatusEnum.ACTIVE_STATUSES.stream().anyMatch(s -> s.getCode().equals(r.getStatus())));
        if (!hasActiveRun) {
            log.warn("工作流处理中但无活跃阶段运行: workflowId={}, status={}", workflowId, workflow.getStatus());
            // 有已成功阶段但工作流未推进：生成 TASK_SUCCEEDED 补偿事件（协调器依据转换表推进）
            PersonCapabilityStageRun succeeded = runs.stream()
                    .filter(r -> StageRunStatusEnum.SUCCEEDED.getCode().equals(r.getStatus()))
                    .filter(r -> LifecycleTransitionRules.findWorkflowTransition(
                            r.getStageType(), StageLifecycleEventType.TASK_SUCCEEDED, workflow.getStatus()) != null)
                    .findFirst().orElse(null);
            if (succeeded != null) {
                publishCompensation(workflow, succeeded, StageLifecycleEventType.TASK_SUCCEEDED,
                        "阶段已成功但工作流未推进");
                compensated++;
            }
            // 有失败阶段但工作流未失败：生成 TASK_FAILED_FINAL 补偿事件
            PersonCapabilityStageRun failed = runs.stream()
                    .filter(r -> StageRunStatusEnum.FAILED_FINAL.getCode().equals(r.getStatus()))
                    .findFirst().orElse(null);
            if (failed != null) {
                publishCompensation(workflow, failed, StageLifecycleEventType.TASK_FAILED_FINAL,
                        "阶段已最终失败但工作流未失败");
                compensated++;
            }
        }

        // 规则 1.5：AI_TEST_EVALUATION 阶段运行仍活跃，但对应测试评分已完成（事件漏发兜底）。
        // 依据业务事实（emp_ai_test.status=2）而非猜测：先确保测试证据已保存，再补发成功事件。
        List<PersonCapabilityStageRun> evaluatingActive = runs.stream()
                .filter(r -> "AI_TEST_EVALUATION".equals(r.getStageType()))
                .filter(r -> StageRunStatusEnum.ACTIVE_STATUSES.stream()
                        .anyMatch(s -> s.getCode().equals(r.getStatus())))
                .toList();
        for (PersonCapabilityStageRun run : evaluatingActive) {
            if (!"AI_TEST".equals(run.getSourceRefType()) || run.getSourceRefId() == null) {
                continue;
            }
            EmpAiTest test = empAiTestMapper.selectById(run.getSourceRefId());
            if (test == null || test.getWorkflowId() == null
                    || !test.getWorkflowId().equals(workflowId)
                    || test.getStatus() == null || test.getStatus() != 2) {
                continue;
            }
            Long evidenceCount = personAbilityClaimMapper.selectCount(
                    new LambdaQueryWrapper<PersonAbilityClaim>()
                            .eq(PersonAbilityClaim::getWorkflowId, workflowId)
                            .eq(PersonAbilityClaim::getSourceType, "AI_TEST")
                            .eq(PersonAbilityClaim::getSourceRefId, test.getId()));
            if (evidenceCount != null && evidenceCount > 0) {
                log.warn("测试评分已完成且证据已保存，但阶段运行未推进，补发 TASK_SUCCEEDED: workflowId={}, stageRunId={}, testId={}",
                        workflowId, run.getId(), run.getSourceRefId());
                publishCompensation(workflow, run, StageLifecycleEventType.TASK_SUCCEEDED,
                        "测试评分已完成且证据已保存，但阶段运行仍活跃");
                compensated++;
            } else {
                // 证据未保存：重新发布评估完成事件，由监听器保存证据并推进工作流
                log.warn("测试评分已完成但证据未保存，重新发布评估事件: workflowId={}, testId={}",
                        workflowId, test.getId());
                applicationEventPublisher.publishEvent(
                        new com.example.matching.event.AiTestEvaluatedEvent(
                                test.getId(), test.getEmpId(), test.getWorkflowId()));
                compensated++;
            }
        }

        // 规则 2：存在 FAILED_RETRYABLE 的阶段但未生成下一次重试任务（协调器/消费者未处理）
        List<PersonCapabilityStageRun> retryable = runs.stream()
                .filter(r -> StageRunStatusEnum.FAILED_RETRYABLE.getCode().equals(r.getStatus()))
                .toList();
        for (PersonCapabilityStageRun run : retryable) {
            Long newerCount = stageRunMapper.selectCount(new LambdaQueryWrapper<PersonCapabilityStageRun>()
                    .eq(PersonCapabilityStageRun::getWorkflowId, workflowId)
                    .eq(PersonCapabilityStageRun::getStageType, run.getStageType())
                    .gt(PersonCapabilityStageRun::getId, run.getId()));
            if (newerCount == null || newerCount == 0) {
                log.warn("存在可重试失败阶段但无后续重试任务: workflowId={}, stageRunId={}, stage={}",
                        workflowId, run.getId(), run.getStageType());
                // 发布 USER_ACTION_STARTED 补偿事件，协调器恢复工作流并重新投递由业务层驱动
                publishCompensation(workflow, run, StageLifecycleEventType.USER_ACTION_STARTED,
                        "可重试失败阶段未生成重试任务");
                compensated++;
            }
        }

        // 规则 4：面试阶段僵尸补偿——工作流 INTERVIEW_IN_PROGRESS/INTERVIEW_ANALYZING 且
        // 面试会话已分析完成（status=5 COMPLETED）但工作流未推进（WS 中断/事件丢失兜底）。
        // 依据业务事实（会话分析完成）而非猜测：分析未完成的会话交给 InterviewAnalysisRecoveryScheduler。
        // 注意：AI_INTERVIEW 运行可能已 SUCCEEDED（分析完成、事件漏发导致工作流未跟进），
        // 必须查最新一条运行（不限活跃状态），否则补偿永远找不到目标。
        if (WorkflowStatusEnum.INTERVIEW_IN_PROGRESS.getCode().equals(workflow.getStatus())
                || WorkflowStatusEnum.INTERVIEW_ANALYZING.getCode().equals(workflow.getStatus())) {
            PersonCapabilityStageRun interviewRun = null;
            for (PersonCapabilityStageRun r : runs) {
                if ("AI_INTERVIEW".equals(r.getStageType())) {
                    interviewRun = r; // runs 按 id 升序，最后一条即最新
                }
            }
            if (interviewRun != null && interviewRun.getSourceRefId() != null) {
                com.example.matching.entity.employee.EmpVideoInterviewSession session =
                        interviewSessionMapper.selectById(interviewRun.getSourceRefId());
                boolean analysisCompleted = session != null && session.getStatus() != null && session.getStatus() == 5;
                if (analysisCompleted) {
                    if (WorkflowStatusEnum.INTERVIEW_IN_PROGRESS.getCode().equals(workflow.getStatus())) {
                        log.warn("面试已结束且分析完成但工作流仍进行中，补发 USER_ACTION_COMPLETED: workflowId={}, sessionId={}",
                                workflowId, session.getId());
                        publishCompensation(workflow, interviewRun, StageLifecycleEventType.USER_ACTION_COMPLETED,
                                "面试已结束且分析完成，但工作流未进入分析");
                        compensated++;
                    }
                    if (StageRunStatusEnum.SUCCEEDED.getCode().equals(interviewRun.getStatus())) {
                        // 阶段已成功（证据已保存、聚合阶段已创建）：补发 TASK_SUCCEEDED 推进工作流
                        log.warn("面试分析完成但工作流未推进聚合，补发 TASK_SUCCEEDED: workflowId={}, sessionId={}",
                                workflowId, session.getId());
                        publishCompensation(workflow, interviewRun, StageLifecycleEventType.TASK_SUCCEEDED,
                                "面试分析完成但工作流未推进聚合审核");
                        compensated++;
                    } else {
                        // 分析完成但阶段从未推进成功：重放完整链路（幂等，仅此一次）
                        log.warn("面试分析完成但阶段未推进，重放证据保存与聚合推进: workflowId={}, sessionId={}",
                                workflowId, session.getId());
                        try {
                            interviewEvidenceService.saveInterviewEvidenceAndAdvance(
                                    workflowId, workflow.getEmpId(), session.getId());
                            compensated++;
                        } catch (Exception e) {
                            log.error("重放面试证据保存失败: workflowId={}, sessionId={}, error={}",
                                    workflowId, session.getId(), e.getMessage(), e);
                        }
                    }
                }
            }
        }

        // 规则 3：工作流超过配置时长无更新时间（僵尸状态），仅告警，交由人工/业务恢复
        if (workflow.getUpdatedTime() != null
                && workflow.getUpdatedTime().isBefore(LocalDateTime.now().minusMinutes(STALE_MINUTES))) {
            log.warn("活跃工作流长时间无更新: workflowId={}, status={}, updatedTime={}",
                    workflowId, workflow.getStatus(), workflow.getUpdatedTime());
        }
        return compensated;
    }

    /**
     * 生成补偿生命周期事件（依据业务记录与阶段运行的真实状态，不猜测成功）。
     * 事件写入审计日志：原状态、目标状态、依据记录、处理结果由协调器完成。
     */
    private void publishCompensation(PersonCapabilityWorkflow workflow, PersonCapabilityStageRun stageRun,
                                     StageLifecycleEventType eventType, String remark) {
        try {
            lifecycleEventPublisher.publish(CapabilityStageLifecycleEvent.of(
                    workflow.getId(), stageRun.getId(), stageRun.getStageType(),
                    stageRun.getSourceRefType(), stageRun.getSourceRefId(), eventType,
                    "RECONCILER_COMPENSATION", remark));
        } catch (Exception e) {
            log.warn("生成补偿事件失败: workflowId={}, stageRunId={}, event={}, error={}",
                    workflow.getId(), stageRun.getId(), eventType, e.getMessage());
        }
    }
}
