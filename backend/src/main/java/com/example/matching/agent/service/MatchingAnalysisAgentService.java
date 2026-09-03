package com.example.matching.agent.service;

import com.example.matching.agent.dto.MatchingAnalysisAgentRequest;
import com.example.matching.agent.dto.MatchingAnalysisAgentResult;

/**
 * 匹配分析Agent服务接口
 *
 * @author system
 */
public interface MatchingAnalysisAgentService {

    /**
     * 分析匹配
     *
     * @param request 请求
     * @return 分析结果
     */
    MatchingAnalysisAgentResult analyze(MatchingAnalysisAgentRequest request);
}
