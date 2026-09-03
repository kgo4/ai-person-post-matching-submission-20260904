package com.example.matching.service.rag.impl;

import com.example.matching.service.rag.KnowledgeSearchHit;
import com.example.matching.service.rag.RagContextService;
import com.example.matching.service.rag.RagRetrievalRequest;
import com.example.matching.service.rag.RagRetrievalResult;
import com.example.matching.service.rag.RagRetrievalService;
import com.example.matching.service.rag.RagScenarioEnum;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * RAG 上下文服务兼容门面（已废弃）。
 * <p>
 * 仅保留历史调用方兼容，检索逻辑统一委托 {@link RagRetrievalService}：
 * <pre>
 *   业务调用方 -> RagRetrievalService -> Volcengine / MySQL / Hybrid Provider
 * </pre>
 * 不再包含 provider 选择、topK 截断或 fallback 逻辑。
 */
@Slf4j
@Service
@Deprecated
public class RagContextServiceImpl implements RagContextService {

    private final RagRetrievalService ragRetrievalService;

    public RagContextServiceImpl(RagRetrievalService ragRetrievalService) {
        this.ragRetrievalService = ragRetrievalService;
    }

    @Override
    @Transactional(readOnly = true)
    public String retrieveContext(String query, String scenario, int topK) {
        if (query == null || query.isBlank()) {
            return "";
        }
        RagScenarioEnum scenarioEnum = resolveScenario(scenario);
        if (scenarioEnum == null) {
            log.warn("未知RAG场景: {}, 返回空上下文", scenario);
            return "";
        }
        return ragRetrievalService.retrieveContext(query, scenarioEnum, topK);
    }

    @Override
    @Transactional(readOnly = true)
    public List<KnowledgeSearchHit> retrieveHits(String query, String scenario, int topK) {
        if (query == null || query.isBlank()) {
            return List.of();
        }
        RagScenarioEnum scenarioEnum = resolveScenario(scenario);
        if (scenarioEnum == null) {
            log.warn("未知RAG场景: {}, 返回空上下文", scenario);
            return List.of();
        }
        RagRetrievalResult result = ragRetrievalService.retrieve(RagRetrievalRequest.builder()
                .queryText(query)
                .scenario(scenarioEnum)
                .topK(topK)
                .build());
        if (result == null || !result.hasHits()) {
            return List.of();
        }
        List<KnowledgeSearchHit> hits = new ArrayList<>();
        for (RagRetrievalResult.RagHit ragHit : result.getHits()) {
            hits.add(new KnowledgeSearchHit(
                    ragHit.getChunkId() != null ? "mysql:" + ragHit.getChunkId() : null,
                    ragHit.getDocumentId() != null ? "mysql-doc:" + ragHit.getDocumentId() : null,
                    ragHit.getSourceType(),
                    ragHit.getTitle(),
                    ragHit.getContent(),
                    (float) ragHit.getScore(),
                    new LinkedHashMap<>(),
                    ragHit.getNormalizedScore(),
                    ragHit.getScoreSemantics(),
                    null,
                    null));
        }
        return hits;
    }

    @Override
    @Transactional(readOnly = true)
    public List<Long> retrieveChunkIds(String query, String scenario, int topK) {
        if (query == null || query.isBlank()) {
            return List.of();
        }
        return retrieveHits(query, scenario, topK).stream()
                .map(KnowledgeSearchHit::mysqlChunkIdOrNull)
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
    }

    /**
     * 历史场景字符串兼容映射：仅保留兼容性，新调用方应直接使用 {@link RagScenarioEnum}。
     */
    private RagScenarioEnum resolveScenario(String scenario) {
        if (scenario == null || scenario.isBlank()) {
            return null;
        }
        String normalizedScenario = switch (scenario) {
            // Legacy names still used by callers that have not moved to RagRetrievalService.
            case "ABILITY_TAG" -> RagScenarioEnum.JD_ABILITY_EXTRACT.name();
            case "ABILITY_EVIDENCE" -> RagScenarioEnum.EVIDENCE_TRACE.name();
            // RagScoreService（匹配 L2 RAG 评分）历史使用 "matching"，映射到匹配分析场景
            case "matching" -> RagScenarioEnum.MATCHING_ANALYSIS.name();
            default -> scenario;
        };
        if (!normalizedScenario.equals(scenario)) {
            log.info("Mapping legacy RAG scenario {} to {}", scenario, normalizedScenario);
        }
        try {
            return RagScenarioEnum.valueOf(normalizedScenario);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
