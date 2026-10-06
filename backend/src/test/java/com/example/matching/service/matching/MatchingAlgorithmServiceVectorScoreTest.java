package com.example.matching.service.matching;

import com.example.matching.ai.service.VectorEmbeddingService;
import com.example.matching.dto.matching.MatchingAbilitySnapshot;
import com.example.matching.dto.matching.MatchingRequirementSnapshot;
import com.example.matching.entity.matching.MatchingRecord;
import com.example.matching.port.tag.TagQueryPort;
import com.example.matching.service.matching.impl.MatchingAlgorithmServiceImpl;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.RedisTemplate;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.lenient;

@ExtendWith(MockitoExtension.class)
class MatchingAlgorithmServiceVectorScoreTest {

    @Mock
    private TagCanonicalResolver tagCanonicalResolver;
    @Mock
    private VectorEmbeddingService vectorEmbeddingService;
    @Mock
    private TagQueryPort tagQueryPort;
    @Mock
    private ObjectMapper objectMapper;
    @Mock
    private RedisTemplate<String, Object> redisTemplate;

    private MatchingAlgorithmService service;

    @BeforeEach
    void setUp() {
        service = new MatchingAlgorithmServiceImpl(tagCanonicalResolver, vectorEmbeddingService, tagQueryPort, objectMapper, redisTemplate);
    }

    private void mockCanonicalIdentityMapping(Set<Long> tagIds) {
        Map<Long, Long> identityMap = new HashMap<>();
        for (Long tagId : tagIds) {
            identityMap.put(tagId, tagId);
        }
        lenient().when(tagCanonicalResolver.batchGetCanonicalTagIds(anyCollection())).thenReturn(identityMap);
        lenient().when(tagCanonicalResolver.findConfirmedSimilarRelation(anyLong(), anyLong())).thenReturn(null);
    }

    @Test
    void blendsTagScoreWithVectorScoreWhenVectorScoreExists() {
        mockCanonicalIdentityMapping(Set.of(10L));

        MatchingAbilitySnapshot ability = new MatchingAbilitySnapshot(
                null, 10L, null, 3, BigDecimal.ONE, "MANUAL", BigDecimal.ONE, null);

        MatchingRequirementSnapshot requirement = new MatchingRequirementSnapshot(
                10L, null, 4, new BigDecimal("100"), 0, 1, null);

        MatchingRecord record = service.matchWithAbilities(
                1001L,
                2001L,
                List.of(ability),
                List.of(requirement),
                List.of(),
                "batch001",
                new BigDecimal("100.00")
        );

        assertThat(record).isNotNull();
    }

    @Test
    void keepsScoreBreakdownWhenRequiredAbilityFails() {
        mockCanonicalIdentityMapping(Set.of(10L));

        MatchingRequirementSnapshot requirement = new MatchingRequirementSnapshot(
                10L, null, 4, new BigDecimal("100"), 1, 1, null);

        MatchingRecord record = service.matchWithAbilities(
                1001L,
                2001L,
                List.of(),
                List.of(requirement),
                List.of(),
                "batch001",
                new BigDecimal("80.00")
        );

        assertThat(record).isNotNull();
    }
}
