package com.example.matching.agent.service;

import com.example.matching.agent.dto.PostAbilityAgentRequest;
import com.example.matching.agent.dto.PostAbilityAgentResult;
import com.example.matching.agent.dto.post.PostAbilityExtractRequest;
import com.example.matching.agent.dto.post.PostAbilityExtractionResult;

/**
 * 岗位能力Agent服务接口
 *
 * @author system
 */
public interface PostAbilityAgentService {

    /**
     * 分析岗位能力模型（解释已有模型）
     *
     * @param request 请求
     * @return 分析结果
     */
    PostAbilityAgentResult analyze(PostAbilityAgentRequest request);

    /**
     * 从来源材料提取岗位能力声明
     * <p>
     * 职责：从指定来源材料中提取岗位能力标签，返回统一格式的 claim 列表。
     * 每个 claim 都必须包含 evidenceText 和 sourceRefs。
     *
     * @param request 提取请求（包含 sourceText）
     * @return 提取结果
     */
    PostAbilityExtractionResult extractAbilities(PostAbilityExtractRequest request);
}
