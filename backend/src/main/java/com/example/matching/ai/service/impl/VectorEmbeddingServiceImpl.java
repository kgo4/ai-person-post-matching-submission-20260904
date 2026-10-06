package com.example.matching.ai.service.impl;

import com.example.matching.ai.service.VectorEmbeddingService;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.embedding.request.EmbeddingRequest;
import dev.langchain4j.model.embedding.response.EmbeddingResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * 向量嵌入服务实现
 * <p>
 * 基于 LangChain4j {@link EmbeddingModel} 实现文本向量化。
 * 提供的 bean 名为 {@code langchain4jEmbeddingModel}（参见 {@link com.example.matching.config.LangChain4jEmbeddingConfig}）。
 * 当模型不可用时返回空向量，由调用方跳过向量写入或检索。
 */
@Slf4j
@Service
public class VectorEmbeddingServiceImpl implements VectorEmbeddingService {

    @org.springframework.beans.factory.annotation.Value("${ai.embedding-timeout-seconds:${ai.request-timeout-seconds:30}}")
    private long timeoutSeconds = 30;

    @Autowired(required = false)
    @Qualifier("langchain4jEmbeddingModel")
    private EmbeddingModel embeddingModel;

    @Override
    public List<Float> embed(String text) {
        if (embeddingModel == null) {
            log.debug("EmbeddingModel 未配置，跳过向量化");
            return Collections.emptyList();
        }
        try {
            EmbeddingResponse response = CompletableFuture
                    .supplyAsync(() -> embeddingModel.embed(EmbeddingRequest.builder().input(text).build()))
                    .get(Math.max(1L, timeoutSeconds), TimeUnit.SECONDS);
            List<Embedding> embeddings = response == null ? null : response.embeddings();
            if (embeddings != null && !embeddings.isEmpty()) {
                Embedding embedding = embeddings.get(0);
                if (embedding.vector() != null) {
                    return convertToFloatList(embedding.vector());
                }
            }
        } catch (TimeoutException e) {
            log.error("Embedding timed out after {} seconds", timeoutSeconds);
        } catch (Exception e) {
            log.error("向量嵌入失败: {}", e.getMessage());
        }
        return Collections.emptyList();
    }

    @Override
    public List<List<Float>> embedBatch(List<String> texts) {
        if (embeddingModel == null || texts == null || texts.isEmpty()) {
            return texts == null
                    ? Collections.emptyList()
                    : texts.stream().map(t -> Collections.<Float>emptyList()).collect(Collectors.toList());
        }
        try {
            List<TextSegment> segments = texts.stream()
                    .map(TextSegment::from)
                    .collect(Collectors.toList());
            EmbeddingResponse response = CompletableFuture
                    .supplyAsync(() -> embeddingModel.embed(EmbeddingRequest.builder().textSegments(segments).build()))
                    .get(Math.max(1L, timeoutSeconds), TimeUnit.SECONDS);
            List<Embedding> embeddings = response == null ? null : response.embeddings();
            if (embeddings != null) {
                return embeddings.stream()
                        .map(e -> e.vector() != null ? convertToFloatList(e.vector()) : Collections.<Float>emptyList())
                        .collect(Collectors.toList());
            }
        } catch (TimeoutException e) {
            log.error("Batch embedding timed out after {} seconds", timeoutSeconds);
        } catch (Exception e) {
            log.error("批量向量嵌入失败: {}", e.getMessage());
        }
        return texts.stream().map(t -> Collections.<Float>emptyList()).collect(Collectors.toList());
    }

    @Override
    public Float cosineSimilarity(List<Float> vectorA, List<Float> vectorB) {
        if (vectorA == null || vectorB == null || vectorA.size() != vectorB.size()) {
            return 0f;
        }
        float dotProduct = 0;
        float normA = 0;
        float normB = 0;
        for (int i = 0; i < vectorA.size(); i++) {
            dotProduct += vectorA.get(i) * vectorB.get(i);
            normA += vectorA.get(i) * vectorA.get(i);
            normB += vectorB.get(i) * vectorB.get(i);
        }
        if (normA == 0 || normB == 0) {
            return 0f;
        }
        return dotProduct / (float) (Math.sqrt(normA) * Math.sqrt(normB));
    }

    private List<Float> convertToFloatList(float[] output) {
        List<Float> result = new ArrayList<>(output.length);
        for (float v : output) result.add(v);
        return result;
    }
}
