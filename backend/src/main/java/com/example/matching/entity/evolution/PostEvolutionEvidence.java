package com.example.matching.entity.evolution;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 演化证据实体
 * <p>
 * 记录每个演化变更项的证据来源，支持证据链追溯。
 *
 * @author system
 */
@Data
@EqualsAndHashCode(callSuper = false)
@TableName("post_evolution_evidence")
public class PostEvolutionEvidence implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 主键，自增 */
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /** 演化任务ID */
    private Long taskId;

    /** 关联的变更项ID（可为空，表示任务级证据） */
    private Long changeItemId;

    /**
     * 证据来源类型：
     * MARKET_JD - 市场招聘JD
     * MATCHING_FEEDBACK - 人岗匹配反馈
     * LEARNING_GAP - 学习路径能力缺口
     * MANUAL_JD - 手动输入的JD
     * INDUSTRY_REPORT - 行业报告
     */
    private String sourceType;

    /** 来源数据ID（如market_jd_data.id、matching_feedback_dataset.id） */
    private Long sourceId;

    /** 来源标题（如JD的岗位名称+公司名） */
    private String sourceTitle;

    /** 来源链接 */
    private String sourceUrl;

    /** 证据原文片段 */
    private String evidenceText;

    /** 来源发布时间 */
    private LocalDateTime publishedTime;

    /** 采集时间 */
    private LocalDateTime collectedTime;

    /** 来源可信度权重：0.00-1.00 */
    private BigDecimal sourceWeight;

    /** 与岗位的相关度分数 */
    private BigDecimal similarityScore;

    /** 综合可信度分数 */
    private BigDecimal trustScore;

    /** 统一 sourceRef 格式 */
    private String sourceRef;

    /** 创建时间 */
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdTime;
}
