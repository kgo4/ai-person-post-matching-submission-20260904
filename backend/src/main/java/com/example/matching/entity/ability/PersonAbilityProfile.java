package com.example.matching.entity.ability;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 人员能力画像实体（最终融合结果）
 * <p>
 * 综合多来源能力证据，生成最终人员能力画像。
 * 来源包括：简历解析、AI测评、AI面试、PMS、项目系统、学习成果等。
 *
 * @author system
 */
@Data
@EqualsAndHashCode(callSuper = false)
@TableName("person_ability_profile")
public class PersonAbilityProfile implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 主键，自增 */
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /** 员工ID */
    private Long empId;

    /** 能力标签ID */
    private Long tagId;

    /** 能力标签名称 */
    private String abilityName;

    /** 本次能力评估中的稳定能力身份。 */
    private Long assessmentAbilityId;

    /** 最终 Harness 所属评估工作流。 */
    private Long workflowId;

    /** 最终能力等级：1入门，2熟悉，3掌握，4精通，5专家 */
    private Integer finalLevel;

    /** 置信度评分：0-100 */
    private BigDecimal confidenceScore;

    /** 来源明细JSON（各来源贡献度） */
    private String sourceBreakdownJson;

    /** 证据数量 */
    private Integer evidenceCount;

    /** 最后证据时间 */
    private LocalDateTime lastEvidenceTime;

    /** 风险信号（JSON数组） */
    private String riskSignalsJson;

    /** 审核状态：AUTO/PENDING_REVIEW/REVIEWED */
    private String reviewStatus;

    /** 审核状态机：AUTO|PENDING|APPROVED|REJECTED|LEGACY_REVIEWED */
    private String reviewState;

    /** 审核人ID */
    private Long reviewedBy;

    /** 审核时间 */
    private LocalDateTime reviewedTime;

    /** 审核意见 */
    private String reviewComment;

    /** 机器可读审核决策原因码 */
    private String reviewDecisionReasonCode;

    /** 逻辑删除：0未删除，1已删除 */
    @TableLogic
    private Integer isDeleted;

    /** 创建人ID */
    @TableField(fill = FieldFill.INSERT)
    private Long createdBy;

    /** 创建时间 */
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdTime;

    /** 更新人ID */
    @TableField(fill = FieldFill.UPDATE)
    private Long updatedBy;

    /** 更新时间 */
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedTime;

    /** 乐观锁版本号 */
    @Version
    private Integer version;
}
