package com.example.matching.service.matching;

import com.example.matching.ai.service.VectorEmbeddingService;
import com.example.matching.dto.matching.MatchingAbilitySnapshot;
import com.example.matching.dto.matching.MatchingRequirementSnapshot;
import com.example.matching.entity.matching.MatchingRecord;
import com.example.matching.port.tag.TagQueryPort;
import com.example.matching.service.matching.impl.MatchingAlgorithmServiceImpl;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MatchingAlgorithmServiceReportTest {

    @Mock
    private TagCanonicalResolver tagCanonicalResolver;
    @Mock
    private VectorEmbeddingService vectorEmbeddingService;
    @Mock
    private TagQueryPort tagQueryPort;

    private MatchingAlgorithmService service;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    void setUp() {
        service = new MatchingAlgorithmServiceImpl(tagCanonicalResolver, vectorEmbeddingService, tagQueryPort, objectMapper);
    }

    @Test
    void generateReport_usesMatchedEmployeeTagEvidence_whenCanonicalMatch() throws Exception {
        when(tagCanonicalResolver.batchGetCanonicalTagIds(anyCollection()))
                .thenReturn(Map.of(10L, 100L))
                .thenReturn(Map.of(11L, 100L));

        MatchingAbilitySnapshot empAbility = new MatchingAbilitySnapshot(
                null, 11L, "Java后端开发", 4, BigDecimal.ONE, "MANUAL", BigDecimal.ONE, null);

        MatchingRequirementSnapshot requirement = new MatchingRequirementSnapshot(
                10L, "Java开发", 3, new BigDecimal("100"), 0, 0, null);

        MatchingRecord record = new MatchingRecord();
        record.setL2Score(new BigDecimal("100.00"));
        record.setAiMatchScore(new BigDecimal("100.00"));
        record.setMatchStatus(1);

        String report = service.generateReport(
                record,
                "张三",
                "Java后端工程师",
                List.of(empAbility),
                List.of(requirement),
                Map.of(10L, "Java开发", 11L, "Java后端开发")
        );

        JsonNode root = objectMapper.readTree(report);
        JsonNode detail = root.path("abilityDetails").get(0);

        assertThat(detail.path("matchType").asText()).isEqualTo("CANONICAL");
        assertThat(detail.path("matchedEmpTagId").asLong()).isEqualTo(11L);
        assertThat(detail.path("evidences")).hasSize(1);
        assertThat(detail.path("evidences").get(0).path("source").asText()).isEqualTo("MANUAL");
        assertThat(detail.path("weakEvidence").asBoolean()).isFalse();
    }
}
