package com.example.matching.service.evolution.support;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.example.matching.config.RedisCacheNames;
import com.example.matching.entity.system.AbilityTag;
import com.example.matching.service.system.AbilityTagService;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Provides cached active ability tags for evolution processing.
 */
@Component
@RequiredArgsConstructor
public class EvolutionAbilityTagCatalog {

    private final AbilityTagService abilityTagService;

    @Cacheable(cacheNames = RedisCacheNames.EVOLUTION_ACTIVE_ABILITY_TAGS, key = "'evolution:active'", sync = true)
    public List<AbilityTag> activeTags() {
        return abilityTagService.list(new LambdaQueryWrapper<AbilityTag>()
                .eq(AbilityTag::getStatus, 1)
                .eq(AbilityTag::getIsDeleted, 0));
    }
}
