package com.example.matching.service.rag;

import java.util.List;

/**
 * RAG 统一检索服务接口
 * <p>
 * 职责：提供结构化的知识检索结果，供各业务场景使用。
 * <p>
 * 核心原则：
 * - RAG 只负责：文本依据检索、上下文组装、引用追溯、幻觉防控辅助
 * - 不替代能力标签匹配
 * - 不替代 Milvus/Zilliz 的人岗向量召回
 * - 不替代证据中心的可信来源判断
 * - 不直接决定最终录用/推荐结果
 */
public interface RagRetrievalService {

    /**
     * 统一检索入口
     *
     * @param request 检索请求
     * @return 结构化检索结果
     */
    RagRetrievalResult retrieve(RagRetrievalRequest request);

    /**
     * 简化检索入口（使用场景默认配置）
     *
     * @param queryText 查询文本
     * @param scenario  场景枚举
     * @return 结构化检索结果
     */
    RagRetrievalResult retrieve(String queryText, RagScenarioEnum scenario);

    /**
     * 简化检索入口：直接返回拼接后的上下文文本（topK 为 0 或负数时使用场景默认值）。
     *
     * @param queryText 查询文本
     * @param scenario  场景枚举
     * @param topK      返回数量（&lt;=0 时使用场景默认值）
     * @return 拼接后的上下文文本
     */
    String retrieveContext(String queryText, RagScenarioEnum scenario, int topK);
}
