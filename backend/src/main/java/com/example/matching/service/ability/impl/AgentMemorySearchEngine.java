package com.example.matching.service.ability.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.example.matching.entity.ability.AgentMemory;
import com.example.matching.mapper.ability.AgentMemoryMapper;
import com.example.matching.ai.service.VectorEmbeddingService;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.stats.CacheStats;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * Agent 记忆语义检索：向量/字面匹配、缺失嵌入补齐、规则命中排序。
 * <p>
 * 从 AgentMemoryServiceImpl（500 行）中拆分的检索组件，行为与拆分前一致。
 */
@Slf4j
@Component
public class AgentMemorySearchEngine extends ServiceImpl<AgentMemoryMapper, AgentMemory> {

    private static final float SEMANTIC_MATCH_THRESHOLD = 0.72f;
    /** 检索返回规则总数上限 */
    private static final int MAX_RULES_TOTAL = 8;
    /** 每记忆类型规则上限 */
    private static final int MAX_RULES_PER_TYPE = 3;

    private final ObjectMapper objectMapper;
    private final VectorEmbeddingService vectorEmbeddingService;
    private final MemorySearchCacheEpoch cacheEpoch;

    private final Cache<String, List<AgentMemory>> searchCache = Caffeine.newBuilder()
            .maximumSize(200)
            .expireAfterWrite(5, TimeUnit.MINUTES)
            .recordStats()
            .build();

    public AgentMemorySearchEngine(ObjectMapper objectMapper,
                                    @Autowired(required = false) VectorEmbeddingService vectorEmbeddingService,
                                    MemorySearchCacheEpoch cacheEpoch) {
        this.objectMapper = objectMapper;
        this.vectorEmbeddingService = vectorEmbeddingService;
        this.cacheEpoch = cacheEpoch;
    }

    public List<AgentMemory> getActiveMemories(String scope) {
        return list(Wrappers.<AgentMemory>lambdaQuery()
                .eq(AgentMemory::getStatus, "ACTIVE")
                .and(w -> w.eq(AgentMemory::getApplicableScope, scope).or()
                        .eq(AgentMemory::getApplicableScope, "ALL"))
                .and(w -> w.isNull(AgentMemory::getExpireTime).or()
                        .gt(AgentMemory::getExpireTime, LocalDateTime.now()))
                .orderByDesc(AgentMemory::getPriority)
                .orderByDesc(AgentMemory::getUpdatedTime));
    }

    public List<AgentMemory> searchMemories(String text, String scope) {
        if (!StringUtils.hasText(text)) return new ArrayList<>();
        return searchWithCache(text, scope);
    }

    public List<AgentMemory> searchActiveRules(String text, String scope) {
        if (!StringUtils.hasText(text)) return new ArrayList<>();
        return searchWithCache(normalizeText(text), scope);
    }

    private List<AgentMemory> searchWithCache(String text, String scope) {
        String cacheKey = buildCacheKey(scope, text);
        try {
            List<AgentMemory> cached = searchCache.getIfPresent(cacheKey);
            if (cached != null) {
                return cached;
            }
            List<AgentMemory> result = searchMatchingMemories(text, scope);
            if (!result.isEmpty()) {
                searchCache.put(cacheKey, result);
            }
            return result;
        } catch (Exception e) {
            return searchMatchingMemories(text, scope);
        }
    }

    private String buildCacheKey(String scope, String text) {
        long epoch = cacheEpoch.current(scope != null ? scope : "ALL");
        // 全文本哈希作缓存键：禁止截断，防止前 500 字符相同而后续不同的文本串用同一缓存键
        String hash = sha256(text);
        return scope + ":" + epoch + ":" + hash;
    }

