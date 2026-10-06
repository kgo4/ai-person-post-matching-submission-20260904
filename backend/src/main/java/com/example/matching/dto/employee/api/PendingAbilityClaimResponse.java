package com.example.matching.dto.employee.api;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/** A source ability claim awaiting Harness review and fusion into the employee profile. */
public record PendingAbilityClaimResponse(
        Long id,
        Long empId,
        Long tagId,
        String abilityName,
        Integer claimedLevel,
        String sourceType,
        Long sourceRefId,
        String evidenceText,
        BigDecimal confidenceScore,
        String harnessDecision,
        Long harnessLogId,
        String status,
        LocalDateTime createdTime) {
}
