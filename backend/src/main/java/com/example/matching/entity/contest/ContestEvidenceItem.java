package com.example.matching.entity.contest;

import com.baomidou.mybatisplus.annotation.*;
import com.baomidou.mybatisplus.extension.handlers.JacksonTypeHandler;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 竞赛证据项实体
 *
 * @author system
 */
@Data
@EqualsAndHashCode(callSuper = false)
@TableName("contest_evidence_item")
public class ContestEvidenceItem implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 主键，自增 */
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /** 证据编码，稳定可读ID */
    private String evidenceCode;

    /** 来源类型：JD_IMPORT/RESUME_PARSE/AI_TEST/VIDEO_INTERVIEW/PMS_ANALYSIS/MATCHING_FEEDBACK/MANUAL */
    private String sourceType;

    /** 来源记录ID */
    private Long sourceRefId;

    /** 来源标题或文件名 */
    private String sourceTitle;

    /** 原始来源片段或完整来源文本 */
    private String sourceText;

    /** 目标类型：ABILITY_TAG/EMP_ABILITY/POST_ABILITY_MODEL/MATCHING_RECORD */
    private String targetType;

    /** 目标实体ID */
    private Long targetRefId;

    /** 提取或关联的能力名称 */
    private String abilityName;

    /** 解析的能力标签ID */
    private Long tagId;

    /** 模型置信度 0-100 */
    private BigDecimal confidenceScore;

    /** 来源可信度 0-100 */
    private BigDecimal credibilityScore;

    /** 证据状态：PENDING/VERIFIED/REJECTED */
    private String evidenceStatus;

    /** 人工审核意见 */
    private String reviewComment;

    /** RAG知识分块ID列表，用于来源追溯（JSON格式存储） */
    @TableField(typeHandler = JacksonTypeHandler.class)
    private List<Long> ragChunkIds;

    /** RAG知识文档ID列表，用于来源追溯（JSON格式存储） */
    @TableField(typeHandler = JacksonTypeHandler.class)
    private List<Long> ragDocumentIds;

    /** 审核人用户ID */
    private Long reviewedBy;

    /** 审核时间 */
    private LocalDateTime reviewedTime;

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

    /** 逻辑删除：0未删除，1已删除 */
    @TableLogic
    private Integer isDeleted;

    /** 乐观锁版本号 */
    @Version
    private Integer version;
}
