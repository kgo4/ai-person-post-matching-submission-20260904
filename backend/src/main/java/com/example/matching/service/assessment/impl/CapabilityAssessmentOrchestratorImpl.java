package com.example.matching.service.assessment.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.example.matching.dto.assessment.AssessmentBlueprintDTO;
import com.example.matching.dto.assessment.AssessmentScopeDTO;
import com.example.matching.entity.workflow.AssessmentBlueprint;
import com.example.matching.entity.workflow.AssessmentScopeSnapshot;
import com.example.matching.mapper.workflow.AssessmentBlueprintMapper;
import com.example.matching.mapper.workflow.AssessmentScopeSnapshotMapper;
import com.example.matching.service.assessment.AssessmentScopeService;
import com.example.matching.service.assessment.CapabilityAssessmentOrchestrator;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/** Persists the server-owned contracts that every agent must consume. */
@Service
@RequiredArgsConstructor
public class CapabilityAssessmentOrchestratorImpl implements CapabilityAssessmentOrchestrator {
    private static final String TAXONOMY_VERSION = "ABILITY_TAG_TREE_V1";
    private final AssessmentScopeService scopeService;
    private final AssessmentScopeSnapshotMapper scopeSnapshotMapper;
    private final AssessmentBlueprintMapper blueprintMapper;
    private final ObjectMapper objectMapper;

    @Override
    @Transactional
    public AssessmentScopeDTO freezeScope(Long workflowId, Long empId, Long postId) {
        AssessmentScopeSnapshot existing = scopeSnapshotMapper.selectOne(new LambdaQueryWrapper<AssessmentScopeSnapshot>()
                .eq(AssessmentScopeSnapshot::getWorkflowId, workflowId).last("LIMIT 1"));
        if (existing != null) return deserializeScope(existing.getSnapshotJson());
        AssessmentScopeDTO scope = scopeService.build(workflowId, empId, postId);
        AssessmentScopeSnapshot snapshot = new AssessmentScopeSnapshot();
        snapshot.setWorkflowId(workflowId);
        snapshot.setScopeHash(scope.scopeHash());
        snapshot.setTaxonomyVersion(TAXONOMY_VERSION);
        snapshot.setSnapshotJson(serialize(scope));
        snapshot.setCreatedTime(LocalDateTime.now());
        scopeSnapshotMapper.insert(snapshot);
        return scope;
    }

    @Override
    public AssessmentScopeDTO loadScope(Long workflowId) {
        AssessmentScopeSnapshot snapshot = scopeSnapshotMapper.selectOne(new LambdaQueryWrapper<AssessmentScopeSnapshot>()
                .eq(AssessmentScopeSnapshot::getWorkflowId, workflowId).last("LIMIT 1"));
        if (snapshot == null) throw new IllegalStateException("评估范围尚未冻结: workflowId=" + workflowId);
        return deserializeScope(snapshot.getSnapshotJson());
    }

    @Override
    @Transactional
    public AssessmentBlueprintDTO loadOrCreateBlueprint(Long workflowId, Long empId, Long postId) {
        AssessmentBlueprint existing = blueprintMapper.selectOne(new LambdaQueryWrapper<AssessmentBlueprint>()
                .eq(AssessmentBlueprint::getWorkflowId, workflowId).last("LIMIT 1"));
        if (existing != null) return deserializeBlueprint(existing.getBlueprintJson());
        AssessmentScopeDTO scope = freezeScope(workflowId, empId, postId);
        AssessmentBlueprintDTO blueprint = buildBlueprint(scope);
        AssessmentBlueprint entity = new AssessmentBlueprint();
        entity.setWorkflowId(workflowId);
        entity.setScopeHash(scope.scopeHash());
        entity.setBlueprintJson(serialize(blueprint));
        entity.setCreatedTime(LocalDateTime.now());
        entity.setUpdatedTime(LocalDateTime.now());
        blueprintMapper.insert(entity);
        return blueprint;
    }

    private AssessmentBlueprintDTO buildBlueprint(AssessmentScopeDTO scope) {
        List<AssessmentScopeDTO.AssessmentScopeItem> ordered = new ArrayList<>(scope.items());
        ordered.sort(Comparator.comparingInt(this::priority).reversed()
                .thenComparing(AssessmentScopeDTO.AssessmentScopeItem::assessmentAbilityId));
        List<AssessmentBlueprintDTO.QuestionSlot> slots = new ArrayList<>();
        String[] types = {"MULTIPLE_CHOICE", "MULTIPLE_CHOICE", "SCENARIO", "SCENARIO", "COMPREHENSIVE"};
        for (int i = 0; i < Math.min(5, ordered.size()); i++) {
            var item = ordered.get(i);
            slots.add(new AssessmentBlueprintDTO.QuestionSlot((long) i + 1,
                    List.of(item.assessmentAbilityId()), item.resumeClaimIds(),
                    item.postRequirementId() == null ? List.of() : List.of(item.postRequirementId()),
                    types[i], item.requiredLevel() != null ? item.requiredLevel() : item.claimedLevel(),
                    "按能力独立评分并输出证据", priority(item), item.weight() == null ? 1 : item.weight().intValue()));
        }
        return new AssessmentBlueprintDTO(scope.workflowId(), scope.scopeHash(), List.copyOf(slots));
    }

    private int priority(AssessmentScopeDTO.AssessmentScopeItem item) {
        return (item.core() ? 100 : 0) + (item.required() ? 50 : 0)
                + (item.weight() == null ? 0 : item.weight().intValue());
    }

    private String serialize(Object value) {
        try { return objectMapper.writeValueAsString(value); }
        catch (Exception e) { throw new IllegalStateException("评估契约序列化失败", e); }
    }
    private AssessmentScopeDTO deserializeScope(String json) {
        try { return objectMapper.readValue(json, AssessmentScopeDTO.class); }
        catch (Exception e) { throw new IllegalStateException("评估范围快照损坏", e); }
    }
    private AssessmentBlueprintDTO deserializeBlueprint(String json) {
        try { return objectMapper.readValue(json, AssessmentBlueprintDTO.class); }
        catch (Exception e) { throw new IllegalStateException("测试蓝图快照损坏", e); }
    }
}
