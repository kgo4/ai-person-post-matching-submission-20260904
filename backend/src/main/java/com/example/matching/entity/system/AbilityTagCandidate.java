package com.example.matching.entity.system;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 候选标签池实体
 * <p>
 * AI、JD、简历、视频面试、PMS项目发现的新能力先进入候选池。
 * 审核通过后可升级为正式标签或合并到已有标签。
 * <p>
 * 状态流转：
 * PENDING -> APPROVED (升级为正式标签)
 * PENDING -> MERGED (合并到已有标签)
 * PENDING -> REJECTED (拒绝)
 */
@Data
@EqualsAndHashCode(callSuper = false)
@TableName(value = "ability_tag_candidate", autoResultMap = true)
public class AbilityTagCandidate implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /** 候选标签名称 */
    private String candidateName;

    /** 标签分类：TECHNICAL, SOFT, BUSINESS */
    private String tagCategory;

    /** 领域分类：AI/BIG_DATA/IOT/SMART_SYSTEM/CLOUD/BLOCKCHAIN/GENERAL */
    private String domain;

    /** 能力描述 */
    private String description;

    /** AI推荐理由 */
    private String reason;

    /** AI推理理由（reason 的别名，用于幻觉防护等场景） */
    private String reasoning;

    /** 匹配到的正式标签ID（与 similarTagId 语义相同，部分场景使用此字段） */
    private Long matchedTagId;

    /** 证据片段 */
    private String evidenceText;

    /** 来源渠道：JD_IMPORT, RESUME_PARSE, VIDEO_INTERVIEW, PMS_ANALYSIS, POST_EVOLUTION, EXTERNAL_JD */
    private String sourceType;

    /** 来源关联ID */
    private Long sourceRefId;

    /** 关联岗位ID */
    private Long sourcePostId;

    /** 关联员工ID */
    private Long sourceEmpId;

    /** 出现次数 */
    private Integer occurrenceCount;

    /** 关联岗位数 */
    private Integer relatedPostCount;

    /** 关联员工数 */
    private Integer relatedEmpCount;

    /** 最相似的正式标签ID */
    private Long similarTagId;

    /** 最相似的正式标签名称 */
    private String similarTagName;

    /** 相似度分数 */
    private BigDecimal similarityScore;

    /** 状态：PENDING, APPROVED, REJECTED, MERGED */
    private String status;

    /** 审核意见 */
    private String reviewComment;

    /** 审核人ID */
    private Long reviewedBy;

    /** 审核时间 */
    private LocalDateTime reviewedTime;

    /** 合并到的正式标签ID */
    private Long mergedTagId;

    /** 标签名称的向量嵌入 */
    @TableField(typeHandler = com.baomidou.mybatisplus.extension.handlers.JacksonTypeHandler.class)
    private List<Float> embeddingVector;

    /** 逻辑删除 */
    @TableLogic
    private Integer isDeleted;

    /** 创建人ID */
    @TableField(fill = FieldFill.INSERT)
    private Long createdBy;

    /** 创建时间 */
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdTime;

    /** 更新时间 */
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedTime;
}
