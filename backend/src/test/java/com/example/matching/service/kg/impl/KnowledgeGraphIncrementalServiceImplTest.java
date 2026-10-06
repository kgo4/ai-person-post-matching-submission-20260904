package com.example.matching.service.kg.impl;

import com.example.matching.dto.kg.GraphBuildResultDTO;
import com.example.matching.entity.kg.KgGraphChangeSet;
import com.example.matching.entity.kg.KgGraphEdge;
import com.example.matching.entity.kg.KgGraphNode;
import com.example.matching.mapper.kg.KgGraphEdgeMapper;
import com.example.matching.mapper.kg.KgGraphNodeMapper;
import com.example.matching.port.post.PostQueryPort;
import com.example.matching.port.tag.TagQueryPort;
import com.example.matching.port.talent.TalentQueryPort;
import com.example.matching.service.kg.KnowledgeGraphBuildService;
import com.example.matching.service.kg.KnowledgeGraphIncrementalService.IncrementalGraphResult;
import com.example.matching.service.kg.Neo4jGraphStore;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.beans.factory.ObjectProvider;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.*;

/**
 * {@link KnowledgeGraphIncrementalServiceImpl} 单元测试。
 *
 * <p>覆盖 apply 的四条来源分支（POST_MODEL / EMP_ABILITY / ABILITY_TAG / 兜底全量重建）、
 * 删除降级、标签缺失时的技能点/能力事实节点、Neo4j 同步成功与失败、边替换与节点删除。
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class KnowledgeGraphIncrementalServiceImplTest {

    @Mock private KgGraphNodeMapper graphNodeMapper;
    @Mock private KgGraphEdgeMapper graphEdgeMapper;
    @Mock private PostQueryPort postQueryPort;
    @Mock private TalentQueryPort talentQueryPort;
    @Mock private TagQueryPort tagQueryPort;
    @Mock private KnowledgeGraphBuildService fullBuildService;
    @SuppressWarnings("rawtypes")
    @Mock private ObjectProvider neo4jGraphStoreProvider;
    @Mock private Neo4jGraphStore neo4jGraphStore;

    private KnowledgeGraphIncrementalServiceImpl service;

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
        // 生产环境注入的是 Spring Boot 自动配置的 ObjectMapper（已注册 JavaTimeModule），
        // 这里显式注册以对齐，否则序列化 LocalDate 会抛异常。
        ObjectMapper objectMapper = new ObjectMapper()
                .registerModule(new com.fasterxml.jackson.datatype.jsr310.JavaTimeModule());
        service = new KnowledgeGraphIncrementalServiceImpl(
                graphNodeMapper, graphEdgeMapper, postQueryPort, talentQueryPort, tagQueryPort,
                fullBuildService, neo4jGraphStoreProvider, objectMapper);
        // 默认无 Neo4j
        when(neo4jGraphStoreProvider.getIfAvailable()).thenReturn(null);
    }

    private KgGraphChangeSet changeSet(String sourceType, Long entityId, String operationType) {
        KgGraphChangeSet cs = new KgGraphChangeSet();
        cs.setChangeCode("KGC-1");
        cs.setSourceType(sourceType);
        cs.setEntityId(entityId);
        cs.setOperationType(operationType);
        return cs;
    }

    private PostQueryPort.PostDTO post(Long id, String name) {
        return new PostQueryPort.PostDTO(id, name, "P" + id, "P6", 1L, 1, "JD");
    }

    private PostQueryPort.PostAbilityDTO requirement(Long id, Long postId, Long tagId, String abilityName,
                                                     String techStack, Integer level, BigDecimal weight,
                                                     Integer isRequired, Integer isCore) {
        return new PostQueryPort.PostAbilityDTO(id, postId, tagId, level, weight, isRequired, isCore,
                "v1", null, abilityName, techStack, null);
    }

    private TagQueryPort.TagDTO tag(Long id, String name) {
        return new TagQueryPort.TagDTO(id, name, "T" + id, "TECH", "TECH", 3,
                null, null, "SYSTEM", null, null, null);
    }

    private TalentQueryPort.EmployeeDTO employee(Long id, String name) {
        return new TalentQueryPort.EmployeeDTO(id, name, "E" + id, 1, "L4", 1L, 2L, 1);
    }

    private TalentQueryPort.EmployeeAbilityDTO employeeAbility(Long id, Long empId, Long tagId,
                                                               String abilityName, Integer masteryLevel,
                                                               BigDecimal sourceWeight) {
        return new TalentQueryPort.EmployeeAbilityDTO(id, empId, tagId, masteryLevel, "AI_TEST",
                sourceWeight, LocalDate.now(), null, abilityName);
    }

    // ==================== POST_MODEL ====================

    @Test
    @DisplayName("apply(POST_MODEL)：岗位存在 + 标签命中 → upsert 岗位/能力节点与 REQUIRES 边")
    void apply_postModel_upsertsPostAndAbilityNodes() {
        when(postQueryPort.getPostById(100L)).thenReturn(post(100L, "Java工程师"));
        when(postQueryPort.listRequirementsByPostId(100L)).thenReturn(List.of(
                requirement(1L, 100L, 5L, null, "Java", 4, BigDecimal.ONE, 1, 1),
                requirement(2L, 100L, null, "接口自动化测试", "测试", 3, BigDecimal.ONE, 0, 0)));
        when(tagQueryPort.getTagById(5L)).thenReturn(tag(5L, "Java"));
        when(tagQueryPort.getTagById(null)).thenReturn(null);
        // 节点都不存在 → 走 insert
        when(graphNodeMapper.selectOne(any())).thenReturn(null);
        when(graphEdgeMapper.selectList(any())).thenReturn(Collections.emptyList());

        IncrementalGraphResult result = service.apply(changeSet("POST_MODEL", 100L, "UPSERT"));

        assertNotNull(result.graphVersion());
        assertTrue(result.graphVersion().startsWith("KGV_"));
        // 岗位节点 + Java 能力节点 + POST_SKILL_POINT 节点 = 3
        assertEquals(3, result.affectedNodeCount());
        // 两条 REQUIRES 边 + 被替换删除的旧边（0 条）
        assertEquals(2, result.affectedEdgeCount());
        verify(graphNodeMapper, times(3)).insert(any(KgGraphNode.class));
        verify(graphEdgeMapper, times(2)).insert(any(KgGraphEdge.class));

        ArgumentCaptor<KgGraphNode> nodeCaptor = ArgumentCaptor.forClass(KgGraphNode.class);
        verify(graphNodeMapper, times(3)).insert(nodeCaptor.capture());
        assertTrue(nodeCaptor.getAllValues().stream()
                .anyMatch(n -> "POST_SKILL_POINT:2".equals(n.getNodeKey())));
        assertTrue(nodeCaptor.getAllValues().stream()
                .anyMatch(n -> "ABILITY:5".equals(n.getNodeKey())));
    }

    @Test
    @DisplayName("apply(POST_MODEL)：岗位不存在 → 删除节点并统计删除量")
    void apply_postModel_postMissingRemovesNode() {
        when(postQueryPort.getPostById(100L)).thenReturn(null);
        when(graphEdgeMapper.selectList(any())).thenReturn(List.of(
                newKgEdge("REQUIRES_POST:100_ABILITY:5")));

        IncrementalGraphResult result = service.apply(changeSet("POST_MODEL", 100L, "UPSERT"));

        assertEquals(1, result.affectedNodeCount());
        assertEquals(1, result.affectedEdgeCount());
        verify(graphNodeMapper).delete(any());
        verify(graphNodeMapper, never()).insert(any(KgGraphNode.class));
    }

    @Test
    @DisplayName("apply(POST_MODEL)：DELETE 操作 → 同样走删除节点分支")
    void apply_postModel_deleteOperation() {
        when(postQueryPort.getPostById(100L)).thenReturn(post(100L, "Java工程师"));
        when(graphEdgeMapper.selectList(any())).thenReturn(Collections.emptyList());

        IncrementalGraphResult result = service.apply(changeSet("POST_MODEL", 100L, "DELETE"));

        assertEquals(1, result.affectedNodeCount());
        verify(graphNodeMapper).delete(any());
    }

    @Test
    @DisplayName("apply(POST_MODEL)：标签缺失且能力名为空 → 跳过该要求，不产生节点")
    void apply_postModel_skipsUnnamedRequirement() {
        when(postQueryPort.getPostById(100L)).thenReturn(post(100L, "Java工程师"));
        when(postQueryPort.listRequirementsByPostId(100L)).thenReturn(List.of(
                requirement(1L, 100L, null, "  ", "测试", 3, BigDecimal.ONE, 0, 0)));
        when(graphNodeMapper.selectOne(any())).thenReturn(null);
        when(graphEdgeMapper.selectList(any())).thenReturn(Collections.emptyList());

        IncrementalGraphResult result = service.apply(changeSet("POST_MODEL", 100L, "UPSERT"));

        // 仅岗位节点
        assertEquals(1, result.affectedNodeCount());
        assertEquals(0, result.affectedEdgeCount());
    }

    @Test
    @DisplayName("apply(POST_MODEL)：节点已存在 → 走 updateById 并回填 id")
    void apply_postModel_updatesExistingNode() {
        when(postQueryPort.getPostById(100L)).thenReturn(post(100L, "Java工程师"));
        when(postQueryPort.listRequirementsByPostId(100L)).thenReturn(Collections.emptyList());
        KgGraphNode existing = new KgGraphNode();
        existing.setId(999L);
        existing.setNodeKey("POST:100");
        when(graphNodeMapper.selectOne(any())).thenReturn(existing);
        when(graphEdgeMapper.selectList(any())).thenReturn(Collections.emptyList());

        IncrementalGraphResult result = service.apply(changeSet("POST_MODEL", 100L, "UPSERT"));

        assertEquals(1, result.affectedNodeCount());
        ArgumentCaptor<KgGraphNode> captor = ArgumentCaptor.forClass(KgGraphNode.class);
        verify(graphNodeMapper).updateById(captor.capture());
        assertEquals(999L, captor.getValue().getId());
    }

    // ==================== EMP_ABILITY ====================

    @Test
    @DisplayName("apply(EMP_ABILITY)：员工存在 + 标签命中 → HAS_ABILITY 边；无标签 → HAS_ABILITY_FACT")
    void apply_empAbility_upsertsBothEdgeTypes() {
        when(talentQueryPort.getEmployeeById(200L)).thenReturn(employee(200L, "张三"));
        when(talentQueryPort.listAbilitiesByEmpId(200L)).thenReturn(List.of(
                employeeAbility(11L, 200L, 5L, "Java", 4, BigDecimal.ONE),
                employeeAbility(12L, 200L, null, "接口自动化测试", 3, BigDecimal.ONE)));
        when(tagQueryPort.getTagById(5L)).thenReturn(tag(5L, "Java"));
        when(tagQueryPort.getTagById(null)).thenReturn(null);
        when(graphNodeMapper.selectOne(any())).thenReturn(null);
        when(graphEdgeMapper.selectList(any())).thenReturn(Collections.emptyList());

        IncrementalGraphResult result = service.apply(changeSet("EMP_ABILITY", 200L, "UPSERT"));

        // 员工节点 + Java 能力节点 + 能力事实节点 = 3
        assertEquals(3, result.affectedNodeCount());
        // HAS_ABILITY 一条 + HAS_ABILITY_FACT 一条
        assertEquals(2, result.affectedEdgeCount());

        ArgumentCaptor<KgGraphEdge> edgeCaptor = ArgumentCaptor.forClass(KgGraphEdge.class);
        verify(graphEdgeMapper, times(2)).insert(edgeCaptor.capture());
        assertTrue(edgeCaptor.getAllValues().stream()
                .anyMatch(e -> "HAS_ABILITY".equals(e.getEdgeType())));
        assertTrue(edgeCaptor.getAllValues().stream()
                .anyMatch(e -> "HAS_ABILITY_FACT".equals(e.getEdgeType())));
    }

    @Test
    @DisplayName("apply(EMP_ABILITY)：员工不存在 → 删除节点")
    void apply_empAbility_employeeMissing() {
        when(talentQueryPort.getEmployeeById(200L)).thenReturn(null);
        when(graphEdgeMapper.selectList(any())).thenReturn(Collections.emptyList());

        IncrementalGraphResult result = service.apply(changeSet("EMP_ABILITY", 200L, "UPSERT"));

        assertEquals(1, result.affectedNodeCount());
        verify(graphNodeMapper).delete(any());
    }

    @Test
    @DisplayName("apply(EMP_ABILITY)：无标签且能力名为空 → 跳过该能力")
    void apply_empAbility_skipsUnnamedAbility() {
        when(talentQueryPort.getEmployeeById(200L)).thenReturn(employee(200L, "张三"));
        when(talentQueryPort.listAbilitiesByEmpId(200L)).thenReturn(List.of(
                new TalentQueryPort.EmployeeAbilityDTO(12L, 200L, null, 3, null, null, null, null, "   ")));
        when(tagQueryPort.getTagById(null)).thenReturn(null);
        when(graphNodeMapper.selectOne(any())).thenReturn(null);
        when(graphEdgeMapper.selectList(any())).thenReturn(Collections.emptyList());

        IncrementalGraphResult result = service.apply(changeSet("EMP_ABILITY", 200L, "UPSERT"));

        assertEquals(1, result.affectedNodeCount());
        assertEquals(0, result.affectedEdgeCount());
    }

    @Test
    @DisplayName("apply(EMP_ABILITY)：DISABLE 操作 → 删除节点")
    void apply_empAbility_disableOperation() {
        when(talentQueryPort.getEmployeeById(200L)).thenReturn(employee(200L, "张三"));
        when(graphEdgeMapper.selectList(any())).thenReturn(Collections.emptyList());

        IncrementalGraphResult result = service.apply(changeSet("EMP_ABILITY", 200L, "DISABLE"));

        assertEquals(1, result.affectedNodeCount());
        verify(graphNodeMapper).delete(any());
    }

    // ==================== ABILITY_TAG ====================

    @Test
    @DisplayName("apply(ABILITY_TAG)：标签存在 → upsert 能力节点")
    void apply_abilityTag_upsertsTagNode() {
        when(tagQueryPort.getTagById(7L)).thenReturn(tag(7L, "Redis"));
        when(graphNodeMapper.selectOne(any())).thenReturn(null);

        IncrementalGraphResult result = service.apply(changeSet("ABILITY_TAG", 7L, "UPSERT"));

        assertEquals(1, result.affectedNodeCount());
        assertEquals(0, result.affectedEdgeCount());
        ArgumentCaptor<KgGraphNode> captor = ArgumentCaptor.forClass(KgGraphNode.class);
        verify(graphNodeMapper).insert(captor.capture());
        assertEquals("ABILITY:7", captor.getValue().getNodeKey());
        assertEquals("ABILITY", captor.getValue().getNodeType());
        assertEquals(3, captor.getValue().getLevelValue());
    }

    @Test
    @DisplayName("apply(ABILITY_TAG)：标签不存在 → 删除节点")
    void apply_abilityTag_tagMissing() {
        when(tagQueryPort.getTagById(7L)).thenReturn(null);
        when(graphEdgeMapper.selectList(any())).thenReturn(Collections.emptyList());

        IncrementalGraphResult result = service.apply(changeSet("ABILITY_TAG", 7L, "UPSERT"));

        assertEquals(1, result.affectedNodeCount());
        verify(graphNodeMapper).delete(any());
    }

    // ==================== 兜底全量重建 ====================

    @Test
    @DisplayName("apply(未知来源)：降级为全量重建，返回重建的节点/边数量")
    void apply_unknownSourceFallsBackToFullRebuild() {
        GraphBuildResultDTO full = new GraphBuildResultDTO();
        full.setNodeCount(321);
        full.setEdgeCount(654);
        full.setGraphVersion("KGV_FULL");
        when(fullBuildService.rebuildFullGraph()).thenReturn(full);

        IncrementalGraphResult result = service.apply(changeSet("KNOWLEDGE_DOMAIN", 1L, "UPSERT"));

        assertEquals(321, result.affectedNodeCount());
        assertEquals(654, result.affectedEdgeCount());
        // 注意：返回值用的是 apply 内部生成的时间戳版本号，而非重建结果的 graphVersion
        assertTrue(result.graphVersion().startsWith("KGV_"));
        verify(fullBuildService).rebuildFullGraph();
        verify(graphNodeMapper, never()).insert(any(KgGraphNode.class));
    }

    @Test
    @DisplayName("apply(未知来源) + Neo4j 可用：全量重建分支不触发增量同步")
    void apply_fullRebuildSkipsNeo4jSync() {
        GraphBuildResultDTO full = new GraphBuildResultDTO();
        full.setNodeCount(1);
        full.setEdgeCount(1);
        full.setGraphVersion("KGV_FULL");
        when(fullBuildService.rebuildFullGraph()).thenReturn(full);
        when(neo4jGraphStoreProvider.getIfAvailable()).thenReturn(neo4jGraphStore);

        service.apply(changeSet("UNKNOWN_TYPE", 1L, "UPSERT"));

        verify(neo4jGraphStore, never()).syncIncremental(anyList(), anyList(), anyList(), anyList());
    }

    // ==================== Neo4j 同步 ====================

    @Test
    @DisplayName("apply：Neo4j 可用 → 调用增量同步；返回 FAIL 时保留 MySQL 结果不回滚")
    void apply_neo4jSyncFailureKeepsMysqlResult() {
        when(tagQueryPort.getTagById(7L)).thenReturn(tag(7L, "Redis"));
        when(graphNodeMapper.selectOne(any())).thenReturn(null);
        when(neo4jGraphStoreProvider.getIfAvailable()).thenReturn(neo4jGraphStore);
        when(neo4jGraphStore.syncIncremental(anyList(), anyList(), anyList(), anyList()))
                .thenReturn(Map.of("status", "FAIL", "message", "neo4j down"));

        IncrementalGraphResult result = service.apply(changeSet("ABILITY_TAG", 7L, "UPSERT"));

        assertEquals(1, result.affectedNodeCount());
        verify(neo4jGraphStore).syncIncremental(anyList(), anyList(), anyList(), anyList());
    }

    @Test
    @DisplayName("apply：Neo4j 同步成功 → 正常返回统计")
    void apply_neo4jSyncSuccess() {
        when(tagQueryPort.getTagById(7L)).thenReturn(tag(7L, "Redis"));
        when(graphNodeMapper.selectOne(any())).thenReturn(null);
        when(neo4jGraphStoreProvider.getIfAvailable()).thenReturn(neo4jGraphStore);
        when(neo4jGraphStore.syncIncremental(anyList(), anyList(), anyList(), anyList()))
                .thenReturn(Map.of("status", "OK"));

        IncrementalGraphResult result = service.apply(changeSet("ABILITY_TAG", 7L, "UPSERT"));

        assertEquals(1, result.affectedNodeCount());
    }

    // ==================== 边替换与节点删除细节 ====================

    @Test
    @DisplayName("apply(ABILITY_TAG)：replaceSourceEdges 统计被删除的旧边")
    void apply_replaceSourceEdgesCountsDeletedEdges() {
        when(postQueryPort.getPostById(100L)).thenReturn(post(100L, "Java工程师"));
        when(postQueryPort.listRequirementsByPostId(100L)).thenReturn(List.of(
                requirement(1L, 100L, 5L, null, "Java", 4, BigDecimal.ONE, 1, 1)));
        when(tagQueryPort.getTagById(5L)).thenReturn(tag(5L, "Java"));
        when(graphNodeMapper.selectOne(any())).thenReturn(null);
        when(graphEdgeMapper.selectList(any())).thenReturn(List.of(
                newKgEdge("OLD-1"), newKgEdge("OLD-2")));

        IncrementalGraphResult result = service.apply(changeSet("POST_MODEL", 100L, "UPSERT"));

        // 新增 2 个节点（岗位 + 能力），边：1 新增 + 2 删除
        assertEquals(2, result.affectedNodeCount());
        assertEquals(3, result.affectedEdgeCount());
        verify(graphEdgeMapper).delete(any());
    }

    @Test
    @DisplayName("apply(ABILITY_TAG)：removeNode 同时删除关联边与节点并统计")
    void apply_removeNodeCountsRelatedEdges() {
        when(tagQueryPort.getTagById(7L)).thenReturn(null);
        when(graphEdgeMapper.selectList(any())).thenReturn(List.of(newKgEdge("REL-1"), newKgEdge("REL-2")));

        IncrementalGraphResult result = service.apply(changeSet("ABILITY_TAG", 7L, "UPSERT"));

        assertEquals(1, result.affectedNodeCount());
        assertEquals(2, result.affectedEdgeCount());
        verify(graphEdgeMapper).delete(any());
        verify(graphNodeMapper).delete(any());
    }

    private KgGraphEdge newKgEdge(String key) {
        KgGraphEdge e = new KgGraphEdge();
        e.setEdgeKey(key);
        return e;
    }
}
