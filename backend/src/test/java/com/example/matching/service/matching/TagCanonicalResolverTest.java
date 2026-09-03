package com.example.matching.service.matching;

import com.example.matching.common.enums.RelationStatusEnum;
import com.example.matching.common.enums.RelationTypeEnum;
import com.example.matching.entity.system.AbilityTag;
import com.example.matching.entity.system.AbilityTagRelation;
import com.example.matching.port.tag.TagQueryPort;
import com.example.matching.port.tag.TagQueryPort.TagCanonicalMap;
import com.example.matching.port.tag.TagQueryPort.TagDTO;
import com.example.matching.port.tag.TagQueryPort.TagRelationDTO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TagCanonicalResolverTest {

    @Mock
    private TagQueryPort tagQueryPort;

    private TagCanonicalResolver resolver;

    @BeforeEach
    void setUp() {
        resolver = new TagCanonicalResolver(tagQueryPort);
    }

    // ===== getCanonicalTagId =====

    @Test
    void getCanonicalTagId_returnsNull_forNullInput() {
        assertThat(resolver.getCanonicalTagId(null)).isNull();
    }

    @Test
    void getCanonicalTagId_returnsTagId_itself_whenTagNotFound() {
        when(tagQueryPort.getTagById(999L)).thenReturn(null);
        assertThat(resolver.getCanonicalTagId(999L)).isEqualTo(999L);
    }

    @Test
    void getCanonicalTagId_returnsCanonicalTagId_whenSet() {
        when(tagQueryPort.getTagById(100L)).thenReturn(tagDto(100L, 200L));
        assertThat(resolver.getCanonicalTagId(100L)).isEqualTo(200L);
    }

    @Test
    void getCanonicalTagId_returnsSelfId_whenCanonicalIsNull() {
        when(tagQueryPort.getTagById(100L)).thenReturn(tagDto(100L, null));
        assertThat(resolver.getCanonicalTagId(100L)).isEqualTo(100L);
    }

    // ===== batchGetCanonicalTagIds =====

    @Test
    void batchGetCanonicalTagIds_returnsEmptyMap_forNullInput() {
        assertThat(resolver.batchGetCanonicalTagIds(null)).isEmpty();
    }

    @Test
    void batchGetCanonicalTagIds_returnsEmptyMap_forEmptyInput() {
        assertThat(resolver.batchGetCanonicalTagIds(Set.of())).isEmpty();
    }

    @Test
    void batchGetCanonicalTagIds_returnsIdentityForMissingTags() {
        when(tagQueryPort.batchGetCanonicalIds(any())).thenReturn(new TagCanonicalMap(Map.of()));

        Map<Long, Long> result = resolver.batchGetCanonicalTagIds(Set.of(1L, 2L, 3L));

        assertThat(result).containsEntry(1L, 1L);
        assertThat(result).containsEntry(2L, 2L);
        assertThat(result).containsEntry(3L, 3L);
    }

    @Test
    void batchGetCanonicalTagIds_mapsCanonicalCorrectly() {
        when(tagQueryPort.batchGetCanonicalIds(any()))
                .thenReturn(new TagCanonicalMap(Map.of(1L, 10L, 2L, 20L, 3L, 3L)));

        Map<Long, Long> result = resolver.batchGetCanonicalTagIds(Set.of(1L, 2L, 3L, 4L));

        assertThat(result).containsEntry(1L, 10L);
        assertThat(result).containsEntry(2L, 20L);
        assertThat(result).containsEntry(3L, 3L);
        assertThat(result).containsEntry(4L, 4L); // missing tag uses self
    }

    // ===== isSameCanonical =====

    @Test
    void isSameCanonical_returnsFalse_forNullInputs() {
        assertThat(resolver.isSameCanonical(null, 2L)).isFalse();
        assertThat(resolver.isSameCanonical(1L, null)).isFalse();
        assertThat(resolver.isSameCanonical(null, null)).isFalse();
    }

    @Test
    void isSameCanonical_returnsTrue_forSameTagId() {
        assertThat(resolver.isSameCanonical(100L, 100L)).isTrue();
    }

    @Test
    void isSameCanonical_returnsTrue_whenCanonicalIsSame() {
        when(tagQueryPort.getTagById(1L)).thenReturn(tagDto(1L, 100L));
        when(tagQueryPort.getTagById(2L)).thenReturn(tagDto(2L, 100L));

        assertThat(resolver.isSameCanonical(1L, 2L)).isTrue();
    }

    @Test
    void isSameCanonical_returnsFalse_whenCanonicalDiffers() {
        when(tagQueryPort.getTagById(1L)).thenReturn(tagDto(1L, 100L));
        when(tagQueryPort.getTagById(2L)).thenReturn(tagDto(2L, 200L));

        assertThat(resolver.isSameCanonical(1L, 2L)).isFalse();
    }

    // ===== findConfirmedSimilarRelation =====

    @Test
    void findConfirmedSimilarRelation_returnsNull_forNullInputs() {
        assertThat(resolver.findConfirmedSimilarRelation(null, 2L)).isNull();
        assertThat(resolver.findConfirmedSimilarRelation(1L, null)).isNull();
    }

    @Test
    void findConfirmedSimilarRelation_findsForwardRelation() {
        when(tagQueryPort.batchFindConfirmedSimilarRelations(eq(1L), any()))
                .thenReturn(Map.of(2L, new TagRelationDTO(1L, 2L, null)));

        AbilityTagRelation result = resolver.findConfirmedSimilarRelation(1L, 2L);
        assertThat(result).isNotNull();
        assertThat(result.getSourceTagId()).isEqualTo(1L);
        assertThat(result.getTargetTagId()).isEqualTo(2L);
    }

    @Test
    void findConfirmedSimilarRelation_findsReverseRelation() {
        when(tagQueryPort.batchFindConfirmedSimilarRelations(eq(1L), any()))
                .thenReturn(Map.of(2L, new TagRelationDTO(2L, 1L, null)));

        AbilityTagRelation result = resolver.findConfirmedSimilarRelation(1L, 2L);
        assertThat(result).isNotNull();
        assertThat(result.getSourceTagId()).isEqualTo(2L);
    }

    @Test
    void findConfirmedSimilarRelation_returnsNull_whenNeitherDirectionExists() {
        when(tagQueryPort.batchFindConfirmedSimilarRelations(eq(1L), any())).thenReturn(Map.of());

        assertThat(resolver.findConfirmedSimilarRelation(1L, 2L)).isNull();
    }

    // ===== getSimilarCoefficient =====

    @Test
    void getSimilarCoefficient_returnsDefault_whenRelationIsNull() {
        BigDecimal coeff = resolver.getSimilarCoefficient(null);
        assertThat(coeff).isEqualByComparingTo("0.92");
    }

    @Test
    void getSimilarCoefficient_returnsDefault_whenScoreIsNull() {
        AbilityTagRelation relation = new AbilityTagRelation();
        relation.setSimilarityScore(null);

        BigDecimal coeff = resolver.getSimilarCoefficient(relation);
        assertThat(coeff).isEqualByComparingTo("0.92");
    }

    @Test
    void getSimilarCoefficient_returnsDefault_whenScoreIsZero() {
        AbilityTagRelation relation = new AbilityTagRelation();
        relation.setSimilarityScore(BigDecimal.ZERO);

        BigDecimal coeff = resolver.getSimilarCoefficient(relation);
        assertThat(coeff).isEqualByComparingTo("0.92");
    }

    @Test
    void getSimilarCoefficient_returnsScore_whenValid() {
        AbilityTagRelation relation = new AbilityTagRelation();
        relation.setSimilarityScore(new BigDecimal("0.85"));

        BigDecimal coeff = resolver.getSimilarCoefficient(relation);
        assertThat(coeff).isEqualByComparingTo("0.85");
    }

    // ===== batchFindConfirmedSimilarRelations =====

    @Test
    void batchFindConfirmedSimilarRelations_returnsEmpty_forNullSource() {
        assertThat(resolver.batchFindConfirmedSimilarRelations(null, Set.of(1L))).isEmpty();
    }

    @Test
    void batchFindConfirmedSimilarRelations_returnsEmpty_forNullTargets() {
        assertThat(resolver.batchFindConfirmedSimilarRelations(1L, null)).isEmpty();
    }

    @Test
    void batchFindConfirmedSimilarRelations_returnsEmpty_forEmptyTargets() {
        assertThat(resolver.batchFindConfirmedSimilarRelations(1L, Set.of())).isEmpty();
    }

    @Test
    void batchFindConfirmedSimilarRelations_findsForwardRelations() {
        when(tagQueryPort.batchFindConfirmedSimilarRelations(eq(1L), any()))
                .thenReturn(Map.of(2L, new TagRelationDTO(1L, 2L, new BigDecimal("0.90"))));

        Map<Long, AbilityTagRelation> result = resolver.batchFindConfirmedSimilarRelations(1L, Set.of(2L, 3L));
        assertThat(result).containsKey(2L);
        assertThat(result.get(2L).getSimilarityScore()).isEqualByComparingTo("0.90");
        assertThat(result).doesNotContainKey(3L);
    }

    @Test
    void batchFindConfirmedSimilarRelations_findsReverseRelations() {
        when(tagQueryPort.batchFindConfirmedSimilarRelations(eq(1L), any()))
                .thenReturn(Map.of(2L, new TagRelationDTO(2L, 1L, null)));

        Map<Long, AbilityTagRelation> result = resolver.batchFindConfirmedSimilarRelations(1L, Set.of(2L));
        assertThat(result).containsKey(2L);
        assertThat(result.get(2L).getSourceTagId()).isEqualTo(2L);
    }

    // ===== helpers =====

    private static TagDTO tagDto(Long id, Long canonicalId) {
        return new TagDTO(id, null, null, null, null, null, null, canonicalId, null, null, null, null);
    }
}
