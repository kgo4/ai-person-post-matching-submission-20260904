package com.example.matching.vo.post;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.util.List;

/**
 * 岗位趋势解析进度视图对象。
 */
@Data
public class PostTrendProgressVO {

    private Long taskId;

    /** 任务状态（与 task_status 一致），前端据此判断是否继续轮询 */
    private String taskStatus;

    /** 当前执行阶段 */
    private String currentStep;

    private Integer percent;

    private String errorMessage;

    /** 无候选时的原因说明 */
    private String diagnostics;

    private List<StepProgress> steps;

    @Data
    @AllArgsConstructor
    public static class StepProgress {
        private String name;
        /** PENDING / RUNNING / DONE / ERROR */
        private String status;
    }
}
