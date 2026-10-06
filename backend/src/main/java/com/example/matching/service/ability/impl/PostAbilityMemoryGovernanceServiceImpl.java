package com.example.matching.service.ability.impl;

import com.example.matching.dto.post.PostAbilityModelConfigDTO;
import com.example.matching.entity.ability.AgentMemory;
import com.example.matching.entity.system.AbilityTag;
import com.example.matching.mapper.system.AbilityTagMapper;
import com.example.matching.service.ability.AgentMemoryService;
import com.example.matching.service.ability.PostAbilityMemoryGovernanceService;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class PostAbilityMemoryGovernanceServiceImpl implements PostAbilityMemoryGovernanceService {

    private static final String SCOPE = "POST_ABILITY_EXTRACTION";

    private final AgentMemoryService agentMemoryService;
    private final AbilityTagMapper abilityTagMapper;
    private final ObjectMapper objectMapper;

    @Override
    public void createFutureJdExtractionRule(PostAbilityModelConfigDTO dto) {
        if (dto == null || !dto.isGenerateFutureJdRule() || dto.getTagId() == null) return;

        AbilityTag tag = abilityTagMapper.selectById(dto.getTagId());
        if (tag == null || tag.getTagName() == null || tag.getTagName().isBlank()) {
            log.warn("Skip future JD rule because ability tag is unavailable: tagId={}", dto.getTagId());
            return;
        }

        String payload = buildPayload(tag, dto.getRemark());
        AgentMemory memory = new AgentMemory();
        memory.setMemoryType("TERM_INTERPRETATION");
        memory.setTitle("JD interpretation: " + tag.getTagName());
        memory.setContent(dto.getRemark() == null || dto.getRemark().isBlank()
                ? "Treat \"" + tag.getTagName() + "\" as the confirmed ability tag in future JD extraction."
                : dto.getRemark());
        memory.setTriggerExpressionsJson("[\"" + escapeJson(tag.getTagName()) + "\"]");
        memory.setApplicableScope(SCOPE);
        memory.setPriority(60);
        memory.setStatus("ACTIVE");
        memory.setRuleStrength("GUIDANCE");
        memory.setRulePayloadJson(payload);
        memory.setRuleKey(AgentMemoryServiceImpl.computeRuleKey(SCOPE, memory.getMemoryType(), payload));
        agentMemoryService.createMemory(memory);
    }

    private String buildPayload(AbilityTag tag, String reason) {
        try {
            ObjectNode root = objectMapper.createObjectNode();
            ObjectNode condition = root.putObject("condition");
            condition.putArray("sourceTerms").add(tag.getTagName());
            condition.put("candidateTagId", tag.getId());
            ObjectNode action = root.putObject("action");
            action.put("kind", "INTERPRET_AS_TAG");
            action.put("targetTagId", tag.getId());
            action.put("targetName", tag.getTagName());
            root.put("reason", reason == null ? "Administrator-confirmed JD interpretation" : reason);
            root.put("ruleStrength", "GUIDANCE");
            return objectMapper.writeValueAsString(root);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to build future JD extraction rule", e);
        }
    }

    private String escapeJson(String value) {
        return value.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}
