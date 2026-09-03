package com.example.matching.entity.matching;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * AI反馈数据集表实体
 *
 * @author system
 */
@Data
@EqualsAndHashCode(callSuper = false)
@TableName("matching_feedback_dataset")
public class MatchingFeedbackDataset implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 主键，自增 */
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /** 关联的匹配记录ID */
    private Long matchingRecordId;

    /** 员工ID */
    private Long empId;

    /** 岗位ID */
    private Long postId;

    /** 原始AI匹配度 */
    private BigDecimal aiMatchScore;

    /** 人工最终匹配度 */
    private BigDecimal finalMatchScore;

    /** 人工最终匹配状态 */
    private Integer finalMatchStatus;

    /** 采纳情况：1完全采纳，2部分采纳，3未采纳 */
    private Integer adoptionStatus;

    /** 结构化反馈原因，JSON数组格式，如 ["POST_MODEL_INACCURATE","RESUME_OVERESTIMATED"] */
    private String feedbackReasons;

    /** 人工补充说明 */
    private String feedbackComment;

    /** 反馈时间 */
    private LocalDateTime feedbackTime;

    /** 校准来源：STRUCTURED_REVIEW / MANUAL_FEEDBACK */
    private String calibrationSource;

    /** 校准模板版本，固定 v1 */
    private String calibrationTemplateVersion;

    /** 是否允许导出（替代语义不准确的 is_used_for_training） */
    private Integer exportEnabled;
}
