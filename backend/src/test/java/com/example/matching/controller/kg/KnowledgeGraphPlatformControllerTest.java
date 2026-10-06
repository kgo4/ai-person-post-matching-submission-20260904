package com.example.matching.controller.kg;

import com.example.matching.application.kg.KnowledgeGraphPlatformApiFacade;
import com.example.matching.common.result.R;
import com.example.matching.dto.kg.GraphBuildTaskStatusDTO;
import com.example.matching.dto.kg.GraphSnapshotCreateDTO;
import com.example.matching.dto.kg.api.GraphChangeSetResponse;
import com.example.matching.dto.kg.api.GraphSnapshotResponse;
import com.example.matching.dto.kg.context.GraphAbilityEvidenceContext;
import com.example.matching.dto.kg.context.GraphContextStatus;
import com.example.matching.dto.kg.context.GraphLearningPrerequisiteContext;
import com.example.matching.dto.kg.context.GraphMatchContext;
import com.example.matching.utils.SecurityUtils;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class KnowledgeGraphPlatformControllerTest {

    @AfterEach
    void clearSecurityContext() {
        SecurityUtils.clear();
    }

    private static GraphBuildTaskStatusDTO createTaskStatus() {
        GraphBuildTaskStatusDTO status = new GraphBuildTaskStatusDTO();
        status.setTaskCode("TASK_001");
        status.setTaskStatus("SUCCESS");
        return status;
    }

    private static GraphChangeSetResponse createChangeSet() {
        return new GraphChangeSetResponse(
                1L, "CHG_001", "POST", "ABILITY", 10L, "ADD", "{}", "v1",
                "PROCESSED", 0, 2, 1, null, null, null, 1L, null, null);
    }

    private static GraphSnapshotResponse createSnapshot() {
        return new GraphSnapshotResponse(
                1L, "SNAP_001", "基线快照", "MANUAL", 20, 15,
                "{\"nodes\":[]}", 1L, null);
    }

    @Test
    void rebuildFullGraphReturnsTaskStatus() {
        KnowledgeGraphPlatformApiFacade facade = mock(KnowledgeGraphPlatformApiFacade.class);
        KnowledgeGraphPlatformController controller = new KnowledgeGraphPlatformController(facade);

        GraphBuildTaskStatusDTO status = createTaskStatus();
        SecurityUtils.setCurrentUserId(7L);
        when(facade.rebuildFullGraph(7L)).thenReturn(status);

        R<GraphBuildTaskStatusDTO> response = controller.rebuildFullGraph();

        assertThat(response.getCode()).isEqualTo(200);
        assertThat(response.getData()).isEqualTo(status);
        verify(facade).rebuildFullGraph(7L);
    }

    @Test
    void getBuildTaskReturnsTaskStatus() {
        KnowledgeGraphPlatformApiFacade facade = mock(KnowledgeGraphPlatformApiFacade.class);
        KnowledgeGraphPlatformController controller = new KnowledgeGraphPlatformController(facade);

        GraphBuildTaskStatusDTO status = createTaskStatus();
        when(facade.getBuildTask("TASK_001")).thenReturn(status);

        R<GraphBuildTaskStatusDTO> response = controller.getBuildTask("TASK_001");

        assertThat(response.getCode()).isEqualTo(200);
        assertThat(response.getData()).isEqualTo(status);
    }

    @Test
    void getChangeSetReturnsChangeSet() {
        KnowledgeGraphPlatformApiFacade facade = mock(KnowledgeGraphPlatformApiFacade.class);
        KnowledgeGraphPlatformController controller = new KnowledgeGraphPlatformController(facade);

        GraphChangeSetResponse changeSet = createChangeSet();
        when(facade.getChangeSet("CHG_001")).thenReturn(changeSet);

        R<GraphChangeSetResponse> response = controller.getChangeSet("CHG_001");

        assertThat(response.getCode()).isEqualTo(200);
        assertThat(response.getData()).isEqualTo(changeSet);
    }

    @Test
    void listChangeSetsReturnsChangeSets() {
        KnowledgeGraphPlatformApiFacade facade = mock(KnowledgeGraphPlatformApiFacade.class);
        KnowledgeGraphPlatformController controller = new KnowledgeGraphPlatformController(facade);

        GraphChangeSetResponse changeSet = createChangeSet();
        when(facade.listChangeSets("PROCESSED", 20)).thenReturn(List.of(changeSet));

        R<List<GraphChangeSetResponse>> response = controller.listChangeSets("PROCESSED", 20);

        assertThat(response.getCode()).isEqualTo(200);
        assertThat(response.getData()).containsExactly(changeSet);
    }

    @Test
    void neo4jHealthReturnsHealth() {
        KnowledgeGraphPlatformApiFacade facade = mock(KnowledgeGraphPlatformApiFacade.class);
        KnowledgeGraphPlatformController controller = new KnowledgeGraphPlatformController(facade);

        Map<String, Object> health = Map.of("enabled", true, "status", "UP", "nodes", 12);
        when(facade.neo4jHealth()).thenReturn(health);

        R<Map<String, Object>> response = controller.neo4jHealth();

        assertThat(response.getCode()).isEqualTo(200);
        assertThat(response.getData()).containsEntry("status", "UP").containsEntry("enabled", true);
    }

    @Test
    void getPanoramaReturnsGraph() {
        KnowledgeGraphPlatformApiFacade facade = mock(KnowledgeGraphPlatformApiFacade.class);
        KnowledgeGraphPlatformController controller = new KnowledgeGraphPlatformController(facade);

        Map<String, Object> panorama = Map.of("nodeCount", 10, "edgeCount", 8);
        when(facade.getPanorama(List.of("ABILITY"), "Java", null, 50)).thenReturn(panorama);

        R<Map<String, Object>> response = controller.getPanorama(List.of("ABILITY"), "Java", null, 50);

        assertThat(response.getCode()).isEqualTo(200);
        assertThat(response.getData()).containsEntry("nodeCount", 10).containsEntry("edgeCount", 8);
    }

    @Test
    void getPostCenteredGraphReturnsGraph() {
        KnowledgeGraphPlatformApiFacade facade = mock(KnowledgeGraphPlatformApiFacade.class);
        KnowledgeGraphPlatformController controller = new KnowledgeGraphPlatformController(facade);

        Map<String, Object> graph = Map.of("postId", 10L, "abilityCount", 5);
        when(facade.getPostCenteredGraph(10L)).thenReturn(graph);

        R<Map<String, Object>> response = controller.getPostCenteredGraph(10L);

        assertThat(response.getCode()).isEqualTo(200);
        assertThat(response.getData()).containsEntry("postId", 10L).containsEntry("abilityCount", 5);
    }

    @Test
    void getEmployeeCenteredGraphReturnsGraph() {
        KnowledgeGraphPlatformApiFacade facade = mock(KnowledgeGraphPlatformApiFacade.class);
        KnowledgeGraphPlatformController controller = new KnowledgeGraphPlatformController(facade);

        Map<String, Object> graph = Map.of("empId", 1L, "abilityCount", 6);
        when(facade.getEmployeeCenteredGraph(1L)).thenReturn(graph);

        R<Map<String, Object>> response = controller.getEmployeeCenteredGraph(1L);

        assertThat(response.getCode()).isEqualTo(200);
        assertThat(response.getData()).containsEntry("empId", 1L).containsEntry("abilityCount", 6);
    }

    @Test
    void getAbilityGapPathReturnsPath() {
        KnowledgeGraphPlatformApiFacade facade = mock(KnowledgeGraphPlatformApiFacade.class);
        KnowledgeGraphPlatformController controller = new KnowledgeGraphPlatformController(facade);

        Map<String, Object> path = Map.of("gapCount", 3, "steps", List.of("a", "b"));
        when(facade.getAbilityGapPath(1L, 10L)).thenReturn(path);

        R<Map<String, Object>> response = controller.getAbilityGapPath(1L, 10L);

        assertThat(response.getCode()).isEqualTo(200);
        assertThat(response.getData()).containsEntry("gapCount", 3);
    }

    @Test
    void getMemoryGraphReturnsGraph() {
        KnowledgeGraphPlatformApiFacade facade = mock(KnowledgeGraphPlatformApiFacade.class);
        KnowledgeGraphPlatformController controller = new KnowledgeGraphPlatformController(facade);

        Map<String, Object> graph = Map.of("nodeCount", 30, "edgeCount", 25);
        when(facade.getMemoryGraph(100)).thenReturn(graph);

        R<Map<String, Object>> response = controller.getMemoryGraph(100);

        assertThat(response.getCode()).isEqualTo(200);
        assertThat(response.getData()).containsEntry("nodeCount", 30);
    }

    @Test
    void getTimelineReturnsTimeline() {
        KnowledgeGraphPlatformApiFacade facade = mock(KnowledgeGraphPlatformApiFacade.class);
        KnowledgeGraphPlatformController controller = new KnowledgeGraphPlatformController(facade);

        Map<String, Object> timeline = Map.of("events", List.of("e1", "e2"));
        when(facade.getTimeline(50)).thenReturn(timeline);

        R<Map<String, Object>> response = controller.getTimeline(50);

        assertThat(response.getCode()).isEqualTo(200);
        assertThat(response.getData()).containsEntry("events", List.of("e1", "e2"));
    }

    @Test
    void getMatchContextReturnsContext() {
        KnowledgeGraphPlatformApiFacade facade = mock(KnowledgeGraphPlatformApiFacade.class);
        KnowledgeGraphPlatformController controller = new KnowledgeGraphPlatformController(facade);

        GraphMatchContext context = GraphMatchContext.empty(GraphContextStatus.AVAILABLE, 1L, 10L);
        when(facade.getMatchContext(1L, 10L)).thenReturn(context);

        R<GraphMatchContext> response = controller.getMatchContext(1L, 10L);

        assertThat(response.getCode()).isEqualTo(200);
        assertThat(response.getData()).isEqualTo(context);
    }

    @Test
    void getAbilityEvidenceContextReturnsContext() {
        KnowledgeGraphPlatformApiFacade facade = mock(KnowledgeGraphPlatformApiFacade.class);
        KnowledgeGraphPlatformController controller = new KnowledgeGraphPlatformController(facade);

        GraphAbilityEvidenceContext context = new GraphAbilityEvidenceContext(1L, "Java并发", List.of());
        when(facade.getAbilityEvidenceContext(1L, 5L)).thenReturn(context);

        R<GraphAbilityEvidenceContext> response = controller.getAbilityEvidenceContext(1L, 5L);

        assertThat(response.getCode()).isEqualTo(200);
        assertThat(response.getData()).isEqualTo(context);
    }

    @Test
    void getLearningPrerequisiteContextReturnsContext() {
        KnowledgeGraphPlatformApiFacade facade = mock(KnowledgeGraphPlatformApiFacade.class);
        KnowledgeGraphPlatformController controller = new KnowledgeGraphPlatformController(facade);

        GraphLearningPrerequisiteContext context =
                new GraphLearningPrerequisiteContext(List.of(1L, 2L), List.of());
        when(facade.getLearningPrerequisiteContext(List.of(1L, 2L))).thenReturn(context);

        R<GraphLearningPrerequisiteContext> response =
                controller.getLearningPrerequisiteContext(List.of(1L, 2L));

        assertThat(response.getCode()).isEqualTo(200);
        assertThat(response.getData()).isEqualTo(context);
    }

    @Test
    void createSnapshotReturnsSnapshot() {
        KnowledgeGraphPlatformApiFacade facade = mock(KnowledgeGraphPlatformApiFacade.class);
        KnowledgeGraphPlatformController controller = new KnowledgeGraphPlatformController(facade);

        GraphSnapshotCreateDTO dto = new GraphSnapshotCreateDTO("{\"nodes\":[]}");
        GraphSnapshotResponse snapshot = createSnapshot();
        when(facade.createSnapshot("MANUAL", "基线快照", "{\"nodes\":[]}", 1L)).thenReturn(snapshot);

        R<GraphSnapshotResponse> response = controller.createSnapshot("MANUAL", "基线快照", dto, 1L);

        assertThat(response.getCode()).isEqualTo(200);
        assertThat(response.getData()).isEqualTo(snapshot);
        verify(facade).createSnapshot("MANUAL", "基线快照", "{\"nodes\":[]}", 1L);
    }

    @Test
    void getSnapshotPageReturnsPage() {
        KnowledgeGraphPlatformApiFacade facade = mock(KnowledgeGraphPlatformApiFacade.class);
        KnowledgeGraphPlatformController controller = new KnowledgeGraphPlatformController(facade);

        Map<String, Object> page = Map.of("total", 2, "records", List.of());
        when(facade.getSnapshotPage("MANUAL", 1, 10)).thenReturn(page);

        R<Map<String, Object>> response = controller.getSnapshotPage("MANUAL", 1, 10);

        assertThat(response.getCode()).isEqualTo(200);
        assertThat(response.getData()).containsEntry("total", 2);
    }

    @Test
    void getSnapshotByIdReturnsSnapshot() {
        KnowledgeGraphPlatformApiFacade facade = mock(KnowledgeGraphPlatformApiFacade.class);
        KnowledgeGraphPlatformController controller = new KnowledgeGraphPlatformController(facade);

        GraphSnapshotResponse snapshot = createSnapshot();
        when(facade.getSnapshotById(1L)).thenReturn(snapshot);

        R<GraphSnapshotResponse> response = controller.getSnapshotById(1L);

        assertThat(response.getCode()).isEqualTo(200);
        assertThat(response.getData()).isEqualTo(snapshot);
    }

    @Test
    void createPostAbilitySnapshotReturnsCode() {
        KnowledgeGraphPlatformApiFacade facade = mock(KnowledgeGraphPlatformApiFacade.class);
        KnowledgeGraphPlatformController controller = new KnowledgeGraphPlatformController(facade);

        Map<String, String> result = Map.of("snapshotCode", "SNAP_002");
        SecurityUtils.setCurrentUserId(7L);
        when(facade.createPostAbilitySnapshot("MANUAL", 7L)).thenReturn(result);

        R<Map<String, String>> response = controller.createPostAbilitySnapshot("MANUAL");

        assertThat(response.getCode()).isEqualTo(200);
        assertThat(response.getData()).isEqualTo(result);
        verify(facade).createPostAbilitySnapshot("MANUAL", 7L);
    }

    @Test
    void diffPostAbilitySnapshotsReturnsDiff() {
        KnowledgeGraphPlatformApiFacade facade = mock(KnowledgeGraphPlatformApiFacade.class);
        KnowledgeGraphPlatformController controller = new KnowledgeGraphPlatformController(facade);

        Map<String, Object> diff = Map.of("added", List.of("a"), "removed", List.of("b"));
        when(facade.diffPostAbilitySnapshots("SNAP_001", "SNAP_002")).thenReturn(diff);

        R<Map<String, Object>> response =
                controller.diffPostAbilitySnapshots("SNAP_001", "SNAP_002");

        assertThat(response.getCode()).isEqualTo(200);
        assertThat(response.getData()).containsEntry("added", List.of("a")).containsEntry("removed", List.of("b"));
    }
}
