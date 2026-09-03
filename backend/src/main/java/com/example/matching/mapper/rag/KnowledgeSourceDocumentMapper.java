package com.example.matching.mapper.rag;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.example.matching.entity.rag.KnowledgeSourceDocument;
import org.apache.ibatis.annotations.Mapper;

/**
 * 知识来源文档 Mapper
 *
 * @author system
 */
@Mapper
public interface KnowledgeSourceDocumentMapper extends BaseMapper<KnowledgeSourceDocument> {
}
