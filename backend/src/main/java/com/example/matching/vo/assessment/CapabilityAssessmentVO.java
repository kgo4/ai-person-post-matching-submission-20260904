package com.example.matching.vo.assessment;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * 能力评估工作流视图对象
 * <p>
 * 前端只展示后端返回的 {@link #displayStatus}、{@link #nextStepHint} 与
 * {@link #currentStage}，不在前端自行推断业务状态。
 *
 * @author system
 */
public final class CapabilityAssessmentVO {

    private CapabilityAssessmentVO() {
    }

    /**
     * 工作流视图
     */
    @Data
    public static class WorkflowView {
        private Long workflowId;
        private Long empId;
        /** 工作流原始状态（枚举 code），前端一般使用 displayStatus */
        private String workflowStatus;
        /** 兼容字段：工作流状态枚举 code（同 workflowStatus） */
        private String status;
        /** 展示用中文状态（后端统一生成，前端不自行推断） */
        private String displayStatus;
        private String currentStage;
        private Long activeStageRunId;
        private Integer workflowVersion;
        private LocalDateTime startedAt;
        private LocalDateTime completedAt;
        private String failedReason;
        /** 当前可执行操作 */
        private List<String> availableActions = new ArrayList<>();
        /** 阶段运行历史 */
        private List<StageRunView> stageRuns = new ArrayList<>();
        /** 下一步提示 */
        private String nextStepHint;
        /** 当前阶段运行详情（页面以工作流为主、阶段运行为证据） */
        private CurrentStageView currentStageDetail;
        /** 证据结果：GROUNDED / NO_EVIDENCE / EXTRACTION_FAILED */
        private String evidenceOutcome;
        /** 证据失败代码（evidenceOutcome 非 GROUNDED 时） */
        private String evidenceFailureCode;
        /** 证据失败信息 */
        private String evidenceFailureMessage;
    }

    /**
     * 当前阶段运行详情
     */
    @Data
    public static class CurrentStageView {
        private String stageType;
        /** 阶段运行状态：PENDING/RUNNING/WAITING_USER/SUCCEEDED/FAILED_RETRYABLE/FAILED_FINAL/CANCELLED */
        private String runStatus;
        private Long sourceRefId;
        private LocalDateTime updatedAt;
        private String failureMessage;
        /** 是否可重试失败阶段 */
        private boolean retryable;
    }

    /**
     * 阶段运行视图
     */
    @Data
    public static class StageRunView {
        private Long stageRunId;
        private String stageType;
        private String status;
        private Integer attemptCount;
        private LocalDateTime startedAt;
        private LocalDateTime completedAt;
        private String failureCode;
        private String failureMessage;
        /** 关联的资源 ID（如 resumeParseId, testId, sessionId） */
        private Long sourceRefId;
        /** 关联的资源类型（如 RESUME_PARSE, AI_TEST, AI_INTERVIEW） */
        private String sourceRefType;
        /** 最近一次生命周期事件类型 */
        private String latestLifecycleEvent;
    }
}
