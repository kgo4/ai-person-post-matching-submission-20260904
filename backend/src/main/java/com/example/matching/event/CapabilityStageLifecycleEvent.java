package com.example.matching.event;

import com.example.matching.common.enums.StageLifecycleEventType;

import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * 能力评估阶段生命周期统一事件
 * <p>
 * 业务服务（简历/测试/面试/Harness/等级确认）不再直接推进工作流状态，
 * 而是发布本事件描述"发生了什么"；由 CapabilityAssessmentLifecycleCoordinator
 * 消费并依据状态转换表统一更新阶段运行与工作流状态。
 * <p>
 * 事件必须经 Outbox 持久化后投递 RabbitMQ（可靠链路）；进程内事件仅作加速。
 *
 * @param workflowId     工作流ID
 * @param stageRunId     阶段运行ID（可为空，协调器按 stageType+sourceRef 解析）
 * @param stageType      阶段类型（StageTypeEnum code）
 * @param sourceRefType  来源引用类型（如 AI_TEST / AI_INTERVIEW / RESUME_PARSE）
 * @param sourceRefId    来源引用ID（如 emp_ai_test.id / 会话ID / 解析记录ID）
 * @param eventType      生命周期事件类型
 * @param errorCode      失败代码（失败类事件）
 * @param errorMessage   失败信息（失败类事件）
 * @param occurredAt     发生时间
 * @param eventId        事件唯一ID（幂等去重键）
 * @author system
 */
public record CapabilityStageLifecycleEvent(
        Long workflowId,
        Long stageRunId,
        String stageType,
        String sourceRefType,
        Long sourceRefId,
        StageLifecycleEventType eventType,
        String errorCode,
        String errorMessage,
        LocalDateTime occurredAt,
        String eventId) implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 快捷创建事件：自动填充 occurredAt 与 eventId。
     */
    public static CapabilityStageLifecycleEvent of(
            Long workflowId, Long stageRunId, String stageType,
            String sourceRefType, Long sourceRefId, StageLifecycleEventType eventType,
            String errorCode, String errorMessage) {
        return new CapabilityStageLifecycleEvent(
                workflowId, stageRunId, stageType, sourceRefType, sourceRefId,
                eventType, errorCode, errorMessage, LocalDateTime.now(), UUID.randomUUID().toString());
    }

    /**
     * 任务成功事件。
     */
    public static CapabilityStageLifecycleEvent succeeded(
            Long workflowId, Long stageRunId, String stageType,
            String sourceRefType, Long sourceRefId) {
        return of(workflowId, stageRunId, stageType, sourceRefType, sourceRefId,
                StageLifecycleEventType.TASK_SUCCEEDED, null, null);
    }

    /**
     * 最终失败事件。
     */
    public static CapabilityStageLifecycleEvent failedFinal(
            Long workflowId, Long stageRunId, String stageType,
            String sourceRefType, Long sourceRefId, String errorCode, String errorMessage) {
        return of(workflowId, stageRunId, stageType, sourceRefType, sourceRefId,
                StageLifecycleEventType.TASK_FAILED_FINAL, errorCode, errorMessage);
    }

    /**
     * 可重试失败事件。
     */
    public static CapabilityStageLifecycleEvent failedRetryable(
            Long workflowId, Long stageRunId, String stageType,
            String sourceRefType, Long sourceRefId, String errorCode, String errorMessage) {
        return of(workflowId, stageRunId, stageType, sourceRefType, sourceRefId,
                StageLifecycleEventType.TASK_FAILED_RETRYABLE, errorCode, errorMessage);
    }

    /**
     * 无证据事件：证据提取完成但无可用证据（阶段运行仍标记 SUCCEEDED，工作流进入 RESUME_PARSED_NO_EVIDENCE）。
     */
    public static CapabilityStageLifecycleEvent noEvidence(
            Long workflowId, Long stageRunId, String stageType,
            String sourceRefType, Long sourceRefId) {
        return of(workflowId, stageRunId, stageType, sourceRefType, sourceRefId,
                StageLifecycleEventType.NO_EVIDENCE, "NO_EVIDENCE",
                "证据提取完成但无可采信证据");
    }
}
