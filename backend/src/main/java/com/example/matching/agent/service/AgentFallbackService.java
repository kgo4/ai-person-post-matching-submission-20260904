package com.example.matching.agent.service;

import com.example.matching.agent.dto.*;

/**
 * Agent降级服务接口
 *
 * @author system
 */
public interface AgentFallbackService {

    /**
     * 证据治理降级方案
     */
    EvidenceGovernanceAgentResult fallbackEvidenceGovernance(EvidenceGovernanceAgentRequest request);

    /**
     * 学习路径降级方案
     */
    LearningPathAgentResult fallbackLearningPath(AgentContextPackage context);

    /**
     * 匹配分析降级方案
     */
    MatchingAnalysisAgentResult fallbackMatchingAnalysis(AgentContextPackage context);
}
