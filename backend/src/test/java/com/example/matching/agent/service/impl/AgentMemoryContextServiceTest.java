package com.example.matching.agent.service.impl;

import com.example.matching.agent.config.AgentMemoryProperties;
import com.example.matching.application.agent.AgentMemoryPort;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AgentMemoryContextServiceTest {

    @Test
    void keepsGlobalPriorityOrderWhileApplyingPerTypeAndTotalLimits() {
        AgentMemoryPort port = mock(AgentMemoryPort.class);
        List<AgentMemoryPort.MemoryEntry> entries = List.of(
                entry(1L, "TAG_NORMALIZE", 100), entry(2L, "TAG_NORMALIZE", 99),
                entry(3L, "TAG_NORMALIZE", 98), entry(4L, "TAG_NORMALIZE", 97),
                entry(5L, "TAG_REJECT", 96), entry(6L, "TAG_REJECT", 95),
                entry(7L, "TAG_REJECT", 94), entry(8L, "LEVEL_RULE", 93),
                entry(9L, "LEVEL_RULE", 92), entry(10L, "LEVEL_RULE", 91),
                entry(11L, "SOURCE_POLICY", 90));
        when(port.searchActiveRules("java", AgentMemoryContextService.SCOPE_EMPLOYEE)).thenReturn(entries);

        AgentMemoryContextService.ContextRules rules = new AgentMemoryContextService(port, new ObjectMapper(), new AgentMemoryProperties())
                .resolveRules(" Java ", AgentMemoryContextService.SCOPE_EMPLOYEE);

        assertThat(rules.hardRules()).extracting(AgentMemoryPort.MemoryEntry::id)
                .containsExactly(1L, 2L, 3L, 5L, 6L, 7L, 8L, 9L);
    }

    private AgentMemoryPort.MemoryEntry entry(Long id, String type, int priority) {
        return new AgentMemoryPort.MemoryEntry(id, type, type, "rule", "[]",
                AgentMemoryContextService.SCOPE_EMPLOYEE, priority, "{}", "HARD", "key" + id);
    }
}
