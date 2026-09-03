package com.example.matching.controller.kg;

import com.example.matching.application.kg.KnowledgeGraphPlatformApiFacade;
import com.example.matching.common.result.R;
import com.example.matching.dto.kg.GraphBuildTaskStatusDTO;
import com.example.matching.dto.kg.api.GraphChangeSetResponse;
import com.example.matching.dto.kg.api.GraphSnapshotResponse;
import com.example.matching.dto.kg.context.GraphAbilityEvidenceContext;
import com.example.matching.dto.kg.context.GraphLearningPrerequisiteContext;
import com.example.matching.dto.kg.context.GraphMatchContext;
import com.example.matching.utils.SecurityUtils;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/kg")
@RequiredArgsConstructor
public class KnowledgeGraphPlatformController {

    private final KnowledgeGraphPlatformApiFacade facade;

    @PostMapping("/build/full")
    public R<GraphBuildTaskStatusDTO> rebuildFullGraph() {
        return R.ok(facade.rebuildFullGraph(SecurityUtils.getCurrentUserId()));
    }

    @GetMapping("/build/tasks/{taskCode}")
    public R<GraphBuildTaskStatusDTO> getBuildTask(@PathVariable String taskCode) {
        return R.ok(facade.getBuildTask(taskCode));
    }

    @GetMapping("/build/changes/{changeCode}")
    public R<GraphChangeSetResponse> getChangeSet(@PathVariable String changeCode) {
        return R.ok(facade.getChangeSet(changeCode));
    }

    @GetMapping("/build/changes")
    public R<List<GraphChangeSetResponse>> listChangeSets(@RequestParam(required = false) String processStatus,
                                                           @RequestParam(required = false) Integer limit) {
        return R.ok(facade.listChangeSets(processStatus, limit));
    }

    @GetMapping("/neo4j/health")
    public R<Map<String, Object>> neo4jHealth() {
        return R.ok(facade.neo4jHealth());
    }

    @GetMapping("/panorama")
    public R<Map<String, Object>> getPanorama(
            @RequestParam(required = false) List<String> nodeTypes,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String category,
            @RequestParam(required = false) Integer limit) {
        Map<String, Object> result = facade.getPanorama(nodeTypes, keyword, category, limit);
        return R.ok(result);
    }

    @GetMapping("/post/{postId}")
    public R<Map<String, Object>> getPostCenteredGraph(@PathVariable Long postId) {
        Map<String, Object> result = facade.getPostCenteredGraph(postId);
        return R.ok(result);
    }

    @GetMapping("/employee/{empId}")
    public R<Map<String, Object>> getEmployeeCenteredGraph(@PathVariable Long empId) {
        Map<String, Object> result = facade.getEmployeeCenteredGraph(empId);
        return R.ok(result);
    }

    @GetMapping("/path/employee/{empId}/post/{postId}")
    public R<Map<String, Object>> getAbilityGapPath(
            @PathVariable Long empId, @PathVariable Long postId) {
        Map<String, Object> result = facade.getAbilityGapPath(empId, postId);
        return R.ok(result);
    }

    @GetMapping("/memory-graph")
    public R<Map<String, Object>> getMemoryGraph(
            @RequestParam(required = false) Integer limit) {
        Map<String, Object> result = facade.getMemoryGraph(limit);
        return R.ok(result);
    }

    @GetMapping("/timeline")
    public R<Map<String, Object>> getTimeline(
            @RequestParam(required = false) Integer limit) {
        Map<String, Object> result = facade.getTimeline(limit);
        return R.ok(result);
    }

    @GetMapping("/context/match/employee/{empId}/post/{postId}")
    public R<GraphMatchContext> getMatchContext(@PathVariable Long empId, @PathVariable Long postId) {
        return R.ok(facade.getMatchContext(empId, postId));
    }

    @GetMapping("/context/ability/{abilityId}/evidence")
    public R<GraphAbilityEvidenceContext> getAbilityEvidenceContext(
            @PathVariable Long abilityId, @RequestParam(required = false) Long employeeId) {
        return R.ok(facade.getAbilityEvidenceContext(abilityId, employeeId));
    }

    @GetMapping("/context/learning/prerequisites")
    public R<GraphLearningPrerequisiteContext> getLearningPrerequisiteContext(@RequestParam List<Long> abilityIds) {
        return R.ok(facade.getLearningPrerequisiteContext(abilityIds));
    }

    @GetMapping("/business-analysis")
    public R<Map<String, Object>> getBusinessAnalysis(
            @RequestParam(required = false) Long employeeId,
            @RequestParam(required = false) Long postId) {
        return R.ok(facade.getBusinessAnalysis(employeeId, postId));
    }

    @PostMapping("/snapshots")
    public R<GraphSnapshotResponse> createSnapshot(
            @RequestParam String snapshotType,
            @RequestParam String snapshotName,
            @Valid @RequestBody com.example.matching.dto.kg.GraphSnapshotCreateDTO dto,
            @RequestParam(required = false) Long createdBy) {
        GraphSnapshotResponse snapshot = facade.createSnapshot(
                snapshotType, snapshotName, dto.graphJson(), createdBy);
        return R.ok(snapshot);
    }

    @GetMapping("/snapshots/page")
    public R<Map<String, Object>> getSnapshotPage(
            @RequestParam(required = false) String snapshotType,
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size) {
        Map<String, Object> result = facade.getSnapshotPage(snapshotType, page, size);
        return R.ok(result);
    }

    @GetMapping("/snapshots/{id}")
    public R<GraphSnapshotResponse> getSnapshotById(@PathVariable Long id) {
        GraphSnapshotResponse snapshot = facade.getSnapshotById(id);
        return R.ok(snapshot);
    }

    @PostMapping("/snapshots/post-abilities")
    public R<Map<String, String>> createPostAbilitySnapshot(@RequestParam(defaultValue = "MANUAL") String snapshotType) {
        return R.ok(facade.createPostAbilitySnapshot(
                snapshotType, SecurityUtils.getCurrentUserId()));
    }

    @GetMapping("/snapshots/post-abilities/diff")
    public R<Map<String, Object>> diffPostAbilitySnapshots(@RequestParam String baselineSnapshotCode,
                                                             @RequestParam String targetSnapshotCode) {
        return R.ok(facade.diffPostAbilitySnapshots(baselineSnapshotCode, targetSnapshotCode));
    }
}
