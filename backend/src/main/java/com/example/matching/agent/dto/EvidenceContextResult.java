package com.example.matching.agent.dto;

import java.util.List;

/**
 * 证据上下文工具结果 - 将已验证证据与 RAG 引用严格分离。
 * <p>
 * {@code verifiedEvidence} 只包含数据库中的已验证证据；{@code ragReferences}
 * 只包含检索到的参考片段，无可信度分数、不可独立支持 PASS/通过决策。
 */
public record EvidenceContextResult(
        List<VerifiedEvidence> verifiedEvidence,
        List<RagReference> ragReferences,
        boolean degraded,
        String notice
) {

    public static EvidenceContextResult empty(String notice) {
        return new EvidenceContextResult(List.of(), List.of(), true, notice);
    }

    /**
     * 数据库已验证证据，携带受控 sourceRef。
     */
    public record VerifiedEvidence(
            Long id,
            String evidenceCode,
            String sourceType,
            String sourceTitle,
            String sourceText,
            String abilityName,
            Long tagId,
            String targetType,
            Long targetRefId,
            Double confidenceScore,
            Double credibilityScore,
            String evidenceStatus,
            String sourceRef
    ) {
    }

    /**
     * RAG 检索参考片段 - 无 credibility 字段，明确非决定性。
     */
    public record RagReference(
            String documentId,
            String chunkId,
            String sourceType,
            String title,
            String content,
            Float score
    ) {
    }
}
