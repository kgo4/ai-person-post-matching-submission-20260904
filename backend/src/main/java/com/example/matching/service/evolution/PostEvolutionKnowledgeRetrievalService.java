package com.example.matching.service.evolution;

import java.util.List;

/**
 * 岗位演化知识检索服务接口
 * <p>
 * 负责从云知识库和行业白皮书中检索与岗位演化相关的证据片段。
 * 检索结果携带统一 sourceRef，供 Agent 和 Harness 使用。
 *
 * @author system
 */
public interface PostEvolutionKnowledgeRetrievalService {

    /**
     * 检索结果
     */
    record RetrievalResult(
            String chunkCode,
            String chunkText,
            String sectionTitle,
            String chunkType,
            String sourceRef,
            Double relevanceScore,
            String sourceUrl
    ) {
        public RetrievalResult(String chunkCode, String chunkText, String sectionTitle,
                               String chunkType, String sourceRef, Double relevanceScore) {
            this(chunkCode, chunkText, sectionTitle, chunkType, sourceRef, relevanceScore, null);
        }
    }

    /**
     * 检索请求
     */
    record RetrievalRequest(
            Long postId,
            String postName,
            String businessDomain,
            String industry,
            List<String> keywords,
            List<String> knowledgeBaseScope,
            int maxResults
    ) {}

    /**
     * 检索岗位相关的知识片段
     *
     * @param request 检索请求
     * @return 检索结果列表
     */
    List<RetrievalResult> retrieveForPost(RetrievalRequest request);

    /**
     * 检索行业白皮书中的趋势信号
     *
     * @param industry  行业
     * @param keywords  关键词
     * @param maxResults 最大结果数
     * @return 检索结果列表
     */
    List<RetrievalResult> retrieveIndustryTrends(String industry, List<String> keywords, int maxResults);

    /**
     * 检索云知识库中的业务变化信号
     *
     * @param businessDomain 业务领域
     * @param keywords       关键词
     * @param maxResults     最大结果数
     * @return 检索结果列表
     */
    List<RetrievalResult> retrieveBusinessChanges(String businessDomain, List<String> keywords, int maxResults);

    /**
     * 从已治理、非重复的市场 JD 中提取与指定岗位能力模型相交的原文线索。
     * 该方法只提供证据，不会写入岗位能力模型。
     */
    List<RetrievalResult> retrieveMarketEvolutionClues(Long postId, List<Long> abilityTagIds, int maxResults);
}
