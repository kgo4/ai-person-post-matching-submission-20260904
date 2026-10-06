package com.example.matching.infrastructure.persistence;

import com.example.matching.application.agent.AgentMemoryPort;
import com.example.matching.entity.ability.AgentMemory;
import com.example.matching.service.ability.AgentMemoryService;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AgentMemoryPortAdapterTest {

    @Test
    void mapsSearchResultsToApplicationMemoryEntries() {
        AgentMemoryService memoryService = mock(AgentMemoryService.class);
        AgentMemory memory = new AgentMemory();
        memory.setId(7L);
        memory.setMemoryType("TAG_NORMALIZE");
        memory.setTitle("Normalize Java");
        memory.setContent("Use the canonical Java tag");
        memory.setTriggerExpressionsJson("[\"Java\"]");
        memory.setApplicableScope("ALL");
        memory.setPriority(10);
        when(memoryService.searchMemories("Java", "AI_INTERVIEW")).thenReturn(List.of(memory));

        AgentMemoryPort port = new AgentMemoryPortAdapter(memoryService);

        assertThat(port.searchMemories("Java", "AI_INTERVIEW"))
                .containsExactly(new AgentMemoryPort.MemoryEntry(7L, "TAG_NORMALIZE", "Normalize Java",
                        "Use the canonical Java tag", "[\"Java\"]", "ALL", 10));
    }

    @Test
    void delegatesUsageTracking() {
        AgentMemoryService memoryService = mock(AgentMemoryService.class);

        new AgentMemoryPortAdapter(memoryService).markUsed(7L);

        verify(memoryService).markUsed(7L);
    }
}
