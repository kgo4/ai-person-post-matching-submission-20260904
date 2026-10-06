package com.example.matching.dto.employee.api;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDate;

public record EmployeeAbilityUpdateRequest(
        String abilityName,
        Long tagId,
        Integer masteryLevel,
        String evaluationSource,
        BigDecimal sourceWeight,
        LocalDate evaluationDate,
        String remark) implements Serializable {
}
