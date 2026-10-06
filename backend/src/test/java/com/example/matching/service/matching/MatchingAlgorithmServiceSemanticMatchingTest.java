package com.example.matching.service.matching;

import com.example.matching.ai.service.VectorEmbeddingService;
import com.example.matching.common.enums.MatchTypeEnum;
import com.example.matching.dto.matching.MatchDetailDTO;
import com.example.matching.dto.matching.MatchingAbilitySnapshot;
import com.example.matching.dto.matching.MatchingRequirementSnapshot;
import com.example.matching.port.tag.TagQueryPort;
import com.example.matching.service.matching.impl.MatchingAlgorithmServiceImpl;
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
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.when;

/**
 * 现行匹配契约：人员正式能力表与岗位能力表的 abilityName 是主数据；
 * tagId 只用于返回元数据，不能作为匹配门槛。
 */
@ExtendWith(MockitoExtension.class)
class MatchingAlgorithmServiceSemanticMatchingTest {

    @Mock private TagCanonicalResolver tagCanonicalResolver;
    @Mock private VectorEmbeddingService vectorEmbeddingService;
    @Mock private TagQueryPort tagQueryPort;
    @Mock private ObjectMapper objectMapper;

    private MatchingAlgorithmService service;

    @BeforeEach
    void setUp() {
        service = new MatchingAlgorithmServiceImpl(tagCanonicalResolver, vectorEmbeddingService, tagQueryPort, objectMapper);
    }

    @Test
    void matchesSameFormalAbilityNameExactlyWhenTagIdsDiffer() {
        var employee = ability(101L, 11L, "Java", 3);
        var requirement = requirement(21L, "Java", 2, false, false);

        var result = service.performSemanticMatching(levels(employee), List.of(employee), List.of(requirement));

        assertThat(result).singleElement().satisfies(detail -> {
            assertThat(detail.getMatchType()).isEqualTo(MatchTypeEnum.EXACT);
            assertThat(detail.getMatchedEmpAbilityId()).isEqualTo(101L);
            assertThat(detail.getMatchedEmpTagId()).isEqualTo(11L);
            assertThat(detail.isPassed()).isTrue();
        });
    }

    @Test
    void normalizesDisplayFormattingBeforeExactNameComparison() {
        var employee = ability(101L, 11L, "Spring Boot", 3);
        var requirement = requirement(21L, "spring-boot", 2, false, false);

        var result = service.performSemanticMatching(levels(employee), List.of(employee), List.of(requirement));

        assertThat(result).singleElement().extracting(MatchDetailDTO::getMatchType)
                .isEqualTo(MatchTypeEnum.EXACT);
    }

    @Test
    void usesLexicalFallbackForContainedAbilityNames() {
        var employee = ability(101L, 11L, "Java", 3);
        var requirement = requirement(21L, "Java Backend Development", 2, true, false);

        var result = service.performSemanticMatching(levels(employee), List.of(employee), List.of(requirement));

        assertThat(result).singleElement().satisfies(detail -> {
            assertThat(detail.getMatchType()).isEqualTo(MatchTypeEnum.SEMANTIC_FALLBACK);
            assertThat(detail.getSimilarityScore()).isEqualByComparingTo("0.86");
            assertThat(detail.isPassed()).isTrue();
        });
    }

    @Test
    void usesEmbeddingFallbackForDifferentButSemanticallyRelatedNames() {
        var employee = ability(101L, 11L, "Redis", 3);
        var requirement = requirement(21L, "Distributed Cache", 2, false, false);
        when(vectorEmbeddingService.embedBatch(anyList())).thenReturn(List.of(
                List.of(1.0f, 0.0f), List.of(0.96f, 0.1f)));
        when(vectorEmbeddingService.cosineSimilarity(anyList(), anyList())).thenReturn(0.91f);

        var result = service.performSemanticMatching(levels(employee), List.of(employee), List.of(requirement));

        assertThat(result).singleElement().satisfies(detail -> {
            assertThat(detail.getMatchType()).isEqualTo(MatchTypeEnum.SEMANTIC_FALLBACK);
            assertThat(detail.getSimilarityScore()).isEqualByComparingTo("0.91");
            assertThat(detail.getMatchedEmpAbilityName()).isEqualTo("Redis");
        });
    }

    @Test
    void keepsUnrelatedAbilityUnmatchedWhenEmbeddingScoreIsBelowThreshold() {
        var employee = ability(101L, 11L, "Kubernetes", 3);
        var requirement = requirement(21L, "Distributed Cache", 2, false, false);
        when(vectorEmbeddingService.embedBatch(anyList())).thenReturn(List.of(
                List.of(1.0f, 0.0f), List.of(0.0f, 1.0f)));
        when(vectorEmbeddingService.cosineSimilarity(anyList(), anyList())).thenReturn(0.10f);

        var result = service.performSemanticMatching(levels(employee), List.of(employee), List.of(requirement));

        assertThat(result).singleElement().satisfies(detail -> {
            assertThat(detail.getMatchType()).isEqualTo(MatchTypeEnum.NONE);
            assertThat(detail.getMatchedEmpAbilityId()).isNull();
        });
    }

    @Test
    void assignsEachEmployeeAbilityToAtMostOneRequirement() {
        var java = ability(101L, 11L, "Java", 3);
        var spring = ability(102L, 12L, "Spring Boot", 3);
        var javaRequirement = requirement(21L, "Java", 2, true, false);
        var springRequirement = requirement(22L, "Spring Boot", 2, true, false);

        var result = service.performSemanticMatching(levels(java, spring), List.of(java, spring),
                List.of(javaRequirement, springRequirement));

        assertThat(result).extracting(MatchDetailDTO::getMatchedEmpAbilityId)
                .containsExactly(101L, 102L);
        assertThat(result).allMatch(detail -> detail.getMatchType() == MatchTypeEnum.EXACT);
    }

    private static MatchingAbilitySnapshot ability(Long abilityId, Long tagId, String name, int level) {
        return new MatchingAbilitySnapshot(abilityId, tagId, name, level, null, "MANUAL", null, null);
    }

    private static MatchingRequirementSnapshot requirement(Long tagId, String name, int level,
                                                             boolean required, boolean core) {
        return new MatchingRequirementSnapshot(tagId, name, level, new BigDecimal("100"),
                required ? 1 : 0, core ? 1 : 0, null);
    }

    private static Map<Long, BigDecimal> levels(MatchingAbilitySnapshot... abilities) {
        return java.util.Arrays.stream(abilities).collect(java.util.stream.Collectors.toMap(
                MatchingAbilitySnapshot::abilityId,
                ability -> BigDecimal.valueOf(ability.level())));
    }
}
