package com.example.matching.service.assessment;

import com.example.matching.common.enums.StageLifecycleEventType;
import com.example.matching.common.enums.StageRunStatusEnum;
import com.example.matching.common.enums.WorkflowStatusEnum;

import java.util.Map;
import java.util.Set;

/**
 * 能力评估生命周期状态转换规则（纯表驱动，可独立单元测试）
 * <p>
 * 规则只定义"事件 + 当前状态 -> 目标状态"，不允许跳转、倒退或跨阶段；
 * 由 {@link CapabilityAssessmentLifecycleCoordinator} 在执行时配合 CAS 使用。
 *
 * @author system
 */
public final class LifecycleTransitionRules {

    /** 工作流状态转换键：(stageType, eventType, 当前工作流状态) */
    public record WorkflowRuleKey(String stageType, StageLifecycleEventType eventType, String fromWorkflowStatus) {
    }

    /** 工作流转换结果 */
    public record WorkflowTransition(String toWorkflowStatus, String currentStage) {
    }

    /** 阶段运行转换结果 */
    public record StageRunTransition(Set<StageRunStatusEnum> allowedFrom, StageRunStatusEnum to) {
    }

    private LifecycleTransitionRules() {
    }

    /**
     * 工作流状态转换表。
     * <p>
     * 每个条目表示：当阶段 stageType 收到 eventType 事件、且工作流当前状态为 fromWorkflowStatus 时，
     * 允许推进到 toWorkflowStatus 并设置 currentStage。
     * 不允许的跳转/倒退/跨阶段在表中无条目，协调器据此拒绝。
     */
    private static final Map<WorkflowRuleKey, WorkflowTransition> WORKFLOW_TRANSITIONS = Map.ofEntries(
            // ==================== 简历解析 ====================
            // 解析任务被消费者抢占 -> 简历解析中
            entry("RESUME_PARSE", StageLifecycleEventType.TASK_CLAIMED, WorkflowStatusEnum.RESUME_REQUIRED.getCode(),
                    WorkflowStatusEnum.RESUME_PARSING.getCode(), "RESUME_PARSE"),
            entry("RESUME_PARSE", StageLifecycleEventType.TASK_CLAIMED, WorkflowStatusEnum.RESUME_PARSING.getCode(),
                    WorkflowStatusEnum.RESUME_PARSING.getCode(), "RESUME_PARSE"),
            // 简历解析完成/证据保存 -> 证据就绪（有证据）或 无证据（解析成功但无可信证据）
            entry("RESUME_PARSE", StageLifecycleEventType.TASK_SUCCEEDED, WorkflowStatusEnum.RESUME_PARSING.getCode(),
                    WorkflowStatusEnum.RESUME_EVIDENCE_READY.getCode(), "RESUME_CLAIM_EXTRACTION"),
            entry("RESUME_CLAIM_EXTRACTION", StageLifecycleEventType.TASK_SUCCEEDED, WorkflowStatusEnum.RESUME_PARSING.getCode(),
                    WorkflowStatusEnum.RESUME_EVIDENCE_READY.getCode(), "RESUME_CLAIM_EXTRACTION"),
            entry("RESUME_CLAIM_EXTRACTION", StageLifecycleEventType.TASK_SUCCEEDED, WorkflowStatusEnum.RESUME_EVIDENCE_READY.getCode(),
                    WorkflowStatusEnum.RESUME_EVIDENCE_READY.getCode(), "RESUME_CLAIM_EXTRACTION"),
            // 无证据状态下人工补充/确认证据成功 -> 证据就绪
            entry("RESUME_CLAIM_EXTRACTION", StageLifecycleEventType.TASK_SUCCEEDED, WorkflowStatusEnum.RESUME_PARSED_NO_EVIDENCE.getCode(),
                    WorkflowStatusEnum.RESUME_EVIDENCE_READY.getCode(), "RESUME_CLAIM_EXTRACTION"),
            // 证据提取无可用证据 -> 无证据状态（解析成功但无可信地证据）
            entry("RESUME_CLAIM_EXTRACTION", StageLifecycleEventType.NO_EVIDENCE, WorkflowStatusEnum.RESUME_EVIDENCE_READY.getCode(),
                    WorkflowStatusEnum.RESUME_PARSED_NO_EVIDENCE.getCode(), "RESUME_CLAIM_EXTRACTION"),
            entry("RESUME_CLAIM_EXTRACTION", StageLifecycleEventType.NO_EVIDENCE, WorkflowStatusEnum.RESUME_PARSED_NO_EVIDENCE.getCode(),
                    WorkflowStatusEnum.RESUME_PARSED_NO_EVIDENCE.getCode(), "RESUME_CLAIM_EXTRACTION"),

            // ==================== 测试题目生成 ====================
            // 题目生成任务抢占 -> 测试生成中（有证据或无证据均可生成测试）
            entry("AI_TEST_GENERATION", StageLifecycleEventType.TASK_CLAIMED, WorkflowStatusEnum.RESUME_EVIDENCE_READY.getCode(),
                    WorkflowStatusEnum.TEST_GENERATING.getCode(), "AI_TEST_GENERATION"),
            entry("AI_TEST_GENERATION", StageLifecycleEventType.TASK_CLAIMED, WorkflowStatusEnum.RESUME_PARSED_NO_EVIDENCE.getCode(),
                    WorkflowStatusEnum.TEST_GENERATING.getCode(), "AI_TEST_GENERATION"),
            entry("AI_TEST_GENERATION", StageLifecycleEventType.TASK_CLAIMED, WorkflowStatusEnum.TEST_GENERATING.getCode(),
                    WorkflowStatusEnum.TEST_GENERATING.getCode(), "AI_TEST_GENERATION"),
            // 题目生成完成 -> 测试进行中（等待答题）
            entry("AI_TEST_GENERATION", StageLifecycleEventType.TASK_SUCCEEDED, WorkflowStatusEnum.TEST_GENERATING.getCode(),
                    WorkflowStatusEnum.TEST_IN_PROGRESS.getCode(), "AI_TEST_GENERATION"),
            // 题目生成失败（可重试）不推进工作流，仍保持 TEST_GENERATING

            // ==================== 测试答题与评分 ====================
            // 用户提交答案 -> 测试评分中
            entry("AI_TEST_EVALUATION", StageLifecycleEventType.USER_ACTION_STARTED, WorkflowStatusEnum.TEST_IN_PROGRESS.getCode(),
                    WorkflowStatusEnum.TEST_EVALUATING.getCode(), "AI_TEST_EVALUATION"),
            // 评分任务抢占 -> 保持评分中
            entry("AI_TEST_EVALUATION", StageLifecycleEventType.TASK_CLAIMED, WorkflowStatusEnum.TEST_EVALUATING.getCode(),
                    WorkflowStatusEnum.TEST_EVALUATING.getCode(), "AI_TEST_EVALUATION"),
            // 评分完成 -> 测试证据就绪
            entry("AI_TEST_EVALUATION", StageLifecycleEventType.TASK_SUCCEEDED, WorkflowStatusEnum.TEST_EVALUATING.getCode(),
                    WorkflowStatusEnum.TEST_EVIDENCE_READY.getCode(), "AI_TEST_EVALUATION"),

            // ==================== AI 面试 ====================
            // 创建会话/准备任务开始 -> 面试准备中
            entry("AI_INTERVIEW", StageLifecycleEventType.TASK_CLAIMED, WorkflowStatusEnum.TEST_EVIDENCE_READY.getCode(),
                    WorkflowStatusEnum.INTERVIEW_PREPARING.getCode(), "AI_INTERVIEW"),
            entry("AI_INTERVIEW", StageLifecycleEventType.TASK_CLAIMED, WorkflowStatusEnum.INTERVIEW_PREPARING.getCode(),
                    WorkflowStatusEnum.INTERVIEW_PREPARING.getCode(), "AI_INTERVIEW"),
            // 会话初始化、首题生成完成 -> 面试进行中（等待候选人开始）
            entry("AI_INTERVIEW", StageLifecycleEventType.TASK_READY_FOR_USER, WorkflowStatusEnum.INTERVIEW_PREPARING.getCode(),
                    WorkflowStatusEnum.INTERVIEW_IN_PROGRESS.getCode(), "AI_INTERVIEW"),
            // 候选人完成面试 -> 面试分析中
            entry("AI_INTERVIEW", StageLifecycleEventType.USER_ACTION_COMPLETED, WorkflowStatusEnum.INTERVIEW_IN_PROGRESS.getCode(),
                    WorkflowStatusEnum.INTERVIEW_ANALYZING.getCode(), "AI_INTERVIEW"),
            // 面试分析任务抢占 -> 保持分析中
            entry("AI_INTERVIEW", StageLifecycleEventType.TASK_CLAIMED, WorkflowStatusEnum.INTERVIEW_ANALYZING.getCode(),
                    WorkflowStatusEnum.INTERVIEW_ANALYZING.getCode(), "AI_INTERVIEW"),
            // 面试分析与证据保存成功 -> 聚合 Harness 运行中
            entry("AI_INTERVIEW", StageLifecycleEventType.TASK_SUCCEEDED, WorkflowStatusEnum.INTERVIEW_ANALYZING.getCode(),
                    WorkflowStatusEnum.AGGREGATE_HARNESS_RUNNING.getCode(), "AGGREGATE_HARNESS"),

            // ==================== 聚合 Harness ====================
            // Harness 任务抢占 -> 聚合审核中
            entry("AGGREGATE_HARNESS", StageLifecycleEventType.TASK_CLAIMED, WorkflowStatusEnum.INTERVIEW_ANALYZING.getCode(),
                    WorkflowStatusEnum.AGGREGATE_HARNESS_RUNNING.getCode(), "AGGREGATE_HARNESS"),
            entry("AGGREGATE_HARNESS", StageLifecycleEventType.TASK_CLAIMED, WorkflowStatusEnum.AGGREGATE_HARNESS_RUNNING.getCode(),
                    WorkflowStatusEnum.AGGREGATE_HARNESS_RUNNING.getCode(), "AGGREGATE_HARNESS"),
            // Harness 成功 -> 等级确认中
            entry("AGGREGATE_HARNESS", StageLifecycleEventType.TASK_SUCCEEDED, WorkflowStatusEnum.AGGREGATE_HARNESS_RUNNING.getCode(),
                    WorkflowStatusEnum.COMPLETED.getCode(), "AGGREGATE_HARNESS"),
            // ==================== 等级确认 ====================
            // 确认任务抢占 -> 等级确认中
            entry("LEVEL_CONFIRMATION", StageLifecycleEventType.TASK_CLAIMED, WorkflowStatusEnum.AGGREGATE_HARNESS_RUNNING.getCode(),
                    WorkflowStatusEnum.LEVEL_CONFIRMING.getCode(), "LEVEL_CONFIRMATION"),
            entry("LEVEL_CONFIRMATION", StageLifecycleEventType.TASK_CLAIMED, WorkflowStatusEnum.LEVEL_CONFIRMING.getCode(),
                    WorkflowStatusEnum.LEVEL_CONFIRMING.getCode(), "LEVEL_CONFIRMATION"),
            // 等级确认完成即结束评估；待人工 Harness 审核由独立治理门户处理。
            entry("LEVEL_CONFIRMATION", StageLifecycleEventType.TASK_SUCCEEDED, WorkflowStatusEnum.LEVEL_CONFIRMING.getCode(),
                    WorkflowStatusEnum.COMPLETED.getCode(), "LEVEL_CONFIRMATION"),

            // ==================== 失败恢复（重试失败阶段） ====================
            entry("RESUME_PARSE", StageLifecycleEventType.USER_ACTION_STARTED, WorkflowStatusEnum.FAILED.getCode(),
                    WorkflowStatusEnum.RESUME_PARSING.getCode(), "RESUME_PARSE"),
            entry("RESUME_PARSE", StageLifecycleEventType.USER_ACTION_STARTED, WorkflowStatusEnum.RECOVERY_REQUIRED.getCode(),
                    WorkflowStatusEnum.RESUME_PARSING.getCode(), "RESUME_PARSE"),
            entry("AI_TEST_GENERATION", StageLifecycleEventType.USER_ACTION_STARTED, WorkflowStatusEnum.FAILED.getCode(),
                    WorkflowStatusEnum.TEST_GENERATING.getCode(), "AI_TEST_GENERATION"),
            entry("AI_TEST_GENERATION", StageLifecycleEventType.USER_ACTION_STARTED, WorkflowStatusEnum.RECOVERY_REQUIRED.getCode(),
                    WorkflowStatusEnum.TEST_GENERATING.getCode(), "AI_TEST_GENERATION"),
            entry("AI_TEST_EVALUATION", StageLifecycleEventType.USER_ACTION_STARTED, WorkflowStatusEnum.FAILED.getCode(),
                    WorkflowStatusEnum.TEST_EVALUATING.getCode(), "AI_TEST_EVALUATION"),
            entry("AI_TEST_EVALUATION", StageLifecycleEventType.USER_ACTION_STARTED, WorkflowStatusEnum.RECOVERY_REQUIRED.getCode(),
                    WorkflowStatusEnum.TEST_EVALUATING.getCode(), "AI_TEST_EVALUATION"),
            entry("AI_INTERVIEW", StageLifecycleEventType.USER_ACTION_STARTED, WorkflowStatusEnum.FAILED.getCode(),
                    WorkflowStatusEnum.INTERVIEW_PREPARING.getCode(), "AI_INTERVIEW"),
            entry("AI_INTERVIEW", StageLifecycleEventType.USER_ACTION_STARTED, WorkflowStatusEnum.RECOVERY_REQUIRED.getCode(),
                    WorkflowStatusEnum.INTERVIEW_PREPARING.getCode(), "AI_INTERVIEW"),
            entry("AGGREGATE_HARNESS", StageLifecycleEventType.USER_ACTION_STARTED, WorkflowStatusEnum.FAILED.getCode(),
                    WorkflowStatusEnum.AGGREGATE_HARNESS_RUNNING.getCode(), "AGGREGATE_HARNESS"),
            entry("AGGREGATE_HARNESS", StageLifecycleEventType.USER_ACTION_STARTED, WorkflowStatusEnum.RECOVERY_REQUIRED.getCode(),
                    WorkflowStatusEnum.AGGREGATE_HARNESS_RUNNING.getCode(), "AGGREGATE_HARNESS"),
            entry("LEVEL_CONFIRMATION", StageLifecycleEventType.USER_ACTION_STARTED, WorkflowStatusEnum.FAILED.getCode(),
                    WorkflowStatusEnum.LEVEL_CONFIRMING.getCode(), "LEVEL_CONFIRMATION"),
            entry("LEVEL_CONFIRMATION", StageLifecycleEventType.USER_ACTION_STARTED, WorkflowStatusEnum.RECOVERY_REQUIRED.getCode(),
                    WorkflowStatusEnum.LEVEL_CONFIRMING.getCode(), "LEVEL_CONFIRMATION")
    );

