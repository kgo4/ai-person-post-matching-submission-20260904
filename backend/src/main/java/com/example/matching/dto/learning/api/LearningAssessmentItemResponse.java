package com.example.matching.dto.learning.api;

import io.swagger.v3.oas.annotations.media.Schema;

import java.io.Serializable;
import java.time.LocalDateTime;

@Schema(description = "学习评估题目响应")
public record LearningAssessmentItemResponse(
        @Schema(description = "主键ID") Long id,
        @Schema(description = "所属计划ID") Long planId,
        @Schema(description = "关联步骤ID") Long stepId,
        @Schema(description = "关联能力标签ID") Long abilityTagId,
        @Schema(description = "题目类型") String questionType,
        @Schema(description = "题目文本") String questionText,
        @Schema(description = "参考答案") String referenceAnswer,
        @Schema(description = "难度等级") String difficultyLevel,
        @Schema(description = "来源") String source,
        @Schema(description = "用户答案") String answerText,
        @Schema(description = "评分，0-100") Integer score,
        @Schema(description = "作答状态") String assessmentStatus,
        @Schema(description = "评分反馈") String scoringFeedback,
        @Schema(description = "作答时间") LocalDateTime answeredTime,
        @Schema(description = "评分时间") LocalDateTime scoredTime,
        @Schema(description = "创建时间") LocalDateTime createdTime,
        @Schema(description = "更新时间") LocalDateTime updatedTime
) implements Serializable {
}
