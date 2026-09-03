package com.example.matching.service.rag;

import java.util.List;

/**
 * RAG上下文服务接口
 * <p>
 * 负责检索相关知识并格式化为prompt上下文。
 *
 * @author system
 */
public interface RagContextService {

    /**
     * 检索并格式化RAG上下文
     *
     * @param query    查询文本
     * @param scenario RAG场景
     * @param topK     返回数量
     * @return 格式化的上下文文本
     */
    String retrieveContext(String query, String scenario, int topK);

    List<KnowledgeSearchHit> retrieveHits(String query, String scenario, int topK);

    /**
     * 检索相关分块ID列表
     *
     * @param query    查询文本
     * @param scenario RAG场景
     * @param topK     返回数量
     * @return 分块ID列表
     */
    List<Long> retrieveChunkIds(String query, String scenario, int topK);
}
