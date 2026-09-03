package com.example.matching.entity.interview;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 面试能力观察实体
 * <p>
 * AI面试输出的是"面试能力观察"，不是最终能力画像。
 * 它表示在本次面试场景下观察到的能力表现，不是永久能力等级。
 *
 * @author system
 */
@Data
@EqualsAndHashCode(callSuper = false)
@TableName("interview_ability_observation")
public class InterviewAbilityObservation implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 主键，自增 */
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /** 面试会话ID */
    private Long sessionId;

    /** 员工ID */
    private Long empId;

    /** 目标岗位ID */
    private Long postId;

    /** 能力标签ID */
    private Long tagId;

    /** 能力标签名称 */
    private String abilityName;

    /** 观察到的能力等级：1入门，2熟悉，3掌握，4精通，5专家 */
    private Integer observedLevel;

    /** 置信度评分：0-100 */
    private BigDecimal confidenceScore;

    /** 证据文本 */
    private String evidenceText;

    /** 关联问题ID列表（JSON数组） */
    private String questionIdsJson;

    /** 关联回答引用（JSON数组） */
    private String answerRefsJson;

    /** 追问引用（JSON数组） */
    private String followUpRefsJson;

    /** 风险信号（JSON数组） */
    private String riskSignalsJson;

    /** 面试结论 */
    private String interviewConclusion;

    /** 来源引用（JSON数组），统一sourceRef格式 */
    private String sourceRefsJson;

    /** Harness决策：PASS/BLOCK/REVIEW */
    private String harnessDecision;

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
