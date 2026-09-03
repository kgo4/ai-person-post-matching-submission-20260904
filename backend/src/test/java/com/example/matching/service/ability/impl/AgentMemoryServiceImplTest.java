package com.example.matching.service.ability.impl;

import com.example.matching.ai.service.VectorEmbeddingService;
import com.example.matching.entity.ability.AgentMemory;
import com.example.matching.mapper.ability.AgentMemoryHitLogMapper;
import com.example.matching.mapper.ability.PersonAbilityGovernanceEventMapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.util.List;
import java.lang.reflect.Field;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.when;

class AgentMemoryServiceImplTest {

    @Test
    void ruleKeyUsesFullCanonicalSha256OfConditionAndAction() {
        String first = """
                {"reason":"first","action":{"targetTagId":17,"kind":"NORMALIZE_TO_TAG"},
                 "condition":{"sourceTerms":["springboot"],"candidateTagId":42}}
                """;
        String reordered = """
                {"condition":{"candidateTagId":42,"sourceTerms":["springboot"]},
                 "action":{"kind":"NORMALIZE_TO_TAG","targetTagId":17},"reason":"second"}
                """;

        String firstKey = AgentMemoryServiceImpl.computeRuleKey("EMPLOYEE_ABILITY_EXTRACTION", "TAG_NORMALIZE", first);
        String reorderedKey = AgentMemoryServiceImpl.computeRuleKey("EMPLOYEE_ABILITY_EXTRACTION", "TAG_NORMALIZE", reordered);

        assertThat(firstKey).hasSize(64).isEqualTo(reorderedKey);
    }

    @Test
    void matchesConfiguredJsonTriggerExpressions() {
        AgentMemory memory = new AgentMemory();
        memory.setId(1L);
        memory.setTriggerExpressionsJson("[\"Kafka\", \"Kubernetes\"]");

        AgentMemoryServiceImpl service = serviceWith(memory);

        assertThat(service.searchMemories("We operate Kafka clusters", "AI_INTERVIEW"))
                .containsExactly(memory);
    }

    @Test
    void fallsBackToTitleWhenNoTriggerExpressionsAreConfigured() {
        AgentMemory memory = new AgentMemory();
        memory.setId(2L);
        memory.setTitle("Evidence corroboration");
        memory.setTriggerExpressionsJson(null);

        AgentMemoryServiceImpl service = serviceWith(memory);

        assertThat(service.searchMemories("Need evidence corroboration for this claim", "AI_INTERVIEW"))
                .containsExactly(memory);
    }

    @Test
    void retrievesStoredMemoryBySemanticSimilarityWhenNoLiteralTermMatches() throws Exception {
        AgentMemory memory = new AgentMemory();
        memory.setId(3L);
        memory.setTitle("Canonical backend framework policy");
        memory.setContent("Use the approved service framework for asynchronous processing.");
        memory.setEmbeddingVector(List.of(1.0f, 0.0f));

        AgentMemoryServiceImpl service = serviceWith(memory);
        VectorEmbeddingService embeddingService = mock(VectorEmbeddingService.class);
        when(embeddingService.embed("Build event-driven server components"))
                .thenReturn(List.of(0.9f, 0.1f));
        when(embeddingService.cosineSimilarity(List.of(0.9f, 0.1f), memory.getEmbeddingVector()))
                .thenReturn(0.91f);
        Field engineField = AgentMemoryServiceImpl.class.getDeclaredField("searchEngine");
        engineField.setAccessible(true);
        AgentMemorySearchEngine searchEngine = (AgentMemorySearchEngine) engineField.get(service);
        Field vectorService = AgentMemorySearchEngine.class.getDeclaredField("vectorEmbeddingService");
        vectorService.setAccessible(true);
        vectorService.set(searchEngine, embeddingService);

        assertThat(service.searchMemories("Build event-driven server components", "AI_INTERVIEW"))
                .containsExactly(memory);
    }

    @Test
    void versionedUpdateCreatesNewRowAndSupersedesOldOne() throws Exception {
        AgentMemorySearchEngine searchEngine = mock(AgentMemorySearchEngine.class);
        AgentMemoryServiceImpl service = mock(AgentMemoryServiceImpl.class);
        org.mockito.Mockito.doCallRealMethod().when(service).createMemory(org.mockito.ArgumentMatchers.any(AgentMemory.class));

        String ruleKey = "abc-rule-key";
        AgentMemory existing = new AgentMemory();
        existing.setId(1L);
        existing.setRuleKey(ruleKey);
        existing.setStatus("ACTIVE");
        existing.setRevision(1);
        existing.setMemoryType("TAG_NORMALIZE");
        existing.setApplicableScope("ALL");
        existing.setRulePayloadJson("{\"condition\":{\"c\":1},\"action\":{\"a\":1}}");

        org.mockito.Mockito.doReturn(existing).when(service)
                .getOne(org.mockito.ArgumentMatchers.any());
        org.mockito.Mockito.doReturn(true).when(service)
                .save(org.mockito.ArgumentMatchers.any(AgentMemory.class));
        org.mockito.Mockito.doReturn(true).when(service)
                .updateById(org.mockito.ArgumentMatchers.any(AgentMemory.class));

        Field engineField = AgentMemoryServiceImpl.class.getDeclaredField("searchEngine");
        engineField.setAccessible(true);
        engineField.set(service, searchEngine);
        Field eventField = AgentMemoryServiceImpl.class.getDeclaredField("eventMapper");
        eventField.setAccessible(true);
        eventField.set(service, mock(PersonAbilityGovernanceEventMapper.class));
        Field epochField = AgentMemoryServiceImpl.class.getDeclaredField("cacheEpoch");
        epochField.setAccessible(true);
        epochField.set(service, mock(MemorySearchCacheEpoch.class));

        AgentMemory updated = new AgentMemory();
        updated.setRuleKey(ruleKey);
        updated.setStatus("ACTIVE");
        updated.setMemoryType("TAG_NORMALIZE");
        updated.setApplicableScope("ALL");
        updated.setRulePayloadJson("{\"condition\":{\"c\":2},\"action\":{\"a\":2}}");

        service.createMemory(updated);

        org.mockito.Mockito.verify(service).save(org.mockito.ArgumentMatchers.argThat(m ->
                m.getId() == null
                        && m.getSupersedesMemoryId() != null
                        && m.getSupersedesMemoryId().equals(1L)
                        && m.getRevision() != null
                        && m.getRevision() == 2
                        && "ACTIVE".equals(m.getStatus())));
        org.mockito.Mockito.verify(service).updateById(org.mockito.ArgumentMatchers.argThat(m ->
                "SUPERSEDED".equals(m.getStatus()) && m.getId().equals(1L)));
    }

    private AgentMemoryServiceImpl serviceWith(AgentMemory memory) {
        StringRedisTemplate stringRedisTemplate = mock(StringRedisTemplate.class);
        MemorySearchCacheEpoch cacheEpoch = new MemorySearchCacheEpoch(stringRedisTemplate);
        AgentMemorySearchEngine searchEngine = spy(new AgentMemorySearchEngine(
                new ObjectMapper(), null, cacheEpoch));
        AgentMemoryServiceImpl service = spy(new AgentMemoryServiceImpl(
                mock(PersonAbilityGovernanceEventMapper.class),
                mock(AgentMemoryHitLogMapper.class),
                new ObjectMapper(), searchEngine, cacheEpoch));
        doReturn(List.of(memory)).when(searchEngine).getActiveMemories("AI_INTERVIEW");
        return service;
    }
}
