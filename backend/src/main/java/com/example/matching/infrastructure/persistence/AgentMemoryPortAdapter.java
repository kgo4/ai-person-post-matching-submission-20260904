package com.example.matching.infrastructure.persistence;

import com.example.matching.application.agent.AgentMemoryPort;
import com.example.matching.entity.ability.AgentMemory;
import com.example.matching.service.ability.AgentMemoryService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@RequiredArgsConstructor
public class AgentMemoryPortAdapter implements AgentMemoryPort {

    private final AgentMemoryService agentMemoryService;

    @Override
    public List<MemoryEntry> searchMemories(String text, String scope) {
        return agentMemoryService.searchMemories(text, scope).stream().map(this::toEntry).toList();
    }

    @Override
    public List<MemoryEntry> searchActiveRules(String text, String scope) {
        return agentMemoryService.searchActiveRules(text, scope).stream().map(this::toEntry).toList();
    }

    @Override
    public void markUsed(Long memoryId) {
        agentMemoryService.markUsed(memoryId);
    }

    @Override
    public void logHit(Long memoryId, String agentName, String sourceType, Long sourceRefId,
                       String hitText, String hitContextJson, String outcome) {
        agentMemoryService.logHit(memoryId, agentName, sourceType, sourceRefId, hitText, hitContextJson, outcome);
    }

    @Override
    public void markUsedAndLogHit(Long memoryId, String agentName, String sourceType, Long sourceRefId,
                                  String hitText, String hitContextJson, String outcome) {
        agentMemoryService.markUsedAndLogHit(memoryId, agentName, sourceType, sourceRefId, hitText, hitContextJson, outcome);
    }

    @Override
    public void supersedeByRuleKey(String ruleKey, Long excludeId) {
        agentMemoryService.supersedeByRuleKey(ruleKey, excludeId);
    }

    @Override
    public List<MemoryEntry> getTagNormalizeMemories() {
        return agentMemoryService.getTagNormalizeMemories().stream().map(this::toEntry).toList();
    }

    @Override
    public List<MemoryEntry> getTagRejectMemories() {
        return agentMemoryService.getTagRejectMemories().stream().map(this::toEntry).toList();
    }

    private MemoryEntry toEntry(AgentMemory memory) {
        return new MemoryEntry(memory.getId(), memory.getMemoryType(), memory.getTitle(), memory.getContent(),
                memory.getTriggerExpressionsJson(), memory.getApplicableScope(),
                memory.getPriority() == null ? 0 : memory.getPriority(),
                memory.getRulePayloadJson(), memory.getRuleStrength(), memory.getRuleKey());
    }
}
