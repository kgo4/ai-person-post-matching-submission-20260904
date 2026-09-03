package com.example.matching.dto.matching;

import lombok.Data;

import java.math.BigDecimal;

@Data
public class GapAbilityDTO {

    private String name;

    private Integer requiredLevel;

    private BigDecimal actualLevel;

    private boolean weakEvidence;
}
