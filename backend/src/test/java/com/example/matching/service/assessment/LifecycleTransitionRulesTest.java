package com.example.matching.service.assessment;

import com.example.matching.common.enums.StageLifecycleEventType;
import com.example.matching.common.enums.StageRunStatusEnum;
import com.example.matching.common.enums.WorkflowStatusEnum;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 生命周期状态转换表单元测试
 * <p>
 * 覆盖方案验收标准：
 * 9. 所有阶段的状态转换表有单元测试；
 * 6. 乱序消息不会让状态倒退。
 */
class LifecycleTransitionRulesTest {

    // ==================== 简历解析 ====================

    @Test
    void resumeParseClaimed_requiresWorkflow_advancesToParsing() {
        LifecycleTransitionRules.WorkflowTransition t = LifecycleTransitionRules.findWorkflowTransition(
                "RESUME_PARSE", StageLifecycleEventType.TASK_CLAIMED,
                WorkflowStatusEnum.RESUME_REQUIRED.getCode());
        assertNotNull(t);
        assertEquals(WorkflowStatusEnum.RESUME_PARSING.getCode(), t.toWorkflowStatus());
        assertEquals("RESUME_PARSE", t.currentStage());
    }

    @Test
    void resumeParseSucceeded_advancesToEvidenceReady() {
        LifecycleTransitionRules.WorkflowTransition t = LifecycleTransitionRules.findWorkflowTransition(
                "RESUME_PARSE", StageLifecycleEventType.TASK_SUCCEEDED,
                WorkflowStatusEnum.RESUME_PARSING.getCode());
        assertNotNull(t);
        assertEquals(WorkflowStatusEnum.RESUME_EVIDENCE_READY.getCode(), t.toWorkflowStatus());
    }

    @Test
    void resumeClaimExtractionSucceeded_advancesToEvidenceReady() {
        LifecycleTransitionRules.WorkflowTransition t = LifecycleTransitionRules.findWorkflowTransition(
                "RESUME_CLAIM_EXTRACTION", StageLifecycleEventType.TASK_SUCCEEDED,
                WorkflowStatusEnum.RESUME_PARSING.getCode());
        assertNotNull(t);
        assertEquals(WorkflowStatusEnum.RESUME_EVIDENCE_READY.getCode(), t.toWorkflowStatus());
    }

    // ==================== 测试题目生成 ====================

    @Test
    void testGenerationClaimed_advancesToGenerating() {
        LifecycleTransitionRules.WorkflowTransition t = LifecycleTransitionRules.findWorkflowTransition(
                "AI_TEST_GENERATION", StageLifecycleEventType.TASK_CLAIMED,
                WorkflowStatusEnum.RESUME_EVIDENCE_READY.getCode());
        assertNotNull(t);
        assertEquals(WorkflowStatusEnum.TEST_GENERATING.getCode(), t.toWorkflowStatus());
    }

    @Test
    void testGenerationSucceeded_advancesToInProgress() {
        LifecycleTransitionRules.WorkflowTransition t = LifecycleTransitionRules.findWorkflowTransition(
                "AI_TEST_GENERATION", StageLifecycleEventType.TASK_SUCCEEDED,
                WorkflowStatusEnum.TEST_GENERATING.getCode());
        assertNotNull(t);
        assertEquals(WorkflowStatusEnum.TEST_IN_PROGRESS.getCode(), t.toWorkflowStatus());
    }

    // ==================== 测试答题与评分 ====================

    @Test
    void testEvaluationUserActionStarted_advancesToEvaluating() {
        LifecycleTransitionRules.WorkflowTransition t = LifecycleTransitionRules.findWorkflowTransition(
                "AI_TEST_EVALUATION", StageLifecycleEventType.USER_ACTION_STARTED,
                WorkflowStatusEnum.TEST_IN_PROGRESS.getCode());
        assertNotNull(t);
        assertEquals(WorkflowStatusEnum.TEST_EVALUATING.getCode(), t.toWorkflowStatus());
    }

    @Test
    void testEvaluationSucceeded_advancesToEvidenceReady() {
        LifecycleTransitionRules.WorkflowTransition t = LifecycleTransitionRules.findWorkflowTransition(
                "AI_TEST_EVALUATION", StageLifecycleEventType.TASK_SUCCEEDED,
                WorkflowStatusEnum.TEST_EVALUATING.getCode());
        assertNotNull(t);
        assertEquals(WorkflowStatusEnum.TEST_EVIDENCE_READY.getCode(), t.toWorkflowStatus());
    }

    // ==================== AI 面试 ====================

    @Test
    void interviewClaimed_advancesToPreparing() {
        LifecycleTransitionRules.WorkflowTransition t = LifecycleTransitionRules.findWorkflowTransition(
                "AI_INTERVIEW", StageLifecycleEventType.TASK_CLAIMED,
                WorkflowStatusEnum.TEST_EVIDENCE_READY.getCode());
        assertNotNull(t);
        assertEquals(WorkflowStatusEnum.INTERVIEW_PREPARING.getCode(), t.toWorkflowStatus());
    }

