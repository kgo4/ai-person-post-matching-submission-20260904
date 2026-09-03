package com.example.matching.service.rag.impl;

import com.example.matching.ai.service.VectorEmbeddingService;
import com.example.matching.entity.rag.RagKnowledgeChunk;
import com.example.matching.entity.rag.RagKnowledgeDocument;
import com.example.matching.mapper.rag.RagKnowledgeChunkMapper;
import com.example.matching.mapper.rag.RagKnowledgeDocumentMapper;
import com.example.matching.service.rag.KnowledgeSearchHit;
import com.example.matching.service.rag.KnowledgeSearchProvider;
import com.example.matching.service.rag.KnowledgeSearchRequest;
import com.example.matching.service.rag.RagVectorStore;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * MySQL 知识搜索提供者 —— 向量检索 + 关键词匹配混合排序（RRF 融合）
 * <p>
 * 向量检索走 {@link RagVectorStore}（默认 {@link MilvusRagVectorStore}）获取语义相似度，
 * 关键词检索走 MySQL LIKE 匹配获取字面命中。
 * <p>
 * 分数契约：两路候选各自排序后按倒数排名融合（reciprocal rank fusion），
 * 最终分数归一化到 [0,1] 区间。绝不把原始余弦距离与 0-100 的关键词分直接相加。
 */
@Slf4j
@Service
public class MysqlKnowledgeSearchProvider implements KnowledgeSearchProvider {

    /** RRF 排名常数（k=60） */
    private static final double RRF_K = 60.0;

    /** 关键词 bigram 数量上限 */
    private static final int MAX_BIGRAMS = 12;

    private final VectorEmbeddingService vectorEmbeddingService;
    private final RagVectorStore ragVectorStore;
    private final RagKnowledgeDocumentMapper documentMapper;
    private final RagKnowledgeChunkMapper chunkMapper;
    private final MeterRegistry meterRegistry;
    private final Counter providerCounter;

    public MysqlKnowledgeSearchProvider(VectorEmbeddingService vectorEmbeddingService,
                                        RagVectorStore ragVectorStore,
                                        RagKnowledgeDocumentMapper documentMapper,
                                        RagKnowledgeChunkMapper chunkMapper,
                                        MeterRegistry meterRegistry) {
        this.vectorEmbeddingService = vectorEmbeddingService;
        this.ragVectorStore = ragVectorStore;
        this.documentMapper = documentMapper;
        this.chunkMapper = chunkMapper;
        this.meterRegistry = meterRegistry;
        this.providerCounter = Counter.builder("rag.retrieval.provider")
                .tag("provider", "mysql")
                .tag("rerank_applied", "false")
                .register(meterRegistry);
    }

