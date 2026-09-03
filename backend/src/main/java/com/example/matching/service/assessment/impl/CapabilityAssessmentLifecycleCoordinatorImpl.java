package com.example.matching.service.assessment.impl;

import com.example.matching.common.enums.StageLifecycleEventType;
import com.example.matching.common.enums.StageRunStatusEnum;
import com.example.matching.common.enums.WorkflowStatusEnum;
import com.example.matching.entity.workflow.CapabilityStageLifecycleEventLog;
import com.example.matching.entity.workflow.PersonCapabilityStageRun;
import com.example.matching.entity.workflow.PersonCapabilityWorkflow;
import com.example.matching.event.CapabilityStageLifecycleEvent;
import com.example.matching.service.assessment.AbilityLevelConfirmationService;
import com.example.matching.service.assessment.AbilityProfileProjectionService;
import com.example.matching.service.assessment.CapabilityAssessmentLifecycleCoordinator;
import com.example.matching.service.assessment.CapabilityAssessmentWorkflowService;
import com.example.matching.service.assessment.LifecycleTransitionRules;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.time.LocalDateTime;

/**
 * 能力评估生命周期协调器实现
 * <p>
 * 唯一允许推进/结束工作流状态的入口：幂等去重 -> 校验 -> StageRun CAS ->
 * Workflow 状态转换表 CAS -> 审计日志。
 * <p>
 * 协调器只依赖 assessment 域接口（{@link CapabilityAssessmentWorkflowService} 提供底层 CAS，
 * {@link AbilityLevelConfirmationService} 提供等级确认业务状态），不直接依赖 mapper。
 *
 * @author system
 */
@Slf4j
@Service
public class CapabilityAssessmentLifecycleCoordinatorImpl implements CapabilityAssessmentLifecycleCoordinator {

    /** 最终失败时工作流进入 FAILED；处理结果标记 */
    private static final String RESULT_HANDLED = "HANDLED";
    private static final String RESULT_DUPLICATE = "SKIPPED_DUPLICATE";
    private static final String RESULT_ILLEGAL = "SKIPPED_ILLEGAL";
    private static final String RESULT_STAGE_RUN_NOT_FOUND = "STAGE_RUN_NOT_FOUND";
    private static final String RESULT_FAILED = "FAILED";

    private final CapabilityAssessmentWorkflowService workflowService;
    private final AbilityLevelConfirmationService levelConfirmationService;

    @org.springframework.beans.factory.annotation.Autowired
    public CapabilityAssessmentLifecycleCoordinatorImpl(
            CapabilityAssessmentWorkflowService workflowService,
            AbilityLevelConfirmationService levelConfirmationService) {
        this.workflowService = workflowService;
        this.levelConfirmationService = levelConfirmationService;
    }

    /** Compatibility constructor retained for existing integration fixtures. */
    public CapabilityAssessmentLifecycleCoordinatorImpl(
            CapabilityAssessmentWorkflowService workflowService,
            AbilityLevelConfirmationService levelConfirmationService,
            AbilityProfileProjectionService ignoredProjectionService) {
        this(workflowService, levelConfirmationService);
    }

