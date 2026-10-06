package com.example.matching.service.rag;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.example.matching.dto.rag.KnowledgeChunkResultDTO;
import com.example.matching.dto.rag.KnowledgeChunkSearchDTO;
import com.example.matching.dto.rag.KnowledgeDocumentQueryDTO;
import com.example.matching.dto.rag.KnowledgeDocumentSaveDTO;
import com.example.matching.entity.rag.RagKnowledgeDocument;

import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 知识文档服务接口
 *
 * @author system
 */
public interface KnowledgeDocumentService {

    /**
     * 创建或更新知识文档
     *
     * @param dto 保存DTO
     * @return 文档实体
     */
    RagKnowledgeDocument saveDocument(KnowledgeDocumentSaveDTO dto);

    /**
     * 分页查询知识文档
     *
     * @param page  分页参数
     * @param query 查询条件
     * @return 分页结果
     */
    IPage<RagKnowledgeDocument> pageDocuments(Page<RagKnowledgeDocument> page, KnowledgeDocumentQueryDTO query);

    /**
     * 获取文档详情
     *
     * @param id 文档ID
     * @return 文档实体
     */
    RagKnowledgeDocument getDocumentById(Long id);

    /**
     * 对文档进行分块和向量化索引
     *
     * @param documentId 文档ID
     * @return 分块数量
     */
    int indexDocument(Long documentId);

    /**
     * 批量索引知识文档。
     *
     * @param sourceType     来源类型，为空时索引全部来源
     * @param onlyUnindexed 是否只索引未索引文档
     * @param limit         最大处理数量
     * @return documentCount/chunkCount
     */
    Map<String, Object> indexDocuments(String sourceType, boolean onlyUnindexed, int limit);

    /**
     * 从现有数据回填知识文档
     *
     * @param sourceType 来源类型：POST_PROTOTYPE/ABILITY_TAG/LEARNING_RESOURCE/JD_IMPORT/CONTEST_EVIDENCE
     * @param limit      最大回填数量
     * @return 创建的文档数量
     */
    int backfillDocuments(String sourceType, int limit);

    /**
     * 搜索知识分块
     *
     * @param dto 搜索DTO
     * @return 搜索结果列表
     */
    List<KnowledgeChunkResultDTO> searchChunks(KnowledgeChunkSearchDTO dto);

    /**
     * 根据来源类型和引用ID查询已存在的文档ID，未找到返回 null。
     */
    Long findExistingDocumentId(String sourceType, Long sourceRefId);

    /**
     * 列出所有活跃文档（用于批量同步）。
     */
    List<RagKnowledgeDocument> listAllActiveDocuments(int limit);

    /**
     * 按知识层读取活跃文档，供趋势发现等知识资产业务使用；不允许业务直接访问 RAG Mapper。
     */
    List<RagKnowledgeDocument> listActiveDocumentsByLayers(Set<RagKnowledgeLayer> layers, int limit);

    /**
     * 删除知识文档及其全部分块与向量投影。
     *
     * <p>供「材料管理里删除一份权威材料」使用。文档走软删除（保留审计痕迹与去重指纹），
     * 分块与向量必须**物理**清理 —— 否则脏片段仍会被关键词检索与向量召回命中，
     * 用户删了材料却还能被解析引用，这是最坏的一种「删了但没删掉」。
     *
     * @param documentId 知识文档ID；不存在时静默返回（幂等，便于重试）
     * @return 实际清理的分块数
     */
    int deleteDocument(Long documentId);
}
