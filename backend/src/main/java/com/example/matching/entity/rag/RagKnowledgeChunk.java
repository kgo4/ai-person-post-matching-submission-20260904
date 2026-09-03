package com.example.matching.entity.rag;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * RAG知识分块实体
 *
 * @author system
 */
@Data
@EqualsAndHashCode(callSuper = false)
@TableName("rag_knowledge_chunk")
public class RagKnowledgeChunk implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 主键，自增 */
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /** 知识文档ID */
    private Long documentId;

    /** 分块在文档中的索引 */
    private Integer chunkIndex;

    /** 分块文本 */
    private String chunkText;

    /** 嵌入向量JSON数组 */
    private String embeddingVector;

    /** 估算token数量 */
    private Integer tokenCount;

    /** 分块元数据JSON */
    private String metadataJson;

    /** 分块状态：ACTIVE/INACTIVE */
    private String chunkStatus;

    /** 文档版本号 */
    private Long documentRevision;

    /** 是否为当前版本：0否，1是 */
    private Integer isCurrent;

    /** 分块配置名：JD/EVIDENCE/LEARNING/GENERAL */
    private String chunkProfile;

    /** 创建时间 */
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdTime;
}
