package com.example.matching.service.matching;

import com.example.matching.dto.matching.MatchingAbilitySnapshot;
import com.example.matching.dto.matching.MatchingEmployeeProfile;
import com.example.matching.dto.matching.MatchingPostProfile;
import com.example.matching.dto.matching.MatchingRequirementSnapshot;
import com.example.matching.service.rag.RagRetrievalRequest;
import com.example.matching.service.rag.RagRetrievalResult;
import com.example.matching.service.rag.RagRetrievalService;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class RagScoreServiceTest {

    @Test
    void rewardsHigherRetrievalRelevanceInsteadOfLongerRetrievedText() {
        RagRetrievalService retrievalService = mock(RagRetrievalService.class);
        when(retrievalService.retrieve(any(RagRetrievalRequest.class))).thenReturn(result(
                List.of(hit("short", 95D))
        ));
        BigDecimal highRelevanceScore = new RagScoreService(retrievalService)
                .calculateRagScore(employee(), post(), List.of(employeeAbility()), List.of(requirement()));

        when(retrievalService.retrieve(any(RagRetrievalRequest.class))).thenReturn(result(
                List.of(hit("A very long low-relevance text ".repeat(100), 5D))
        ));
        BigDecimal lowRelevanceScore = new RagScoreService(retrievalService)
                .calculateRagScore(employee(), post(), List.of(employeeAbility()), List.of(requirement()));

        assertTrue(highRelevanceScore.compareTo(lowRelevanceScore) > 0);
    }

    private RagRetrievalResult result(List<RagRetrievalResult.RagHit> hits) {
        return RagRetrievalResult.builder()
                .scenario("MATCHING_ANALYSIS")
                .hits(hits)
                .contextText("")
                .build();
    }

    private RagRetrievalResult.RagHit hit(String content, double score) {
        return RagRetrievalResult.RagHit.builder()
                .chunkId(1L)
                .documentId(1L)
                .sourceType("TEST")
                .title("test")
                .content(content)
                .score(score)
                .build();
    }

    private MatchingEmployeeProfile employee() {
        return new MatchingEmployeeProfile(1L, "E001", "张三", null, null, null, List.of());
    }

    private MatchingPostProfile post() {
        return new MatchingPostProfile(2L, "P2", "Engineer", null, null, null, List.of());
    }

    private MatchingAbilitySnapshot employeeAbility() {
        return new MatchingAbilitySnapshot(null, 10L, "Java", 3, null, "MANUAL", null, null);
    }

    private MatchingRequirementSnapshot requirement() {
        return new MatchingRequirementSnapshot(10L, "Java", 3, null, null, null, null);
    }
}
