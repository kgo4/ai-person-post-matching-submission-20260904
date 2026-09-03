package com.example.matching.dto.employee.api;

import java.io.Serializable;
import java.time.LocalDateTime;

public record ResumeParseResponse(
        Long id,
        Long empId,
        String fileName,
        String fileType,
        String parsedContent,
        String aiAnalysisResult,
        Integer status,
        String errorMessage,
        Integer retryCount,
        LocalDateTime createdTime,
        LocalDateTime updatedTime) implements Serializable {
}
