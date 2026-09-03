package com.example.matching.dto.rag;

import lombok.Data;

import java.util.List;

/**
 * 知识分块搜索DTO
 *
 * @author system
 */
@Data
public class KnowledgeChunkSearchDTO {

    /** 查询文本 */
    private String queryText;

    /** RAG场景 */
    private String scenario;

    /** 返回数量 */
    private Integer topK = 5;

    /** 来源类型过滤 */
    private List<String> sourceTypes;
}
