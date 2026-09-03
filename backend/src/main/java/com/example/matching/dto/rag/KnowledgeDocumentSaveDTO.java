package com.example.matching.dto.rag;

import lombok.Data;

/**
 * 知识文档保存DTO
 *
 * @author system
 */
@Data
public class KnowledgeDocumentSaveDTO {

    /** 文档ID（更新时使用） */
    private Long id;

    /** 来源类型：MANUAL_TEXT 等 */
    private String sourceType;

    /** 来源业务ID */
    private Long sourceRefId;

    /** 文档标题 */
    private String title;

    /** 文档内容 */
    private String content;

    /** 结构化元数据JSON */
    private String metadataJson;
}
