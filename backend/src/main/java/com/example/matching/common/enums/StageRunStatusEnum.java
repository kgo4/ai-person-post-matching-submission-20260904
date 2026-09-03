package com.example.matching.common.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.Set;

/**
 * 能力评估阶段运行状态枚举
 * <p>
 * 阶段运行（PersonCapabilityStageRun）是任务执行状态的唯一事实来源，
 * 由 CapabilityAssessmentLifecycleCoordinator 通过 CAS 维护，业务服务不直接改。
 *
 * @author system
 */
@Getter
@AllArgsConstructor
public enum StageRunStatusEnum {

    /** 已创建，尚未被消费者抢占 */
    PENDING("PENDING", "待执行"),

    /** 已被实际任务抢占并执行 */
    RUNNING("RUNNING", "执行中"),

    /** 任务就绪，等待候选人作答或 HR 操作 */
    WAITING_USER("WAITING_USER", "等待用户"),

    /** 阶段成功完成 */
    SUCCEEDED("SUCCEEDED", "成功"),

    /** 可重试失败 */
    FAILED_RETRYABLE("FAILED_RETRYABLE", "可重试失败"),

    /** 最终失败 */
    FAILED_FINAL("FAILED_FINAL", "最终失败"),

    /** 已取消 */
    CANCELLED("CANCELLED", "已取消");

    private final String code;
    private final String description;

    /** 活跃状态：任务仍然在途，页面应轮询 */
    public static final Set<StageRunStatusEnum> ACTIVE_STATUSES = Set.of(
            PENDING, RUNNING, WAITING_USER
    );

    public static StageRunStatusEnum fromCode(String code) {
        if (code == null) {
            return null;
        }
        for (StageRunStatusEnum status : values()) {
            if (status.code.equals(code)) {
                return status;
            }
        }
        return null;
    }

    public boolean isTerminal() {
        return this == SUCCEEDED || this == FAILED_RETRYABLE || this == FAILED_FINAL || this == CANCELLED;
    }
}
