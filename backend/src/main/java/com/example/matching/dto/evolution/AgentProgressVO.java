package com.example.matching.dto.evolution;

import lombok.Data;

import java.util.List;

/**
 * Agent 执行进度 VO
 *
 * @author system
 */
@Data
public class AgentProgressVO {

    /** 任务ID */
    private Long taskId;

    /** 当前阶段 */
    private String currentStep;

    /** 进度百分比 */
    private int percent;

    /** 失败时提供给前端的可读原因 */
    private String errorMessage;

    /** 各步骤状态 */
    private List<StepProgress> steps;

    /**
     * 步骤进度
     */
    @Data
    public static class StepProgress {
        private String name;
        private String status; // PENDING/RUNNING/DONE/ERROR

        public StepProgress() {}

        public StepProgress(String name, String status) {
            this.name = name;
            this.status = status;
        }
    }
}
