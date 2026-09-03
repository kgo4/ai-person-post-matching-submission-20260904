package com.example.matching.service.rag;

import lombok.Builder;
import lombok.Data;

import java.util.List;

/**
 * RAG 检索结果
 */
@Data
@Builder
public class RagRetrievalResult {

    /** 场景 */
    private String scenario;

    /** 查询文本 */
    private String queryText;

    /** 提供者模式（mysql/volcengine/hybrid） */
    private String providerMode;

    /** 是否使用了降级 */
    private boolean fallbackUsed;

    /** 命中列表 */
    private List<RagHit> hits;

    /** 拼接后的上下文文本（供 LLM 使用） */
    private String contextText;

    /** 日志ID */
    private Long logId;

    /** 耗时（毫秒） */
    private long latencyMs;

    /**
     * 单条命中结果
     */
    @Data
    @Builder
    public static class RagHit {
        private Long chunkId;

        private Long documentId;

        private String sourceType;

        private Long sourceRefId;

        private String title;

        private String content;

        private double score;

        private Double normalizedScore;

        private String scoreSemantics;
    }

    /**
     * 是否有命中结果
     */
    public boolean hasHits() {
        return hits != null && !hits.isEmpty();
    }

    /**
     * 获取命中数量
     */
    public int getHitCount() {
        return hits == null ? 0 : hits.size();
    }
}
