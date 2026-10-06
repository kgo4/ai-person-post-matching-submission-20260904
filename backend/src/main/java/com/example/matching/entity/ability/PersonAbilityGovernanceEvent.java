package com.example.matching.entity.ability;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 人员能力治理事件实体
 * <p>
 * 记录人工对最终入库能力标签的修改事件。
 * 人工修改的是最终结果，不是原始抽取结果。
 *
 * @author system
 */
@Data
@EqualsAndHashCode(callSuper = false)
@TableName(value = "person_ability_governance_event", autoResultMap = true)
public class PersonAbilityGovernanceEvent implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /** 员工ID */
    private Long empId;

    /** 原标签ID */
    private Long oldTagId;

    /** 原标签名称 */
    private String oldTagName;

    /** 新标签ID（标签替换时） */
    private Long newTagId;

    /** 新标签名称 */
    private String newTagName;

    /** 原等级 */
    private Integer oldLevel;

    /** 新等级 */
    private Integer newLevel;

    /** 原置信度 */
    private BigDecimal oldConfidence;

    /** 新置信度 */
    private BigDecimal newConfidence;

    /** 来源分解JSON（各来源对这个能力的贡献） */
    @TableField(typeHandler = com.baomidou.mybatisplus.extension.handlers.JacksonTypeHandler.class)
    private String sourceBreakdownJson;

    /** 证据快照JSON（修改时的证据状态） */
    @TableField(typeHandler = com.baomidou.mybatisplus.extension.handlers.JacksonTypeHandler.class)
    private String evidenceSnapshotJson;

    /** 修改类型：TAG_RENAME, TAG_REPLACE, LEVEL_UP, LEVEL_DOWN, MERGE_TO_EXISTING_TAG, REMOVE_TAG, EVIDENCE_CORRECTION */
    private String modifyType;

    /** 修改原因 */
    private String modifyReason;

    /** 模板payload JSON（结构化修改详情） */
    @TableField(typeHandler = com.baomidou.mybatisplus.extension.handlers.JacksonTypeHandler.class)
    private String templatePayloadJson;

    /** 是否生成Agent记忆：0否，1是 */
    private Integer generateMemory;

    /** 关联的记忆ID */
    private Long memoryId;

    /** 创建人ID */
    @TableField(fill = FieldFill.INSERT)
    private Long createdBy;

    /** 创建时间 */
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdTime;

    /** 逻辑删除 */
    @TableLogic
    private Integer isDeleted;
}