    @Override
    public List<KnowledgeSearchHit> search(KnowledgeSearchRequest request) {
        if (request == null || request.query() == null || request.query().isBlank()) {
            return List.of();
        }

        String query = request.query();
        int topK = Math.max(request.topK(), 5);
        List<String> sourceTypes = request.sourceTypes();

        // 1. 向量检索：按相似度排序（rawScore 保留原始语义相似度供阈值过滤）
        List<Long> vectorOrder = new ArrayList<>();
        Map<Long, Float> vectorScores = new LinkedHashMap<>();
        List<Float> queryVector = vectorEmbeddingService.embed(query);
        if (queryVector != null && !queryVector.isEmpty()) {
            List<RagVectorStore.ScoredChunk> chunks = ragVectorStore.search(queryVector, topK * 2, sourceTypes);
            for (RagVectorStore.ScoredChunk sc : chunks) {
                Long chunkId = sc.chunk().getId();
                if (chunkId != null) {
                    vectorOrder.add(chunkId);
                    vectorScores.put(chunkId, sc.score());
                }
            }
        }

        // 2. 关键词检索：bigram 命中次数排序（去重 + 上限 + 单次分组查询）
        Map<Long, Integer> keywordHits = new LinkedHashMap<>();
        List<String> bigrams = extractHighSignalBigrams(query);
        if (!bigrams.isEmpty()) {
            List<RagKnowledgeChunk> matched = chunkMapper.findActiveByKeywordBigrams(bigrams, topK * 2);
            for (RagKnowledgeChunk chunk : matched) {
                if (chunk.getId() == null) {
                    continue;
                }
                int hits = countBigramHits(chunk.getChunkText(), bigrams);
                keywordHits.merge(chunk.getId(), hits, Integer::sum);
            }
        }
        List<Long> keywordOrder = keywordHits.entrySet().stream()
                .sorted((a, b) -> Integer.compare(b.getValue(), a.getValue()))
                .map(Map.Entry::getKey)
                .toList();

        // 3. RRF 融合：两路独立排名，倒数排名相加，除以最大可能贡献归一化到 [0,1]
        Map<Long, Double> rrfScores = new LinkedHashMap<>();
        addRankContribution(rrfScores, vectorOrder);
        addRankContribution(rrfScores, keywordOrder);

        // 归一化：单路最高贡献是 1/61，两路最高是 2/61
        double maxContribution = (vectorOrder.isEmpty() ? 0.0 : 1.0 / (RRF_K + 1))
                + (keywordOrder.isEmpty() ? 0.0 : 1.0 / (RRF_K + 1));
        Map<Long, Double> normalized = new LinkedHashMap<>();
        for (Map.Entry<Long, Double> entry : rrfScores.entrySet()) {
            normalized.put(entry.getKey(), maxContribution > 0
                    ? entry.getValue() / maxContribution
                    : 0.0);
        }

        // 4. 加载 chunk 与文档（各一次批量查询）
        Set<Long> allChunkIds = normalized.keySet();
        Map<Long, RagKnowledgeChunk> chunksById = allChunkIds.isEmpty()
                ? Map.of()
                : chunkMapper.selectBatchIds(allChunkIds).stream()
                        .collect(Collectors.toMap(RagKnowledgeChunk::getId, chunk -> chunk, (a, b) -> a));

        List<RagKnowledgeChunk> orderedChunks = normalized.entrySet().stream()
                .sorted(Map.Entry.<Long, Double>comparingByValue().reversed())
                .limit(topK)
                .map(entry -> chunksById.get(entry.getKey()))
                .filter(Objects::nonNull)
                .toList();

        Map<Long, RagKnowledgeDocument> documentsById = orderedChunks.stream()
                .map(RagKnowledgeChunk::getDocumentId)
                .filter(Objects::nonNull)
                .distinct()
                .collect(Collectors.toSet())
                .isEmpty()
                ? Map.of()
                : documentMapper.selectBatchIds(orderedChunks.stream()
                                .map(RagKnowledgeChunk::getDocumentId)
                                .filter(Objects::nonNull)
                                .distinct()
                                .collect(Collectors.toSet()))
                        .stream()
                        .collect(Collectors.toMap(RagKnowledgeDocument::getId, doc -> doc, (a, b) -> a));

        recordProviderMetric();
        return orderedChunks.stream()
                .map(chunk -> {
                    RagKnowledgeDocument doc = documentsById.get(chunk.getDocumentId());
                    String sourceType = doc != null ? doc.getSourceType() : "UNKNOWN";
                    String title = doc != null ? doc.getTitle() : "未知文档";
                    double rrfScore = normalized.getOrDefault(chunk.getId(), 0.0);
                    KnowledgeSearchHit hit = KnowledgeSearchHit.fromMysqlChunk(
                            chunk, (float) rrfScore, sourceType, title,
                            rrfScore, "RRF", "RRF_K60",
                            vectorScores.get(chunk.getId()) != null
                                    ? vectorScores.get(chunk.getId()).doubleValue() : null);
                    // M25：业务来源引用 ID 从文档映射写入 metadata（来源可回溯）
                    if (doc != null && doc.getSourceRefId() != null) {
                        hit.metadata().put("sourceRefId", doc.getSourceRefId());
                    }
                    hit.metadata().put("rerankApplied", false);
                    return hit;
                })
                .collect(Collectors.toList());
    }

    private void addRankContribution(Map<Long, Double> scores, List<Long> orderedIds) {
        for (int i = 0; i < orderedIds.size(); i++) {
            Long chunkId = orderedIds.get(i);
            if (chunkId != null) {
                scores.merge(chunkId, 1.0 / (RRF_K + i + 1), Double::sum);
            }
        }
    }

    /**
     * 提取高信号 bigram：去空白/标点后保留至多 {@link #MAX_BIGRAMS} 个。
     */
    private List<String> extractHighSignalBigrams(String text) {
        if (text == null) {
            return Collections.emptyList();
        }
        String cleaned = text.replaceAll("[\\s\\p{Punct}]+", "");
        if (cleaned.length() < 2) {
            return cleaned.isEmpty() ? Collections.emptyList() : Collections.singletonList(cleaned);
        }
        Set<String> bigrams = new LinkedHashSet<>();
        for (int i = 0; i < cleaned.length() - 1 && bigrams.size() < MAX_BIGRAMS; i++) {
            bigrams.add(cleaned.substring(i, i + 2));
        }
        return new ArrayList<>(bigrams);
    }

    private int countBigramHits(String chunkText, List<String> bigrams) {
        if (chunkText == null) {
            return 0;
        }
        int count = 0;
        for (String bigram : bigrams) {
            if (chunkText.contains(bigram)) {
                count++;
            }
        }
        return count;
    }

    private void recordProviderMetric() {
        providerCounter.increment();
    }
}
