package com.example.matching.dto.rag;

import lombok.Data;

/**
 * 知识分块搜索结果DTO
 *
 * @author system
 */
@Data
public class KnowledgeChunkResultDTO {

    /** 分块ID */
    private Long chunkId;

    /** 文档ID */
    private Long documentId;

    /** 文档标题 */
    private String documentTitle;

    /** 来源类型 */
    private String sourceType;

    /** 分块文本 */
    private String chunkText;

    /** 相似度分数 */
    private Float score;

    /** 分块索引 */
    private Integer chunkIndex;
}
