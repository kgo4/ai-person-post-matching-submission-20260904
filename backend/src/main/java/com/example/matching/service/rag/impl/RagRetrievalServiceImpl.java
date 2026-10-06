package com.example.matching.service.rag.impl;

import com.example.matching.config.VolcengineKnowledgeBaseProperties;
import com.example.matching.service.rag.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
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
        if (scenario == null) {
            return buildEmptyResult(null, request.getQueryText(), startTime);
        }
        String queryText = request.getQueryText();
        Set<RagKnowledgeLayer> allowedLayers = RagScenarioPolicy.allowedLayers(scenario);
        int requestedTopK = request.getTopK() != null ? request.getTopK() : scenario.getDefaultTopK();
        if (requestedTopK <= 0) {
            requestedTopK = scenario.getDefaultTopK();
        }
        int topK = Math.min(requestedTopK, maxContextChunks);

        if (queryText == null || queryText.isBlank()) {
            return buildEmptyResult(scenario, queryText, startTime);
        }

        try {
            List<String> effectiveSourceTypes = resolveSourceTypes(request, allowedLayers, scenario);
            KnowledgeSearchRequest searchRequest = new KnowledgeSearchRequest(
                    queryText, scenario.name(), topK, effectiveSourceTypes, allowedLayers);
            String mode = resolveProviderMode(request, scenario);

            List<KnowledgeSearchHit> hits;
            boolean fallbackUsed = false;
            String fallbackReason = null;
            Set<String> rejectedSourceTypes = new LinkedHashSet<>();

            if ("volcengine".equals(mode)) {
                hits = volcengineKnowledgeSearchProvider.search(searchRequest);
            } else if ("mysql".equals(mode)) {
                hits = mysqlKnowledgeSearchProvider.search(searchRequest);
            } else {
                // hybrid 只允许在同一场景层集合内回退，不能把另一个层的资料带入上下文。
                hits = volcengineKnowledgeSearchProvider.search(searchRequest);
                int rawHitCount = hits.size();
                rejectedSourceTypes.addAll(rejectedSourceTypes(hits, scenario, allowedLayers));
                hits = filterByRequestedSourceTypes(hits, effectiveSourceTypes);
                hits = filterBySourceType(hits, scenario, allowedLayers);
                if (hits.isEmpty()) {
                    log.info("RAG hybrid fallback: provider=volcengine rawHitCount={} allowedHitCount=0 "
                                    + "fallbackToMysql=true scenario={}",
                            rawHitCount, scenario.name());
                    hits = mysqlKnowledgeSearchProvider.search(searchRequest);
                    rejectedSourceTypes.addAll(rejectedSourceTypes(hits, scenario, allowedLayers));
                    hits = filterByRequestedSourceTypes(hits, effectiveSourceTypes);
                    hits = filterBySourceType(hits, scenario, allowedLayers);
                    mode = "mysql";
                    fallbackUsed = true;
                    fallbackReason = "cloud_empty_after_layer_filter";
                } else {
                    mode = "volcengine";
                    log.info("RAG hybrid: provider=volcengine rawHitCount={} allowedHitCount={} "
                                    + "fallbackToMysql=false scenario={}",
                            rawHitCount, hits.size(), scenario.name());
                }
            }

            rejectedSourceTypes.addAll(rejectedSourceTypes(hits, scenario, allowedLayers));
            hits = filterByRequestedSourceTypes(hits, effectiveSourceTypes);
            hits = filterBySourceType(hits, scenario, allowedLayers);

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
                logId = saveQueryLog(scenario, queryText, mode, fallbackUsed, topK, ragHits, contextText,
                        latencyMs, allowedLayers, rejectedSourceTypes, fallbackReason);
            }

            Set<String> hitLayers = hits.stream()
                    .map(this::resolveHitLayer)
                    .filter(java.util.Objects::nonNull)
                    .map(Enum::name)
                    .collect(Collectors.toCollection(LinkedHashSet::new));

            return RagRetrievalResult.builder()
                    .scenario(scenario.name())
                    .allowedLayers(allowedLayers.stream().map(Enum::name).collect(Collectors.toCollection(LinkedHashSet::new)))
                    .hitLayers(hitLayers)
                    .rejectedSourceTypes(List.copyOf(rejectedSourceTypes))
                    .fallbackReason(fallbackReason)
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

    private List<String> resolveSourceTypes(RagRetrievalRequest request,
                                            Set<RagKnowledgeLayer> allowedLayers,
                                            RagScenarioEnum scenario) {
        Set<String> policySourceTypes = new LinkedHashSet<>(RagSourceCatalog.sourceTypesForLayers(allowedLayers));
        if (scenario.isAllowCloud()) {
            policySourceTypes.add("VOLCENGINE_KB");
        }
        if (request.getSourceTypes() == null || request.getSourceTypes().isEmpty()) {
            return List.copyOf(policySourceTypes);
        }
        return request.getSourceTypes().stream()
                .filter(policySourceTypes::contains)
                .distinct()
                .toList();
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
    private List<KnowledgeSearchHit> filterBySourceType(List<KnowledgeSearchHit> hits,
                                                        RagScenarioEnum scenario,
                                                        Set<RagKnowledgeLayer> allowedLayers) {
        if (hits == null || hits.isEmpty()) {
            return hits;
        }
        return hits.stream()
                .filter(hit -> isHitAllowed(hit, scenario, allowedLayers))
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

    private List<String> rejectedSourceTypes(List<KnowledgeSearchHit> hits,
                                             RagScenarioEnum scenario,
                                             Set<RagKnowledgeLayer> allowedLayers) {
        if (hits == null || hits.isEmpty()) {
            return List.of();
        }
        return hits.stream()
                .filter(hit -> !isHitAllowed(hit, scenario, allowedLayers))
                .map(KnowledgeSearchHit::sourceType)
                .filter(java.util.Objects::nonNull)
                .distinct()
                .toList();
    }

    private RagKnowledgeLayer resolveHitLayer(KnowledgeSearchHit hit) {
        return RagSourceCatalog.resolveLayer(hit.sourceType(), hit.metadata());
    }

    private boolean isHitAllowed(KnowledgeSearchHit hit,
                                 RagScenarioEnum scenario,
                                 Set<RagKnowledgeLayer> allowedLayers) {
        if ("VOLCENGINE_KB".equals(hit.sourceType()) && !scenario.isAllowCloud()) {
            return false;
        }
        // sourceType 只描述业务来源，层级策略是唯一的检索边界。
        return RagSourceCatalog.isAllowed(hit.sourceType(), hit.metadata(), allowedLayers);
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
                .knowledgeLayer(resolveHitLayer(hit) == null ? null : resolveHitLayer(hit).name())
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
                               List<RagRetrievalResult.RagHit> hits, String contextText, long latencyMs,
                               Set<RagKnowledgeLayer> allowedLayers,
                               Set<String> rejectedSourceTypes,
                               String fallbackReason) {
        try {
            com.example.matching.entity.rag.RagQueryLog logEntity = new com.example.matching.entity.rag.RagQueryLog();
            String uuid = UUID.randomUUID().toString();
            logEntity.setQueryCode(uuid);
            logEntity.setQueryId(uuid);
            logEntity.setScenario(scenario.name());
            logEntity.setAllowedLayers(allowedLayers.stream().map(Enum::name).sorted().collect(Collectors.joining(",")));
            logEntity.setHitLayers(hits == null ? null : hits.stream()
                    .map(RagRetrievalResult.RagHit::getKnowledgeLayer)
                    .filter(java.util.Objects::nonNull)
                    .distinct()
                    .sorted()
                    .collect(Collectors.joining(",")));
            logEntity.setRejectedSourceTypes(rejectedSourceTypes == null || rejectedSourceTypes.isEmpty()
                    ? null : String.join(",", rejectedSourceTypes));
            logEntity.setFallbackReason(fallbackReason);
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
        Set<RagKnowledgeLayer> allowedLayers = RagScenarioPolicy.allowedLayers(scenario);
        return RagRetrievalResult.builder()
                .scenario(scenario == null ? null : scenario.name())
                .allowedLayers(allowedLayers.stream().map(Enum::name)
                        .collect(Collectors.toCollection(LinkedHashSet::new)))
                .hitLayers(Set.of())
                .rejectedSourceTypes(List.of())
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
