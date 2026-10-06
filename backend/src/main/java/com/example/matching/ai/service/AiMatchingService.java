package com.example.matching.ai.service;

import java.util.List;
import java.util.Map;

/**
 * AI匹配服务接口
 */
public interface AiMatchingService {

    /**
     * 执行AI人岗匹配
     */
    List<Map<String, Object>> executeMatching(Long postId, List<Long> empIds, String strategy);

    /**
     * 生成AI分析报告（JSON字符串）
     */
    String generateAnalysisReport(Long matchingRecordId);

    /**
     * 生成结构化AI评分
     *
     * @param matchingRecordId 匹配记录ID
     * @return 结构化结果，包含 aiScore(BigDecimal)、report(String)、conclusion(String)
     */
    Map<String, Object> generateStructuredScore(Long matchingRecordId);
}
