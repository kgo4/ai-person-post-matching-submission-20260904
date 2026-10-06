package com.example.matching.common.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.Set;

/**
 * 人员能力评估工作流状态枚举
 *
 * @author system
 */
@Getter
@AllArgsConstructor
public enum WorkflowStatusEnum {

    RESUME_REQUIRED("RESUME_REQUIRED", "待上传简历"),
    RESUME_PARSING("RESUME_PARSING", "简历解析中"),
    RESUME_EVIDENCE_READY("RESUME_EVIDENCE_READY", "简历证据就绪"),
    RESUME_PARSED_NO_EVIDENCE("RESUME_PARSED_NO_EVIDENCE", "简历已解析（无可用证据）"),
    TEST_GENERATING("TEST_GENERATING", "测试生成中"),
    TEST_IN_PROGRESS("TEST_IN_PROGRESS", "测试进行中"),
    TEST_EVALUATING("TEST_EVALUATING", "测试评分中"),
    TEST_EVIDENCE_READY("TEST_EVIDENCE_READY", "测试证据就绪"),
    INTERVIEW_PREPARING("INTERVIEW_PREPARING", "面试准备中"),
    INTERVIEW_IN_PROGRESS("INTERVIEW_IN_PROGRESS", "面试进行中"),
    INTERVIEW_ANALYZING("INTERVIEW_ANALYZING", "面试分析中"),
    EVIDENCE_READY("EVIDENCE_READY", "证据就绪"),
    AGGREGATE_HARNESS_RUNNING("AGGREGATE_HARNESS_RUNNING", "聚合审核中"),
    LEVEL_CONFIRMING("LEVEL_CONFIRMING", "等级确认中"),
    /** A failed stage is retained for explicit recovery; evidence is never discarded. */
    RECOVERY_REQUIRED("RECOVERY_REQUIRED", "需恢复失败阶段"),
    COMPLETED("COMPLETED", "已完成"),
    REVIEW_REQUIRED("REVIEW_REQUIRED", "需人工复核"),
    FAILED("FAILED", "已失败"),
    CANCELLED("CANCELLED", "已取消");

    private final String code;
    private final String description;

    private static final Set<WorkflowStatusEnum> TERMINAL_STATES = Set.of(
            COMPLETED, FAILED, CANCELLED
    );

    private static final Set<WorkflowStatusEnum> ACTIVE_STATES = Set.of(
            RESUME_PARSING, RESUME_EVIDENCE_READY, RESUME_PARSED_NO_EVIDENCE,
            TEST_GENERATING, TEST_IN_PROGRESS, TEST_EVALUATING, TEST_EVIDENCE_READY,
            INTERVIEW_PREPARING, INTERVIEW_IN_PROGRESS, INTERVIEW_ANALYZING,
            EVIDENCE_READY, AGGREGATE_HARNESS_RUNNING, LEVEL_CONFIRMING,
            REVIEW_REQUIRED, RECOVERY_REQUIRED
    );

    public static WorkflowStatusEnum fromCode(String code) {
        if (code == null) return null;
        for (WorkflowStatusEnum status : values()) {
            if (status.code.equals(code)) {
                return status;
            }
        }
        return null;
    }

    public boolean isTerminal() {
        return TERMINAL_STATES.contains(this);
    }

    public boolean isActive() {
        return ACTIVE_STATES.contains(this);
    }
}
