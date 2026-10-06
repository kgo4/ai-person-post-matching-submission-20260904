package com.example.matching.service.learning;

import com.example.matching.dto.learning.AiLearningSuggestionDTO;

import java.util.List;

/**
 * AI 学习建议服务接口
 * <p>
 * 保留现有确定性学习路径作为"底座"，新增 AI 增强层。
 * AI 只能基于系统检索到的资源生成学习建议，不能凭空编造资源或能力。
 * <p>
 * 流程：匹配差距诊断 -> 资源库检索 -> AI 生成学习建议 -> 系统校验 -> 返回结果
 *
 * @author system
 */
public interface AiLearningSuggestionService {

    /**
     * 生成 AI 学习建议
     * <p>
     * 1. 从匹配记录诊断能力差距
     * 2. 从资源库检索匹配资源
     * 3. 通过 RAG 检索相关上下文
     * 4. 调用 AI 生成学习建议
     * 5. 校验 AI 输出
     *
     * @param request 请求参数
     * @return AI 学习建议（已校验）
     */
    AiLearningSuggestionDTO.Response generateSuggestions(AiLearningSuggestionDTO.Request request);

    /**
     * 获取已缓存的 AI 学习建议
     *
     * @param matchingRecordId 匹配记录ID
     * @return 已缓存的建议（如果有）
     */
    List<AiLearningSuggestionDTO.Response> getCachedSuggestions(Long matchingRecordId);
}
