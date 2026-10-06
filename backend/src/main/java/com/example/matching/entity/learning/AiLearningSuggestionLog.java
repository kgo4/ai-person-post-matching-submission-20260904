package com.example.matching.entity.learning;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * AI 学习建议审计日志实体
 * <p>
 * 记录 AI 生成的每条学习建议，用于追溯和审计。
 *
 * @author system
 */
@Data
@EqualsAndHashCode(callSuper = false)
@TableName("ai_learning_suggestion_log")
public class AiLearningSuggestionLog implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 主键，自增 */
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /** 关联匹配记录ID */
    private Long matchingRecordId;

    /** 员工ID */
    private Long empId;

    /** 岗位ID */
    private Long postId;

    /** 能力名称 */
    private String abilityName;

    /** 能力标签ID */
    private Long tagId;

    /** AI原始返回JSON */
    private String aiResponseJson;

    /** 校验后的JSON */
    private String validatedJson;

    /** 被过滤的步骤数 */
    private Integer filteredCount;

    /** RAG检索的chunkId列表(JSON数组) */
    private String ragChunkIds;

    /** 建议来源 */
    private String suggestionSource;

    /** 状态: ACTIVE/ARCHIVED/INVALIDATED */
    private String status;

    /** 创建时间 */
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    /** 更新时间 */
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}
