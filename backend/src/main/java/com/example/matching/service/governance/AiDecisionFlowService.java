package com.example.matching.service.governance;

import java.util.List;

/**
 * AI决策分流服务接口
 * <p>
 * 职责：根据AI生成内容的风险等级，决定是自动写入、人工确认还是拒绝。
 * 只负责"分流判断"，不负责具体写库。
 *
 * @author system
 */
public interface AiDecisionFlowService {

    /**
     * 评估AI生成内容的风险等级
     *
     * @param request 请求
     * @return 评估结果
     */
    AiDecisionFlowResult evaluate(AiDecisionFlowRequest request);

    /**
     * 决策枚举
     */
    enum Decision {
        /** 自动写入 */
        AUTO_APPLY,
        /** 人工确认 */
        HUMAN_REVIEW,
        /** 拒绝 */
        REJECT
    }

    /**
     * 风险等级枚举
     */
    enum RiskLevel {
        /** 低风险 */
        LOW,
        /** 中风险 */
        MEDIUM,
        /** 高风险 */
        HIGH
    }

    /**
     * AI决策分流请求
     */
    @lombok.Data
    class AiDecisionFlowRequest {
        /** 场景 */
        private String scenario;
        /** 声明类型 */
        private String claimType;
        /** 声明内容 */
        private String claimText;
        /** 证据文本 */
        private String evidenceText;
        /** 来源引用列表 */
        private List<String> sourceRefs;
        /** 置信度分数 (0-100) */
        private Integer confidenceScore;
        /** 目标实体类型 */
        private String targetEntityType;
        /** 目标实体ID */
        private Long targetEntityId;
        /** 候选数据 */
        private Object candidatePayload;
    }

    /**
     * AI决策分流结果
     */
    @lombok.Data
    class AiDecisionFlowResult {
        /** 决策 */
        private Decision decision;
        /** 风险等级 */
        private RiskLevel riskLevel;
        /** 原因列表 */
        private List<String> reasons;
        /** Harness判定 */
        private String harnessDecision;
        /** 来源是否有效 */
        private Boolean sourceRefValid;
    }
}
