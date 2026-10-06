package com.example.matching.service.assessment.impl;

import com.example.matching.common.enums.DecisionStatusEnum;
import com.example.matching.common.enums.StageTypeEnum;
import com.example.matching.common.enums.WorkflowStatusEnum;
import com.example.matching.entity.workflow.PersonCapabilityStageRun;
import com.example.matching.entity.workflow.PersonCapabilityWorkflow;
import com.example.matching.mapper.workflow.PersonCapabilityStageRunMapper;
import com.example.matching.service.assessment.AbilityLevelConfirmationService;
import com.example.matching.service.assessment.AbilityProfileProjectionService;
import com.example.matching.service.assessment.AggregateAbilityHarnessService;
import com.example.matching.service.assessment.CapabilityAssessmentStageRunner;
import com.example.matching.service.assessment.CapabilityAssessmentWorkflowService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 能力评估阶段执行器实现
 * <p>
 * 执行单个阶段，调用既有 Resume/Test/Interview/PMS 服务。
 * AGGREGATE_HARNESS / LEVEL_CONFIRMATION 已完整接入；
 * 简历、测试、面试阶段由后续改造接入。
 *
 * @author system
 */
@Slf4j
@Service
public class CapabilityAssessmentStageRunnerImpl implements CapabilityAssessmentStageRunner {

    private final PersonCapabilityStageRunMapper stageRunMapper;
    private final CapabilityAssessmentWorkflowService workflowService;
    private final AggregateAbilityHarnessService aggregateHarnessService;
    private final AbilityLevelConfirmationService levelConfirmationService;
    private final AbilityProfileProjectionService projectionService;
    private final com.example.matching.service.employee.ResumeParseService resumeParseService;
    private final com.example.matching.service.assessment.InterviewAssessmentEvidenceService interviewEvidenceService;
    private final com.example.matching.port.assessment.CapabilityStageLifecycleEventPublisher lifecycleEventPublisher;
    private final com.example.matching.service.assessment.AssessmentReportService assessmentReportService;

    public CapabilityAssessmentStageRunnerImpl(
            PersonCapabilityStageRunMapper stageRunMapper,
            CapabilityAssessmentWorkflowService workflowService,
            AggregateAbilityHarnessService aggregateHarnessService,
            AbilityLevelConfirmationService levelConfirmationService,
            AbilityProfileProjectionService projectionService,
            com.example.matching.service.employee.ResumeParseService resumeParseService,
            com.example.matching.service.assessment.InterviewAssessmentEvidenceService interviewEvidenceService,
            com.example.matching.port.assessment.CapabilityStageLifecycleEventPublisher lifecycleEventPublisher,
            com.example.matching.service.assessment.AssessmentReportService assessmentReportService) {
        this.stageRunMapper = stageRunMapper;
        this.workflowService = workflowService;
        this.aggregateHarnessService = aggregateHarnessService;
        this.levelConfirmationService = levelConfirmationService;
        this.projectionService = projectionService;
        this.resumeParseService = resumeParseService;
        this.interviewEvidenceService = interviewEvidenceService;
        this.lifecycleEventPublisher = lifecycleEventPublisher;
        this.assessmentReportService = assessmentReportService;
    }

