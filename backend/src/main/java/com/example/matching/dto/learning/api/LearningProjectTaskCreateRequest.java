package com.example.matching.dto.learning.api;

import io.swagger.v3.oas.annotations.media.Schema;

import java.io.Serializable;

@Schema(description = "学习项目任务创建请求")
public record LearningProjectTaskCreateRequest(
        @Schema(description = "所属计划ID") Long planId,
        @Schema(description = "关联步骤ID") Long stepId,
        @Schema(description = "关联能力标签ID") Long abilityTagId,
        @Schema(description = "项目名称") String projectName,
        @Schema(description = "项目URL") String projectUrl,
        @Schema(description = "任务标题") String taskTitle,
        @Schema(description = "任务背景") String taskBackground,
        @Schema(description = "任务要求") String taskRequirements,
        @Schema(description = "验收标准") String acceptanceCriteria,
        @Schema(description = "难度等级") String difficultyLevel,
        @Schema(description = "期望输出") String expectedOutput
) implements Serializable {
}
