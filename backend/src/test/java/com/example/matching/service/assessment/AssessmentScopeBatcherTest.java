package com.example.matching.service.assessment;

import com.example.matching.dto.assessment.AssessmentScopeDTO;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;

class AssessmentScopeBatcherTest {

    @Test
    void partitionsScopeWithoutDroppingOrDuplicatingAbilityTags() {
        AssessmentScopeDTO scope = new AssessmentScopeDTO(1L, 2L, 3L,
                IntStream.rangeClosed(1, 27)
                        .mapToObj(id -> new AssessmentScopeDTO.AssessmentScopeItem(
                                (long) id, "skill-" + id, List.of((long) id), 3,
                                null, null, false, false, null, List.of()))
                        .toList(),
                List.of(), "scope-hash");

        List<AssessmentScopeDTO> batches = AssessmentScopeBatcher.partition(scope, 5);

        assertThat(batches).hasSize(6);
        assertThat(batches).allSatisfy(batch -> assertThat(batch.items()).hasSizeLessThanOrEqualTo(5));
        assertThat(batches.stream().flatMap(batch -> batch.items().stream())
                .map(AssessmentScopeDTO.AssessmentScopeItem::abilityTagId))
                .containsExactlyElementsOf(IntStream.rangeClosed(1, 27).mapToObj(Long::valueOf).toList());
    }
}
