package com.example.matching.dto.learning;

import jakarta.validation.constraints.NotBlank;

/** 学习路径测评作答请求。 */
public record LearningAssessmentAnswerRequest(@NotBlank String answerText) {
}
