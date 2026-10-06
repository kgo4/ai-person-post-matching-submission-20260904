package com.example.matching.service.evolution.support;

import com.example.matching.entity.system.AbilityTag;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.Comparator;
import java.util.Optional;

/**
 * Resolves evidence text to an existing active ability tag without creating new tags.
 */
@Component
@RequiredArgsConstructor
public class EvolutionAbilityTagResolver {

    private final EvolutionAbilityTagCatalog abilityTagCatalog;

    public ResolvedEvolutionAbility resolve(String evidenceText) {
        if (!StringUtils.hasText(evidenceText)) {
            return null;
        }

        return abilityTagCatalog.activeTags().stream()
                .filter(tag -> StringUtils.hasText(tag.getTagName()))
                .filter(tag -> evidenceText.contains(tag.getTagName()))
                .max(Comparator
                        .comparingInt((AbilityTag tag) -> tag.getTagName().length())
                        .thenComparingLong(tag -> Optional.ofNullable(tag.getId()).orElse(Long.MIN_VALUE)))
                .map(tag -> new ResolvedEvolutionAbility(
                        Optional.ofNullable(tag.getCanonicalTagId()).orElse(tag.getId()),
                        tag.getTagName(),
                        1.0D))
                .orElse(null);
    }

}
