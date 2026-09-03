package com.example.matching.service.rag.impl;

import com.example.matching.ai.service.VectorEmbeddingService;
import com.example.matching.entity.rag.RagKnowledgeChunk;
import com.example.matching.config.MilvusConfig;
import com.example.matching.service.rag.RagVectorStore;
import com.example.matching.config.ResilientMilvusClient;
import io.milvus.client.MilvusServiceClient;
import io.milvus.common.clientenum.ConsistencyLevelEnum;
import io.milvus.grpc.DataType;
import io.milvus.grpc.SearchResults;
import io.milvus.param.IndexType;
import io.milvus.param.MetricType;
import io.milvus.param.R;
import io.milvus.param.collection.*;
import io.milvus.param.dml.DeleteParam;
import io.milvus.param.dml.InsertParam;
import io.milvus.param.dml.SearchParam;
import io.milvus.param.index.CreateIndexParam;
import io.milvus.response.SearchResultsWrapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;

/**
 * Milvus 向量存储实现 —— RAG 知识分块专用
 * <p>
 * 使用独立 Collection {@value getCollection()}，HNSW 索引 + COSINE 度量。
 * Milvus 不可用时（getMilvusClient() == null）自动降级为 {@link MysqlRagVectorStore}。
 * <p>
 * 与 {@code person_post_vector} Collection 物理隔离、逻辑独立。
 */
@Slf4j
@Service
@Primary
public class MilvusRagVectorStore implements RagVectorStore {

    @Autowired(required = false)
    private MilvusConfig milvusConfig;

    /** H3：Milvus 不可用时的真实 MySQL 向量兜底（rag.vector.fallback 指标 + 补偿同步） */
    @Autowired
    @org.springframework.beans.factory.annotation.Qualifier("mysqlRagVectorStore")
    private MysqlRagVectorStore mysqlFallbackStore;

    private static final java.util.concurrent.atomic.AtomicLong FALLBACK_COUNT =
            new java.util.concurrent.atomic.AtomicLong(0);

    /** rag.vector.fallback 累计次数（指标） */
    public long getFallbackCount() {
        return FALLBACK_COUNT.get();
    }

    private String getCollection() {
        return milvusConfig != null && milvusConfig.getRagCollectionName() != null
                ? milvusConfig.getRagCollectionName() : "rag_knowledge_chunks";
    }

    private int getVectorDim() {
        return milvusConfig != null && milvusConfig.getDimension() > 0
                ? milvusConfig.getDimension() : 1536;
    }
    private static final String INDEX_NAME = "rag_chunk_vector_idx";

    @Autowired(required = false)
    private ResilientMilvusClient resilientMilvusClient;

    private MilvusServiceClient getMilvusClient() {
        return resilientMilvusClient != null ? resilientMilvusClient.getClient() : null;
    }

    @Autowired
    private VectorEmbeddingService vectorEmbeddingService;

    private final AtomicBoolean initialized = new AtomicBoolean(false);

    // ==================== 集合初始化 ====================

    private void ensureCollection() {
        if (!initialized.get() && getMilvusClient() != null) {
            synchronized (this) {
                if (!initialized.get() && getMilvusClient() != null) {
                    initCollection();
                    initialized.set(true);
                }
            }
        }
    }

    private void initCollection() {
        R<Boolean> hasColl = getMilvusClient().hasCollection(
                HasCollectionParam.newBuilder().withCollectionName(getCollection()).build());
        if (hasColl.getData() != null && hasColl.getData()) {
            log.info("RAG Milvus Collection {} already exists", getCollection());
            return;
        }

        FieldType idField = FieldType.newBuilder()
                .withName("id").withDataType(DataType.Int64).withPrimaryKey(true)
                .withAutoID(true)
                .build();
        FieldType chunkIdField = FieldType.newBuilder()
                .withName("chunk_id").withDataType(DataType.Int64).build();
        FieldType documentIdField = FieldType.newBuilder()
                .withName("document_id").withDataType(DataType.Int64).build();
        FieldType sourceTypeField = FieldType.newBuilder()
                .withName("source_type").withDataType(DataType.VarChar).withMaxLength(50).build();
        FieldType textField = FieldType.newBuilder()
                .withName("text").withDataType(DataType.VarChar).withMaxLength(65535).build();
        FieldType vectorField = FieldType.newBuilder()
                .withName("vector").withDataType(DataType.FloatVector).withDimension(getVectorDim()).build();

        @SuppressWarnings("deprecation")
        CreateCollectionParam param = CreateCollectionParam.newBuilder()
                .withCollectionName(getCollection())
                .withDescription("RAG knowledge chunk vectors for fast similarity search")
                .addFieldType(idField)
                .addFieldType(chunkIdField)
                .addFieldType(documentIdField)
                .addFieldType(sourceTypeField)
                .addFieldType(textField)
                .addFieldType(vectorField)
                .withConsistencyLevel(ConsistencyLevelEnum.BOUNDED)
                .build();

        getMilvusClient().createCollection(param);

        getMilvusClient().createIndex(CreateIndexParam.newBuilder()
                .withCollectionName(getCollection())
                .withFieldName("vector")
                .withIndexName(INDEX_NAME)
                .withIndexType(IndexType.HNSW)
                .withMetricType(MetricType.COSINE)
                .withExtraParam("{\"M\": 16, \"efConstruction\": 200}")
                .build());

        getMilvusClient().loadCollection(
                LoadCollectionParam.newBuilder().withCollectionName(getCollection()).build());

        log.info("RAG Milvus Collection {} created (HNSW, COSINE, dim={})", getCollection(), getVectorDim());
    }

