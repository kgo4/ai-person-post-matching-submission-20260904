package com.example.matching.service.rag.impl;

import com.example.matching.config.VolcengineKnowledgeBaseProperties;
import com.example.matching.service.rag.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * RAG 统一检索服务实现
 * <p>
 * 职责：提供结构化的知识检索结果，供各业务场景使用。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RagRetrievalServiceImpl implements RagRetrievalService {

    private final MysqlKnowledgeSearchProvider mysqlKnowledgeSearchProvider;
    private final VolcengineKnowledgeSearchProvider volcengineKnowledgeSearchProvider;
    private final VolcengineKnowledgeBaseProperties knowledgeBaseProperties;
    private final RagQueryLogService ragQueryLogService;

    @org.springframework.beans.factory.annotation.Value("${rag.context.max-estimated-tokens:3500}")
    private int maxEstimatedTokens;

    @org.springframework.beans.factory.annotation.Value("${rag.context.max-chunks:8}")
    private int maxContextChunks;

    @Override
    public RagRetrievalResult retrieve(RagRetrievalRequest request) {
        long startTime = System.currentTimeMillis();

        RagScenarioEnum scenario = request.getScenario();
        String queryText = request.getQueryText();
        int requestedTopK = request.getTopK() != null ? request.getTopK() : scenario.getDefaultTopK();
        if (requestedTopK <= 0) {
            requestedTopK = scenario.getDefaultTopK();
        }
        int topK = Math.min(requestedTopK, maxContextChunks);

        if (queryText == null || queryText.isBlank()) {
            return buildEmptyResult(scenario, queryText, startTime);
        }

        try {
            // 执行检索
            KnowledgeSearchRequest searchRequest = new KnowledgeSearchRequest(
                    queryText, scenario.name(), topK, request.getSourceTypes());
            String mode = resolveProviderMode(request, scenario);

            List<KnowledgeSearchHit> hits;
            boolean fallbackUsed = false;

            if ("volcengine".equals(mode)) {
                hits = volcengineKnowledgeSearchProvider.search(searchRequest);
            } else if ("mysql".equals(mode)) {
                hits = mysqlKnowledgeSearchProvider.search(searchRequest);
            } else {
                // hybrid 模式：火山优先，但必须立即按来源白名单过滤；
                // 火山原始命中非空但过滤后为空时回退 MySQL，不允许直接返回空结果
                hits = volcengineKnowledgeSearchProvider.search(searchRequest);
                int rawHitCount = hits.size();
                hits = filterByRequestedSourceTypes(hits, request.getSourceTypes());
                hits = filterBySourceType(hits, scenario);
                if (hits.isEmpty()) {
                    log.info("RAG hybrid fallback: provider=volcengine rawHitCount={} allowedHitCount=0 "
                                    + "fallbackToMysql=true scenario={}",
                            rawHitCount, scenario.name());
                    hits = mysqlKnowledgeSearchProvider.search(searchRequest);
                    hits = filterByRequestedSourceTypes(hits, request.getSourceTypes());
                    hits = filterBySourceType(hits, scenario);
                    mode = "mysql";
                    fallbackUsed = true;
                } else {
                    mode = "volcengine";
                    log.info("RAG hybrid: provider=volcengine rawHitCount={} allowedHitCount={} "
                                    + "fallbackToMysql=false scenario={}",
                            rawHitCount, hits.size(), scenario.name());
                }
            }

            // 过滤来源类型
            hits = filterByRequestedSourceTypes(hits, request.getSourceTypes());
            hits = filterBySourceType(hits, scenario);

            // 应用相似度阈值
            hits = filterByMinSimilarity(hits, scenario, request);

            // 转换为结构化结果
            List<RagRetrievalResult.RagHit> ragHits = hits.stream()
                    .map(this::convertHit)
                    .collect(Collectors.toList());

            // 拼接上下文文本
            String contextText = buildContextText(ragHits);

            long latencyMs = System.currentTimeMillis() - startTime;

            // 记录日志
            Long logId = null;
            if (scenario.isLogEnabled()) {
                logId = saveQueryLog(scenario, queryText, mode, fallbackUsed, topK, ragHits, contextText, latencyMs);
            }

            return RagRetrievalResult.builder()
                    .scenario(scenario.name())
                    .queryText(queryText)
                    .providerMode(mode)
                    .fallbackUsed(fallbackUsed)
                    .hits(ragHits)
                    .contextText(contextText)
                    .logId(logId)
                    .latencyMs(latencyMs)
                    .build();

        } catch (Exception e) {
            // 修复：检索异常改为 ERROR 并带降级标记，避免与"确实无结果"混淆、
            // 掩盖 Milvus/火山故障造成的持续空上下文
            log.error("RAG检索失败(降级为空结果): scenario={}, error={}", scenario.name(), e.getMessage(), e);
            return buildEmptyResult(scenario, queryText, startTime);
        }
    }

    @Override
    public RagRetrievalResult retrieve(String queryText, RagScenarioEnum scenario) {
        return retrieve(RagRetrievalRequest.builder()
                .queryText(queryText)
                .scenario(scenario)
                .build());
    }

    @Override
    public String retrieveContext(String queryText, RagScenarioEnum scenario, int topK) {
        if (queryText == null || queryText.isBlank()) {
            return "";
        }
        RagRetrievalResult result = retrieve(RagRetrievalRequest.builder()
                .queryText(queryText)
                .scenario(scenario)
                .topK(topK)
                .build());
        return result != null ? result.getContextText() : "";
    }

    /**
     * 解析提供者模式
     */
    private String resolveProviderMode(RagRetrievalRequest request, RagScenarioEnum scenario) {
        if (request.getForceCloud() != null && request.getForceCloud() && scenario.isAllowCloud()) {
            return "volcengine";
        }
        String mode = knowledgeBaseProperties.getProviderMode();
        if (mode == null || mode.isBlank()) {
            return "hybrid";
        }
        return mode.toLowerCase();
    }

    private List<KnowledgeSearchHit> filterByMinSimilarity(List<KnowledgeSearchHit> hits,
                                                            RagScenarioEnum scenario,
                                                            RagRetrievalRequest request) {
        if (hits == null || hits.isEmpty()) return hits;
        double threshold = effectiveMinSimilarity(request, scenario);
        if (threshold <= 0d) return hits;
        return hits.stream()
                .filter(hit -> passesSimilarityThreshold(hit, threshold))
                .collect(Collectors.toList());
    }

    /**
     * M12：相似度阈值只作用于可解释的原始语义相似度（rawScore）。
     * RRF 只用于排序；RANK_BASED（火山 rank 分）或缺少原始语义分（纯关键词命中）
     * 不按相似度阈值过滤，避免把 rank 分当 similarity 误杀有效命中。
     */
    private boolean passesSimilarityThreshold(KnowledgeSearchHit hit, double threshold) {
        // RANK_BASED：归一化分数由排名推导（1-i/total），不具备相似度语义
        if ("RANK_BASED".equals(hit.normalizationMethod()) || hit.rawScore() == null) {
            log.debug("normalizationMethod={}, rawScore=null，跳过相似度阈值过滤: chunkId={}",
                    hit.normalizationMethod(), hit.chunkId());
            return true;
        }
        return hit.rawScore() >= threshold;
    }

    private double effectiveMinSimilarity(RagRetrievalRequest request, RagScenarioEnum scenario) {
        double requested = request.getMinSimilarity() == null ? 0d : request.getMinSimilarity();
        if (requested < 0d || requested > 1d) throw new IllegalArgumentException("minSimilarity must be in [0,1]");
        return Math.max(scenario.getMinSimilarity(), requested);
    }

    /**
     * 按来源类型过滤
     * <p>
     * 火山知识库命中（VOLCENGINE_KB）：仅 allowCloud 场景可采纳；命中若携带真实业务
     * 来源类型（metadata.originSourceType），按真实类型做精细过滤。
     */
    private List<KnowledgeSearchHit> filterBySourceType(List<KnowledgeSearchHit> hits, RagScenarioEnum scenario) {
        if (scenario.getAllowedSourceTypes() == null || scenario.getAllowedSourceTypes().length == 0) {
            return hits;
        }
        return hits.stream()
                .filter(hit -> isHitAllowed(hit, scenario))
                .collect(Collectors.toList());
    }

    private List<KnowledgeSearchHit> filterByRequestedSourceTypes(List<KnowledgeSearchHit> hits,
                                                                   List<String> sourceTypes) {
        if (hits == null || hits.isEmpty() || sourceTypes == null || sourceTypes.isEmpty()) {
            return hits;
        }
        return hits.stream().filter(hit -> {
            if (sourceTypes.contains(hit.sourceType())) {
                return true;
            }
            if (!"VOLCENGINE_KB".equals(hit.sourceType())) {
                return false;
            }
            Object origin = hit.metadata().get("originSourceType");
            return origin instanceof String originType && sourceTypes.contains(originType);
        }).collect(Collectors.toList());
    }

    private boolean isHitAllowed(KnowledgeSearchHit hit, RagScenarioEnum scenario) {
        if (!"VOLCENGINE_KB".equals(hit.sourceType())) {
            return scenario.isSourceTypeAllowed(hit.sourceType());
        }
        if (!scenario.isAllowCloud()) {
            return false;
        }
        Object origin = hit.metadata().get("originSourceType");
        if (origin instanceof String originType && !originType.isBlank()) {
            return scenario.isSourceTypeAllowed(originType);
        }
        return scenario.isSourceTypeAllowed("VOLCENGINE_KB");
    }

    /**
     * 转换命中结果
     */
    private RagRetrievalResult.RagHit convertHit(KnowledgeSearchHit hit) {
        return RagRetrievalResult.RagHit.builder()
                .chunkId(hit.mysqlChunkIdOrNull())
                .documentId(hit.documentIdOrNull())
                .sourceType(hit.sourceType())
                .sourceRefId(hit.sourceRefIdOrNull())
                .title(hit.title())
                .content(hit.content())
                .score(hit.effectiveScore())
                .normalizedScore(hit.normalizedScore())
                .scoreSemantics(hit.scoreSemantics())
                .build();
    }

    /**
     * 拼接上下文文本（受估算 token 预算约束，保留来源标注）
     */
    private String buildContextText(List<RagRetrievalResult.RagHit> hits) {
        if (hits == null || hits.isEmpty()) {
            return "";
        }
        List<KnowledgeSearchHit> searchHits = hits.stream()
                .map(hit -> new KnowledgeSearchHit(
                        hit.getChunkId() != null ? "mysql:" + hit.getChunkId() : null,
                        hit.getDocumentId() != null ? "mysql-doc:" + hit.getDocumentId() : null,
                        hit.getSourceType(),
                        hit.getTitle(),
                        hit.getContent(),
                        (float) hit.getScore(),
                        new java.util.LinkedHashMap<>(),
                        hit.getNormalizedScore(),
                        hit.getScoreSemantics(),
                        null,
                        null))
                .collect(Collectors.toList());
        return com.example.matching.service.rag.RagContextAssembler.assemble(searchHits, maxEstimatedTokens);
    }

    /**
     * 保存查询日志
     */
    private Long saveQueryLog(RagScenarioEnum scenario, String queryText, String providerMode,
                               boolean fallbackUsed, int requestedTopK,
                               List<RagRetrievalResult.RagHit> hits, String contextText, long latencyMs) {
        try {
            com.example.matching.entity.rag.RagQueryLog logEntity = new com.example.matching.entity.rag.RagQueryLog();
            String uuid = UUID.randomUUID().toString();
            logEntity.setQueryCode(uuid);
            logEntity.setQueryId(uuid);
            logEntity.setScenario(scenario.name());
            logEntity.setProviderMode(providerMode);
            logEntity.setIsDegraded(fallbackUsed);
            logEntity.setRequestedTopK(requestedTopK);
            logEntity.setHitCount(hits != null ? hits.size() : 0);
            logEntity.setLatencyMs(latencyMs);

            if (hits != null && !hits.isEmpty()) {
                String chunkIds = hits.stream()
                        .map(h -> h.getChunkId() != null ? String.valueOf(h.getChunkId()) : "")
                        .collect(Collectors.joining(","));
                logEntity.setRetrievedChunkIds(chunkIds);

                String scores = hits.stream()
                        .map(h -> h.getNormalizedScore() != null ? String.format("%.4f", h.getNormalizedScore()) : "0.0000")
                        .collect(Collectors.joining(","));
                logEntity.setNormalizedScores(scores);
            }

            if (contextText != null && !contextText.isEmpty()) {
                logEntity.setContextHash(truncatedSha256(contextText));
                logEntity.setContextTokenEstimate(TokenEstimator.estimate(contextText));
            }

            logEntity.setQueryText(queryText != null && !queryText.isBlank()
                    ? truncatedSha256(queryText)
                    : null);

            ragQueryLogService.saveQueryLog(logEntity);
            return logEntity.getId();
        } catch (Exception e) {
            log.error("保存RAG查询日志失败: {}", e.getMessage(), e);
            return null;
        }
    }

    /**
     * 构建空结果
     */
    private RagRetrievalResult buildEmptyResult(RagScenarioEnum scenario, String queryText, long startTime) {
        return RagRetrievalResult.builder()
                .scenario(scenario.name())
                .queryText(queryText)
                .providerMode("none")
                .fallbackUsed(false)
                .hits(List.of())
                .contextText("")
                .latencyMs(System.currentTimeMillis() - startTime)
                .build();
    }

    private static String truncatedSha256(String input) {
        if (input == null || input.isBlank()) {
            return null;
        }
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] digest = md.digest(input.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder();
            for (byte b : digest) {
                hex.append(String.format("%02x", b));
            }
            return hex.substring(0, Math.min(16, hex.length()));
        } catch (NoSuchAlgorithmException e) {
            return Integer.toHexString(input.hashCode());
        }
    }
}
