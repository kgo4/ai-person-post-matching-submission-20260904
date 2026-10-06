package com.example.matching.agent.dto;

import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

/**
 * 匹配分析Agent结果DTO
 *
 * @author system
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class MatchingAnalysisAgentResult extends AgentRunResult {
    /** 建议的LLM分数 */
    private BigDecimal suggestedLlmScore;

    /** 结论 */
    private String conclusion;

    /** 优势列表 */
    private List<String> strengths;

    /** 差距列表 */
    private List<String> gaps;

    /** 风险信号列表 */
    private List<String> riskSignals;

    /** 人工关注点列表 */
    private List<String> humanAttentionPoints;

    /** 维度评分列表 */
    private List<Map<String, Object>> dimensionScores;

    /** 结构化结论（方案第十三章：abilityTagId/sourceRefs/graphNodeKeys 回链校验） */
    private List<AgentFinding> findings;

    /** 建议（3-5 条可执行建议） */
    private List<String> suggestions;

    /** 打分依据（{factor, direction, impact, reason, factRefs}） */
    private List<Map<String, Object>> scoreReasons;

    /** 证据分析（{ability, confidence, fusedLevel, sources, conflict}） */
    private List<Map<String, Object>> evidenceAnalysis;
}
