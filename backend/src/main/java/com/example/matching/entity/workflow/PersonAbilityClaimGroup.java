package com.example.matching.entity.workflow;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 人员能力主张聚合组实体
 * <p>
 * 将同一能力的多来源 Claim 聚合到一个组，便于统一审核和等级确认。
 *
 * @author system
 */
@Data
@EqualsAndHashCode(callSuper = false)
@TableName("person_ability_claim_group")
public class PersonAbilityClaimGroup implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /** 本次评估内稳定的能力身份；与全局 canonicalTagId 解耦。 */
    private Long assessmentAbilityId;

    /** 关联的工作流ID */
    private Long workflowId;

    /** 员工ID（冗余，便于查询） */
    private Long empId;

    /** 规范标签ID（已解析时） */
    private Long canonicalTagId;

    private String taxonomyVersion;

    private Long parentTagId;

    private String taxonomyPath;

    private Integer assessable;

    private String scopeHash;

    /** 标准化能力名称 */
    private String normalizedAbilityName;

    /** 标签解析状态（TagResolutionStatusEnum code） */
    private String tagResolutionStatus;

    /** 组状态（EvidenceStatusEnum code） */
    private String status;

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
