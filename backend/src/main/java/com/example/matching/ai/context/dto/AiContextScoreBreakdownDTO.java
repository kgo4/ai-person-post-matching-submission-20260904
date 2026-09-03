package com.example.matching.ai.context.dto;

import lombok.Data;

import java.math.BigDecimal;

/**
 * AI上下文评分明细DTO
 *
 * @author system
 */
@Data
public class AiContextScoreBreakdownDTO {

    /** 评分维度 */
    private String dimension;

    /** 评分值 */
    private BigDecimal score;

    /** 权重 */
    private BigDecimal weight;

    /** 说明 */
    private String description;
}
