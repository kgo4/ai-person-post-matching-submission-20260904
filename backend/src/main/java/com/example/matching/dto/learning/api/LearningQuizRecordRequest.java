package com.example.matching.dto.learning.api;

import io.swagger.v3.oas.annotations.media.Schema;

import java.io.Serializable;
import java.math.BigDecimal;

@Schema(description = "答题记录提交请求")
public record LearningQuizRecordRequest(
        @Schema(description = "员工ID") Long empId,
        @Schema(description = "题目ID") Long quizId,
        @Schema(description = "学习计划ID") Long planId,
        @Schema(description = "学习步骤ID") Long stepId,
        @Schema(description = "用户答案") String userAnswer,
        @Schema(description = "是否正确") Integer isCorrect,
        @Schema(description = "答题用时(秒)") Integer answerTime,
        @Schema(description = "得分") BigDecimal answerScore
) implements Serializable {
}
