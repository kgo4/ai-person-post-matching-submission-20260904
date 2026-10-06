package com.example.matching.entity.ability;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 人员能力主张实体
 * <p>
 * 各来源提取的能力主张，用于多来源融合构建最终人员能力画像。
 * 来源包括：简历解析、AI测评、PMS、项目系统、学习成果等。
 *
 * @author system
 */
@Data
@EqualsAndHashCode(callSuper = false)
@TableName("person_ability_claim")
public class PersonAbilityClaim implements Serializable {

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

    /** 声称的能力等级：1入门，2熟悉，3掌握，4精通，5专家 */
    private Integer claimedLevel;

    /** 来源类型：RESUME_PARSE/AI_ASSESSMENT/PMS/PROJECT/LEARNING/MANUAL */
    private String sourceType;

    /** 来源引用ID */
    private Long sourceRefId;

    /** 来源权重：0-1 */
    private BigDecimal sourceWeight;

    /** 证据文本 */
    private String evidenceText;

    /** 来源引用（JSON数组），统一sourceRef格式 */
    private String sourceRefsJson;

    /** 置信度评分：0-100 */
    private BigDecimal confidenceScore;

    /** 时效性评分：0-100 */
    private BigDecimal freshnessScore;

    /** 权威度评分：0-100 */
    private BigDecimal authorityScore;

    /** Harness决策：PASS/BLOCK/REVIEW */
    private String harnessDecision;

    private Long harnessLogId;

    private String normalizedAbilityName;

    /** 状态：ACTIVE/INACTIVE/EXPIRED */
    private String status;

    /** 逻辑删除：0未删除，1已删除 */
    @TableLogic
    private Integer isDeleted;

    // ===== 工作流关联字段 =====

    /** 关联的工作流ID */
    private Long workflowId;

    /** 关联的阶段运行ID */
    private Long stageRunId;

    /** 关联的 Claim Group ID */
    private Long claimGroupId;

    /** 冻结的评估范围哈希，防止跨评估串证据。 */
    private String scopeHash;

    /** 证据状态（EvidenceStatusEnum code） */
    private String evidenceStatus;

    /** 可用性（EligibilityEnum code） */
    private String eligibility;

    // ===== 原有审计字段 =====

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
