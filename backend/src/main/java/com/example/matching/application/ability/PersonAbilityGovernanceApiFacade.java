package com.example.matching.application.ability;

import com.example.matching.common.dto.PageResponse;
import com.example.matching.dto.ability.api.*;
import com.example.matching.entity.ability.AgentMemory;
import com.example.matching.entity.ability.PersonAbilityGovernanceEvent;
import com.example.matching.service.ability.AgentMemoryService;
import com.example.matching.service.ability.PersonAbilityGovernanceService;
import com.example.matching.utils.SecurityUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class PersonAbilityGovernanceApiFacade {

    private final PersonAbilityGovernanceService governanceService;
    private final AgentMemoryService agentMemoryService;

    public GovernanceEventResponse replaceTag(ReplaceTagRequest request) {
        Long operatorId = SecurityUtils.getCurrentUserId();
        PersonAbilityGovernanceEvent event = governanceService.replaceTag(
                request.empId(), request.oldTagId(), request.newTagId(),
                request.reason(), operatorId, request.generalizeRule());
        return toEventResponse(event);
    }

    public GovernanceEventResponse changeLevel(ChangeLevelRequest request) {
        Long operatorId = SecurityUtils.getCurrentUserId();
        PersonAbilityGovernanceEvent event = governanceService.changeLevel(
                request.empId(), request.tagId(), request.newLevel(),
                request.reason(), operatorId, request.generalizeRule(), request.maxLevelCap());
        return toEventResponse(event);
    }

    public GovernanceEventResponse removeTag(Long empId, Long tagId, String reason) {
        Long operatorId = SecurityUtils.getCurrentUserId();
        PersonAbilityGovernanceEvent event = governanceService.removeTag(empId, tagId, reason, operatorId);
        return toEventResponse(event);
    }

    public GovernanceEventResponse removeTag(Long empId, Long tagId, String reason, boolean generalizeRule) {
        Long operatorId = SecurityUtils.getCurrentUserId();
        PersonAbilityGovernanceEvent event = governanceService.removeTag(empId, tagId, reason, operatorId, generalizeRule);
        return toEventResponse(event);
    }

    public List<GovernanceEventResponse> renameTag(RenameTagRequest request) {
        Long operatorId = SecurityUtils.getCurrentUserId();
        List<PersonAbilityGovernanceEvent> events = governanceService.renameTag(
                request.tagId(), request.newName(), request.reason(), operatorId);
        return events.stream().map(this::toEventResponse).toList();
    }

    public List<GovernanceEventResponse> getGovernanceHistory(Long empId) {
        return governanceService.getGovernanceHistory(empId).stream()
                .map(this::toEventResponse).toList();
    }

    public List<GovernanceEventResponse> getGovernanceByTag(Long tagId) {
        return governanceService.getGovernanceByTag(tagId).stream()
                .map(this::toEventResponse).toList();
    }

    public List<AgentMemoryResponse> getMemories(String scope) {
        return agentMemoryService.getActiveMemories(scope).stream()
                .map(this::toMemoryResponse).toList();
    }

    public List<AgentMemoryResponse> searchMemories(String text, String scope) {
        return agentMemoryService.searchMemories(text, scope).stream()
                .map(this::toMemoryResponse).toList();
    }

    public List<GovernanceEventResponse> renameTagEvents(RenameTagRequest request) {
        Long operatorId = SecurityUtils.getCurrentUserId();
        List<PersonAbilityGovernanceEvent> events = governanceService.renameTag(
                request.tagId(), request.newName(), request.reason(), operatorId);
        return events.stream().map(this::toEventResponse).toList();
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
}
