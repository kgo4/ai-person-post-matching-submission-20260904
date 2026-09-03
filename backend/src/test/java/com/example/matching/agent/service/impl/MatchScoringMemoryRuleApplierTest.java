package com.example.matching.agent.service.impl;

import com.example.matching.application.agent.AgentMemoryPort;
import com.example.matching.entity.matching.MatchingRecord;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class MatchScoringMemoryRuleApplierTest {

    @Test
    void doesNotBlockWhenEmployeeHasRequiredTag() {
        AgentMemoryContextService contextService = mock(AgentMemoryContextService.class);
        AgentMemoryPort memoryPort = mock(AgentMemoryPort.class);
        AgentMemoryPort.MemoryEntry rule = new AgentMemoryPort.MemoryEntry(
                1L, "MATCH_REQUIRE_TAG", "required", "required tag", "[]",
                AgentMemoryContextService.SCOPE_MATCHING, 1,
                "{\"action\":\"MATCH_REQUIRE_TAG\",\"params\":{\"requiredTagId\":7}}",
                "HARD", "required-tag");
        when(contextService.resolveRules(any(), any())).thenReturn(
                new AgentMemoryContextService.ContextRules(List.of(rule), List.of(), "", ""));

        MatchScoringMemoryRuleApplier applier = new MatchScoringMemoryRuleApplier(
                contextService, memoryPort, new ObjectMapper());

        MatchScoringMemoryRuleApplier.MemoryApplyResult result = applier.apply(
                new MatchingRecord(), "employee", "post", Set.of(7L));

        assertThat(result.excluded()).isFalse();
        assertThat(result.appliedActions()).contains("MATCH_REQUIRE_TAG:7");
    }
}
