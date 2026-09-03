package com.example.matching.service.matching.evaluation;

import lombok.Data;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 统一匹配评估结果
 * <p>
 * 包含各维度评分分解、最终综合分和评估状态。
 * 推荐和正式匹配使用同一评估器产出此结果。
 */
@Data
public class MatchEvaluationResult {

    /** 最终综合分（0-100） */
    private BigDecimal finalScore;

    /** 各维度分解（维度key -> 分解详情） */
    private Map<String, DimensionBreakdown> dimensions = new LinkedHashMap<>();

    /** 评估方向 */
    private MatchEvaluationDirection direction;

    /** 员工ID */
    private Long empId;

    /** 岗位ID */
    private Long postId;

    /** 权重方案版本 */
    private String weightProfileVersion;

    /** 权重快照JSON（评估时使用的精确权重） */
    private String weightSnapshotJson;

    /** 向量语义分是否缺失 */
    private boolean semanticMissing;

    /** LLM分是否可用 */
    private boolean llmAvailable;

    /** 匹配状态：1强适配，2适配，3待观察，4不适配 */
    private Integer matchStatus;

    /** 硬条件检查状态：PASS, RISK, FAIL */
    private String hardConditionStatus;

    /**
     * 获取维度分解
     */
    public DimensionBreakdown getDimension(String key) {
        return dimensions.get(key);
    }

    /**
     * 添加维度分解
     */
    public void addDimension(DimensionBreakdown bd) {
        dimensions.put(bd.getDimension(), bd);
    }

    /**
     * 获取所有维度的系统加权分总和
     */
    public BigDecimal getSystemWeightedTotal() {
        BigDecimal total = BigDecimal.ZERO;
        for (DimensionBreakdown bd : dimensions.values()) {
            if (bd.getWeightedScore() != null) {
                total = total.add(bd.getWeightedScore());
            }
        }
        return total;
    }

    /**
     * 获取最终分（含人工修正）
     */
    public BigDecimal getEffectiveFinalScore() {
        return finalScore != null ? finalScore : BigDecimal.ZERO;
    }
}