    @Test
    void interviewReadyForUser_advancesToInProgress() {
        // 关键修复：创建会话不推进到面试进行中；只有会话和首题真正准备完成才推进
        LifecycleTransitionRules.WorkflowTransition t = LifecycleTransitionRules.findWorkflowTransition(
                "AI_INTERVIEW", StageLifecycleEventType.TASK_READY_FOR_USER,
                WorkflowStatusEnum.INTERVIEW_PREPARING.getCode());
        assertNotNull(t);
        assertEquals(WorkflowStatusEnum.INTERVIEW_IN_PROGRESS.getCode(), t.toWorkflowStatus());
    }

    @Test
    void interviewReadyForUser_doesNotAdvanceFromInProgress() {
        // 乱序/重复：已进入进行中后，TASK_READY_FOR_USER 无规则（不倒退）
        LifecycleTransitionRules.WorkflowTransition t = LifecycleTransitionRules.findWorkflowTransition(
                "AI_INTERVIEW", StageLifecycleEventType.TASK_READY_FOR_USER,
                WorkflowStatusEnum.INTERVIEW_IN_PROGRESS.getCode());
        assertNull(t);
    }

    @Test
    void interviewUserActionCompleted_advancesToAnalyzing() {
        LifecycleTransitionRules.WorkflowTransition t = LifecycleTransitionRules.findWorkflowTransition(
                "AI_INTERVIEW", StageLifecycleEventType.USER_ACTION_COMPLETED,
                WorkflowStatusEnum.INTERVIEW_IN_PROGRESS.getCode());
        assertNotNull(t);
        assertEquals(WorkflowStatusEnum.INTERVIEW_ANALYZING.getCode(), t.toWorkflowStatus());
    }

    @Test
    void interviewSucceeded_advancesToHarnessRunning() {
        LifecycleTransitionRules.WorkflowTransition t = LifecycleTransitionRules.findWorkflowTransition(
                "AI_INTERVIEW", StageLifecycleEventType.TASK_SUCCEEDED,
                WorkflowStatusEnum.INTERVIEW_ANALYZING.getCode());
        assertNotNull(t);
        assertEquals(WorkflowStatusEnum.AGGREGATE_HARNESS_RUNNING.getCode(), t.toWorkflowStatus());
    }

    // ==================== 聚合 Harness ====================

    @Test
    void harnessClaimed_advancesToHarnessRunning() {
        LifecycleTransitionRules.WorkflowTransition t = LifecycleTransitionRules.findWorkflowTransition(
                "AGGREGATE_HARNESS", StageLifecycleEventType.TASK_CLAIMED,
                WorkflowStatusEnum.INTERVIEW_ANALYZING.getCode());
        assertNotNull(t);
        assertEquals(WorkflowStatusEnum.AGGREGATE_HARNESS_RUNNING.getCode(), t.toWorkflowStatus());
    }

    @Test
    void harnessSucceeded_advancesToCompleted() {
        LifecycleTransitionRules.WorkflowTransition t = LifecycleTransitionRules.findWorkflowTransition(
                "AGGREGATE_HARNESS", StageLifecycleEventType.TASK_SUCCEEDED,
                WorkflowStatusEnum.AGGREGATE_HARNESS_RUNNING.getCode());
        assertNotNull(t);
        assertEquals(WorkflowStatusEnum.COMPLETED.getCode(), t.toWorkflowStatus());
    }

    @Test
    void harnessReviewEvents_doNotOwnAssessmentWorkflowState() {
        LifecycleTransitionRules.WorkflowTransition t = LifecycleTransitionRules.findWorkflowTransition(
                "AGGREGATE_HARNESS", StageLifecycleEventType.USER_ACTION_COMPLETED,
                WorkflowStatusEnum.REVIEW_REQUIRED.getCode());
        assertNull(t);
    }

    // ==================== 等级确认 ====================

    @Test
    void levelConfirmationSucceeded_advancesToCompleted() {
        // 待审核项由治理门户处理，不再影响评估工作流完成。
        LifecycleTransitionRules.WorkflowTransition t = LifecycleTransitionRules.findWorkflowTransition(
                "LEVEL_CONFIRMATION", StageLifecycleEventType.TASK_SUCCEEDED,
                WorkflowStatusEnum.LEVEL_CONFIRMING.getCode());
        assertNotNull(t);
        assertEquals(WorkflowStatusEnum.COMPLETED.getCode(), t.toWorkflowStatus());
    }

    @Test
    void levelConfirmationReviewEvents_doNotOwnAssessmentWorkflowState() {
        LifecycleTransitionRules.WorkflowTransition t = LifecycleTransitionRules.findWorkflowTransition(
                "LEVEL_CONFIRMATION", StageLifecycleEventType.USER_ACTION_COMPLETED,
                WorkflowStatusEnum.REVIEW_REQUIRED.getCode());
        assertNull(t);
    }

    @Test
    void levelConfirmationReadyForUser_doesNotMoveWorkflowToReviewRequired() {
        LifecycleTransitionRules.WorkflowTransition t = LifecycleTransitionRules.findWorkflowTransition(
                "LEVEL_CONFIRMATION", StageLifecycleEventType.TASK_READY_FOR_USER,
                WorkflowStatusEnum.LEVEL_CONFIRMING.getCode());
        assertNull(t);
    }