    private static String sha256(String input) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] digest = md.digest(input.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            return Integer.toHexString(input.hashCode());
        }
    }

    public void invalidateCache(String scope) {
        cacheEpoch.advance(scope != null ? scope : "ALL");
        log.debug("Invalidated memory search cache for scope: {}", scope);
    }

    public void invalidateAll() {
        searchCache.invalidateAll();
        log.debug("Invalidated all memory search cache entries");
    }

    public CacheStats cacheStats() {
        return searchCache.stats();
    }

    private List<AgentMemory> searchMatchingMemories(String text, String scope) {
        List<AgentMemory> active = getActiveMemories(scope);
        // 不再在查询路径对缺失向量的记忆现场 embed（成本随记忆量线性增长且不落库）：
        // 缺失向量的记忆降级为字面匹配；向量补齐由写入路径（createMemory/populateEmbedding+save）负责

        List<Float> queryVector = vectorEmbeddingService != null ? vectorEmbeddingService.embed(text) : List.of();
        List<MemoryMatch> matches = new ArrayList<>();
        for (AgentMemory memory : active) {
            float semanticScore = semanticScore(queryVector, memory.getEmbeddingVector());
            boolean semanticAvailable = queryVector != null && !queryVector.isEmpty()
                    && memory.getEmbeddingVector() != null && !memory.getEmbeddingVector().isEmpty();
            boolean literalMatch = !semanticAvailable && matches(memory, text);
            if (literalMatch || semanticScore >= SEMANTIC_MATCH_THRESHOLD) {
                matches.add(new MemoryMatch(memory, literalMatch ? Math.max(semanticScore, 1.0f) : semanticScore));
            }
        }
        return matches.stream()
                .sorted(Comparator.comparing(MemoryMatch::score).reversed()
                        .thenComparing(match -> match.memory().getPriority(), Comparator.nullsLast(Comparator.reverseOrder()))
                        .thenComparing(match -> match.memory().getUpdatedTime(), Comparator.nullsLast(Comparator.reverseOrder())))
                .map(MemoryMatch::memory)
                .limit(MAX_RULES_TOTAL)
                .collect(java.util.stream.Collectors.collectingAndThen(
                        java.util.stream.Collectors.toList(),
                        AgentMemorySearchEngine::capPerType));
    }

    /**
     * 每种记忆类型最多保留 {@value #MAX_RULES_PER_TYPE} 条，保持总量 {@value #MAX_RULES_TOTAL} 上限。
     */
    private static List<AgentMemory> capPerType(List<AgentMemory> ranked) {
        java.util.Map<String, Integer> perType = new java.util.HashMap<>();
        java.util.List<AgentMemory> result = new ArrayList<>();
        for (AgentMemory memory : ranked) {
            String type = memory.getMemoryType() != null ? memory.getMemoryType() : "UNKNOWN";
            int count = perType.merge(type, 1, Integer::sum);
            if (count <= MAX_RULES_PER_TYPE) {
                result.add(memory);
            }
        }
        return result;
    }

    public void populateEmbedding(AgentMemory memory) {
        if (vectorEmbeddingService == null || memory == null) return;
        try {
            List<Float> embedding = vectorEmbeddingService.embed(embeddingText(memory));
            if (embedding != null && !embedding.isEmpty()) {
                memory.setEmbeddingVector(embedding);
            }
        } catch (Exception e) {
            log.debug("Agent记忆嵌入生成失败: memoryId={}", memory.getId());
        }
    }

    private String embeddingText(AgentMemory memory) {
        return String.join("\n", List.of(
                memory.getTitle() != null ? memory.getTitle() : "",
                memory.getContent() != null ? memory.getContent() : "",
                memory.getTriggerExpressionsJson() != null ? memory.getTriggerExpressionsJson() : ""));
    }

    private float semanticScore(List<Float> queryVector, List<Float> memoryVector) {
        if (vectorEmbeddingService == null || queryVector == null || queryVector.isEmpty()
                || memoryVector == null || memoryVector.isEmpty()) {
            return 0f;
        }
        Float score = vectorEmbeddingService.cosineSimilarity(queryVector, memoryVector);
        return score != null ? score : 0f;
    }

    private record MemoryMatch(AgentMemory memory, float score) {
    }

    private String normalizeText(String text) {
        if (text == null) return "";
        return text.toLowerCase().replaceAll("\\s+", " ").trim();
    }

    private boolean matches(AgentMemory memory, String text) {
        for (String expression : parseTriggerExpressions(memory.getTriggerExpressionsJson())) {
            if (containsIgnoreCase(text, expression)) return true;
        }
        for (String sourceTerm : parseSourceTerms(memory.getRulePayloadJson())) {
            if (containsIgnoreCase(text, sourceTerm)) return true;
        }
        return containsIgnoreCase(text, memory.getTitle())
                || containsIgnoreCase(text, memory.getContent());
    }

    private List<String> parseSourceTerms(String rulePayloadJson) {
        if (!StringUtils.hasText(rulePayloadJson)) return List.of();
        try {
            JsonNode terms = objectMapper.readTree(rulePayloadJson).path("condition").path("sourceTerms");
            if (!terms.isArray()) return List.of();
            List<String> sourceTerms = new ArrayList<>();
            for (JsonNode term : terms) {
                if (term.isTextual() && StringUtils.hasText(term.asText())) sourceTerms.add(term.asText());
            }
            return sourceTerms;
        } catch (Exception e) {
            log.warn("Ignoring invalid agent memory rule payload: {}", e.getMessage());
            return List.of();
        }
    }

    private List<String> parseTriggerExpressions(String json) {
        if (!StringUtils.hasText(json)) return List.of();
        try {
            JsonNode node = objectMapper.readTree(json);
            if (node.isTextual()) node = objectMapper.readTree(node.asText());
            if (!node.isArray()) return List.of();
            return objectMapper.convertValue(node, new TypeReference<List<String>>() { }).stream()
                    .filter(StringUtils::hasText)
                    .toList();
        } catch (Exception exception) {
            log.warn("Ignoring invalid agent memory trigger expressions: {}", exception.getMessage());
            return List.of();
        }
    }

    private boolean containsIgnoreCase(String text, String candidate) {
        return StringUtils.hasText(candidate) && text.toLowerCase().contains(candidate.toLowerCase());
    }
}