    // ==================== 向量插入 ====================

    @Override
    public void insert(RagKnowledgeChunk chunk, String sourceType, List<Float> vector) {
        if (getMilvusClient() == null) {
            long fallbacks = FALLBACK_COUNT.incrementAndGet();
            log.warn("[rag.vector.fallback] Milvus 不可用，RAG 向量投影降级：数据已落 MySQL 权威表，"
                    + "等待补偿同步重放 (chunkId={}, fallbackCount={})", chunk.getId(), fallbacks);
            throw new RagVectorStoreFallbackException(
                    "Milvus is unavailable for RAG projection, chunk persisted in MySQL only: chunkId=" + chunk.getId());
        }
        if (vector == null || vector.isEmpty() || isPlaceholderVector(vector)) {
            throw new IllegalArgumentException("RAG projection requires a non-empty embedding vector");
        }
        // 修复：嵌入维度运行时校验（embedding 模型维度与集合维度不匹配时快速失败，
        // 而非等到 Milvus insert 阶段才报错，避免半写状态）
        if (vector.size() != getVectorDim()) {
            throw new IllegalArgumentException("Embedding dimension mismatch: vector=" + vector.size()
                    + ", collection=" + getVectorDim() + " (chunkId=" + chunk.getId() + ")");
        }
        ensureCollection();

        try {
            // Upsert: 先删旧再插新
            deleteByChunkIdInternal(chunk.getId());

            List<InsertParam.Field> fields = new ArrayList<>();
            fields.add(new InsertParam.Field("chunk_id",
                    Collections.singletonList(chunk.getId() != null ? chunk.getId() : 0L)));
            fields.add(new InsertParam.Field("document_id",
                    Collections.singletonList(chunk.getDocumentId() != null ? chunk.getDocumentId() : 0L)));
            fields.add(new InsertParam.Field("source_type",
                    Collections.singletonList(sourceType != null ? sourceType : "UNKNOWN")));
            fields.add(new InsertParam.Field("text",
                    Collections.singletonList(chunk.getChunkText() != null ? chunk.getChunkText() : "")));
            fields.add(new InsertParam.Field("vector", Collections.singletonList(vector)));

            getMilvusClient().insert(InsertParam.newBuilder()
                    .withCollectionName(getCollection()).withFields(fields).build());
            log.debug("RAG chunk vector inserted to Milvus: chunkId={}", chunk.getId());
        } catch (Exception e) {
            long fallbacks = FALLBACK_COUNT.incrementAndGet();
            log.warn("[rag.vector.fallback] Milvus 写入失败，RAG 向量投影降级：chunkId={}, fallbackCount={}, error={}",
                    chunk.getId(), fallbacks, e.getMessage());
            throw new RagVectorStoreFallbackException(
                    "Milvus RAG chunk insert failed, chunk persisted in MySQL only: " + chunk.getId(), e);
        }
    }

    @Override
    public void deleteByDocumentId(Long documentId) {
        if (getMilvusClient() == null) {
            long fallbacks = FALLBACK_COUNT.incrementAndGet();
            log.warn("[rag.vector.fallback] Milvus 不可用，跳过投影删除（MySQL 权威表由文档服务维护）: "
                    + "documentId={}, fallbackCount={}", documentId, fallbacks);
            return;
        }
        ensureCollection();
        try {
            getMilvusClient().delete(DeleteParam.newBuilder()
                    .withCollectionName(getCollection())
                    .withExpr("document_id == " + documentId)
                    .build());
        } catch (Exception e) {
            log.error("Milvus RAG delete by documentId failed: documentId={}", documentId, e);
        }
    }

    @Override
    public void deleteByChunkId(Long chunkId) {
        if (getMilvusClient() == null) {
            long fallbacks = FALLBACK_COUNT.incrementAndGet();
            log.warn("[rag.vector.fallback] Milvus 不可用，跳过投影删除（MySQL 权威表由文档服务维护）: "
                    + "chunkId={}, fallbackCount={}", chunkId, fallbacks);
            return;
        }
        ensureCollection();
        deleteByChunkIdInternal(chunkId);
    }

