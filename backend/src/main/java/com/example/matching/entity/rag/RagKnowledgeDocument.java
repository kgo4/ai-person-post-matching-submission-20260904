package com.example.matching.entity.rag;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * RAG知识文档实体
 *
 * @author system
 */
@Data
@EqualsAndHashCode(callSuper = false)
@TableName("rag_knowledge_document")
public class RagKnowledgeDocument implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 主键，自增 */
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /** 文档编码，稳定可读ID */
    private String docCode;

    /** 来源类型：JD_IMPORT/POST_PROTOTYPE/ABILITY_TAG/LEARNING_RESOURCE/MANUAL_TEXT/CONTEST_EVIDENCE */
    private String sourceType;

    /** 来源业务ID */
    private Long sourceRefId;

    /** 文档标题 */
    private String title;

    /** 文档完整内容 */
    private String content;

    /** 结构化元数据JSON */
    private String metadataJson;

    /** 文档状态：ACTIVE/INACTIVE */
    private String docStatus;

    /** 分块数量 */
    private Integer chunkCount;

    /** 最后索引时间 */
    private LocalDateTime lastIndexedTime;

    /** 内容版本号 */
    private Long contentRevision;

    /** 内容哈希值 */
    private String contentHash;

    /** 已索引版本号 */
    private Long indexedRevision;

    /** 规范化内容哈希（仅用于比较） */
    private String canonicalContentHash;

    /** 规范化来源分组：POST_REQUIREMENT/EVIDENCE/LEARNING */
    private String canonicalSourceGroup;

    /** 生成嵌入的模型名 */
    private String embeddingModel;

    /** 嵌入维度 */
    private Integer embeddingDimension;

    /** 索引状态：PENDING/INDEXING/INDEXED/FAILED/UNKNOWN */
    private String indexingStatus;

    /** 索引错误信息 */
    private String indexingError;

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
