package com.example.matching.application.ability;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.example.matching.common.dto.PageResponse;
import com.example.matching.dto.ability.api.AgentMemoryResponse;
import com.example.matching.dto.ability.api.AgentMemoryUpdateRequest;
import com.example.matching.dto.ability.api.GovernanceEventResponse;
import com.example.matching.dto.ability.api.GovernanceEventQuery;
import com.example.matching.entity.ability.AgentMemory;
import com.example.matching.entity.ability.PersonAbilityGovernanceEvent;
import com.example.matching.service.ability.AgentMemoryService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class AgentMemoryGovernanceApiFacade {

    private final AgentMemoryService agentMemoryService;

    public PageResponse<AgentMemoryResponse> pageMemories(Integer pageNum, Integer pageSize,
                                                          String status, String memoryType,
                                                          String scope, String keyword) {
        Page<AgentMemory> page = agentMemoryService.pageMemories(pageNum, pageSize, status, memoryType, scope, keyword);
        return PageResponse.from(page, this::toMemoryResponse);
    }

    public AgentMemoryResponse getById(Long id) {
        AgentMemory memory = agentMemoryService.getById(id);
        return toMemoryResponse(memory);
    }

    public void update(Long id, AgentMemoryUpdateRequest request) {
        AgentMemory memory = new AgentMemory();
        memory.setId(id);
        memory.setTitle(request.title());
        memory.setContent(request.content());
        memory.setApplicableScope(request.applicableScope());
        memory.setPriority(request.priority());
        agentMemoryService.updateById(memory);
    }

    public void enable(Long id) {
        agentMemoryService.enableMemory(id);
    }

    public void disable(Long id) {
        agentMemoryService.disableMemory(id);
    }

    public void expire(Long id) {
        agentMemoryService.expireMemory(id);
    }

    public GovernanceEventResponse getSourceEvent(Long id) {
        AgentMemory memory = agentMemoryService.getById(id);
        if (memory == null || memory.getSourceEventId() == null) {
            return null;
        }
        return toEventResponse(agentMemoryService.getEventById(memory.getSourceEventId()));
    }

    public PageResponse<GovernanceEventResponse> pageEvents(GovernanceEventQuery query) {
        Page<PersonAbilityGovernanceEvent> page = agentMemoryService.pageEvents(
                query.pageNum(), query.pageSize(), query.modifyType(), query.empId(), query.tagId());
        return PageResponse.from(page, this::toEventResponse);
    }

    public GovernanceEventResponse getEventById(Long id) {
        PersonAbilityGovernanceEvent event = agentMemoryService.getEventById(id);
        return toEventResponse(event);
    }

    AgentMemoryResponse toMemoryResponse(AgentMemory e) {
        if (e == null) return null;
        return new AgentMemoryResponse(
                e.getId(), e.getMemoryType(), e.getTitle(), e.getContent(),
                e.getTriggerExpressionsJson(), e.getRulePayloadJson(), e.getRuleStrength(),
                e.getRuleKey(),
                e.getApplicableScope(), e.getPriority(),
                e.getStatus(), e.getSourceEventId(), e.getUseCount(),
                e.getLastUsedTime(), e.getExpireTime(), e.getCreatedBy(),
                e.getCreatedTime(), e.getUpdatedTime()
        );
    }

    GovernanceEventResponse toEventResponse(PersonAbilityGovernanceEvent e) {
        if (e == null) return null;
        return new GovernanceEventResponse(
                e.getId(), e.getEmpId(), e.getOldTagId(), e.getOldTagName(),
                e.getNewTagId(), e.getNewTagName(), e.getOldLevel(), e.getNewLevel(),
                e.getOldConfidence(), e.getNewConfidence(), e.getSourceBreakdownJson(),
                e.getEvidenceSnapshotJson(), e.getModifyType(), e.getModifyReason(),
                e.getTemplatePayloadJson(), e.getGenerateMemory(), e.getMemoryId(),
                e.getCreatedBy(), e.getCreatedTime()
        );
    }
}
