package com.example.matching.agent.service.impl;

import com.example.matching.agent.config.AgentToolProvider;
import com.example.matching.agent.config.LangChain4jAgentProperties;
import com.example.matching.agent.dto.AgentContextPackage;
import com.example.matching.agent.dto.MatchingAnalysisAgentRequest;
import com.example.matching.agent.dto.MatchingAnalysisAgentResult;
import com.example.matching.agent.dto.MatchingAnalysisModelResult;
import com.example.matching.agent.lc4j.MatchingAnalysisAiService;
import com.example.matching.agent.service.AgentContextPackageService;
import com.example.matching.agent.service.AgentFallbackService;
import com.example.matching.agent.service.MatchingAnalysisAgentService;
import com.example.matching.infrastructure.llm.LlmResponseParser;
import com.example.matching.service.kg.KnowledgeGraphQueryService;
import com.example.matching.service.rag.RagRetrievalRequest;
import com.example.matching.service.rag.RagRetrievalResult;
import com.example.matching.service.rag.RagRetrievalService;
import com.example.matching.service.rag.RagScenarioEnum;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * 匹配分析Agent服务实现
 * <p>
 * 使用LangChain4j AiServices + Tool编排
 *
 * @author system
 */
@Slf4j
@Service
public class MatchingAnalysisAgentServiceImpl extends AbstractAgentService implements MatchingAnalysisAgentService {

    private final LangChain4jAgentProperties properties;
    private final AgentContextPackageService contextPackageService;
    private final AgentFallbackService fallbackService;
    private final ObjectMapper objectMapper;
    private final LlmResponseParser llmResponseParser;
    private final RagRetrievalService ragRetrievalService;
    private final GroundedAgentOutputValidator outputValidator;
    private final com.example.matching.agent.service.AgentGraphContextAssembler agentGraphContextAssembler;
    private final MatchingAnalysisAiService matchingAnalysisAiService;

    public MatchingAnalysisAgentServiceImpl(
            LangChain4jAgentProperties properties,
            AgentContextPackageService contextPackageService,
            AgentFallbackService fallbackService,
            ObjectMapper objectMapper,
            LlmResponseParser llmResponseParser,
            RagRetrievalService ragRetrievalService,
            GroundedAgentOutputValidator outputValidator,
            AgentRunConfidencePolicy confidencePolicy,
            com.example.matching.agent.service.AgentGraphContextAssembler agentGraphContextAssembler,
            ObjectProvider<MatchingAnalysisAiService> aiServiceProvider) {
        super(confidencePolicy);
        this.properties = properties;
        this.contextPackageService = contextPackageService;
        this.fallbackService = fallbackService;
        this.objectMapper = objectMapper;
        this.llmResponseParser = llmResponseParser;
        this.ragRetrievalService = ragRetrievalService;
        this.outputValidator = outputValidator;
        this.agentGraphContextAssembler = agentGraphContextAssembler;
        this.matchingAnalysisAiService = aiServiceProvider.getIfAvailable();
    }