    @Test
    void recoveryRequiredTestGeneration_restartsOnlyTheFailedStage() {
        LifecycleTransitionRules.WorkflowTransition t = LifecycleTransitionRules.findWorkflowTransition(
                "AI_TEST_GENERATION", StageLifecycleEventType.USER_ACTION_STARTED,
                WorkflowStatusEnum.RECOVERY_REQUIRED.getCode());
        assertNotNull(t);
        assertEquals(WorkflowStatusEnum.TEST_GENERATING.getCode(), t.toWorkflowStatus());
        assertEquals("AI_TEST_GENERATION", t.currentStage());
    }

    // ==================== 非法/倒退跳转 ====================

    @Test
    void illegalJump_acrossStages_returnsNull() {
        // 跨阶段：面试成功事件不能从测试生成状态直接跳
        assertNull(LifecycleTransitionRules.findWorkflowTransition(
                "AI_INTERVIEW", StageLifecycleEventType.TASK_SUCCEEDED,
                WorkflowStatusEnum.TEST_GENERATING.getCode()));
        // 倒退：面试分析中不能收到面试进行中的事件推进
        assertNull(LifecycleTransitionRules.findWorkflowTransition(
                "AI_INTERVIEW", StageLifecycleEventType.TASK_READY_FOR_USER,
                WorkflowStatusEnum.INTERVIEW_ANALYZING.getCode()));
        // 未知阶段
        assertNull(LifecycleTransitionRules.findWorkflowTransition(
                "UNKNOWN_STAGE", StageLifecycleEventType.TASK_SUCCEEDED,
                WorkflowStatusEnum.TEST_GENERATING.getCode()));
    }

    @Test
    void finalFailure_isWorkflowFinalFailure() {
        assertTrue(LifecycleTransitionRules.isWorkflowFinalFailure(StageLifecycleEventType.TASK_FAILED_FINAL));
    }

    // ==================== 阶段运行状态转换 ====================

    @Test
    void stageRunClaimed_transitionsPendingToRunning() {
        LifecycleTransitionRules.StageRunTransition t =
                LifecycleTransitionRules.findStageRunTransition(StageLifecycleEventType.TASK_CLAIMED);
        assertNotNull(t);
        assertTrue(t.allowedFrom().contains(StageRunStatusEnum.PENDING));
        assertEquals(StageRunStatusEnum.RUNNING, t.to());
    }

    @Test
    void stageRunReadyForUser_transitionsRunningToWaitingUser() {
        LifecycleTransitionRules.StageRunTransition t =
                LifecycleTransitionRules.findStageRunTransition(StageLifecycleEventType.TASK_READY_FOR_USER);
        assertNotNull(t);
        assertTrue(t.allowedFrom().contains(StageRunStatusEnum.RUNNING));
        assertTrue(t.allowedFrom().contains(StageRunStatusEnum.PENDING));
        assertEquals(StageRunStatusEnum.WAITING_USER, t.to());
    }

    @Test
    void stageRunSucceeded_allowsActiveStatesOnly() {
        LifecycleTransitionRules.StageRunTransition t =
                LifecycleTransitionRules.findStageRunTransition(StageLifecycleEventType.TASK_SUCCEEDED);
        assertNotNull(t);
        Set<StageRunStatusEnum> allowed = t.allowedFrom();
        assertTrue(allowed.containsAll(Set.of(
                StageRunStatusEnum.PENDING, StageRunStatusEnum.RUNNING,
                StageRunStatusEnum.WAITING_USER, StageRunStatusEnum.FAILED_RETRYABLE)));
        // 已成功/最终失败不能再次成功（不倒退、不重复推进）
        assertTrue(!allowed.contains(StageRunStatusEnum.SUCCEEDED));
        assertTrue(!allowed.contains(StageRunStatusEnum.FAILED_FINAL));
        assertEquals(StageRunStatusEnum.SUCCEEDED, t.to());
    }

    @Test
    void stageRunFailedRetryable_allowsActiveStates() {
        LifecycleTransitionRules.StageRunTransition t =
                LifecycleTransitionRules.findStageRunTransition(StageLifecycleEventType.TASK_FAILED_RETRYABLE);
        assertNotNull(t);
        assertTrue(t.allowedFrom().contains(StageRunStatusEnum.RUNNING));
        assertEquals(StageRunStatusEnum.FAILED_RETRYABLE, t.to());
    }

    @Test
    void stageRunFailedFinal_allowsActiveAndRetryableStates() {
        LifecycleTransitionRules.StageRunTransition t =
                LifecycleTransitionRules.findStageRunTransition(StageLifecycleEventType.TASK_FAILED_FINAL);
        assertNotNull(t);
        assertTrue(t.allowedFrom().contains(StageRunStatusEnum.FAILED_RETRYABLE));
        assertEquals(StageRunStatusEnum.FAILED_FINAL, t.to());
    }
}
