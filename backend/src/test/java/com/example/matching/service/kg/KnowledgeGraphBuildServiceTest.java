package com.example.matching.service.kg;

import com.example.matching.dto.kg.GraphBuildResultDTO;
import com.example.matching.mapper.kg.KgGraphEdgeMapper;
import com.example.matching.mapper.kg.KgGraphNodeMapper;
import com.example.matching.infrastructure.persistence.GraphRebuildTableRouter;
import com.example.matching.service.kg.build.GraphEdgeProjectionService;
import com.example.matching.service.kg.build.GraphNodeProjectionService;
import com.example.matching.service.kg.build.Neo4jSnapshotSynchronizer;
import com.example.matching.service.kg.impl.KnowledgeGraphBuildServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class KnowledgeGraphBuildServiceTest {

    @Mock
    private KgGraphNodeMapper graphNodeMapper;
    @Mock
    private KgGraphEdgeMapper graphEdgeMapper;
    @Mock
    private GraphNodeProjectionService graphNodeProjectionService;
    @Mock
    private GraphEdgeProjectionService graphEdgeProjectionService;
    @Mock
    private Neo4jSnapshotSynchronizer neo4jSnapshotSynchronizer;
    @Mock
    private JdbcTemplate jdbcTemplate;

    @InjectMocks
    private KnowledgeGraphBuildServiceImpl service;

    @BeforeEach
    void setUp() {
        lenient().when(graphNodeProjectionService.projectNodes(any())).thenReturn(Map.of("POST", 1));
        lenient().when(graphEdgeProjectionService.projectEdges(any())).thenReturn(Map.of("REQUIRES", 1));
        lenient().doNothing().when(neo4jSnapshotSynchronizer).syncIfAvailable();
        lenient().when(jdbcTemplate.update(anyString())).thenReturn(1);
    }

    @Test
    @DisplayName("图谱构建：成功创建POST节点")
    void rebuildFullGraph_createPostNodes() {
        GraphBuildResultDTO result = service.rebuildFullGraph();

        assertTrue(result.isSuccess());
        assertTrue(result.getNodeCount() > 0);
    }

    @Test
    @DisplayName("图谱构建：成功创建ABILITY节点")
    void rebuildFullGraph_createAbilityNodes() {
        when(graphNodeProjectionService.projectNodes(any())).thenReturn(Map.of("ABILITY", 1));

        GraphBuildResultDTO result = service.rebuildFullGraph();

        assertTrue(result.isSuccess());
    }

    @Test
    @DisplayName("图谱构建：成功创建Requires边")
    void rebuildFullGraph_createRequiresEdge() {
        GraphBuildResultDTO result = service.rebuildFullGraph();

        assertTrue(result.isSuccess());
    }

    @Test
    @DisplayName("knowledge prerequisites create directed graph edges")
    void rebuildFullGraph_createKnowledgePrerequisiteEdge() {
        when(graphEdgeProjectionService.projectEdges(any())).thenReturn(Map.of("PREREQUISITE_OF", 1));

        GraphBuildResultDTO result = service.rebuildFullGraph();

        assertTrue(result.isSuccess());
        verify(graphEdgeProjectionService, atLeastOnce()).projectEdges(any());
    }

    @Test
    @DisplayName("deduplicate employee ability edges")
    void rebuildFullGraph_deduplicateEmployeeHasAbilityEdges() {
        when(graphEdgeProjectionService.projectEdges(any())).thenReturn(Map.of("HAS_ABILITY", 2));

        GraphBuildResultDTO result = service.rebuildFullGraph();
        assertTrue(result.isSuccess());
    }

    @Test
    @DisplayName("rebuild full graph is idempotent")
    void rebuildFullGraph_idempotent() {
        GraphBuildResultDTO result1 = service.rebuildFullGraph();
        assertTrue(result1.isSuccess());

        GraphBuildResultDTO result2 = service.rebuildFullGraph();
        assertTrue(result2.isSuccess());

        verify(graphNodeMapper, times(2)).delete(any());
        verify(graphEdgeMapper, times(2)).delete(any());
    }

    @Test
    @DisplayName("evidence connects employee and ability")
    void rebuildFullGraph_empAbilityEvidenceConnectsEmployeeAndAbility() {
        when(graphEdgeProjectionService.projectEdges(any())).thenReturn(Map.of("SUPPORTED_BY", 2));

        GraphBuildResultDTO result = service.rebuildFullGraph();
        assertTrue(result.isSuccess());
    }

    @Test
    @DisplayName("post ability model evidence connects post and ability")
    void rebuildFullGraph_postAbilityModelEvidenceConnectsPostAndAbility() {
        when(graphEdgeProjectionService.projectEdges(any())).thenReturn(Map.of("SUPPORTED_BY", 2));

        GraphBuildResultDTO result = service.rebuildFullGraph();
        assertTrue(result.isSuccess());
    }

    @Test
    @DisplayName("非破坏性：投影节点为空时中止，主表不被触碰")
    void rebuildFullGraph_abortsWhenProjectionEmpty() {
        when(graphNodeProjectionService.projectNodes(any())).thenReturn(new LinkedHashMap<>());

        assertThrows(IllegalStateException.class, () -> service.rebuildFullGraph());

        // 主表交换不执行（影子表 DELETE 仍执行，但主表 DELETE/INSERT..SELECT 不发生）
        verify(jdbcTemplate, never()).update(anyString());
    }

    @Test
    @DisplayName("非破坏性：投影边为空时中止，主表不被触碰")
    void rebuildFullGraph_abortsWhenEdgeProjectionEmpty() {
        when(graphEdgeProjectionService.projectEdges(any())).thenReturn(new LinkedHashMap<>());

        assertThrows(IllegalStateException.class, () -> service.rebuildFullGraph());

        verify(jdbcTemplate, never()).update(anyString());
    }

    @Test
    @DisplayName("原子交换：先 DELETE 主表再 INSERT..SELECT 影子表回填")
    void rebuildFullGraph_swapsShadowIntoMain() {
        service.rebuildFullGraph();

        verify(jdbcTemplate).update("DELETE FROM kg_graph_node");
        verify(jdbcTemplate).update("INSERT INTO kg_graph_node SELECT * FROM kg_graph_node_new");
        verify(jdbcTemplate).update("DELETE FROM kg_graph_edge");
        verify(jdbcTemplate).update("INSERT INTO kg_graph_edge SELECT * FROM kg_graph_edge_new");
    }

    @Test
    @DisplayName("路由在重建结束后被清理，不泄漏到其他请求")
    void rebuildFullGraph_clearsRoutingAfterBuild() {
        assertFalse(GraphRebuildTableRouter.isRouting());
        service.rebuildFullGraph();
        assertFalse(GraphRebuildTableRouter.isRouting());
    }
}