    @Override
    @Transactional
    public void handle(CapabilityStageLifecycleEvent event) {
        if (event == null || event.eventId() == null || event.eventType() == null || event.workflowId() == null) {
            log.warn("生命周期事件缺少必要字段，丢弃: {}", event);
            return;
        }
        // 1. 幂等去重：eventId 唯一，已处理直接跳过
        if (!beginEvent(event)) {
            log.info("生命周期事件已处理，跳过: eventId={}, type={}", event.eventId(), event.eventType());
            return;
        }
        PersonCapabilityWorkflow workflow = workflowService.getWorkflow(event.workflowId());
        if (workflow == null) {
            log.warn("生命周期事件对应工作流不存在，丢弃: eventId={}, workflowId={}", event.eventId(), event.workflowId());
            return;
        }
        // 终态工作流不再接受任何推进事件（重试由 USER_ACTION_STARTED 从 FAILED 恢复）
        if (workflow.getStatus() != null && !WorkflowStatusEnum.FAILED.getCode().equals(workflow.getStatus())
                && WorkflowStatusEnum.fromCode(workflow.getStatus()) != null
                && WorkflowStatusEnum.fromCode(workflow.getStatus()).isTerminal()) {
            log.warn("生命周期事件到达终态工作流，跳过: eventId={}, workflowId={}, status={}",
                    event.eventId(), event.workflowId(), workflow.getStatus());
            writeLog(event, null, workflow.getStatus(), null, null, RESULT_ILLEGAL, "终态工作流拒绝事件");
            return;
        }

        // 2. 解析阶段运行：优先事件携带的 stageRunId，否则按 workflowId+stageType+sourceRef 解析
        PersonCapabilityStageRun stageRun = resolveStageRun(event);
        if (stageRun == null) {
            log.warn("生命周期事件对应阶段运行不存在，跳过: eventId={}, workflowId={}, stageType={}, sourceRef={}/{}",
                    event.eventId(), event.workflowId(), event.stageType(), event.sourceRefType(), event.sourceRefId());
            writeLog(event, null, workflow.getStatus(), null, null, RESULT_STAGE_RUN_NOT_FOUND, "阶段运行不存在");
            return;
        }
        // 3. 校验阶段归属
        if (!event.workflowId().equals(stageRun.getWorkflowId())) {
            log.warn("生命周期事件阶段运行归属不符，跳过: eventId={}, workflowId={}, stageRun.workflowId={}",
                    event.eventId(), event.workflowId(), stageRun.getWorkflowId());
            writeLog(event, stageRun, workflow.getStatus(), stageRun.getStatus(), stageRun.getStatus(),
                    RESULT_ILLEGAL, "阶段运行不属于该工作流");
            return;
        }
        if (event.stageType() != null && !event.stageType().equals(stageRun.getStageType())) {
            log.warn("生命周期事件阶段类型不匹配，跳过: eventId={}, expect={}, actual={}",
                    event.eventId(), event.stageType(), stageRun.getStageType());
            writeLog(event, stageRun, workflow.getStatus(), stageRun.getStatus(), stageRun.getStatus(),
                    RESULT_ILLEGAL, "阶段类型不匹配");
            return;
        }

        String workflowBefore = workflow.getStatus();
        String stageRunBefore = stageRun.getStatus();
        try {
            // 4. 最终失败：工作流整体 FAILED
            if (LifecycleTransitionRules.isWorkflowFinalFailure(event.eventType())) {
                applyFinalFailure(workflow, stageRun, event);
                writeLog(event, stageRun, workflowBefore, stageRunBefore, StageRunStatusEnum.FAILED_FINAL.getCode(),
                        RESULT_HANDLED, event.errorMessage());
                return;
            }
            // 5. 更新阶段运行状态（CAS，仅状态前进）
            String stageRunAfter = applyStageRunTransition(stageRun, event);
            // 6. 工作流状态转换（状态转换表 CAS）
            String workflowAfter = applyWorkflowTransition(workflow, stageRun, event);
            // 7. 活跃阶段运行同步
            syncActiveStageRun(workflow, stageRun, stageRunAfter);
            writeLog(event, stageRun, workflowBefore, stageRunBefore, stageRunAfter, RESULT_HANDLED,
                    "workflow:" + workflowBefore + "->" + workflowAfter);
        } catch (Exception e) {
            log.error("生命周期事件处理异常: eventId={}, error={}", event.eventId(), e.getMessage(), e);
            writeLog(event, stageRun, workflowBefore, stageRunBefore, stageRunBefore, RESULT_FAILED, e.getMessage());
        }
    }

