package com.example.matching.entity.system;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 能力标签表实体
 *
 * @author system
 */
@Data
@EqualsAndHashCode(callSuper = false)
@TableName(value = "ability_tag", autoResultMap = true)
public class AbilityTag implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 主键，自增 */
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /** 标签唯一编码 */
    private String tagCode;

    /** 标签名称 */
    private String tagName;

    /** 父标签ID，0表示一级标签 */
    private Long parentId;

    /** 标签分类：TECHNICAL-技术能力，SOFT-软技能，BUSINESS-业务能力 */
    private String tagCategory;

    /** 领域分类：AI/BIG_DATA/IOT/SMART_SYSTEM/CLOUD/BLOCKCHAIN/GENERAL */
    private String domain;

    /** 标签层级：1/2/3级 */
    private Integer tagLevel;

    /** 标签描述，说明能力定义与评价标准 */
    private String description;

    /** 排序字段，数值越小越靠前 */
    private Integer sortOrder;

    /** 是否系统内置标签：0否，1是 */
    private Integer isSystem;

    /** 标签来源：MANUAL-手动创建，AI_JD-AI从JD生成，AI_RESUME-AI从简历生成 */
    private String sourceType;

    /** 标签名称的向量嵌入（JSON格式存储，用于语义匹配） */
    @TableField(typeHandler = com.baomidou.mybatisplus.extension.handlers.JacksonTypeHandler.class)
    private List<Float> embeddingVector;

    /** 归一后的标准标签ID，标准标签自身指向自身ID */
    private Long canonicalTagId;

    /** 状态：0停用，1启用 */
    private Integer status;

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
