package com.example.matching.agent.service.impl;

import com.example.matching.agent.config.AgentMemoryProperties;
import com.example.matching.agent.dto.person.PersonAbilityClaim;
import com.example.matching.agent.dto.person.PersonAbilityExtractionResult;
import com.example.matching.agent.dto.post.PostAbilityClaim;
import com.example.matching.agent.dto.post.PostAbilityExtractionResult;
import com.example.matching.application.agent.AgentMemoryPort;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class AgentMemoryRuleEnforcerTest {

    private final AgentMemoryPort memoryPort = mock(AgentMemoryPort.class);
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final AgentMemoryContextService memoryContextService =
            new AgentMemoryContextService(memoryPort, objectMapper, new AgentMemoryProperties());
    private final AgentMemoryRuleEnforcer enforcer =
            new AgentMemoryRuleEnforcer(memoryPort, objectMapper, memoryContextService);

    @Test
    void normalizesLlmOldTagWithHardRuleAndAuditsCodeApplication() {
        PersonAbilityClaim claim = claim("SpringBoot", 42L, 3);

        enforcer.enforce(result(claim), rules(entry(1L, "TAG_NORMALIZE", """
                {"condition":{"sourceTerms":["SpringBoot"]},
                 "action":{"kind":"NORMALIZE_TO_TAG","targetTagId":17,"targetName":"Spring Boot"}}
                """)), "RESUME", 88L, "SpringBoot service");

        assertThat(claim.getAbilityTagId()).isEqualTo(17L);
        assertThat(claim.getNormalizedAbilityName()).isEqualTo("Spring Boot");
        verify(memoryPort).markUsedAndLogHit(eq(1L), eq("EMPLOYEE_ABILITY_EXTRACTION"), eq("RESUME"),
                eq(88L), eq("SpringBoot"), org.mockito.ArgumentMatchers.anyString(), eq("APPLIED_BY_CODE"));
    }

    @Test
    void removesRejectedCandidateAndAuditsValidationRejection() {
        PersonAbilityClaim claim = claim("Office proficiency", 42L, 3);
        PersonAbilityExtractionResult result = result(claim);

        enforcer.enforce(result, rules(entry(2L, "TAG_REJECT", """
                {"condition":{"sourceTerms":["Office"]},"action":{"kind":"REJECT"}}
                """)), "RESUME", 88L, "Office proficiency");

        assertThat(result.getClaims()).isEmpty();
        verify(memoryPort).markUsedAndLogHit(eq(2L), eq("EMPLOYEE_ABILITY_EXTRACTION"), eq("RESUME"),
                eq(88L), eq("Office proficiency"), org.mockito.ArgumentMatchers.anyString(), eq("REJECTED_BY_VALIDATION"));
    }

    @Test
    void capsLevelReturnedByLlmWithHardRule() {
        PersonAbilityClaim claim = claim("Java", 7L, 5);

        enforcer.enforce(result(claim), rules(entry(3L, "LEVEL_RULE", """
                {"condition":{"sourceTerms":["Java"]},
                 "action":{"kind":"ENFORCE_MAX_LEVEL","maxLevel":2}}
                """)), "COURSE", 88L, "Java course");

        assertThat(claim.getMasteryLevel()).isEqualTo(2);
        verify(memoryPort).markUsedAndLogHit(eq(3L), eq("EMPLOYEE_ABILITY_EXTRACTION"), eq("COURSE"),
                eq(88L), eq("Java"), org.mockito.ArgumentMatchers.anyString(), eq("APPLIED_BY_CODE"));
    }

    @Test
    void capsPostRequiredLevelWithHardRule() {
        PostAbilityClaim claim = new PostAbilityClaim();
        claim.setAbilityName("Java");
        claim.setNormalizedAbilityName("Java");
        claim.setRequiredLevel(5);
        PostAbilityExtractionResult result = new PostAbilityExtractionResult();
        result.setClaims(new ArrayList<>(List.of(claim)));

        enforcer.enforcePost(result, rules(entry(4L, "LEVEL_RULE", """
                {"condition":{"sourceTerms":["Java"]},
                 "action":{"kind":"ENFORCE_MAX_LEVEL","maxLevel":3}}
                """)), "JD_IMPORT", 99L, "Senior Java engineer");

        assertThat(claim.getRequiredLevel()).isEqualTo(3);
        verify(memoryPort).markUsedAndLogHit(eq(4L), eq("POST_ABILITY_EXTRACTION"), eq("JD_IMPORT"),
                eq(99L), eq("Java"), org.mockito.ArgumentMatchers.anyString(), eq("APPLIED_BY_CODE"));
    }

    private PersonAbilityExtractionResult result(PersonAbilityClaim claim) {
        PersonAbilityExtractionResult result = new PersonAbilityExtractionResult();
        result.setClaims(new ArrayList<>(List.of(claim)));
        return result;
    }

    private AgentMemoryContextService.ContextRules rules(AgentMemoryPort.MemoryEntry entry) {
        return new AgentMemoryContextService.ContextRules(List.of(entry), List.of(), "", "");
    }

    private AgentMemoryPort.MemoryEntry entry(Long id, String type, String payload) {
        return new AgentMemoryPort.MemoryEntry(id, type, type, type, "[]",
                "EMPLOYEE_ABILITY_EXTRACTION", 1, payload, "HARD", "key" + id);
    }

    private PersonAbilityClaim claim(String name, Long tagId, int level) {
        PersonAbilityClaim claim = new PersonAbilityClaim();
        claim.setAbilityName(name);
        claim.setNormalizedAbilityName(name);
        claim.setAbilityTagId(tagId);
        claim.setMasteryLevel(level);
        return claim;
    }
}
