package com.example.matching.entity.employee;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 员工能力表实体
 *
 * @author system
 */
@Data
@EqualsAndHashCode(callSuper = false)
@TableName("emp_ability")
public class EmpAbility implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 主键，自增 */
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /** 员工ID */
    private Long empId;

    /** 能力标签ID */
    private Long tagId;

    /** 正式人员能力名称；当未关联系统标签时仍必须保留。 */
    private String abilityName;

    /** 本次能力评估中的稳定能力身份。 */
    private Long assessmentAbilityId;

    /** 最终 Harness 所属评估工作流。 */
    private Long workflowId;

    /** 统一证据账本摘要引用。 */
    private String evidenceSummaryRef;

    /** 最终等级决策记录ID。 */
    private Long harnessDecisionId;

    /** 掌握等级：1入门，2熟悉，3掌握，4精通，5专家 */
    private Integer masteryLevel;

    /** 能力等级（与 masteryLevel 语义相同，部分场景使用此字段） */
    private Integer abilityLevel;

    /** 统一评价来源，见 AbilitySourceType */
    private String evaluationSource;

    /** 来源权重，0.00-1.00 */
    private BigDecimal sourceWeight;

    /** 评价时间 */
    private LocalDate evaluationDate;

    /** 备注 */
    private String remark;

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

    /** 治理准入记录ID（AI管道写入必须引用 PASS 准入） */
    private Long governanceAdmissionId;
}
