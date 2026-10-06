package com.example.matching.dto.employee.api;

import java.io.Serializable;
import java.time.LocalDateTime;

public record PmsAnalysisTaskResponse(
        Long id,
        Long empId,
        Long pmsUserId,
        Integer analysisStatus,
        Integer dateRangeMonths,
        Integer workOrderCount,
        Integer bugCount,
        Integer testCaseCount,
        Integer projectCount,
        Integer extractedAbilityCount,
        String errorMessage,
        LocalDateTime createdTime,
        LocalDateTime updatedTime) implements Serializable {
}
