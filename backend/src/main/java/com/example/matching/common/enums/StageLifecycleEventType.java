package com.example.matching.common.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 能力评估阶段生命周期事件类型
 * <p>
 * 业务服务（简历/测试/面试/Harness/等级确认）只发布"发生了什么"的事件，
 * 由 {@code CapabilityAssessmentLifecycleCoordinator} 根据事件与状态转换表
 * 统一推进工作流与阶段运行状态。
 *
 * @author system
 */
@Getter
@AllArgsConstructor
public enum StageLifecycleEventType {

    /** 异步任务已被消费者真正抢占并开始执行 */
    TASK_CLAIMED("TASK_CLAIMED", "任务已抢占开始执行"),

    /** 任务就绪，等待候选人作答或 HR 操作 */
    TASK_READY_FOR_USER("TASK_READY_FOR_USER", "任务就绪等待用户"),

    /** 业务任务成功完成 */
    TASK_SUCCEEDED("TASK_SUCCEEDED", "任务成功完成"),

    /** 业务任务可重试失败 */
    TASK_FAILED_RETRYABLE("TASK_FAILED_RETRYABLE", "任务可重试失败"),

    /** 业务任务最终失败 */
    TASK_FAILED_FINAL("TASK_FAILED_FINAL", "任务最终失败"),

    /** 用户（候选人/HR）开始操作 */
    USER_ACTION_STARTED("USER_ACTION_STARTED", "用户操作开始"),

    /** 用户（候选人/HR）操作完成 */
    USER_ACTION_COMPLETED("USER_ACTION_COMPLETED", "用户操作完成"),

    /** 证据提取完成但无可采信证据（任务本身成功但证据为空） */
    NO_EVIDENCE("NO_EVIDENCE", "证据提取完成但无可用证据");

    private final String code;
    private final String description;

    public static StageLifecycleEventType fromCode(String code) {
        if (code == null) {
            return null;
        }
        for (StageLifecycleEventType type : values()) {
            if (type.code.equals(code)) {
                return type;
            }
        }
        return null;
    }
}
