package com.example.matching.application.ability;

import com.example.matching.dto.ability.api.AgentMemoryUpdateRequest;
import com.example.matching.dto.ability.api.GovernanceEventResponse;
import com.example.matching.entity.ability.AgentMemory;
import com.example.matching.entity.ability.PersonAbilityGovernanceEvent;
import com.example.matching.service.ability.AgentMemoryService;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AgentMemoryGovernanceApiFacadeTest {

    @Test
    void returnsTheEventReferencedByTheMemory() {
        AgentMemoryService memoryService = mock(AgentMemoryService.class);
        AgentMemory memory = new AgentMemory();
        memory.setSourceEventId(12L);
        PersonAbilityGovernanceEvent event = new PersonAbilityGovernanceEvent();
        event.setId(12L);
        event.setModifyType("TAG_REPLACE");
        when(memoryService.getById(8L)).thenReturn(memory);
        when(memoryService.getEventById(12L)).thenReturn(event);

        GovernanceEventResponse response = new AgentMemoryGovernanceApiFacade(memoryService).getSourceEvent(8L);

        assertThat(response.id()).isEqualTo(12L);
        assertThat(response.modifyType()).isEqualTo("TAG_REPLACE");
    }

    @Test
    void updateOnlyBuildsTheEditableMemoryFields() {
        AgentMemoryService memoryService = mock(AgentMemoryService.class);
        AgentMemoryGovernanceApiFacade facade = new AgentMemoryGovernanceApiFacade(memoryService);

        facade.update(8L, new AgentMemoryUpdateRequest("Updated", "New rule", 20, "AI_INTERVIEW"));

        ArgumentCaptor<AgentMemory> captor = ArgumentCaptor.forClass(AgentMemory.class);
        verify(memoryService).updateById(captor.capture());
        AgentMemory updated = captor.getValue();
        assertThat(updated.getId()).isEqualTo(8L);
        assertThat(updated.getTitle()).isEqualTo("Updated");
        assertThat(updated.getContent()).isEqualTo("New rule");
        assertThat(updated.getPriority()).isEqualTo(20);
        assertThat(updated.getApplicableScope()).isEqualTo("AI_INTERVIEW");
        assertThat(updated.getStatus()).isNull();
        assertThat(updated.getSourceEventId()).isNull();
        assertThat(updated.getUseCount()).isNull();
    }
}
