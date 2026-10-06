package com.example.matching.service.rag;

import java.util.EnumSet;
import java.util.Set;

/**
 * RAG 场景策略。调用方不能通过请求参数扩大这里定义的允许层范围。
 */
public final class RagScenarioPolicy {

    private RagScenarioPolicy() {
    }

    public static Set<RagKnowledgeLayer> allowedLayers(RagScenarioEnum scenario) {
        if (scenario == null) {
            return Set.of();
        }
        return switch (scenario) {
            // 岗位演化需要同时读取岗位/JD事实、领域资料和外部趋势；
            // 正式匹配排名仍不使用 RAG，FACT 仅作为演化证据上下文。
            case POST_EVOLUTION -> EnumSet.of(RagKnowledgeLayer.FACT,
                    RagKnowledgeLayer.TREND, RagKnowledgeLayer.DOMAIN);
            case KNOWLEDGE_QA -> EnumSet.of(RagKnowledgeLayer.TREND, RagKnowledgeLayer.DOMAIN);
            case EVIDENCE_TRACE, INTERVIEW_FOLLOWUP, ABILITY_HALLUCINATION ->
                    EnumSet.of(RagKnowledgeLayer.EVIDENCE);
            case EVIDENCE_NARRATIVE, MATCHING_ANALYSIS ->
                    EnumSet.of(RagKnowledgeLayer.FACT, RagKnowledgeLayer.EVIDENCE);
            case REPORT_GENERATION, MATCH_GAP_DIAGNOSIS ->
                    EnumSet.of(RagKnowledgeLayer.FACT, RagKnowledgeLayer.EVIDENCE,
                            RagKnowledgeLayer.DOMAIN);
            case LEARNING_RECOMMENDATION, LEARNING_PATH, AI_LEARNING_SUGGESTION ->
                    EnumSet.of(RagKnowledgeLayer.DOMAIN);
            case JD_ABILITY_EXTRACT -> EnumSet.of(RagKnowledgeLayer.FACT);
            case COMPANY_POST_WEIGHT_GENERATION -> EnumSet.of(RagKnowledgeLayer.FACT,
                    RagKnowledgeLayer.TREND, RagKnowledgeLayer.DOMAIN);
            case RESUME_ABILITY_EXTRACT -> EnumSet.of(RagKnowledgeLayer.EVIDENCE);
        };
    }

    /** 正式匹配分由结构化主数据计算，RAG 没有正式排名策略。 */
    public static boolean usesRagForFormalRanking() {
        return false;
    }
}