    /**
     * 阶段运行状态转换表：事件类型 -> 允许的前置状态集合与目标状态。
     * 状态只前进，不允许回退；与 CAS 配合保证乱序消息不倒退。
     */
    private static final Map<StageLifecycleEventType, StageRunTransition> STAGE_RUN_TRANSITIONS = Map.of(
            StageLifecycleEventType.TASK_CLAIMED,
            new StageRunTransition(Set.of(StageRunStatusEnum.PENDING), StageRunStatusEnum.RUNNING),
            StageLifecycleEventType.TASK_READY_FOR_USER,
            new StageRunTransition(Set.of(StageRunStatusEnum.PENDING, StageRunStatusEnum.RUNNING), StageRunStatusEnum.WAITING_USER),
            StageLifecycleEventType.USER_ACTION_STARTED,
            new StageRunTransition(Set.of(StageRunStatusEnum.WAITING_USER, StageRunStatusEnum.PENDING), StageRunStatusEnum.RUNNING),
            StageLifecycleEventType.USER_ACTION_COMPLETED,
            new StageRunTransition(Set.of(StageRunStatusEnum.WAITING_USER, StageRunStatusEnum.RUNNING, StageRunStatusEnum.PENDING), StageRunStatusEnum.RUNNING),
            StageLifecycleEventType.TASK_SUCCEEDED,
            new StageRunTransition(Set.of(StageRunStatusEnum.PENDING, StageRunStatusEnum.RUNNING,
                    StageRunStatusEnum.WAITING_USER, StageRunStatusEnum.FAILED_RETRYABLE), StageRunStatusEnum.SUCCEEDED),
            StageLifecycleEventType.TASK_FAILED_RETRYABLE,
            new StageRunTransition(Set.of(StageRunStatusEnum.PENDING, StageRunStatusEnum.RUNNING,
                    StageRunStatusEnum.WAITING_USER), StageRunStatusEnum.FAILED_RETRYABLE),
            StageLifecycleEventType.TASK_FAILED_FINAL,
            new StageRunTransition(Set.of(StageRunStatusEnum.PENDING, StageRunStatusEnum.RUNNING,
                    StageRunStatusEnum.WAITING_USER, StageRunStatusEnum.FAILED_RETRYABLE), StageRunStatusEnum.FAILED_FINAL),
            // NO_EVIDENCE 阶段运行同样转入 SUCCESSED（任务过程成功执行，只是结果无证据）
            StageLifecycleEventType.NO_EVIDENCE,
            new StageRunTransition(Set.of(StageRunStatusEnum.PENDING, StageRunStatusEnum.RUNNING,
                    StageRunStatusEnum.WAITING_USER, StageRunStatusEnum.FAILED_RETRYABLE), StageRunStatusEnum.SUCCEEDED)
    );

