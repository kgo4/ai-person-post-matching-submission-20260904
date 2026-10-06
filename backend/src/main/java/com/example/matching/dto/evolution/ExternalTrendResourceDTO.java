package com.example.matching.dto.evolution;

import java.util.List;

public record ExternalTrendResourceDTO(
        String title,
        String contentType,
        String contentId,
        String summary,
        String url,
        Integer commentCount,
        Integer voteUpCount,
        String sourceType,
        boolean verifiedEvidence,
        boolean jdFact
) {
    public ExternalTrendResourceDTO withText(String newTitle, String newSummary, String newUrl) {
        return new ExternalTrendResourceDTO(newTitle, contentType, contentId, newSummary, newUrl,
                commentCount, voteUpCount, sourceType, verifiedEvidence, jdFact);
    }
}
