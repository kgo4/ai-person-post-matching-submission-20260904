package com.example.matching.dto.post.api;

import java.io.Serializable;
import java.util.List;

/**
 * 创建岗位趋势解析任务。
 * <p>
 * 材料由既有上传接口（{@code /api/post/evolution/sources/**}）落库并返回 {@code documentId}，
 * 本请求只引用已索引的文档，不重复上传。
 *
 * @param taskName         任务名；为空时后端按日期生成
 * @param sourceDocumentIds 已索引的知识源文档ID
 * @param sourceCategories 材料类别（POLICY_DOCUMENT / MARKET_REPORT / OCCUPATION_STANDARD …），用于诊断展示
 */
public record TrendTaskCreateRequest(
        String taskName,
        List<Long> sourceDocumentIds,
        List<String> sourceCategories) implements Serializable {
}
