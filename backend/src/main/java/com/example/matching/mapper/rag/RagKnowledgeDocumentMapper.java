package com.example.matching.mapper.rag;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.example.matching.entity.rag.RagKnowledgeDocument;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/**
 * RAG知识文档 Mapper
 */
@Mapper
public interface RagKnowledgeDocumentMapper extends BaseMapper<RagKnowledgeDocument> {

    /**
     * 取出文档并对其加行锁，供索引过程串行化使用。
     * <p>
     * 为什么需要：同一文档可能被两条链路并发索引 —— 上传链路自身会同步调
     * {@code indexKnowledgeSource}，而 {@code KnowledgeProjectionWorker} 也会因
     * outbox 记录在数秒内再跑一次。两个事务若都读到「当前修订版还没有分块」，
     * 就会双双执行 INSERT，撞上 {@code rag_knowledge_chunk} 的唯一键
     * {@code uk_doc_chunk_revision(document_id, chunk_index, document_revision)}。
     * <p>
     * 这里手写 SQL 而不用 {@code selectById}：一是要带 {@code FOR UPDATE}，
     * 二是 {@code @TableLogic} 的软删条件不会自动加进自定义 SQL，必须显式写。
     *
     * @param id 文档主键
     * @return 未删除的文档；不存在时返回 {@code null}
     */
    @Select("SELECT * FROM rag_knowledge_document WHERE id = #{id} AND is_deleted = 0 FOR UPDATE")
    RagKnowledgeDocument selectByIdForUpdate(@Param("id") Long id);
}