    private void deleteByChunkIdInternal(Long chunkId) {
        try {
            getMilvusClient().delete(DeleteParam.newBuilder()
                    .withCollectionName(getCollection())
                    .withExpr("chunk_id == " + chunkId)
                    .build());
        } catch (Exception e) {
            log.warn("Milvus RAG delete chunk failed (may not exist): chunkId={}", chunkId, e.getMessage());
        }
    }

    // ==================== 向量检索 ====================

    @Override
    public List<ScoredChunk> search(List<Float> queryVector, int topK, List<String> sourceTypes) {
        if (getMilvusClient() == null) {
            long fallbacks = FALLBACK_COUNT.incrementAndGet();
            log.warn("[rag.vector.fallback] Milvus 不可用，降级 MySQL 向量检索 (topK={}, fallbackCount={})",
                    topK, fallbacks);
            return mysqlFallbackStore.search(queryVector, topK, sourceTypes);
        }
        if (queryVector == null || queryVector.isEmpty()) return Collections.emptyList();
        if (isPlaceholderVector(queryVector)) {
            log.debug("查询向量为占位向量（EmbeddingModel 未配置），跳过 Milvus 搜索");
            return Collections.emptyList();
        }

        try {
            ensureCollection();

            List<String> outFields = Arrays.asList("chunk_id", "document_id", "source_type", "text");
            StringBuilder expr = new StringBuilder("");

            if (sourceTypes != null && !sourceTypes.isEmpty()) {
                String inClause = sourceTypes.stream()
                        .map(st -> "\"" + st + "\"")
                        .collect(Collectors.joining(", "));
                expr.append("source_type in [").append(inClause).append("]");
            }

            SearchParam.Builder builder = SearchParam.newBuilder()
                    .withCollectionName(getCollection())
                    .withVectorFieldName("vector")
                    .withMetricType(MetricType.COSINE)
                    .withOutFields(outFields)
                    .withTopK(topK)
                    .withFloatVectors(Collections.singletonList(queryVector))
                    .withConsistencyLevel(ConsistencyLevelEnum.BOUNDED);

            if (expr.length() > 0) {
                builder.withExpr(expr.toString());
            }

            R<SearchResults> result = getMilvusClient().search(builder.build());
            if (result.getData() == null) {
                long fallbacks = FALLBACK_COUNT.incrementAndGet();
                log.warn("[rag.vector.fallback] Milvus 检索返回空，降级 MySQL 向量检索 (fallbackCount={})", fallbacks);
                return mysqlFallbackStore.search(queryVector, topK, sourceTypes);
            }

            SearchResultsWrapper wrapper = new SearchResultsWrapper(result.getData().getResults());
            List<SearchResultsWrapper.IDScore> scores = wrapper.getIDScore(0);

            return scores.stream().map(s -> {
                RagKnowledgeChunk chunk = new RagKnowledgeChunk();
                Object chunkId = s.get("chunk_id");
                Object docId = s.get("document_id");
                Object srcType = s.get("source_type");
                Object text = s.get("text");
                chunk.setId(chunkId != null ? Long.parseLong(String.valueOf(chunkId)) : null);
                chunk.setDocumentId(docId != null ? Long.parseLong(String.valueOf(docId)) : null);
                chunk.setChunkText(text != null ? String.valueOf(text) : "");
                double rawDistance = s.getScore();
                double normalizedScore = normalizeScore(rawDistance, MetricType.COSINE);
                return new ScoredChunk(chunk, (float) normalizedScore);
            }).collect(Collectors.toList());
        } catch (Exception e) {
            // RPC 失败通常意味着连接已被服务端断开（Serverless 空闲回收）——立即失效死连接，
            // 避免后续每次检索都复用死连接反复失败并打印 RPC 错误堆栈；冷却期后自动重连。
            if (resilientMilvusClient != null) {
                resilientMilvusClient.invalidate();
            }
            long fallbacks = FALLBACK_COUNT.incrementAndGet();
            log.warn("[rag.vector.fallback] Milvus 检索失败，降级 MySQL 向量检索: fallbackCount={}, error={}",
                    fallbacks, e.getMessage());
            return mysqlFallbackStore.search(queryVector, topK, sourceTypes);
        }
    }

    // ==================== 工具方法 ====================

    /**
     * 归一化分数: 根据 Milvus metric type 将原始距离/相似度 clamp 到 [0, 1]。
     * COSINE: Milvus 返回的是 angular distance, 相似度 = 1 - distance, clamp [0, 1]。
     * L2/IP: 保守 clamp 到 [0, 1]。
     */
    private double normalizeScore(double rawScore, MetricType metricType) {
        // 使用 if-else 而非 switch-on-enum，避免 javac 生成合成类 MilvusRagVectorStore$1
        double normalized;
        if (metricType == MetricType.COSINE) {
            normalized = 1.0 - rawScore;
        } else {
            normalized = rawScore;
        }
        return Math.max(0.0, Math.min(1.0, normalized));
    }

    private boolean isPlaceholderVector(List<Float> vector) {
        for (Float f : vector) {
            if (f != null && f != 0f) return false;
        }
        return true;
    }
}
