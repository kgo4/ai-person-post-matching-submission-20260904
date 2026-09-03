package com.example.matching.dto.learning.api;

import io.swagger.v3.oas.annotations.media.Schema;

import java.io.Serializable;

@Schema(description = "学习项目提交更新请求")
public record LearningProjectSubmissionUpdateRequest(
        @Schema(description = "主键ID") Long id,
        @Schema(description = "仓库URL") String repoUrl,
        @Schema(description = "演示URL") String demoUrl,
        @Schema(description = "报告URL") String reportUrl,
        @Schema(description = "提交文本说明") String submissionText,
        @Schema(description = "审核状态") String reviewStatus,
        @Schema(description = "审核意见") String reviewComment,
        @Schema(description = "AI审核结果") String aiReviewResult
) implements Serializable {
}
