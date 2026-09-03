package com.example.matching.dto.matching;

import lombok.Data;

import java.math.BigDecimal;

/**
 * 维度修正DTO
 * <p>
 * 人工复核时对单个维度的修正请求。
 */
@Data
public class DimensionCorrectionDTO {

    /** 维度标识（如 abilityScore, semanticScore） */
    private String dimensionKey;

    /** 人工修正原始分（0-100） */
    private BigDecimal manualScore;

    /** 修正原因代码（必须匹配 FeedbackReasonCode 枚举值） */
    private String reasonCode;

    /** 修正原因文本（可选补充说明） */
    private String reasonText;

    /** 是否用于训练（默认true） */
}
