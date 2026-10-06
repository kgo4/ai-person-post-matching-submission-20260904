package com.example.matching.port.contest;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 竞赛/证据域查询端口 — 公开只读接口。
 */
public interface ContestQueryPort {

    record ContestEvidenceDTO(
            Long id,
            String sourceType,
            Long sourceRefId,
            String sourceTitle,
            String sourceText,
            String targetType,
            Long targetRefId,
            String abilityName,
            Long tagId,
            BigDecimal confidenceScore,
            BigDecimal credibilityScore,
            String evidenceStatus,
            LocalDateTime createdTime
    ) {
        public static ContestEvidenceDTO from(com.example.matching.entity.contest.ContestEvidenceItem e) {
            return new ContestEvidenceDTO(e.getId(), e.getSourceType(), e.getSourceRefId(),
                    e.getSourceTitle(), e.getSourceText(), e.getTargetType(), e.getTargetRefId(),
                    e.getAbilityName(), e.getTagId(), e.getConfidenceScore(), e.getCredibilityScore(),
                    e.getEvidenceStatus(), e.getCreatedTime());
        }
    }

    record EvidenceWriteCommand(
            String evidenceCode,
            String sourceType,
            Long sourceRefId,
            String sourceTitle,
            String sourceText,
            String targetType,
            Long targetRefId,
            String abilityName,
            Long tagId,
            BigDecimal confidenceScore,
            BigDecimal credibilityScore,
            List<Long> ragChunkIds,
            List<Long> ragDocumentIds
    ) {}

    List<ContestEvidenceDTO> listAllEvidence(int limit);

    List<ContestEvidenceDTO> listEvidencePaginated(int page, int size);

    boolean evidenceExists(String sourceType, Long sourceRefId, String targetType, Long targetRefId);

    void saveEvidence(EvidenceWriteCommand command);
}
