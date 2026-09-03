package com.example.matching.service.rag;

import com.example.matching.entity.rag.RagKnowledgeChunk;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

public class KnowledgeSearchHit {

    private final String chunkId;
    private final String documentId;
    private final String sourceType;
    private final String title;
    private final String content;

    @Deprecated
    private final float score;

    private final Map<String, Object> metadata;
    private final Double normalizedScore;
    private final String scoreSemantics;
    private final String normalizationMethod;
    private final Double rawScore;

    public KnowledgeSearchHit(String chunkId, String documentId, String sourceType,
                              String title, String content, float score,
                              Map<String, Object> metadata,
                              Double normalizedScore, String scoreSemantics,
                              String normalizationMethod, Double rawScore) {
        this.chunkId = chunkId;
        this.documentId = documentId;
        this.sourceType = sourceType;
        this.title = title;
        this.content = content;
        this.score = score;
        this.metadata = metadata != null ? metadata : new LinkedHashMap<>();
        this.normalizedScore = normalizedScore;
        this.scoreSemantics = scoreSemantics;
        this.normalizationMethod = normalizationMethod;
        this.rawScore = rawScore;
    }

    public KnowledgeSearchHit(String chunkId, String documentId, String sourceType,
                              String title, String content, float score,
                              Map<String, Object> metadata) {
        this(chunkId, documentId, sourceType, title, content, score, metadata,
                null, null, null, null);
    }

    public static KnowledgeSearchHit fromMysqlChunk(RagKnowledgeChunk chunk, float score, String sourceType, String title) {
        return fromMysqlChunk(chunk, score, sourceType, title, (double) score, "RRF", "RRF_K60", null);
    }

    public static KnowledgeSearchHit fromMysqlChunk(RagKnowledgeChunk chunk, float score, String sourceType, String title,
                                                     Double normalizedScore, String scoreSemantics,
                                                     String normalizationMethod, Double rawScore) {
        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("backend", "mysql");
        metadata.put("documentId", chunk.getDocumentId());
        metadata.put("chunkIndex", chunk.getChunkIndex());
        return new KnowledgeSearchHit(
                "mysql:" + chunk.getId(),
                "mysql-doc:" + chunk.getDocumentId(),
                sourceType,
                title,
                chunk.getChunkText(),
                score,
                metadata,
                normalizedScore,
                scoreSemantics,
                normalizationMethod,
                rawScore
        );
    }

    public String chunkId() {
        return chunkId;
    }

    public String documentId() {
        return documentId;
    }

    public String sourceType() {
        return sourceType;
    }

    public String title() {
        return title;
    }

    public String content() {
        return content;
    }

    @Deprecated
    public float score() {
        return score;
    }

    public Map<String, Object> metadata() {
        return metadata;
    }

    public Double normalizedScore() {
        return normalizedScore;
    }

    public String scoreSemantics() {
        return scoreSemantics;
    }

    public String normalizationMethod() {
        return normalizationMethod;
    }

    public Double rawScore() {
        return rawScore;
    }

    public double effectiveScore() {
        return normalizedScore != null ? normalizedScore : (double) score;
    }

    public Long mysqlChunkIdOrNull() {
        if (chunkId == null || !chunkId.startsWith("mysql:")) {
            return null;
        }
        try {
            return Long.parseLong(chunkId.substring("mysql:".length()));
        } catch (NumberFormatException e) {
            return null;
        }
    }

    public Long documentIdOrNull() {
        if (documentId == null || !documentId.startsWith("mysql-doc:")) {
            return null;
        }
        try {
            return Long.parseLong(documentId.substring("mysql-doc:".length()));
        } catch (NumberFormatException e) {
            return null;
        }
    }

    public Long sourceRefIdOrNull() {
        if (metadata == null) {
            return null;
        }
        Object refId = metadata.get("sourceRefId");
        if (refId instanceof Long) {
            return (Long) refId;
        }
        if (refId instanceof Number) {
            return ((Number) refId).longValue();
        }
        if (refId instanceof String s && !s.isBlank()) {
            try {
                return Long.parseLong(s.trim());
            } catch (NumberFormatException e) {
                return null;
            }
        }
        return null;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof KnowledgeSearchHit that)) return false;
        return Objects.equals(chunkId, that.chunkId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(chunkId);
    }

    @Override
    public String toString() {
        return "KnowledgeSearchHit[chunkId=" + chunkId + ", sourceType=" + sourceType +
                ", score=" + score + ", normalizedScore=" + normalizedScore +
                ", scoreSemantics=" + scoreSemantics + "]";
    }
}
