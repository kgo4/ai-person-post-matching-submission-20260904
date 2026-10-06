package com.example.matching.dto.evolution;

import lombok.Data;

import java.math.BigDecimal;

/**
 * 岗位演化变更项VO
 *
 * @author system
 */
@Data
public class PostEvolutionChangeItemVO {

    private Long id;
    private Long taskId;
    private String changeType;
    private Long tagId;
    private String abilityName;
    private Integer oldLevel;
    private Integer newLevel;
    private BigDecimal oldWeight;
    private BigDecimal newWeight;
    private Integer oldIsCore;
    private Integer newIsCore;
    private BigDecimal supportScore;
    private String confirmStatus;
    private String reviewComment;
}
