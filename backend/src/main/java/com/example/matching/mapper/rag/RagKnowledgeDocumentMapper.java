package com.example.matching.mapper.rag;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.example.matching.entity.rag.RagKnowledgeDocument;
import org.apache.ibatis.annotations.Mapper;

/**
 * RAG知识文档 Mapper
 */
@Mapper
public interface RagKnowledgeDocumentMapper extends BaseMapper<RagKnowledgeDocument> {
}