    /**
     * 进程内事件加速：业务事务提交后再同步处理（eventId 幂等，与 MQ 链路重复无害）。
     */
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onLocalLifecycleEvent(CapabilityStageLifecycleEvent event) {
        try {
            handle(event);
        } catch (Exception e) {
            // 进程内加速失败不影响业务事务；可靠链路由 Outbox + RabbitMQ 兜底
            log.warn("进程内生命周期事件处理失败（将由 MQ 链路兜底）: eventId={}, error={}",
                    event != null ? event.eventId() : null, e.getMessage());
        }
    }

    /**
     * 幂等开始处理：检查 eventId 是否已处理（并发场景由唯一键兜底）。
     */
    private boolean beginEvent(CapabilityStageLifecycleEvent event) {
        return !workflowService.existsLifecycleEvent(event.eventId());
    }

    /**
     * 解析阶段运行：优先事件携带的 ID；否则按 workflowId + stageType + sourceRef 解析最近一条活跃记录。
     */
    private PersonCapabilityStageRun resolveStageRun(CapabilityStageLifecycleEvent event) {
        if (event.stageRunId() != null) {
            return workflowService.getStageRun(event.stageRunId());
        }
        if (event.stageType() == null) {
            return null;
        }
        return workflowService.resolveActiveStageRun(
                event.workflowId(), event.stageType(), event.sourceRefType(), event.sourceRefId());
    }

    /**
     * 阶段运行状态 CAS 推进，返回更新后的状态；不合法则保持原状态。
     */
    private String applyStageRunTransition(PersonCapabilityStageRun stageRun, CapabilityStageLifecycleEvent event) {
        LifecycleTransitionRules.StageRunTransition rule = LifecycleTransitionRules.findStageRunTransition(event.eventType());
        if (rule == null) {
            return stageRun.getStatus();
        }
        String current = stageRun.getStatus();
        StageRunStatusEnum currentEnum = StageRunStatusEnum.fromCode(current);
        if (currentEnum == null || !rule.allowedFrom().contains(currentEnum)) {
            // 事件乱序/迟到/重复的正常幂等拒绝路径（如 TASK_CLAIMED 晚于 TASK_SUCCEEDED 到达、
            // 已终态 run 收到重复事件），非异常——工作流级转换仍会正确推进，降为 debug 避免噪音
            log.debug("阶段运行状态不允许该事件（幂等/乱序拒绝，保持原状态）: stageRunId={}, status={}, event={}",
                    stageRun.getId(), current, event.eventType());
            return current;
        }
        String target = rule.to().getCode();
        boolean updated = workflowService.casStageRunStatus(
                stageRun.getId(), current, target, event.errorCode(), event.errorMessage());
        if (updated) {
            log.info("阶段运行状态推进: stageRunId={}, {} -> {}", stageRun.getId(), current, target);
            return target;
        }
        return current;
    }

    /**
     * 工作流状态转换（状态转换表 CAS），返回更新后的状态。
     */
    private String applyWorkflowTransition(PersonCapabilityWorkflow workflow, PersonCapabilityStageRun stageRun,
                                           CapabilityStageLifecycleEvent event) {
        LifecycleTransitionRules.WorkflowTransition transition =
                LifecycleTransitionRules.findWorkflowTransition(event.stageType(), event.eventType(), workflow.getStatus());
        if (transition == null) {
            // 允许状态不变化的事件（如 TEST_GENERATING 收到 TASK_CLAIMED 保持），不视为非法
            log.info("工作流状态转换无规则匹配，保持当前状态: workflowId={}, status={}, stage={}, event={}",
                    workflow.getId(), workflow.getStatus(), event.stageType(), event.eventType());
            return workflow.getStatus();
        }
        String target = transition.toWorkflowStatus();
        // 评估执行链在聚合审核和等级确认完成后即结束。待人工 Harness 复核是
        // 独立的治理队列：它不会阻塞本次评估收口，也不会阻塞下一次评估创建。
        // 人工通过时由 AggregateAbilityHarnessReviewService 独立完成等级确认和正式投影。
        if (("LEVEL_CONFIRMATION".equals(event.stageType()) || "AGGREGATE_HARNESS".equals(event.stageType())
                || "AGGREGATE_HARNESS_AND_LEVEL_CONFIRMATION".equals(event.stageType()))
                && event.eventType() == StageLifecycleEventType.TASK_SUCCEEDED) {
            target = WorkflowStatusEnum.COMPLETED.getCode();
        }
        boolean updated = workflowService.transition(
                workflow.getId(), workflow.getStatus(), target, transition.currentStage());
        if (updated) {
            log.info("工作流状态推进: workflowId={}, {} -> {} (stage={})",
                    workflow.getId(), workflow.getStatus(), target, transition.currentStage());
            return target;
        }
        return workflow.getStatus();
    }

