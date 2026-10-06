package com.example.matching.service.matching.evaluation;

import lombok.Data;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

/**
 * 单维度评分分解
 * <p>
 * 每个评分层必须返回完整的分解信息，而非仅一个分数。
 * 包含系统评分和可选的人工修正评分。
 */
@Data
public class DimensionBreakdown {

    /** 维度标识（如 abilityScore, semanticScore） */
    private String dimension;

    /** 维度显示名称 */
    private String label;

    /** 系统原始分（0-100） */
    private BigDecimal rawScore;

    /** 该维度的配置权重 */
    private BigDecimal weight;

    /** 加权后的系统分 = rawScore * weight */
    private BigDecimal weightedScore;

    /** 维度状态 */
    private DimensionStatus status;

    /** 人工修正原始分（复核后填充） */
    private BigDecimal manualScore;

    /** 人工修正加权分 = manualScore * weight */
    private BigDecimal manualWeightedScore;

    /** 人工修正原因代码 */
    private FeedbackReasonCode manualReasonCode;

    /** 人工修正原因文本 */
    private String manualReasonText;

    /** 维度详情（各层自定义结构） */
    private Map<String, Object> details;

    /**
     * 维度状态枚举
     */
    public enum DimensionStatus {
        /** 可用：正常计算出的分数 */
        AVAILABLE,
        /** 缺失：数据源不可用（如 Milvus 宕机） */
        MISSING,
        /** 禁用：配置关闭该维度 */
        DISABLED,
        /** 不可用：计算失败但不影响其他维度 */
        UNAVAILABLE
    }

    /**
     * 创建一个可用维度分解
     */
    public static DimensionBreakdown available(String dimension, String label,
                                                 BigDecimal rawScore, BigDecimal weight) {
        DimensionBreakdown bd = new DimensionBreakdown();
        bd.setDimension(dimension);
        bd.setLabel(label);
        bd.setRawScore(rawScore);
        bd.setWeight(weight);
        bd.setWeightedScore(rawScore.multiply(weight).setScale(2, java.math.RoundingMode.HALF_UP));
        bd.setStatus(DimensionStatus.AVAILABLE);
        return bd;
    }

    /**
     * 创建一个缺失维度分解
     */
    public static DimensionBreakdown missing(String dimension, String label, BigDecimal weight) {
        DimensionBreakdown bd = new DimensionBreakdown();
        bd.setDimension(dimension);
        bd.setLabel(label);
        bd.setRawScore(BigDecimal.ZERO);
        bd.setWeight(weight);
        bd.setWeightedScore(BigDecimal.ZERO);
        bd.setStatus(DimensionStatus.MISSING);
        return bd;
    }

    /**
     * 创建一个不可用维度分解
     */
    public static DimensionBreakdown unavailable(String dimension, String label, BigDecimal weight) {
        DimensionBreakdown bd = new DimensionBreakdown();
        bd.setDimension(dimension);
        bd.setLabel(label);
        bd.setRawScore(BigDecimal.ZERO);
        bd.setWeight(weight);
        bd.setWeightedScore(BigDecimal.ZERO);
        bd.setStatus(DimensionStatus.UNAVAILABLE);
        return bd;
    }
}