    /**
     * 查询工作流转换规则，无匹配返回 null（非法跳转/倒退/跨阶段）。
     */
    public static WorkflowTransition findWorkflowTransition(String stageType, StageLifecycleEventType eventType,
                                                            String currentWorkflowStatus) {
        if ("AGGREGATE_HARNESS_AND_LEVEL_CONFIRMATION".equals(stageType)) {
            stageType = "AGGREGATE_HARNESS";
        }
        return WORKFLOW_TRANSITIONS.get(new WorkflowRuleKey(stageType, eventType, currentWorkflowStatus));
    }

    /**
     * 查询阶段运行转换规则，无匹配返回 null。
     */
    public static StageRunTransition findStageRunTransition(StageLifecycleEventType eventType) {
        return STAGE_RUN_TRANSITIONS.get(eventType);
    }

    /**
     * 工作流最终失败：任何活跃状态收到 TASK_FAILED_FINAL 都进入 FAILED。
     */
    public static boolean isWorkflowFinalFailure(StageLifecycleEventType eventType) {
        return eventType == StageLifecycleEventType.TASK_FAILED_FINAL;
    }

    private static Map.Entry<WorkflowRuleKey, WorkflowTransition> entry(
            String stageType, StageLifecycleEventType eventType, String from, String to, String currentStage) {
        return Map.entry(new WorkflowRuleKey(stageType, eventType, from), new WorkflowTransition(to, currentStage));
    }
}