    /**
     * 同步工作流当前活跃阶段运行。
     */
    private void syncActiveStageRun(PersonCapabilityWorkflow workflow, PersonCapabilityStageRun stageRun, String stageRunAfter) {
        if (StageRunStatusEnum.ACTIVE_STATUSES.stream().anyMatch(s -> s.getCode().equals(stageRunAfter))) {
            workflowService.syncActiveStageRun(workflow.getId(), stageRun.getId());
        }
    }

    /**
     * 最终失败：阶段运行 -> FAILED_FINAL，工作流 -> FAILED + failedReason。
     */
    private void applyFinalFailure(PersonCapabilityWorkflow workflow, PersonCapabilityStageRun stageRun,
                                   CapabilityStageLifecycleEvent event) {
        boolean stageUpdated = workflowService.casStageRunStatus(
                stageRun.getId(), stageRun.getStatus(), StageRunStatusEnum.FAILED_FINAL.getCode(),
                event.errorCode(), event.errorMessage());
        String reason = "阶段[" + stageRun.getStageType() + "]最终失败: "
                + (event.errorMessage() != null ? event.errorMessage() : event.errorCode());
        workflowService.markWorkflowFinalFailed(workflow.getId(), reason);
        log.warn("工作流最终失败: workflowId={}, stageRunId={}, stage={}, reason={}, stageUpdated={}",
                workflow.getId(), stageRun.getId(), stageRun.getStageType(), reason, stageUpdated);
    }

    /**
     * 审计日志补充：状态变化后回填 before/after。
     */
    private void writeLog(CapabilityStageLifecycleEvent event, PersonCapabilityStageRun stageRun,
                          String workflowBefore, String stageRunBefore, String stageRunAfter,
                          String result, String remark) {
        try {
            CapabilityStageLifecycleEventLog logRecord = new CapabilityStageLifecycleEventLog();
            logRecord.setEventId(event.eventId());
            logRecord.setWorkflowId(event.workflowId());
            logRecord.setStageRunId(stageRun != null ? stageRun.getId() : event.stageRunId());
            logRecord.setStageType(event.stageType());
            logRecord.setEventType(event.eventType().getCode());
            logRecord.setSourceRefType(event.sourceRefType());
            logRecord.setSourceRefId(event.sourceRefId());
            logRecord.setWorkflowStatusBefore(workflowBefore);
            logRecord.setStageRunStatusBefore(stageRunBefore);
            logRecord.setStageRunStatusAfter(stageRunAfter);
            logRecord.setHandledResult(result);
            logRecord.setRemark(truncate(remark, 1000));
            logRecord.setOccurredAt(event.occurredAt());
            logRecord.setCreatedTime(LocalDateTime.now());
            workflowService.recordLifecycleEventLog(logRecord);
            // Complete the durable audit row as well; this keeps the event log
            // contract consistent for both inserted and updated lifecycle records.
            workflowService.completeLifecycleEventLog(logRecord);
        } catch (Exception e) {
            log.warn("生命周期事件审计日志补充写入失败: eventId={}, error={}", event.eventId(), e.getMessage());
        }
    }

    private String truncate(String text, int max) {
        if (text == null) {
            return null;
        }
        return text.length() <= max ? text : text.substring(0, max);
    }
}