    @Override
    public void runStage(Long stageRunId) {
        PersonCapabilityStageRun stageRun = stageRunMapper.selectById(stageRunId);
        if (stageRun == null) {
            log.warn("阶段运行不存在: {}", stageRunId);
            return;
        }
        // 幂等抢占：重复投递/重复消费只执行一次
        if ("PENDING".equals(stageRun.getStatus())) {
            if (!workflowService.claimStageRun(stageRunId)) {
                log.info("阶段运行抢占失败（并发消费）: stageRunId={}", stageRunId);
                return;
            }
        } else if (!"RUNNING".equals(stageRun.getStatus()) || stageRun.getStartedAt() != null) {
            // retryStage 双队列竞态兜底：协调器先处理 USER_ACTION_STARTED 会把新阶段运行
            // PENDING -> RUNNING（且未置 startedAt），此时 stage.execute 任务才到达；
            // RUNNING + startedAt 为空 = 协调器预置未执行，允许继续执行。
            log.info("阶段运行非 PENDING 且非协调器预置 RUNNING，跳过重复执行: stageRunId={}, status={}",
                    stageRunId, stageRun.getStatus());
            return;
        }
        // 抢占成功：发布 TASK_CLAIMED，协调器同步阶段运行 RUNNING 并推进工作流状态
        publishLifecycle(stageRun, com.example.matching.common.enums.StageLifecycleEventType.TASK_CLAIMED, null, null);
        try {
            switch (stageRun.getStageType()) {
                case "AGGREGATE_HARNESS_AND_LEVEL_CONFIRMATION", "AGGREGATE_HARNESS" -> runAggregateHarnessStage(stageRun);
                case "LEVEL_CONFIRMATION" -> acknowledgeLegacyLevelStage(stageRun);
                case "RESUME_PARSE", "RESUME_CLAIM_EXTRACTION" -> runResumeEvidenceRetry(stageRun);
                case "AI_TEST_GENERATION", "AI_TEST_EVALUATION" -> runTestStageRetry(stageRun);
                case "AI_INTERVIEW" -> runInterviewStageRetry(stageRun);
                default -> throw new UnsupportedOperationException("未知阶段类型: " + stageRun.getStageType());
            }
        } catch (Exception e) {
            log.error("阶段执行失败: stageRunId={}, type={}", stageRunId, stageRun.getStageType(), e);
            boolean retryable = !(e instanceof UnsupportedOperationException)
                    && !(e instanceof IllegalStateException);
            // 不再直接改工作流：发布可重试/最终失败生命周期事件，由协调器统一处理
            publishLifecycle(stageRun, retryable
                            ? com.example.matching.common.enums.StageLifecycleEventType.TASK_FAILED_RETRYABLE
                            : com.example.matching.common.enums.StageLifecycleEventType.TASK_FAILED_FINAL,
                    e.getClass().getSimpleName(), e.getMessage() != null ? e.getMessage() : e.toString());
        }
    }

    /**
     * 发布阶段生命周期事件（工作流/重试场景）。
     */
    private void publishLifecycle(PersonCapabilityStageRun stageRun,
                                  com.example.matching.common.enums.StageLifecycleEventType eventType,
                                  String errorCode, String errorMessage) {
        try {
            lifecycleEventPublisher.publish(com.example.matching.event.CapabilityStageLifecycleEvent.of(
                    stageRun.getWorkflowId(), stageRun.getId(), stageRun.getStageType(),
                    stageRun.getSourceRefType(), stageRun.getSourceRefId(), eventType, errorCode, errorMessage));
        } catch (Exception e) {
            log.warn("发布阶段生命周期事件失败: stageRunId={}, event={}, error={}",
                    stageRun.getId(), eventType, e.getMessage());
        }
    }

    /**
     * 简历阶段重试：重新触发证据保存（幂等），不重复建组。
     */
    private void runResumeEvidenceRetry(PersonCapabilityStageRun stageRun) {
        if (stageRun.getSourceRefId() != null) {
            try {
                int saved = resumeParseService.saveResumeEvidenceForWorkflow(stageRun.getSourceRefId());
                // 证据保存内部会发布 TASK_SUCCEEDED；这里不再直接标记成功
                log.info("简历证据重试成功: stageRunId={}, saved={}", stageRun.getId(), saved);
                return;
            } catch (Exception e) {
                log.error("简历证据重试失败: stageRunId={}, error={}", stageRun.getId(), e.getMessage(), e);
                publishLifecycle(stageRun, com.example.matching.common.enums.StageLifecycleEventType.TASK_FAILED_FINAL,
                        "RETRY_FAILED", e.getMessage());
                return;
            }
        }
        publishLifecycle(stageRun, com.example.matching.common.enums.StageLifecycleEventType.TASK_SUCCEEDED, null, null);
    }

    /**
     * 测试阶段重试：题目生成/评分由独立 MQ 任务驱动并自带重试，
     * 工作流阶段标记成功避免 FAILED_FINAL 死锁。
     */
    private void runTestStageRetry(PersonCapabilityStageRun stageRun) {
        log.info("测试阶段由异步任务驱动（自带重试），工作流阶段标记成功: stageRunId={}, type={}",
                stageRun.getId(), stageRun.getStageType());
        publishLifecycle(stageRun, com.example.matching.common.enums.StageLifecycleEventType.TASK_SUCCEEDED, null, null);
    }

