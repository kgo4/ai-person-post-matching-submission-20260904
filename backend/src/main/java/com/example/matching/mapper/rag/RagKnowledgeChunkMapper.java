package com.example.matching.mapper.rag;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.example.matching.entity.rag.RagKnowledgeChunk;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * RAG知识分块 Mapper
 */
@Mapper
public interface RagKnowledgeChunkMapper extends BaseMapper<RagKnowledgeChunk> {

    @Select("""
            <script>
            SELECT *
            FROM rag_knowledge_chunk
            WHERE chunk_status = 'ACTIVE'
              AND (
                <foreach collection="bigrams" item="bigram" separator=" OR ">
                  chunk_text LIKE CONCAT('%', #{bigram}, '%')
                </foreach>
              )
            LIMIT #{limit}
            </script>
            """)
    List<RagKnowledgeChunk> findActiveByKeywordBigrams(
            @Param("bigrams") List<String> bigrams,
            @Param("limit") int limit);
}
