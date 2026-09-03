package com.example.matching.service.evolution.support;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.example.matching.entity.system.AbilityTag;
import com.example.matching.service.system.AbilityTagService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class EvolutionAbilityTagResolverTest {

    @Mock
    private AbilityTagService abilityTagService;

    @Test
    void resolve_returnsTheLongestActiveTagContainedInEvidence() {
        when(abilityTagService.list(org.mockito.ArgumentMatchers.<Wrapper<AbilityTag>>any())).thenReturn(List.of(
                abilityTag(1L, "Java"),
                abilityTag(2L, "Spring Boot")
        ));

        EvolutionAbilityTagResolver resolver = new EvolutionAbilityTagResolver(new EvolutionAbilityTagCatalog(abilityTagService));

        ResolvedEvolutionAbility resolved = resolver.resolve("候选人需要具备 Java 与 Spring Boot 开发经验");

        assertThat(resolved).isNotNull();
        assertThat(resolved.tagId()).isEqualTo(2L);
        assertThat(resolved.abilityName()).isEqualTo("Spring Boot");
    }

    @Test
    void resolve_returnsNullWhenEvidenceDoesNotContainAnActiveTag() {
        when(abilityTagService.list(org.mockito.ArgumentMatchers.<Wrapper<AbilityTag>>any())).thenReturn(List.of(abilityTag(1L, "Kubernetes")));

        EvolutionAbilityTagResolver resolver = new EvolutionAbilityTagResolver(new EvolutionAbilityTagCatalog(abilityTagService));

        ResolvedEvolutionAbility resolved = resolver.resolve("加强跨团队协作与交付能力");

        assertThat(resolved).isNull();
    }

    private AbilityTag abilityTag(Long id, String tagName) {
        AbilityTag tag = new AbilityTag();
        tag.setId(id);
        tag.setTagName(tagName);
        tag.setStatus(1);
        tag.setIsDeleted(0);
        return tag;
    }
}
