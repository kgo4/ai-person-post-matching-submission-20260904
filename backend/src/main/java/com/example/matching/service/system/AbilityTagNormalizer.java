package com.example.matching.service.system;

/**
 * 能力标签名称规范化接口
 * <p>
 * 将 AI 提取的原始能力名称规范化为标准标签名称，
 * 避免因表达差异导致标签爆炸。
 * <p>
 * 示例：
 * - "熟练掌握 Spring Boot 开发能力" -> "Spring Boot"
 * - "具备微服务项目经验" -> "微服务架构"
 * - "负责 Vue3 前端页面开发" -> "Vue"
 * - "AI 大模型 RAG 项目经验" -> "RAG"
 *
 * @author system
 */
public interface AbilityTagNormalizer {

    /**
     * 规范化标签名称
     *
     * @param rawName 原始标签名称（AI 提取的原始文本）
     * @return 规范化后的标签名称
     */
    String normalize(String rawName);

    /**
     * 判断是否是低质量标签名称
     *
     * @param normalizedName 规范化后的标签名称
     * @return true 表示低质量，应拒绝或进入候选池
     */
    boolean isLowQualityName(String normalizedName);

    /**
     * 判断是否是句子型标签（不是能力名）
     *
     * @param normalizedName 规范化后的标签名称
     * @return true 表示是句子，应拒绝
     */
    boolean isSentenceLike(String normalizedName);

    /**
     * 获取标签质量评分（0-100）
     *
     * @param normalizedName 规范化后的标签名称
     * @return 质量评分，越高越好
     */
    int getQualityScore(String normalizedName);
}
