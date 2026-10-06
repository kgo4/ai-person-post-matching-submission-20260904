package com.example.matching.dto.learning.api;

import io.swagger.v3.oas.annotations.media.Schema;

import java.io.Serializable;

@Schema(description = "学习项目提交创建请求")
public record LearningProjectSubmissionCreateRequest(
        @Schema(description = "关联任务ID") Long taskId,
        @Schema(description = "所属计划ID") Long planId,
        @Schema(description = "关联步骤ID") Long stepId,
        @Schema(description = "提交员工ID") Long empId,
        @Schema(description = "仓库URL") String repoUrl,
        @Schema(description = "演示URL") String demoUrl,
        @Schema(description = "报告URL") String reportUrl,
        @Schema(description = "提交文本说明") String submissionText
) implements Serializable {
}
