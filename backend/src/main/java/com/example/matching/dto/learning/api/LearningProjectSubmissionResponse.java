package com.example.matching.dto.learning.api;

import io.swagger.v3.oas.annotations.media.Schema;

import java.io.Serializable;
import java.time.LocalDateTime;

@Schema(description = "学习项目提交响应")
public record LearningProjectSubmissionResponse(
        @Schema(description = "主键ID") Long id,
        @Schema(description = "关联任务ID") Long taskId,
        @Schema(description = "所属计划ID") Long planId,
        @Schema(description = "关联步骤ID") Long stepId,
        @Schema(description = "提交员工ID") Long empId,
        @Schema(description = "仓库URL") String repoUrl,
        @Schema(description = "演示URL") String demoUrl,
        @Schema(description = "报告URL") String reportUrl,
        @Schema(description = "提交文本说明") String submissionText,
        @Schema(description = "AI审核结果") String aiReviewResult,
        @Schema(description = "审核状态") String reviewStatus,
        @Schema(description = "审核意见") String reviewComment,
        @Schema(description = "关联证据ID") Long evidenceId,
        @Schema(description = "审核人ID") Long reviewedBy,
        @Schema(description = "审核时间") LocalDateTime reviewedTime,
        @Schema(description = "创建时间") LocalDateTime createdTime,
        @Schema(description = "更新时间") LocalDateTime updatedTime
) implements Serializable {
}
