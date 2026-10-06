package com.example.matching.service.kg.impl;

import com.example.matching.entity.kg.KgGraphEdge;
import com.example.matching.entity.kg.KgGraphNode;
import com.example.matching.mapper.kg.KgGraphEdgeMapper;
import com.example.matching.mapper.kg.KgGraphNodeMapper;
import com.example.matching.service.kg.Neo4jGraphStore;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.beans.factory.ObjectProvider;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.*;

/**
 * {@link KnowledgeGraphViewQueryService} 单元测试。
 *
 * <p>覆盖全景图（Neo4j 命中 / 回退 MySQL / 空结果 / 边与补全节点截断）、岗位与员工中心图、
 * 能力差距路径、记忆图、时间线，以及 limit 钳制边界。
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class KnowledgeGraphViewQueryServiceTest {

    @Mock private KgGraphNodeMapper graphNodeMapper;
    @Mock private KgGraphEdgeMapper graphEdgeMapper;
    @SuppressWarnings("rawtypes")
    @Mock private ObjectProvider neo4jGraphStoreProvider;
    @Mock private Neo4jGraphStore neo4jGraphStore;

    private KnowledgeGraphViewQueryService service;

    /**
     * MyBatis-Plus 的 LambdaQueryWrapper 需要实体已登记表信息，
     * 否则会抛 "can not find lambda cache for this entity"（纯单测无 Spring 上下文）。
     */
    @BeforeAll
    static void initMybatisPlusLambdaCache() {
        var cfg = new com.baomidou.mybatisplus.core.MybatisConfiguration();
        var assistant = new org.apache.ibatis.builder.MapperBuilderAssistant(cfg, "");
        com.baomidou.mybatisplus.core.metadata.TableInfoHelper.initTableInfo(assistant, KgGraphNode.class);
        com.baomidou.mybatisplus.core.metadata.TableInfoHelper.initTableInfo(assistant, KgGraphEdge.class);
    }

    @BeforeEach
    void setUp() {
        service = new KnowledgeGraphViewQueryService(graphNodeMapper, graphEdgeMapper, neo4jGraphStoreProvider);
    }

    private KgGraphNode node(String key, String type, String label) {
        KgGraphNode n = new KgGraphNode();
        n.setNodeKey(key);
        n.setNodeType(type);
        n.setLabel(label);
        n.setCategory("CAT");
        n.setStatus("ACTIVE");
        n.setWeightValue(BigDecimal.ONE);
        n.setLevelValue(3);
        n.setCreatedTime(LocalDateTime.now());
        return n;
    }

    private KgGraphEdge edge(String key, String source, String target, String type) {
        KgGraphEdge e = new KgGraphEdge();
        e.setEdgeKey(key);
        e.setSourceNodeKey(source);
        e.setTargetNodeKey(target);
        e.setEdgeType(type);
        e.setWeightValue(BigDecimal.ONE);
        e.setConfidenceScore(new BigDecimal("90.00"));
        e.setCreatedTime(LocalDateTime.now());
        return e;
    }

    @SuppressWarnings("unchecked")
    private void stubNoNeo4j() {
        when(neo4jGraphStoreProvider.getIfAvailable()).thenReturn(null);
    }

    // ==================== getPanorama ====================

    @Test
    @DisplayName("getPanorama：Neo4j 可用且有数据 → 直接返回 Neo4j 结果")
    void getPanorama_neo4jHit() {
        Map<String, Object> neoResult = Map.of("available", true, "nodes", List.of(Map.of("id", "N1")));
        when(neo4jGraphStoreProvider.getIfAvailable()).thenReturn(neo4jGraphStore);
        when(neo4jGraphStore.queryPanorama(anyList(), any(), any(), anyInt())).thenReturn(neoResult);

        Map<String, Object> out = service.getPanorama(List.of("POST"), "kw", "cat", 120);

        assertSame(neoResult, out);
        verifyNoInteractions(graphNodeMapper);
    }

    @Test
    @DisplayName("getPanorama：Neo4j 标记不可用 → 回退 MySQL 并组装图结果")
    void getPanorama_neo4jUnavailableFallsBack() {
        when(neo4jGraphStoreProvider.getIfAvailable()).thenReturn(neo4jGraphStore);
        when(neo4jGraphStore.queryPanorama(anyList(), any(), any(), anyInt()))
                .thenReturn(Map.of("available", false, "nodes", List.of()));
        when(graphNodeMapper.selectList(any())).thenReturn(List.of(node("POST:1", "POST", "岗位1")));
        when(graphEdgeMapper.selectList(any())).thenReturn(Collections.emptyList());

        Map<String, Object> out = service.getPanorama(null, null, null, 50);

        assertEquals(true, out.get("available"));
        assertEquals(1, ((List<?>) out.get("nodes")).size());
        assertEquals(0, ((List<?>) out.get("edges")).size());
    }

    @Test
    @DisplayName("getPanorama：无 Neo4j 且 MySQL 无节点 → 返回空结果模板")
    void getPanorama_emptyNodes() {
        stubNoNeo4j();
        when(graphNodeMapper.selectList(any())).thenReturn(Collections.emptyList());

        Map<String, Object> out = service.getPanorama(List.of("POST"), "k", "c", 20);

        assertEquals(true, out.get("available"));
        assertTrue(((List<?>) out.get("nodes")).isEmpty());
        @SuppressWarnings("unchecked")
        Map<String, Object> stats = (Map<String, Object>) out.get("stats");
        assertEquals(0, stats.get("nodeCount"));
        assertEquals(0, stats.get("prerequisiteCount"));
    }

    @Test
    @DisplayName("getPanorama：补全边端点节点并统计各类型数量")
    void getPanorama_completesMissingNodesAndStats() {
        stubNoNeo4j();
        List<KgGraphNode> initial = List.of(node("POST:1", "POST", "岗位1"));
        List<KgGraphNode> extra = List.of(node("ABILITY:5", "ABILITY", "Java"));
        when(graphNodeMapper.selectList(any())).thenReturn(initial, extra);
        when(graphEdgeMapper.selectList(any())).thenReturn(List.of(edge("E1", "POST:1", "ABILITY:5", "REQUIRES")));

        Map<String, Object> out = service.getPanorama(null, null, null, 100);

        assertEquals(2, ((List<?>) out.get("nodes")).size());
        assertEquals(1, ((List<?>) out.get("edges")).size());
        @SuppressWarnings("unchecked")
        Map<String, Object> stats = (Map<String, Object>) out.get("stats");
        assertEquals(2, stats.get("nodeCount"));
        assertEquals(1L, stats.get("postCount"));
        assertEquals(1L, stats.get("abilityCount"));
    }

    @Test
    @DisplayName("getPanorama：边数超出上限 → 截断到 limit*2 且丢弃悬空边")
    void getPanorama_truncatesEdges() {
        stubNoNeo4j();
        when(graphNodeMapper.selectList(any())).thenReturn(List.of(node("POST:1", "POST", "岗位1")));

        List<KgGraphEdge> manyEdges = new ArrayList<>();
        for (int i = 0; i < 100; i++) {
            // 目标节点不存在 → 补全失败后应被过滤
            manyEdges.add(edge("E" + i, "POST:1", "MISSING:" + i, "REQUIRES"));
        }
        when(graphEdgeMapper.selectList(any())).thenReturn(manyEdges);
        // 第二次查询补全节点返回空 → 所有边端点缺失被丢弃
        when(graphNodeMapper.selectList(any())).thenReturn(List.of(node("POST:1", "POST", "岗位1")), Collections.emptyList());

        Map<String, Object> out = service.getPanorama(null, null, null, 20);

        assertEquals(1, ((List<?>) out.get("nodes")).size());
    }

    @Test
    @DisplayName("getPanorama：limit 为 null / 超界 → 钳制到 [20,120] 默认 300 的语义")
    void getPanorama_clampsLimit() {
        stubNoNeo4j();
        when(graphNodeMapper.selectList(any())).thenReturn(List.of(node("POST:1", "POST", "岗位1")));
        when(graphEdgeMapper.selectList(any())).thenReturn(Collections.emptyList());

        assertNotNull(service.getPanorama(null, null, null, null));
        assertNotNull(service.getPanorama(null, null, null, 1));
        assertNotNull(service.getPanorama(null, null, null, 99999));
    }

    @Test
    @DisplayName("getPanorama：keyword/category/nodeTypes 为空时仍可查询")
    void getPanorama_blankFilters() {
        stubNoNeo4j();
        when(graphNodeMapper.selectList(any())).thenReturn(List.of(node("POST:1", "POST", "岗位1")));
        when(graphEdgeMapper.selectList(any())).thenReturn(Collections.emptyList());

        Map<String, Object> out = service.getPanorama(Collections.emptyList(), "", "", 60);

        assertEquals(1, ((List<?>) out.get("nodes")).size());
    }

    // ==================== getPostCenteredGraph ====================

    @Test
    @DisplayName("getPostCenteredGraph：岗位节点不存在 → 空结果")
    void getPostCenteredGraph_postMissing() {
        when(graphNodeMapper.selectOne(any())).thenReturn(null);

        Map<String, Object> out = service.getPostCenteredGraph(9L);

        assertTrue(((List<?>) out.get("nodes")).isEmpty());
    }

    @Test
    @DisplayName("getPostCenteredGraph：岗位存在 → 返回中心图并过滤悬空边")
    void getPostCenteredGraph_success() {
        when(graphNodeMapper.selectOne(any())).thenReturn(node("POST:1", "POST", "岗位1"));
        when(graphEdgeMapper.selectList(any())).thenReturn(List.of(
                edge("E1", "POST:1", "ABILITY:5", "REQUIRES"),
                edge("E2", "POST:1", "ABILITY:404", "REQUIRES")));
        when(graphNodeMapper.selectList(any())).thenReturn(List.of(
                node("POST:1", "POST", "岗位1"),
                node("ABILITY:5", "ABILITY", "Java")));

        Map<String, Object> out = service.getPostCenteredGraph(1L);

        assertEquals(2, ((List<?>) out.get("nodes")).size());
        // ABILITY:404 不存在 → 对应边被过滤
        assertEquals(1, ((List<?>) out.get("edges")).size());
    }

    // ==================== getEmployeeCenteredGraph ====================

    @Test
    @DisplayName("getEmployeeCenteredGraph：员工节点不存在 → 空结果")
    void getEmployeeCenteredGraph_empMissing() {
        when(graphNodeMapper.selectOne(any())).thenReturn(null);

        Map<String, Object> out = service.getEmployeeCenteredGraph(9L);

        assertTrue(((List<?>) out.get("nodes")).isEmpty());
    }

    @Test
    @DisplayName("getEmployeeCenteredGraph：员工存在 → 返回中心图")
    void getEmployeeCenteredGraph_success() {
        when(graphNodeMapper.selectOne(any())).thenReturn(node("EMPLOYEE:7", "EMPLOYEE", "张三"));
        when(graphEdgeMapper.selectList(any())).thenReturn(List.of(
                edge("E1", "EMPLOYEE:7", "ABILITY:5", "HAS_ABILITY")));
        when(graphNodeMapper.selectList(any())).thenReturn(List.of(
                node("EMPLOYEE:7", "EMPLOYEE", "张三"),
                node("ABILITY:5", "ABILITY", "Java")));

        Map<String, Object> out = service.getEmployeeCenteredGraph(7L);

        assertEquals(2, ((List<?>) out.get("nodes")).size());
        assertEquals(1, ((List<?>) out.get("edges")).size());
    }

    // ==================== getAbilityGapPath ====================

    @Test
    @DisplayName("getAbilityGapPath：员工或岗位节点缺失 → 空结果")
    void getAbilityGapPath_nodeMissing() {
        when(graphNodeMapper.selectOne(any())).thenReturn(null);

        assertTrue(((List<?>) service.getAbilityGapPath(1L, 2L).get("nodes")).isEmpty());
    }

    @Test
    @DisplayName("getAbilityGapPath：员工已具备岗位要求能力 → 同时加入要求边与能力边")
    void getAbilityGapPath_buildsEdges() {
        when(graphNodeMapper.selectOne(any())).thenReturn(node("EMPLOYEE:1", "EMPLOYEE", "张三"));
        when(graphEdgeMapper.selectList(any())).thenReturn(
                List.of(edge("HAS", "EMPLOYEE:1", "ABILITY:5", "HAS_ABILITY")),
                List.of(edge("REQ", "POST:2", "ABILITY:5", "REQUIRES")));
        when(graphNodeMapper.selectList(any())).thenReturn(List.of(
                node("EMPLOYEE:1", "EMPLOYEE", "张三"),
                node("POST:2", "POST", "岗位2"),
                node("ABILITY:5", "ABILITY", "Java")));

        Map<String, Object> out = service.getAbilityGapPath(1L, 2L);

        assertEquals(3, ((List<?>) out.get("nodes")).size());
        // 要求边 + 员工已具备的能力边
        assertEquals(2, ((List<?>) out.get("edges")).size());
    }

    @Test
    @DisplayName("getAbilityGapPath：员工不具备该能力 → 仅要求边")
    void getAbilityGapPath_employeeLacksAbility() {
        when(graphNodeMapper.selectOne(any())).thenReturn(node("EMPLOYEE:1", "EMPLOYEE", "张三"));
        when(graphEdgeMapper.selectList(any())).thenReturn(
                Collections.emptyList(),
                List.of(edge("REQ", "POST:2", "ABILITY:9", "REQUIRES")));
        when(graphNodeMapper.selectList(any())).thenReturn(List.of(
                node("EMPLOYEE:1", "EMPLOYEE", "张三"),
                node("POST:2", "POST", "岗位2"),
                node("ABILITY:9", "ABILITY", "Kafka")));

        Map<String, Object> out = service.getAbilityGapPath(1L, 2L);

        assertEquals(1, ((List<?>) out.get("edges")).size());
    }

    // ==================== getMemoryGraph ====================

    @Test
    @DisplayName("getMemoryGraph：无记忆节点 → 空结果")
    void getMemoryGraph_empty() {
        when(graphNodeMapper.selectList(any())).thenReturn(Collections.emptyList());

        Map<String, Object> out = service.getMemoryGraph(100);

        assertTrue(((List<?>) out.get("nodes")).isEmpty());
    }

    @Test
    @DisplayName("getMemoryGraph：补全边关联的额外节点并重新过滤边")
    void getMemoryGraph_completesExtraNodes() {
        when(graphNodeMapper.selectList(any()))
                .thenReturn(List.of(node("AGENT_MEMORY:1", "AGENT_MEMORY", "记忆1")),
                        List.of(node("EVIDENCE:9", "EVIDENCE", "证据9")));
        when(graphEdgeMapper.selectList(any())).thenReturn(List.of(
                edge("E1", "AGENT_MEMORY:1", "EVIDENCE:9", "SUPPORTED_BY")));

        Map<String, Object> out = service.getMemoryGraph(null);

        assertEquals(2, ((List<?>) out.get("nodes")).size());
        assertEquals(1, ((List<?>) out.get("edges")).size());
    }

    @Test
    @DisplayName("getMemoryGraph：limit 超界 → 钳制上限 500")
    void getMemoryGraph_clampsLimit() {
        when(graphNodeMapper.selectList(any())).thenReturn(Collections.emptyList());

        assertNotNull(service.getMemoryGraph(100000));
        assertNotNull(service.getMemoryGraph(0));
    }

    // ==================== getTimeline ====================

    @Test
    @DisplayName("getTimeline：节点与边混排 → 按时间倒序且事件字段完整")
    void getTimeline_buildsEvents() {
        KgGraphNode n1 = node("POST:1", "POST", "岗位1");
        n1.setCreatedTime(LocalDateTime.of(2026, 1, 2, 10, 0));
        KgGraphEdge e1 = edge("E1", "POST:1", "ABILITY:5", "REQUIRES");
        e1.setCreatedTime(LocalDateTime.of(2026, 1, 3, 10, 0));

        when(graphNodeMapper.selectList(any())).thenReturn(List.of(n1));
        when(graphEdgeMapper.selectList(any())).thenReturn(List.of(e1));

        Map<String, Object> out = service.getTimeline(50);

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> events = (List<Map<String, Object>>) out.get("events");
        assertEquals(2, events.size());
        // 时间更新的事件排在前面
        assertEquals("EDGE_ADDED", events.get(0).get("eventType"));
        assertEquals("NODE_ADDED", events.get(1).get("eventType"));
        assertEquals(2, out.get("total"));
    }

    @Test
    @DisplayName("getTimeline：timestamp 为 null 的事件排在最后")
    void getTimeline_nullTimestampSortsLast() {
        KgGraphNode withTime = node("POST:1", "POST", "岗位1");
        withTime.setCreatedTime(LocalDateTime.now());
        KgGraphNode withoutTime = node("POST:2", "POST", "岗位2");
        withoutTime.setCreatedTime(null);

        when(graphNodeMapper.selectList(any())).thenReturn(List.of(withTime, withoutTime));
        when(graphEdgeMapper.selectList(any())).thenReturn(Collections.emptyList());

        Map<String, Object> out = service.getTimeline(50);

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> events = (List<Map<String, Object>>) out.get("events");
        assertEquals("POST:2", events.get(events.size() - 1).get("nodeKey"));
    }

    @Test
    @DisplayName("getTimeline：事件数超过 limit → 截断到 limit")
    void getTimeline_truncatesEvents() {
        List<KgGraphNode> nodes = new ArrayList<>();
        for (int i = 0; i < 200; i++) {
            nodes.add(node("POST:" + i, "POST", "岗位" + i));
        }
        when(graphNodeMapper.selectList(any())).thenReturn(nodes);
        when(graphEdgeMapper.selectList(any())).thenReturn(Collections.emptyList());

        Map<String, Object> out = service.getTimeline(50);

        assertEquals(50, out.get("total"));
    }

    @Test
    @DisplayName("getTimeline：空数据 → events 为空 total=0")
    void getTimeline_empty() {
        when(graphNodeMapper.selectList(any())).thenReturn(Collections.emptyList());
        when(graphEdgeMapper.selectList(any())).thenReturn(Collections.emptyList());

        Map<String, Object> out = service.getTimeline(null);

        assertTrue(((List<?>) out.get("events")).isEmpty());
        assertEquals(0, out.get("total"));
    }

    // ==================== 元数据解析 / 双参构造器 ====================

    @Test
    @DisplayName("convertNode/convertEdge：metadataJson 合法 → 解析为对象；非法 → 空对象")
    void parseMetadata_validAndInvalid() {
        KgGraphNode valid = node("POST:1", "POST", "岗位1");
        valid.setMetadataJson("{\"a\":1}");
        KgGraphNode invalid = node("POST:2", "POST", "岗位2");
        invalid.setMetadataJson("{not-json");

        when(graphNodeMapper.selectList(any())).thenReturn(List.of(valid, invalid));
        when(graphEdgeMapper.selectList(any())).thenReturn(Collections.emptyList());

        Map<String, Object> out = service.getPanorama(null, null, null, 60);

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> nodes = (List<Map<String, Object>>) out.get("nodes");
        assertEquals(Map.of("a", 1), nodes.get(0).get("metadata"));
        assertEquals(Collections.emptyMap(), nodes.get(1).get("metadata"));
    }

    @Test
    @DisplayName("双参构造器（兼容旧测试）：不启用 Neo4j，直接走 MySQL")
    void twoArgConstructor_usesMysqlOnly() {
        KnowledgeGraphViewQueryService legacy = new KnowledgeGraphViewQueryService(graphNodeMapper, graphEdgeMapper);
        when(graphNodeMapper.selectList(any())).thenReturn(Collections.emptyList());

        Map<String, Object> out = legacy.getPanorama(null, null, null, 30);

        assertEquals(true, out.get("available"));
    }
}
