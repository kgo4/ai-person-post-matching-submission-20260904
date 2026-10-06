package com.example.matching.ai.service;

import java.util.List;

/**
 * 向量嵌入服务接口
 */
public interface VectorEmbeddingService {

    /**
     * 将文本向量化
     *
     * @param text 输入文本
     * @return 向量数组
     */
    List<Float> embed(String text);

    /**
     * 批量文本向量化
     *
     * @param texts 输入文本列表
     * @return 向量数组列表
     */
    List<List<Float>> embedBatch(List<String> texts);

    /**
     * 计算两个向量的余弦相似度
     *
     * @param vectorA 向量A
     * @param vectorB 向量B
     * @return 相似度值
     */
    Float cosineSimilarity(List<Float> vectorA, List<Float> vectorB);
}
