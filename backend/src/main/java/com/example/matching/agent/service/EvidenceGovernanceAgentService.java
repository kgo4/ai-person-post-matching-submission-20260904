package com.example.matching.agent.service;

import com.example.matching.agent.dto.EvidenceGovernanceAgentRequest;
import com.example.matching.agent.dto.EvidenceGovernanceAgentResult;

/**
 * 证据治理Agent服务接口
 *
 * @author system
 */
public interface EvidenceGovernanceAgentService {

    /**
     * 审核证据
     *
     * @param request 请求
     * @return 审核结果
     */
    EvidenceGovernanceAgentResult review(EvidenceGovernanceAgentRequest request);
}
