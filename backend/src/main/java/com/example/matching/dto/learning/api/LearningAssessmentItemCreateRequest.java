package com.example.matching.dto.learning.api;

import io.swagger.v3.oas.annotations.media.Schema;

import java.io.Serializable;

@Schema(description = "学习评估题目创建请求")
public record LearningAssessmentItemCreateRequest(
        @Schema(description = "所属计划ID") Long planId,
        @Schema(description = "关联步骤ID") Long stepId,
        @Schema(description = "关联能力标签ID") Long abilityTagId,
        @Schema(description = "题目类型") String questionType,
        @Schema(description = "题目文本") String questionText,
        @Schema(description = "参考答案") String referenceAnswer,
        @Schema(description = "难度等级") String difficultyLevel,
        @Schema(description = "来源") String source
) implements Serializable {
}