    /**
     * 面试阶段重试：重新推进面试证据与聚合审核（幂等，同阶段 run 复用）。
     */
    private void runInterviewStageRetry(PersonCapabilityStageRun stageRun) {
        if (stageRun.getSourceRefId() != null) {
            try {
                PersonCapabilityWorkflow workflow = workflowService.getWorkflow(stageRun.getWorkflowId());
                interviewEvidenceService.saveInterviewEvidenceAndAdvance(
                        stageRun.getWorkflowId(), workflow.getEmpId(), stageRun.getSourceRefId());
                // 证据保存内部会发布 TASK_SUCCEEDED
                log.info("面试证据重试成功: stageRunId={}", stageRun.getId());
                return;
            } catch (Exception e) {
                log.error("面试证据重试失败: stageRunId={}, error={}", stageRun.getId(), e.getMessage(), e);
                publishLifecycle(stageRun, com.example.matching.common.enums.StageLifecycleEventType.TASK_FAILED_FINAL,
                        "RETRY_FAILED", e.getMessage());
                return;
            }
        }
        publishLifecycle(stageRun, com.example.matching.common.enums.StageLifecycleEventType.TASK_SUCCEEDED, null, null);
    }

    /**
     * 聚合 Harness 阶段：执行批量审核，成功后推进工作流到 LEVEL_CONFIRMING。
     */
    private void runAggregateHarnessStage(PersonCapabilityStageRun stageRun) {
        Long workflowId = stageRun.getWorkflowId();
        aggregateHarnessService.runAggregateHarness(workflowId, stageRun.getId());
        levelConfirmationService.confirmLevels(workflowId, stageRun.getId());
        projectionService.projectConfirmed(workflowId, null);
        // 同步将本阶段运行置为 SUCCEEDED：startNextStage 的前置校验（同事务读库）
        // 早于协调器异步处理 TASK_SUCCEEDED，否则读到 RUNNING 抛"前置阶段未完成"，
        // 被 runStage catch 判为不可重试 → TASK_FAILED_FINAL → 工作流 FAILED。
        // 注意：本方法由 runStage 执行，DB 状态可能已被 claimStageRun/协调器 TASK_CLAIMED
        // 置为 RUNNING（内存中的 stageRun 仍是 PENDING），CAS 必须使用 DB 最新状态。
        PersonCapabilityStageRun latest = stageRunMapper.selectById(stageRun.getId());
        if (latest == null) {
            log.warn("聚合审核阶段运行不存在，跳过推进: stageRunId={}", stageRun.getId());
            return;
        }
        boolean marked = workflowService.casStageRunStatus(stageRun.getId(), latest.getStatus(),
                com.example.matching.common.enums.StageRunStatusEnum.SUCCEEDED.getCode(), null, null);
        if (!marked) {
            log.warn("聚合审核阶段运行状态非预期，跳过推进: workflowId={}, stageRunId={}, status={}",
                    workflowId, stageRun.getId(), latest.getStatus());
            return;
        }
        // 发布 Harness 成功事件，协调器推进 AGGREGATE_HARNESS_RUNNING -> LEVEL_CONFIRMING
        publishLifecycle(stageRun, com.example.matching.common.enums.StageLifecycleEventType.TASK_SUCCEEDED, null, null);
        // 回填评估报告聚合审核结论（失败不阻断流程推进）
        try {
            assessmentReportService.refreshAggregateConclusion(workflowId);
        } catch (Exception e) {
            log.warn("回填报告聚合审核结论失败: workflowId={}, error={}", workflowId, e.getMessage());
        }
        // 创建确认阶段（PENDING），任务抢占后由消费者发布 TASK_CLAIMED
    }

    /** Historical rows are acknowledged only; all new confirmation work runs in the merged stage. */
    private void acknowledgeLegacyLevelStage(PersonCapabilityStageRun stageRun) {
        publishLifecycle(stageRun, com.example.matching.common.enums.StageLifecycleEventType.TASK_SUCCEEDED, null, null);
    }

}
