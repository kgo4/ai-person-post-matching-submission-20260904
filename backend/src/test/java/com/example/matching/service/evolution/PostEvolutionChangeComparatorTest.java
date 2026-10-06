package com.example.matching.service.evolution;

import com.example.matching.dto.post.JdAbilityItemDTO;
import com.example.matching.entity.evolution.PostEvolutionChangeItem;
import com.example.matching.entity.post.PostAbilityModel;
import com.example.matching.mapper.system.AbilityTagMapper;
import com.example.matching.service.evolution.impl.PostEvolutionChangeComparator;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class PostEvolutionChangeComparatorTest {

    @Test
    void ignoresUntypedCurrentProfileAbilitiesDuringCanonicalComparison() {
        PostEvolutionChangeComparator comparator = new PostEvolutionChangeComparator(mock(AbilityTagMapper.class));
        PostAbilityModel untagged = new PostAbilityModel();
        untagged.setId(99L);
        untagged.setAbilityName("Custom internal workflow");

        assertThat(comparator.compareAbilities(1L, List.of(untagged), List.of())).isEmpty();
    }

    @Test
    void emitsOneChangeItemPerChangedField() {
        AbilityTagMapper tagMapper = mock(AbilityTagMapper.class);
        PostEvolutionChangeComparator comparator = new PostEvolutionChangeComparator(tagMapper);

        PostAbilityModel current = new PostAbilityModel();
        current.setTagId(7L);
        current.setMinRequiredLevel(2);
        current.setWeight(new BigDecimal("10"));
        current.setIsCore(0);

        JdAbilityItemDTO incoming = new JdAbilityItemDTO();
        incoming.setMatchedTagId(7L);
        incoming.setMatchedTagName("Java");
        incoming.setMinRequiredLevel(4);
        incoming.setWeight(new BigDecimal("20"));
        incoming.setIsCore(1);

        List<PostEvolutionChangeItem> changes = comparator.compareAbilities(1L, List.of(current), List.of(incoming));

        assertThat(changes).extracting(PostEvolutionChangeItem::getChangeType)
                .containsExactlyInAnyOrder("UPDATED_LEVEL", "UPDATED_WEIGHT", "UPDATED_CORE");
    }

    @Test
    void nullExistingWeightProducesAnUpdateInsteadOfThrowing() {
        PostEvolutionChangeComparator comparator = new PostEvolutionChangeComparator(mock(AbilityTagMapper.class));
        PostAbilityModel current = new PostAbilityModel();
        current.setTagId(7L);
        current.setWeight(null);

        JdAbilityItemDTO incoming = new JdAbilityItemDTO();
        incoming.setMatchedTagId(7L);
        incoming.setWeight(new BigDecimal("20"));

        List<PostEvolutionChangeItem> changes = comparator.compareAbilities(1L, List.of(current), List.of(incoming));

        assertThat(changes).anySatisfy(change -> {
            assertThat(change.getChangeType()).isEqualTo("UPDATED_WEIGHT");
            assertThat(change.getOldWeight()).isNull();
            assertThat(change.getNewWeight()).isEqualByComparingTo("20");
        });
    }
}
