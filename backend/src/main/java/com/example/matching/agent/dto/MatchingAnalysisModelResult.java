package com.example.matching.agent.dto;

import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

/**
 * LangChain4j structured output contract for matching analysis.
 *
 * <p>Keep every collection element a concrete class. LangChain4j 0.35.0
 * cannot generate output instructions for nested generic collection elements.
 */
@Data
public class MatchingAnalysisModelResult {

    private BigDecimal suggestedLlmScore;
    private String conclusion;
    private List<String> strengths;
    private List<String> gaps;
    private List<String> riskSignals;
    private List<String> humanAttentionPoints;
    private List<DimensionScore> dimensionScores;
    private List<AgentFinding> findings;
    /** 建议（3-5 条可执行建议） */
    private List<String> suggestions;
    /** 打分依据（每条含因素/方向/影响/理由/事实引用） */
    private List<ScoreReason> scoreReasons;
    /** 证据分析（基于 ragContext 的证据条目） */
    private List<EvidenceAnalysisItem> evidenceAnalysis;

    @Data
    public static class DimensionScore {
        private String dimension;
        private BigDecimal score;
        private BigDecimal weight;
    }

    @Data
    public static class ScoreReason {
        /** 因素名（如"能力模型分""向量语义分""AI建议分"或具体能力） */
        private String factor;
        /** 方向：'+' 正向 / '-' 负向 */
        private String direction;
        /** 影响幅度（0-100） */
        private BigDecimal impact;
        /** 打分理由 */
        private String reason;
        /** 事实引用（sourceRefs 中的来源） */
        private List<String> factRefs;
    }

    @Data
    public static class EvidenceAnalysisItem {
        /** 能力名 */
        private String ability;
        /** 证据置信度：高/中/低 */
        private String confidence;
        /** 融合等级（数值） */
        private Integer fusedLevel;
        /** 证据来源列表 */
        private List<String> sources;
        /** 冲突说明（无冲突可为空） */
        private String conflict;
    }
}
