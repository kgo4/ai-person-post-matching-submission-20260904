package com.example.matching.dto.employee.api;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

public record AiTestResponse(
        Long id,
        Long empId,
        String testTitle,
        Long abilityTagId,
        String abilityTagName,
        String questions,
        String answers,
        String aiEvaluation,
        String analysisReport,
        String errorMessage,
        BigDecimal score,
        Integer masteryLevel,
        Integer status,
        LocalDateTime createdTime,
        LocalDateTime completedTime,
        LocalDateTime importedTime) implements Serializable {
}
