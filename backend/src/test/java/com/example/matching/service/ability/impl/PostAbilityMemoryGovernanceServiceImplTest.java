package com.example.matching.service.ability.impl;

import com.example.matching.dto.post.PostAbilityModelConfigDTO;
import com.example.matching.entity.ability.AgentMemory;
import com.example.matching.entity.system.AbilityTag;
import com.example.matching.mapper.system.AbilityTagMapper;
import com.example.matching.service.ability.AgentMemoryService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PostAbilityMemoryGovernanceServiceImplTest {

    @Test
    void doesNotGenerateRuleWithoutExplicitGeneralizationFlag() {
        AgentMemoryService memoryService = mock(AgentMemoryService.class);
        PostAbilityMemoryGovernanceServiceImpl service = new PostAbilityMemoryGovernanceServiceImpl(
                memoryService, mock(AbilityTagMapper.class), new ObjectMapper());

        PostAbilityModelConfigDTO dto = new PostAbilityModelConfigDTO();
        dto.setTagId(8L);

        service.createFutureJdExtractionRule(dto);

        verify(memoryService, never()).createMemory(any());
    }

    @Test
    void createsActivePostExtractionGuidanceOnlyWhenExplicitlyRequested() {
        AgentMemoryService memoryService = mock(AgentMemoryService.class);
        AbilityTagMapper tagMapper = mock(AbilityTagMapper.class);
        AbilityTag tag = new AbilityTag();
        tag.setId(8L);
        tag.setTagName("Spring Boot");
        when(tagMapper.selectById(8L)).thenReturn(tag);
        PostAbilityMemoryGovernanceServiceImpl service = new PostAbilityMemoryGovernanceServiceImpl(
                memoryService, tagMapper, new ObjectMapper());

        PostAbilityModelConfigDTO dto = new PostAbilityModelConfigDTO();
        dto.setTagId(8L);
        dto.setGenerateFutureJdRule(true);
        dto.setRemark("Interpret this term as the platform capability.");

        service.createFutureJdExtractionRule(dto);

        org.mockito.ArgumentCaptor<AgentMemory> captured = org.mockito.ArgumentCaptor.forClass(AgentMemory.class);
        verify(memoryService).createMemory(captured.capture());
        assertThat(captured.getValue())
                .extracting(AgentMemory::getApplicableScope, AgentMemory::getMemoryType,
                        AgentMemory::getStatus, AgentMemory::getRuleStrength)
                .containsExactly("POST_ABILITY_EXTRACTION", "TERM_INTERPRETATION", "ACTIVE", "GUIDANCE");
    }
}
