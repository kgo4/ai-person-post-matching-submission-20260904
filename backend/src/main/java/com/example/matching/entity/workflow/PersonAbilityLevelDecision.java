package com.example.matching.entity.workflow;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 人员能力等级决策实体
 * <p>
 * 最终能力等级确认中心的决策记录，保存完整策略快照以便审计回放。
 *
 * @author system
 */
@Data
@EqualsAndHashCode(callSuper = false)
@TableName("person_ability_level_decision")
public class PersonAbilityLevelDecision implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /** 关联的工作流ID */
    private Long workflowId;

    /** 关联的 Claim Group ID */
    private Long claimGroupId;

    /** 员工ID */
    private Long empId;

    /** 能力标签ID */
    private Long tagId;

    /** 决策状态（DecisionStatusEnum code） */
    private String decisionStatus;

    /** 最终等级：1-5 */
    private Integer finalLevel;

    /** 最终置信度：0-100 */
    private Integer finalConfidence;

    /** 审核状态：AUTO/PENDING/APPROVED/REJECTED */
    private String reviewState;

    /** 策略版本号 */
    private String policyVersion;

    /** 策略快照JSON */
    private String policySnapshotJson;

    /** 来源分解JSON */
    private String sourceBreakdownJson;

    /** 有效权重分解JSON */
    private String effectiveWeightBreakdownJson;

    /** 冲突信号JSON */
    private String conflictSignalsJson;

    /** 决策原因码JSON */
    private String decisionReasonCodesJson;

    /** 审核人ID */
    private Long reviewedBy;

    /** 审核时间 */
    private LocalDateTime reviewedTime;

    /** 创建时间 */
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdTime;

    /** 更新时间 */
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedTime;

    /** 乐观锁版本号 */
    @Version
    private Integer version;
}
