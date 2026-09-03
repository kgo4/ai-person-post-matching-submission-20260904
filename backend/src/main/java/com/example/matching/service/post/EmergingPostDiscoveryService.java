package com.example.matching.service.post;

import com.example.matching.dto.post.EmergingPostDiscoveryDTO;

import java.util.List;

/**
 * 新兴岗位发现服务接口
 * <p>
 * 基于向量相似度和时间窗口，从RAG知识库中识别
 * "被频繁提及但尚未标准化"的能力标签组合，
 * 主动发现市场上的新兴岗位。
 */
public interface EmergingPostDiscoveryService {

    /**
     * 发现潜在的新兴岗位
     * <p>
     * 分析逻辑：
     * 1. 从RAG知识库中提取最近导入的JD文档
     * 2. 使用AI分析这些JD中频繁出现但系统岗位库中未覆盖的能力组合
     * 3. 基于能力组合的共现频率和新颖度评分
     * 4. 返回候选新兴岗位列表
     *
     * @param limit 返回结果数量限制
     * @return 候选新兴岗位列表
     */
    List<EmergingPostDiscoveryDTO> discoverEmergingPosts(int limit);

    /**
     * 获取新兴岗位市场洞察
     * <p>
     * 返回当前市场上的热门能力标签、新兴技术趋势等信息。
     *
     * @return 市场洞察数据
     */
    EmergingPostDiscoveryDTO.MarketInsight getMarketInsight();
}
