package com.example.matching.dto.employee.api;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

public record VideoInterviewSessionResponse(
        Long id,
        Long empId,
        Long postId,
        String sessionName,
        String interviewMode,
        BigDecimal overallScore,
        Integer status,
        String conversationState,
        Integer currentQuestionOrder,
        Integer durationSeconds,
        Integer questionCount,
        LocalDateTime createdTime,
        LocalDateTime updatedTime) implements Serializable {
}
