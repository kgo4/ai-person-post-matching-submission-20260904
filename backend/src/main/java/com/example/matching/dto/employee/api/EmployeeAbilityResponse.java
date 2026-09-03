package com.example.matching.dto.employee.api;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

public record EmployeeAbilityResponse(
        Long id,
        Long empId,
        Long tagId,
        String tagName,
        String abilityName,
        Long assessmentAbilityId,
        Long workflowId,
        Integer masteryLevel,
        Integer abilityLevel,
        String evaluationSource,
        BigDecimal sourceWeight,
        LocalDate evaluationDate,
        String remark,
        LocalDateTime createdTime,
        LocalDateTime updatedTime) implements Serializable {
}
