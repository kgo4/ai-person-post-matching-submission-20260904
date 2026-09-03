package com.example.matching.service.matching.algorithm;

import com.example.matching.ai.service.VectorEmbeddingService;
import com.example.matching.dto.matching.MatchingAbilitySnapshot;
import com.example.matching.dto.matching.MatchingRequirementSnapshot;
import com.example.matching.port.tag.TagQueryPort;
import com.example.matching.service.matching.TagCanonicalResolver;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.math.BigDecimal;
import com.example.matching.common.enums.MatchTypeEnum;
import com.example.matching.dto.matching.MatchDetailDTO;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SemanticMatchEngineTest {

    @Mock private TagCanonicalResolver tagCanonicalResolver;
    @Mock private VectorEmbeddingService vectorEmbeddingService;
    @Mock private TagQueryPort tagQueryPort;
    @Mock private RedisTemplate<String, Object> redisTemplate;
    @Mock private ValueOperations<String, Object> valueOperations;

    @Test
    void matchesUntaggedFormalAbilityByNameWithoutCallingTagInfrastructure() {
        SemanticMatchEngine engine = new SemanticMatchEngine(
                tagCanonicalResolver, vectorEmbeddingService, tagQueryPort, redisTemplate);
        MatchingAbilitySnapshot employeeAbility = new MatchingAbilitySnapshot(
                88L, null, "服务器部署", 3, null, "PROFILE_FUSED", null, null);
        MatchingRequirementSnapshot requirement = new MatchingRequirementSnapshot(
                10L, "服务器部署", 3, null, 1, 1, null);

        var details = engine.performSemanticMatching(Map.of(88L, new java.math.BigDecimal("3.00")),
                List.of(employeeAbility), List.of(requirement));

        assertThat(details).singleElement().satisfies(detail -> {
            assertThat(detail.getMatchType().getCode()).isEqualTo("EXACT");
            assertThat(detail.getMatchedEmpAbilityId()).isEqualTo(88L);
            assertThat(detail.getMatchedEmpTagId()).isNull();
            assertThat(detail.isPassed()).isTrue();
            assertThat(detail.getMatchCoefficient()).isEqualByComparingTo("1.00");
        });
    }


    @Test
    void previewDoesNotEmbedUnmappedAbilityNames() {
        SemanticMatchEngine engine = new SemanticMatchEngine(
                tagCanonicalResolver, vectorEmbeddingService, tagQueryPort, redisTemplate);
        MatchingAbilitySnapshot employeeAbility = new MatchingAbilitySnapshot(
                88L, null, "Kotlin", 3, null, "PROFILE_FUSED", null, null);
        MatchingRequirementSnapshot requirement = new MatchingRequirementSnapshot(
                null, "Java", 3, null, 1, 1, null);

        var details = engine.performSemanticMatching(Map.of(88L, new java.math.BigDecimal("3.00")),
                List.of(employeeAbility), List.of(requirement), false);

        assertThat(details).singleElement().satisfies(detail ->
                assertThat(detail.getMatchType().getCode()).isEqualTo("NONE"));
        verify(vectorEmbeddingService, never()).embed(anyString());
    }

    @Test
    void matchesUntaggedAbilitiesBySemanticNameWhenLabelsDiffer() {
        SemanticMatchEngine engine = new SemanticMatchEngine(
                tagCanonicalResolver, vectorEmbeddingService, tagQueryPort, redisTemplate);
        List<Float> vector = List.of(1.0f, 0.0f, 0.0f);
        when(vectorEmbeddingService.embed(anyString())).thenReturn(vector);
        when(vectorEmbeddingService.cosineSimilarity(vector, vector)).thenReturn(1.0f);

        MatchingAbilitySnapshot employeeAbility = new MatchingAbilitySnapshot(
                88L, null, "Redis", 3, null, "PROFILE_FUSED", null, null);
        MatchingRequirementSnapshot requirement = new MatchingRequirementSnapshot(
                null, "Redis缓存", 3, null, 1, 1, null);

        var details = engine.performSemanticMatching(Map.of(88L, new BigDecimal("3.00")),
                List.of(employeeAbility), List.of(requirement));

        assertThat(details).singleElement().satisfies(detail -> {
            assertThat(detail.getMatchType()).isEqualTo(MatchTypeEnum.SEMANTIC_FALLBACK);
            assertThat(detail.getSimilarityScore()).isEqualByComparingTo("1.00");
            assertThat(detail.isPassed()).isTrue();
        });
    }

    @Test
    void matchesRelatedNamesWhenVectorsAreUnavailable() {
        SemanticMatchEngine engine = new SemanticMatchEngine(
                tagCanonicalResolver, vectorEmbeddingService, tagQueryPort, redisTemplate);
        MatchingAbilitySnapshot employeeAbility = new MatchingAbilitySnapshot(
                88L, null, "Redis", 3, null, "PROFILE_FUSED", null, null);
        MatchingRequirementSnapshot requirement = new MatchingRequirementSnapshot(
                null, "Redis缓存", 3, null, 1, 1, null);

        var details = engine.performSemanticMatching(Map.of(88L, new BigDecimal("3.00")),
                List.of(employeeAbility), List.of(requirement));

        assertThat(details).singleElement().satisfies(detail -> {
            assertThat(detail.getMatchType()).isEqualTo(MatchTypeEnum.SEMANTIC_FALLBACK);
            assertThat(detail.getSimilarityScore()).isGreaterThanOrEqualTo(new BigDecimal("0.85"));
            assertThat(detail.isPassed()).isTrue();
        });
    }

    @Test
    void formalAbilityMatchDoesNotRequireEqualTagIds() {
        SemanticMatchEngine engine = new SemanticMatchEngine(
                tagCanonicalResolver, vectorEmbeddingService, tagQueryPort, redisTemplate);
        List<Float> vector = List.of(1.0f, 0.0f, 0.0f);
        when(vectorEmbeddingService.embed(anyString())).thenReturn(vector);
        when(vectorEmbeddingService.cosineSimilarity(vector, vector)).thenReturn(0.91f);

        MatchingAbilitySnapshot employeeAbility = new MatchingAbilitySnapshot(
                88L, 100L, "Redis", 3, null, "MANUAL", null, null);
        MatchingRequirementSnapshot requirement = new MatchingRequirementSnapshot(
                200L, "缓存中间件设计", 3, new BigDecimal("100"), 1, 1, null);

        var details = engine.performSemanticMatching(Map.of(88L, new BigDecimal("3.00")),
                List.of(employeeAbility), List.of(requirement));

        assertThat(details).singleElement().satisfies(detail -> {
            assertThat(detail.getMatchType()).isEqualTo(MatchTypeEnum.SEMANTIC_FALLBACK);
            assertThat(detail.getMatchedEmpAbilityId()).isEqualTo(88L);
            assertThat(detail.isPassed()).isTrue();
        });
    }

    @Test
    void matchesFormattingVariantsWithoutTagIdsBeforeVectorFallback() {
        SemanticMatchEngine engine = new SemanticMatchEngine(
                tagCanonicalResolver, vectorEmbeddingService, tagQueryPort, redisTemplate);
        MatchingAbilitySnapshot employeeAbility = new MatchingAbilitySnapshot(
                88L, null, "SpringBoot", 3, null, "PROFILE_FUSED", null, null);
        MatchingRequirementSnapshot requirement = new MatchingRequirementSnapshot(
                null, "Spring Boot", 3, null, 1, 1, null);

        var details = engine.performSemanticMatching(Map.of(88L, new BigDecimal("3.00")),
                List.of(employeeAbility), List.of(requirement));

        assertThat(details).singleElement().satisfies(detail ->
                assertThat(detail.getMatchType()).isEqualTo(MatchTypeEnum.EXACT));
    }

    @Test
    void batchesRuntimeEmbeddingsOnceForDistinctAbilityNames() {
        SemanticMatchEngine engine = new SemanticMatchEngine(
                tagCanonicalResolver, vectorEmbeddingService, tagQueryPort, redisTemplate);
        List<Float> vector = List.of(1.0f, 0.0f, 0.0f);
        when(vectorEmbeddingService.embedBatch(anyList()))
                .thenReturn(List.of(vector, vector));
        when(vectorEmbeddingService.cosineSimilarity(anyList(), anyList())).thenReturn(0.91f);

        MatchingAbilitySnapshot employeeAbility = new MatchingAbilitySnapshot(
                88L, null, "Redis缓存", 3, null, "PROFILE_FUSED", null, null);
        MatchingRequirementSnapshot requirement = new MatchingRequirementSnapshot(
                null, "缓存中间件", 3, null, 1, 1, null);

        var details = engine.performSemanticMatching(Map.of(88L, new BigDecimal("3.00")),
                List.of(employeeAbility), List.of(requirement));

        assertThat(details).singleElement()
                .extracting(MatchDetailDTO::getMatchType)
                .isEqualTo(MatchTypeEnum.SEMANTIC_FALLBACK);
        verify(vectorEmbeddingService).embedBatch(anyList());
        verify(vectorEmbeddingService, never()).embed(anyString());
    }

    @Test
    void doesNotRetryEveryNameWhenBatchEmbeddingReturnsOnlyEmptyVectors() {
        SemanticMatchEngine engine = new SemanticMatchEngine(
                tagCanonicalResolver, vectorEmbeddingService, tagQueryPort, redisTemplate);
        when(vectorEmbeddingService.embedBatch(anyList())).thenReturn(List.of(List.of(), List.of()));

        MatchingAbilitySnapshot employeeAbility = new MatchingAbilitySnapshot(
                88L, null, "Redis缓存", 3, null, "PROFILE_FUSED", null, null);
        MatchingRequirementSnapshot requirement = new MatchingRequirementSnapshot(
                null, "缓存中间件", 3, null, 1, 1, null);

        var details = engine.performSemanticMatching(Map.of(88L, new BigDecimal("3.00")),
                List.of(employeeAbility), List.of(requirement));

        assertThat(details).singleElement()
                .extracting(MatchDetailDTO::getMatchType)
                .isEqualTo(MatchTypeEnum.NONE);
        verify(vectorEmbeddingService).embedBatch(anyList());
        verify(vectorEmbeddingService, never()).embed(anyString());
    }

    @Test
    void keepsPartialScoreForRelatedRequiredAbilityWithoutApplyingASecondPenalty() {
        SemanticMatchEngine engine = new SemanticMatchEngine(
                tagCanonicalResolver, vectorEmbeddingService, tagQueryPort, redisTemplate);
        MatchingRequirementSnapshot requirement = new MatchingRequirementSnapshot(
                10L, "Redis", 3, new BigDecimal("100"), 1, 1, null);
        MatchDetailDTO detail = new MatchDetailDTO();
        detail.setMatchType(MatchTypeEnum.CONFIRMED_SIMILAR);
        detail.setEffectiveLevel(new BigDecimal("2.40"));
        detail.setRequiredLevel(3);
        detail.setRequired(true);
        detail.setPassed(false);

        BigDecimal score = engine.calculateAbilityCompatibilityScore(List.of(detail), List.of(requirement));

        assertThat(score).isEqualByComparingTo("80.00");
    }
}
