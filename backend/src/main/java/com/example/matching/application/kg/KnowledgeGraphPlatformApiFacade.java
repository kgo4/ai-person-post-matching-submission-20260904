package com.example.matching.application.kg;

import com.example.matching.dto.kg.GraphBuildTaskStatusDTO;
import com.example.matching.dto.kg.api.GraphChangeSetResponse;
import com.example.matching.dto.kg.api.GraphSnapshotResponse;
import com.example.matching.dto.kg.context.GraphAbilityEvidenceContext;
import com.example.matching.dto.kg.context.GraphLearningPrerequisiteContext;
import com.example.matching.dto.kg.context.GraphMatchContext;
import com.example.matching.entity.kg.KgGraphChangeSet;
import com.example.matching.entity.kg.KgGraphSnapshot;
import com.example.matching.service.kg.*;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class KnowledgeGraphPlatformApiFacade {

    private final GraphBuildTaskService graphBuildTaskService;
    private final GraphChangeSetService graphChangeSetService;
    private final KnowledgeGraphQueryService knowledgeGraphQueryService;
    private final KnowledgeGraphSnapshotService knowledgeGraphSnapshotService;
    private final ObjectProvider<Neo4jGraphStore> neo4jGraphStoreProvider;

    public GraphBuildTaskStatusDTO rebuildFullGraph(Long requestedBy) {
        return graphBuildTaskService.requestFullRebuild(requestedBy);
    }

    public GraphBuildTaskStatusDTO getBuildTask(String taskCode) {
        return graphBuildTaskService.getTaskStatus(taskCode);
    }

    public GraphChangeSetResponse getChangeSet(String changeCode) {
        KgGraphChangeSet entity = graphChangeSetService.getChange(changeCode);
        return toResponse(entity);
    }

    public List<GraphChangeSetResponse> listChangeSets(String processStatus, Integer limit) {
        List<KgGraphChangeSet> entities = graphChangeSetService.listChanges(processStatus, limit);
        return entities.stream().map(this::toResponse).toList();
    }

    public Map<String, Object> neo4jHealth() {
        Neo4jGraphStore graphStore = neo4jGraphStoreProvider.getIfAvailable();
        if (graphStore == null) {
            return Map.of("enabled", false, "status", "NOT_CONFIGURED");
        }
        return graphStore.health();
    }

    public Map<String, Object> getPanorama(List<String> nodeTypes, String keyword, String category, Integer limit) {
        return knowledgeGraphQueryService.getPanorama(nodeTypes, keyword, category, limit);
    }

    public Map<String, Object> getPostCenteredGraph(Long postId) {
        return knowledgeGraphQueryService.getPostCenteredGraph(postId);
    }

    public Map<String, Object> getEmployeeCenteredGraph(Long empId) {
        return knowledgeGraphQueryService.getEmployeeCenteredGraph(empId);
    }

    public Map<String, Object> getAbilityGapPath(Long empId, Long postId) {
        return knowledgeGraphQueryService.getAbilityGapPath(empId, postId);
    }

    public Map<String, Object> getMemoryGraph(Integer limit) {
        return knowledgeGraphQueryService.getMemoryGraph(limit);
    }

    public Map<String, Object> getTimeline(Integer limit) {
        return knowledgeGraphQueryService.getTimeline(limit);
    }

    public GraphMatchContext getMatchContext(Long empId, Long postId) {
        return knowledgeGraphQueryService.getMatchContext(empId, postId);
    }

    public GraphAbilityEvidenceContext getAbilityEvidenceContext(Long abilityId, Long employeeId) {
        return knowledgeGraphQueryService.getAbilityEvidenceContext(abilityId, employeeId);
    }

    public GraphLearningPrerequisiteContext getLearningPrerequisiteContext(List<Long> abilityIds) {
        return knowledgeGraphQueryService.getLearningPrerequisiteContext(abilityIds);
    }

    public Map<String, Object> getBusinessAnalysis(Long empId, Long postId) {
        Map<String, Object> result = new java.util.LinkedHashMap<>();
        if (empId != null && postId != null) {
            GraphMatchContext context = knowledgeGraphQueryService.getMatchContext(empId, postId);
            result.put("status", context.status());
            result.put("graphVersion", context.graphVersion());
            result.put("abilityCount", context.abilities() == null ? 0 : context.abilities().size());
            result.put("coveredCount", context.abilities() == null ? 0 : context.abilities().stream().filter(a -> a.employeeMasteryLevel() != null).count());
            result.put("gapCount", context.abilities() == null ? 0 : context.abilities().stream().filter(a -> a.employeeMasteryLevel() == null || (a.requiredLevel() != null && a.employeeMasteryLevel() < a.requiredLevel())).count());
            result.put("evidenceCount", context.abilities() == null ? 0 : context.abilities().stream().mapToLong(a -> a.evidence() == null ? 0 : a.evidence().size()).sum());
        }
        if (postId != null) {
            Map<String, Object> graph = knowledgeGraphQueryService.getPostCenteredGraph(postId);
            result.put("postGraph", graph);
            Object edges = graph.get("edges");
            result.put("relationCount", edges instanceof List<?> list ? list.size() : 0);
        }
        result.put("available", !result.isEmpty());
        return result;
    }

    public GraphSnapshotResponse createSnapshot(String snapshotType, String snapshotName, String graphJson, Long createdBy) {
        KgGraphSnapshot entity = knowledgeGraphSnapshotService.createSnapshot(snapshotType, snapshotName, graphJson, createdBy);
        return toResponse(entity);
    }

    public Map<String, Object> getSnapshotPage(String snapshotType, Integer page, Integer size) {
        return knowledgeGraphSnapshotService.getSnapshotPage(snapshotType, page, size);
    }

    public GraphSnapshotResponse getSnapshotById(Long id) {
        KgGraphSnapshot entity = knowledgeGraphSnapshotService.getSnapshotById(id);
        return toResponse(entity);
    }

    public Map<String, String> createPostAbilitySnapshot(String snapshotType, Long createdBy) {
        String snapshotCode = knowledgeGraphSnapshotService.createPostAbilitySnapshot(snapshotType, createdBy);
        return Map.of("snapshotCode", snapshotCode);
    }

    public Map<String, Object> diffPostAbilitySnapshots(String baseline, String target) {
        return knowledgeGraphSnapshotService.diffPostAbilitySnapshots(baseline, target);
    }

    private GraphSnapshotResponse toResponse(KgGraphSnapshot e) {
        if (e == null) return null;
        return new GraphSnapshotResponse(
                e.getId(), e.getSnapshotCode(), e.getSnapshotName(), e.getSnapshotType(),
                e.getNodeCount(), e.getEdgeCount(), e.getSnapshotJson(),
                e.getCreatedBy(), e.getCreatedTime()
        );
    }

    private GraphChangeSetResponse toResponse(KgGraphChangeSet e) {
        if (e == null) return null;
        return new GraphChangeSetResponse(
                e.getId(), e.getChangeCode(), e.getSourceType(), e.getEntityType(),
                e.getEntityId(), e.getOperationType(), e.getPayloadJson(), e.getGraphVersion(),
                e.getProcessStatus(), e.getRetryCount(), e.getAffectedNodeCount(),
                e.getAffectedEdgeCount(), e.getStartedTime(), e.getCompletedTime(),
                e.getErrorMessage(), e.getCreatedBy(), e.getCreatedTime(), e.getUpdatedTime()
        );
    }
}
