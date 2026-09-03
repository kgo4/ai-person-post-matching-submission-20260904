package com.example.matching.service.rag.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.example.matching.ai.service.VectorEmbeddingService;
import com.example.matching.entity.rag.RagKnowledgeChunk;
import com.example.matching.entity.rag.RagKnowledgeDocument;
import com.example.matching.mapper.rag.RagKnowledgeChunkMapper;
import com.example.matching.mapper.rag.RagKnowledgeDocumentMapper;
import com.example.matching.service.rag.RagVectorStore;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.PriorityQueue;
import java.util.stream.Collectors;

/**
 * MySQL向量存储实现
 * <p>
 * 使用分批加载 + 余弦相似度计算的方式实现向量检索。
 * 通过分批查询和最小堆控制内存占用，避免全量加载导致 OOM。
 *
 * @author system
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MysqlRagVectorStore implements RagVectorStore {

    /** 每批加载的分块数量 */
    private static final int BATCH_SIZE = 500;

    private final RagKnowledgeChunkMapper chunkMapper;
    private final RagKnowledgeDocumentMapper documentMapper;
    private final VectorEmbeddingService vectorEmbeddingService;
    private final ObjectMapper objectMapper;

    @Override
    public List<ScoredChunk> search(List<Float> queryVector, int topK, List<String> sourceTypes) {
        if (queryVector == null || queryVector.isEmpty()) {
            return List.of();
        }

        // 1. 加载活跃文档ID（可选来源类型过滤）
        LambdaQueryWrapper<RagKnowledgeDocument> docQuery = new LambdaQueryWrapper<>();
        docQuery.eq(RagKnowledgeDocument::getDocStatus, "ACTIVE");
        docQuery.eq(RagKnowledgeDocument::getIsDeleted, 0);
        if (sourceTypes != null && !sourceTypes.isEmpty()) {
            docQuery.in(RagKnowledgeDocument::getSourceType, sourceTypes);
        }
        List<RagKnowledgeDocument> activeDocs = documentMapper.selectList(docQuery);
        if (activeDocs.isEmpty()) {
            return List.of();
        }
        List<Long> activeDocIds = activeDocs.stream()
                .map(RagKnowledgeDocument::getId)
                .collect(Collectors.toList());

        // 2. 分批加载活跃分块并计算相似度，使用最小堆维护 topK
        //    最小堆：堆顶是当前最小分数，堆满 topK 后新分数必须大于堆顶才入堆
        PriorityQueue<ScoredChunk> minHeap = new PriorityQueue<>(topK + 1,
                (a, b) -> Float.compare(a.score(), b.score()));

        long lastId = 0;
        boolean hasMore = true;
        int totalProcessed = 0;

        while (hasMore) {
            // 分页查询：使用 id > lastId 避免 OFFSET 深分页性能问题
            LambdaQueryWrapper<RagKnowledgeChunk> chunkQuery = new LambdaQueryWrapper<>();
            chunkQuery.in(RagKnowledgeChunk::getDocumentId, activeDocIds);
            chunkQuery.eq(RagKnowledgeChunk::getChunkStatus, "ACTIVE");
            chunkQuery.isNotNull(RagKnowledgeChunk::getEmbeddingVector);
            chunkQuery.gt(RagKnowledgeChunk::getId, lastId);
            chunkQuery.orderByAsc(RagKnowledgeChunk::getId);
            chunkQuery.last("LIMIT " + BATCH_SIZE);

            List<RagKnowledgeChunk> batch = chunkMapper.selectList(chunkQuery);
            if (batch.isEmpty()) {
                hasMore = false;
                break;
            }

            totalProcessed += batch.size();
            lastId = batch.get(batch.size() - 1).getId();

            for (RagKnowledgeChunk chunk : batch) {
                List<Float> chunkVector = parseVector(chunk.getEmbeddingVector());
                if (chunkVector == null || chunkVector.isEmpty()) {
                    continue;
                }
                float score = vectorEmbeddingService.cosineSimilarity(queryVector, chunkVector);
                minHeap.offer(new ScoredChunk(chunk, score));
                // 堆超过 topK 时移除最小元素，控制堆大小
                if (minHeap.size() > topK) {
                    minHeap.poll();
                }
            }

            // 当前批次不足 BATCH_SIZE 说明已无更多数据
            if (batch.size() < BATCH_SIZE) {
                hasMore = false;
            }
        }

        if (minHeap.isEmpty()) {
            return List.of();
        }

        log.debug("RAG向量检索完成: 处理分块={}, 返回topK={}", totalProcessed, minHeap.size());

        // 3. 将最小堆转为列表并按分数降序排列
        List<ScoredChunk> result = new ArrayList<>(minHeap);
        result.sort((a, b) -> Float.compare(b.score(), a.score()));
        return result;
    }

    /**
     * 解析JSON格式的向量
     */
    private List<Float> parseVector(String vectorJson) {
        if (vectorJson == null || vectorJson.isBlank()) {
            return null;
        }
        try {
            return objectMapper.readValue(vectorJson, new TypeReference<List<Float>>() {
            });
        } catch (Exception e) {
            log.warn("解析向量JSON失败: {}", e.getMessage());
            return null;
        }
    }

    @Override
    public void insert(RagKnowledgeChunk chunk, String sourceType, List<Float> vector) {
        // MySQL 实现：数据已由 KnowledgeDocumentServiceImpl 写入 rag_knowledge_chunk 表，无需额外操作
    }

    @Override
    public void deleteByDocumentId(Long documentId) {
        // MySQL 实现：数据已由 KnowledgeDocumentServiceImpl 物理删除，无需额外操作
    }

    @Override
    public void deleteByChunkId(Long chunkId) {
        // MySQL 实现：数据已由 KnowledgeDocumentServiceImpl 物理删除，无需额外操作
    }
}
