package com.example.matching.dto.system.api;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record AbilityTagCandidateResponse(
    Long id,
    String candidateName,
    String tagCategory,
    String domain,
    String description,
    String reason,
    String evidenceText,
    String sourceType,
    Long sourceRefId,
    Long sourcePostId,
    Long sourceEmpId,
    Integer occurrenceCount,
    Integer relatedPostCount,
    Integer relatedEmpCount,
    Long similarTagId,
    String similarTagName,
    BigDecimal similarityScore,
    String status,
    String reviewComment,
    Long reviewedBy,
    LocalDateTime reviewedTime,
    Long mergedTagId,
    LocalDateTime createdTime,
    LocalDateTime updatedTime
) {}
