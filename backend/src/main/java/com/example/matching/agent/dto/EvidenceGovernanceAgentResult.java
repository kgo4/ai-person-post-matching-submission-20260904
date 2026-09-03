package com.example.matching.agent.dto;

import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;
import java.util.List;

/**
 * 证据治理Agent结果DTO
 *
 * @author system
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class EvidenceGovernanceAgentResult extends AgentRunResult {
    /** 决策：PASS, REVIEW, BLOCK */
    private String decision;

    /** 风险等级：LOW, MEDIUM, HIGH */
    private String riskLevel;

    /** 支持分数 */
    private BigDecimal supportScore;

    /** 是否AI自证据 */
    private Boolean selfEvidence;

    /** 原因列表 */
    private List<String> reasons;

    /** 缺失证据列表 */
    private List<String> missingEvidence;

    /** 已接受的来源引用 */
    private List<AgentSourceRef> acceptedSourceRefs;

    /** 建议的人工审核操作 */
    private String suggestedHumanReviewAction;
}
