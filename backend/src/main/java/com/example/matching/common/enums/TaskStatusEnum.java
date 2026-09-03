package com.example.matching.common.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 任务/流程生命周期状态枚举
 * <p>
 * 适用于演化任务、图谱变更集、构建任务、学习路径、匹配任务等所有需要状态流转的业务流程。
 * 取代散落在各 Service 中的 "PENDING"/"RUNNING"/"COMPLETED"/"FAILED" 等魔法字符串。
 *
 * @author system
 */
@Getter
@AllArgsConstructor
public enum TaskStatusEnum {

    /** 待处理 */
    PENDING("PENDING", "待处理"),

    /** 运行中 */
    RUNNING("RUNNING", "运行中"),

    /** 已完成 */
    COMPLETED("COMPLETED", "已完成"),

    /** 已失败 */
    FAILED("FAILED", "已失败"),

    /** 已成功 */
    SUCCEEDED("SUCCEEDED", "已成功"),

    /** 已取消 */
    CANCELLED("CANCELLED", "已取消"),

    /** 重试中 */
    RETRYING("RETRYING", "重试中"),

    /** 待确认（分析完成，等待人工审核） */
    WAIT_CONFIRM("WAIT_CONFIRM", "待确认"),

    /** 已应用（变更已写入能力模型） */
    APPLIED("APPLIED", "已应用"),

    /** 部分应用（部分已审核项未获得 PASS 准入） */
    PARTIALLY_APPLIED("PARTIALLY_APPLIED", "部分应用");

    private final String code;
    private final String description;

    public static TaskStatusEnum fromCode(String code) {
        if (code == null) return null;
        for (TaskStatusEnum status : values()) {
            if (status.code.equals(code)) {
                return status;
            }
        }
        return null;
    }

    public boolean isTerminal() {
        return this == COMPLETED || this == FAILED || this == SUCCEEDED || this == CANCELLED;
    }

    public boolean isActive() {
        return this == PENDING || this == RUNNING || this == RETRYING;
    }
}