    @Override
    public MatchingAnalysisAgentResult analyze(MatchingAnalysisAgentRequest request) {
        AgentContextPackage context = contextPackageService.buildForMatchingRecord(request.getMatchingRecordId());

        if (!properties.isEnabled() || matchingAnalysisAiService == null) {
            log.info("LangChain4j未启用，使用降级方案");
            return fallbackService.fallbackMatchingAnalysis(context);
        }

        return runWithFallback(() -> {
            // 图谱预构建：服务端一次性构建完整受限子图（含预计算匹配/差距/证据/白名单）
            com.example.matching.agent.dto.graph.AgentGraphContext graphContext =
                    agentGraphContextAssembler.buildForMatching(context.getEmpId(), context.getPostId());
            context.setGraphContext(graphContext);

            String ragQuery = buildRagQuery(context);
            String ragContext = "";
            Object ragStructuredHits = null;
            try {
                RagRetrievalResult ragResult = ragRetrievalService.retrieve(
                        RagRetrievalRequest.builder()
                                .queryText(ragQuery)
                                .scenario(RagScenarioEnum.MATCHING_ANALYSIS)
                                .topK(5)
                                .build());
                if (ragResult != null && ragResult.getContextText() != null && !ragResult.getContextText().isBlank()) {
                    ragContext = ragResult.getContextText();
                }
                if (ragResult != null && ragResult.hasHits()) {
                    List<Map<String, Object>> hits = new ArrayList<>();
                    for (RagRetrievalResult.RagHit hit : ragResult.getHits()) {
                        Map<String, Object> hitMap = new LinkedHashMap<>();
                        hitMap.put("chunkId", hit.getChunkId());
                        hitMap.put("sourceType", hit.getSourceType());
                        hitMap.put("title", hit.getTitle());
                        hitMap.put("normalizedScore", hit.getNormalizedScore());
                        hitMap.put("scoreSemantics", hit.getScoreSemantics());
                        hits.add(hitMap);
                    }
                    ragStructuredHits = hits;
                }
            } catch (Exception e) {
                log.warn("RAG context retrieval failed, proceeding without it: {}", e.getMessage());
            }

            Map<String, Object> promptContext = new LinkedHashMap<>();
            promptContext.put("agentContext", context);
            promptContext.put("graphContext", graphContext);
            if (ragContext != null && !ragContext.isBlank()) {
                promptContext.put("ragContext", ragContext);
            }
            if (ragStructuredHits != null) {
                promptContext.put("ragStructuredHits", ragStructuredHits);
            }
            String contextJson = objectMapper.writeValueAsString(promptContext);
            MatchingAnalysisModelResult modelResult = AgentToolProvider.withScope(
                    () -> matchingAnalysisAiService.analyze(contextJson));
            if (modelResult == null) {
                throw new IllegalStateException("Matching analysis returned no structured result");
            }
            MatchingAnalysisAgentResult result = toAgentResult(modelResult);

            Optional<MatchingAnalysisAgentResult> validated = outputValidator.validateMatching(result, context);
            if (validated.isEmpty()) {
                throw new IllegalStateException("Matching analysis validation failed");
            }
            result = validated.get();

            if (result.getSuggestedLlmScore() != null) {
                result.setSuggestedLlmScore(
                        result.getSuggestedLlmScore().max(BigDecimal.ZERO).min(new BigDecimal("100")));
            }

            String rawOutput = objectMapper.writeValueAsString(result);
            log.info("匹配分析Agent完成: conclusion={}", result.getConclusion());
            return finalizeRun(result, result.getSourceRefs(), false, rawOutput);
        }, e -> {
            log.error("LangChain4j调用失败，使用降级方案", e);
            return fallbackService.fallbackMatchingAnalysis(context);
        });
    }

    private String buildRagQuery(AgentContextPackage context) {
        StringBuilder query = new StringBuilder();
        query.append("employeeId=").append(context.getEmpId());
        query.append(" postId=").append(context.getPostId());
        if (context.getPostRequirements() != null) {
            String requirementNames = context.getPostRequirements().stream()
                    .map(r -> r.abilityName())
                    .filter(n -> n != null && !n.isBlank())
                    .limit(10)
                    .collect(Collectors.joining(" "));
            if (!requirementNames.isBlank()) {
                query.append(" ").append(requirementNames);
            }
        }
        return query.toString();
    }

    private MatchingAnalysisAgentResult toAgentResult(MatchingAnalysisModelResult modelResult) {
        MatchingAnalysisAgentResult result = new MatchingAnalysisAgentResult();
        result.setSuggestedLlmScore(modelResult.getSuggestedLlmScore());
        result.setConclusion(modelResult.getConclusion());
        result.setStrengths(modelResult.getStrengths());
        result.setGaps(modelResult.getGaps());
        result.setRiskSignals(modelResult.getRiskSignals());
        result.setHumanAttentionPoints(modelResult.getHumanAttentionPoints());
        result.setFindings(modelResult.getFindings());
        result.setSuggestions(modelResult.getSuggestions());
        result.setScoreReasons(modelResult.getScoreReasons() != null
                ? modelResult.getScoreReasons().stream().map(r -> {
                    Map<String, Object> reason = new LinkedHashMap<>();
                    reason.put("factor", r.getFactor());
                    reason.put("direction", r.getDirection());
                    reason.put("impact", r.getImpact());
                    reason.put("reason", r.getReason());
                    reason.put("factRefs", r.getFactRefs());
                    return reason;
                }).toList()
                : null);
        result.setEvidenceAnalysis(modelResult.getEvidenceAnalysis() != null
                ? modelResult.getEvidenceAnalysis().stream().map(e -> {
                    Map<String, Object> item = new LinkedHashMap<>();
                    item.put("ability", e.getAbility());
                    item.put("confidence", e.getConfidence());
                    item.put("fusedLevel", e.getFusedLevel());
                    item.put("sources", e.getSources());
                    item.put("conflict", e.getConflict());
                    return item;
                }).toList()
                : null);
        if (modelResult.getDimensionScores() != null) {
            result.setDimensionScores(modelResult.getDimensionScores().stream()
                    .map(score -> {
                        Map<String, Object> dimensionScore = new LinkedHashMap<>();
                        dimensionScore.put("dimension", score.getDimension());
                        dimensionScore.put("score", score.getScore());
                        dimensionScore.put("weight", score.getWeight());
                        return dimensionScore;
                    })
                    .toList());
        }
        return result;
    }

}
