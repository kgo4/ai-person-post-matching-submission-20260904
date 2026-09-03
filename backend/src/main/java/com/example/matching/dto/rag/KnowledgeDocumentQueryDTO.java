package com.example.matching.dto.rag;

import lombok.Data;

/**
 * 知识文档查询DTO
 *
 * @author system
 */
@Data
public class KnowledgeDocumentQueryDTO {

    /** 来源类型 */
    private String sourceType;

    /** 文档状态 */
    private String docStatus;

    /** 标题模糊搜索 */
    private String title;
}
