package com.example.matching.entity.matching;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 匹配反馈维度明细表实体
 * <p>
 * 存储人工对每个评分维度的修正数据，用于维度级别的训练信号。
 */
@Data
@EqualsAndHashCode(callSuper = false)
@TableName("matching_feedback_dimension")
public class MatchingFeedbackDimension implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 主键，自增 */
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /** 关联的反馈数据集ID（matching_feedback_dataset.id） */
    private Long feedbackId;

    /** 关联的匹配记录ID */
    private Long matchingRecordId;

    /** 维度标识（如 abilityScore, semanticScore） */
    private String dimensionKey;

    /** 系统原始分（0-100） */
    private BigDecimal systemRawScore;

    /** 系统权重 */
    private BigDecimal systemWeight;

    /** 系统加权分 */
    private BigDecimal systemWeightedScore;

    /** 人工修正原始分 */
    private BigDecimal manualRawScore;

    /** 人工修正加权分 */
    private BigDecimal manualWeightedScore;

    /** 人工修正原因代码 */
    private String reasonCode;

    /** 人工修正原因文本 */
    private String reasonText;

    /** 创建时间 */
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdTime;
}
