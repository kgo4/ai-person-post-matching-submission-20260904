package com.example.matching.service.kg;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.example.matching.entity.kg.KgGraphEdge;
import com.example.matching.entity.kg.KgGraphNode;
import com.example.matching.mapper.kg.KgGraphEdgeMapper;
import com.example.matching.mapper.kg.KgGraphNodeMapper;
import com.example.matching.port.post.PostQueryPort;
import com.example.matching.port.talent.TalentQueryPort;
import com.example.matching.service.kg.impl.KnowledgeGraphQueryServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import com.example.matching.dto.kg.context.GraphAbilityEvidenceContext;
import com.example.matching.dto.kg.context.GraphContextStatus;
import com.example.matching.dto.kg.context.GraphMatchAbilityContext;
import com.example.matching.dto.kg.context.GraphMatchContext;
import com.example.matching.dto.kg.context.GraphMatchState;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * 知识图谱查询服务单元测试
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class KnowledgeGraphQueryServiceTest {

    @Mock
    private KgGraphNodeMapper graphNodeMapper;
    @Mock
    private KgGraphEdgeMapper graphEdgeMapper;
    @Mock
    private PostQueryPort postQueryPort;
    @Mock
    private TalentQueryPort talentQueryPort;

    @InjectMocks
    private KnowledgeGraphQueryServiceImpl knowledgeGraphQueryService;

    private KgGraphNode postNode;
    private KgGraphNode abilityNode;
    private KgGraphEdge requiresEdge;

    @BeforeEach
    void setUp() {
        org.springframework.test.util.ReflectionTestUtils.setField(knowledgeGraphQueryService, "viewQueryService",
                new com.example.matching.service.kg.impl.KnowledgeGraphViewQueryService(graphNodeMapper, graphEdgeMapper));
        org.springframework.test.util.ReflectionTestUtils.setField(knowledgeGraphQueryService, "contextQueryService",
                new com.example.matching.service.kg.impl.KnowledgeGraphContextQueryService(
                        graphNodeMapper, graphEdgeMapper, postQueryPort, talentQueryPort));
        postNode = new KgGraphNode();
        postNode.setId(1L);
        postNode.setNodeKey("POST:1");
        postNode.setNodeType("POST");
        postNode.setRefId(1L);
        postNode.setLabel("Java开发工程师");
        postNode.setCategory("P6");
        postNode.setStatus("ACTIVE");
        postNode.setWeightValue(BigDecimal.ONE);

        abilityNode = new KgGraphNode();
        abilityNode.setId(2L);
        abilityNode.setNodeKey("ABILITY:1");
        abilityNode.setNodeType("ABILITY");
        abilityNode.setRefId(1L);
        abilityNode.setLabel("Java");
        abilityNode.setCategory("TECHNICAL");
        abilityNode.setLevelValue(3);
        abilityNode.setStatus("ACTIVE");
        abilityNode.setWeightValue(BigDecimal.ONE);
        abilityNode.setMetadataJson("{\"tagCode\":\"SKILL-JAVA\",\"description\":\"Java后端开发能力\"}");

        requiresEdge = new KgGraphEdge();
        requiresEdge.setId(1L);
        requiresEdge.setEdgeKey("POST:1-REQUIRES-ABILITY:1");
        requiresEdge.setSourceNodeKey("POST:1");
        requiresEdge.setTargetNodeKey("ABILITY:1");
        requiresEdge.setEdgeType("REQUIRES");
        requiresEdge.setWeightValue(new BigDecimal("80"));
        requiresEdge.setConfidenceScore(new BigDecimal("90"));
        requiresEdge.setMetadataJson("{\"minRequiredLevel\":3,\"isRequired\":1,\"sourceType\":\"JD_IMPORT\"}");
    }

    @Test
    @DisplayName("全景图谱：只包含节点集合中存在的边")
    void getPanorama_onlyIncludeEdgesWithBothNodes() {
        List<KgGraphNode> nodes = Arrays.asList(postNode, abilityNode);
        when(graphNodeMapper.selectList(any())).thenReturn(nodes);

        // 边的两端节点都在节点集合中
        when(graphEdgeMapper.selectList(any())).thenReturn(Arrays.asList(requiresEdge));

        Map<String, Object> result = knowledgeGraphQueryService.getPanorama(null, null, null, 300);

        assertTrue((Boolean) result.get("available"));
        List<?> resultNodes = (List<?>) result.get("nodes");
        List<?> resultEdges = (List<?>) result.get("edges");
        assertEquals(2, resultNodes.size());
        assertEquals(1, resultEdges.size());
    }

    @Test
    @DisplayName("全景图谱：空结果")
    void getPanorama_emptyResult() {
        when(graphNodeMapper.selectList(any())).thenReturn(Collections.emptyList());

        Map<String, Object> result = knowledgeGraphQueryService.getPanorama(null, null, null, 300);

        assertTrue((Boolean) result.get("available"));
        List<?> resultNodes = (List<?>) result.get("nodes");
        assertEquals(0, resultNodes.size());
    }

    @Test
    @DisplayName("全景图谱：返回节点等级和元数据供前端筛选")
    void getPanorama_returnsMetadataForFiltering() {
        when(graphNodeMapper.selectList(any())).thenReturn(Arrays.asList(postNode, abilityNode));
        when(graphEdgeMapper.selectList(any())).thenReturn(Arrays.asList(requiresEdge));

        Map<String, Object> result = knowledgeGraphQueryService.getPanorama(null, null, null, 300);

        List<?> resultNodes = (List<?>) result.get("nodes");
        Map<?, ?> abilityDto = (Map<?, ?>) resultNodes.stream()
                .map(Map.class::cast)
                .filter(node -> "ABILITY:1".equals(node.get("id")))
                .findFirst()
                .orElseThrow();

        assertEquals(3, abilityDto.get("level"));
        assertEquals("SKILL-JAVA", ((Map<?, ?>) abilityDto.get("metadata")).get("tagCode"));

        List<?> resultEdges = (List<?>) result.get("edges");
        Map<?, ?> edgeDto = (Map<?, ?>) resultEdges.get(0);
        assertEquals("JD_IMPORT", ((Map<?, ?>) edgeDto.get("metadata")).get("sourceType"));
    }

    @Test
    @DisplayName("全景图谱：限制补全边和补全节点数量")
    @SuppressWarnings("unchecked")
    void getPanorama_capsRelatedEdgesAndSupplementalNodes() {
        // 构建 1 个初始节点
        when(graphNodeMapper.selectList(any()))
                .thenReturn(List.of(postNode))       // 第一次：初始节点
                .thenReturn(Collections.emptyList()); // 第二次：补全节点（被截断到0）

        // 500 条边远超 maxEdges=min(20*2,240)=40
        List<KgGraphEdge> manyEdges = new ArrayList<>();
        for (int i = 0; i < 500; i++) {
            KgGraphEdge e = createEdge((long) i, "EDGE:" + i,
                    "POST:1", "ABILITY:" + (i + 100), "REQUIRES",
                    "{\"isRequired\":1}", BigDecimal.TEN);
            manyEdges.add(e);
        }
        when(graphEdgeMapper.selectList(any())).thenReturn(manyEdges);

        Map<String, Object> result = knowledgeGraphQueryService.getPanorama(null, null, null, 20);

        List<?> resultEdges = (List<?>) result.get("edges");
        List<?> resultNodes = (List<?>) result.get("nodes");
        assertTrue(resultEdges.size() <= 100, "edges should be capped but got " + resultEdges.size());
        assertTrue(resultNodes.size() <= 40, "nodes should be capped but got " + resultNodes.size());
    }

    @Test
    @DisplayName("岗位中心图谱：返回岗位及其关联节点")
    void getPostCenteredGraph_success() {
        when(graphNodeMapper.selectOne(any())).thenReturn(postNode);
        when(graphEdgeMapper.selectList(any())).thenReturn(Arrays.asList(requiresEdge));
        when(graphNodeMapper.selectList(any())).thenReturn(Arrays.asList(postNode, abilityNode));

        Map<String, Object> result = knowledgeGraphQueryService.getPostCenteredGraph(1L);

        assertTrue((Boolean) result.get("available"));
        List<?> resultNodes = (List<?>) result.get("nodes");
        assertTrue(resultNodes.size() > 0);
    }

    @Test
    @DisplayName("岗位中心图谱：岗位不存在")
    void getPostCenteredGraph_notFound() {
        when(graphNodeMapper.selectOne(any())).thenReturn(null);

        Map<String, Object> result = knowledgeGraphQueryService.getPostCenteredGraph(999L);

        assertTrue((Boolean) result.get("available"));
        List<?> resultNodes = (List<?>) result.get("nodes");
        assertEquals(0, resultNodes.size());
    }

    @Test
    @DisplayName("人岗匹配上下文：缺失员工返回明确状态")
    void graphMatchContext_marksMissingEmployeeExplicitly() {
        GraphMatchContext context = GraphMatchContext.empty(
                GraphContextStatus.EMPLOYEE_NOT_FOUND, 7L, 9L);

        assertEquals(GraphContextStatus.EMPLOYEE_NOT_FOUND, context.status());
        assertEquals(7L, context.employeeId());
        assertEquals(9L, context.postId());
        assertTrue(context.abilities().isEmpty());
    }

    @Test
    @DisplayName("人岗匹配上下文：派生状态确定性排序")
    void getMatchContext_derivesStatesInDeterministicOrder() {
        stubMatchGraph();

        GraphMatchContext context = knowledgeGraphQueryService.getMatchContext(7L, 9L);

        assertEquals(GraphContextStatus.AVAILABLE, context.status());
        assertEquals(List.of(GraphMatchState.SATISFIED, GraphMatchState.LEVEL_GAP,
                        GraphMatchState.MISSING, GraphMatchState.BONUS),
                context.abilities().stream().map(GraphMatchAbilityContext::state).toList());
        assertEquals(2, context.abilities().get(1).employeeMasteryLevel());
        assertNull(context.abilities().get(2).employeeMasteryLevel());
    }

    @Test
    @DisplayName("能力证据上下文：过滤已审核证据并截断")
    void getAbilityEvidenceContext_filtersAndCapsEvidence() {
        stubEvidenceEdges(8);

        GraphAbilityEvidenceContext context = knowledgeGraphQueryService.getAbilityEvidenceContext(31L, 7L);

        assertEquals(5, context.evidence().size());
        assertTrue(context.evidence().stream().allMatch(e -> "VERIFIED".equals(e.reviewStatus())));
    }

    @Test
    @DisplayName("人岗匹配上下文：图谱边为空时按正式能力名称构建关系")
    void getMatchContext_fallsBackToFormalAbilityNamesWhenGraphHasNoEdges() {
        when(graphNodeMapper.selectOne(any())).thenReturn(null);
        when(postQueryPort.getPostById(9L)).thenReturn(new PostQueryPort.PostDTO(
                9L, "Java开发工程师", "JAVA-DEV", "P6", null, 1, null));
        when(talentQueryPort.getEmployeeById(7L)).thenReturn(new TalentQueryPort.EmployeeDTO(
                7L, "测试员工", "EMP007", 1, null, null, null, 1));
        when(postQueryPort.listRequirementsByPostId(9L)).thenReturn(List.of(
                new PostQueryPort.PostAbilityDTO(101L, 9L, 700L, 3, BigDecimal.valueOf(80),
                        1, 1, "v1", null, "Spring Boot")));
        when(talentQueryPort.listAbilitiesByEmpId(7L)).thenReturn(List.of(
                new TalentQueryPort.EmployeeAbilityDTO(201L, 7L, null, 4,
                        "ASSESSMENT_WORKFLOW", BigDecimal.ONE, null, null, " Spring  Boot ")));

        GraphMatchContext context = knowledgeGraphQueryService.getMatchContext(7L, 9L);

        assertEquals(GraphContextStatus.AVAILABLE, context.status());
        assertEquals("Java开发工程师", context.postName());
        assertEquals(1, context.abilities().size());
        assertEquals(GraphMatchState.SATISFIED, context.abilities().get(0).state());
        assertEquals(4, context.abilities().get(0).employeeMasteryLevel());
    }

    @Test
    @DisplayName("能力证据上下文：员工只能读取与自己关联且已验证的证据")
    void getAbilityEvidenceContext_scopesEvidenceToEmployeeAndVerifiedStatus() {
        KgGraphNode ability = createNode(31L, "ABILITY:31", "ABILITY", 31L, "Spring Boot");
        KgGraphNode verifiedEvidence = createNode(401L, "EVIDENCE:401", "EVIDENCE", 401L, "员工证据");
        KgGraphNode otherEmployeeEvidence = createNode(402L, "EVIDENCE:402", "EVIDENCE", 402L, "他人证据");
        KgGraphNode pendingEvidence = createNode(403L, "EVIDENCE:403", "EVIDENCE", 403L, "待审核证据");

        KgGraphEdge abilityVerified = createEdge(401L, "ABILITY:31-SUPPORTED_BY-EVIDENCE:401",
                "ABILITY:31", "EVIDENCE:401", "SUPPORTED_BY", "{\"evidenceStatus\":\"VERIFIED\"}", BigDecimal.ONE);
        KgGraphEdge abilityOtherEmployee = createEdge(402L, "ABILITY:31-SUPPORTED_BY-EVIDENCE:402",
                "ABILITY:31", "EVIDENCE:402", "SUPPORTED_BY", "{\"evidenceStatus\":\"VERIFIED\"}", BigDecimal.ONE);
        KgGraphEdge abilityPending = createEdge(403L, "ABILITY:31-SUPPORTED_BY-EVIDENCE:403",
                "ABILITY:31", "EVIDENCE:403", "SUPPORTED_BY", "{\"evidenceStatus\":\"PENDING\"}", BigDecimal.ONE);
        KgGraphEdge employeeVerified = createEdge(404L, "EMPLOYEE:7-SUPPORTED_BY-EVIDENCE:401",
                "EMPLOYEE:7", "EVIDENCE:401", "SUPPORTED_BY", "{\"evidenceStatus\":\"VERIFIED\"}", BigDecimal.ONE);
        KgGraphEdge employeePending = createEdge(405L, "EMPLOYEE:7-SUPPORTED_BY-EVIDENCE:403",
                "EMPLOYEE:7", "EVIDENCE:403", "SUPPORTED_BY", "{\"evidenceStatus\":\"PENDING\"}", BigDecimal.ONE);

        when(graphNodeMapper.selectOne(any())).thenReturn(ability);
        when(graphEdgeMapper.selectList(any()))
                .thenReturn(List.of(abilityVerified, abilityOtherEmployee, abilityPending))
                .thenReturn(List.of(employeeVerified, employeePending));
        when(graphNodeMapper.selectList(any())).thenReturn(List.of(verifiedEvidence, otherEmployeeEvidence, pendingEvidence));

        GraphAbilityEvidenceContext context = knowledgeGraphQueryService.getAbilityEvidenceContext(31L, 7L);

        assertEquals(List.of(401L), context.evidence().stream().map(e -> e.evidenceId()).toList());
        assertEquals("VERIFIED", context.evidence().get(0).reviewStatus());
    }

    @Test
    @DisplayName("能力证据上下文：没有可信审核结果的证据不可读")
    void getAbilityEvidenceContext_excludesPendingRejectedAndUnspecifiedEvidence() {
        KgGraphNode ability = createNode(32L, "ABILITY:32", "ABILITY", 32L, "Kafka");
        KgGraphNode verified = createNode(501L, "EVIDENCE:501", "EVIDENCE", 501L, "已验证");
        KgGraphNode pending = createNode(502L, "EVIDENCE:502", "EVIDENCE", 502L, "待审核");
        KgGraphNode rejected = createNode(503L, "EVIDENCE:503", "EVIDENCE", 503L, "已拒绝");
        KgGraphNode unspecified = createNode(504L, "EVIDENCE:504", "EVIDENCE", 504L, "未知状态");

        when(graphNodeMapper.selectOne(any())).thenReturn(ability);
        when(graphEdgeMapper.selectList(any())).thenReturn(List.of(
                createEdge(501L, "a1", "ABILITY:32", "EVIDENCE:501", "SUPPORTED_BY", "{\"evidenceStatus\":\"VERIFIED\"}", BigDecimal.ONE),
                createEdge(502L, "a2", "ABILITY:32", "EVIDENCE:502", "SUPPORTED_BY", "{\"evidenceStatus\":\"PENDING\"}", BigDecimal.ONE),
                createEdge(503L, "a3", "ABILITY:32", "EVIDENCE:503", "SUPPORTED_BY", "{\"evidenceStatus\":\"REJECTED\"}", BigDecimal.ONE),
                createEdge(504L, "a4", "ABILITY:32", "EVIDENCE:504", "SUPPORTED_BY", "{}", BigDecimal.ONE)));
        when(graphNodeMapper.selectList(any())).thenReturn(List.of(verified, pending, rejected, unspecified));

        GraphAbilityEvidenceContext context = knowledgeGraphQueryService.getAbilityEvidenceContext(32L, null);

        assertEquals(List.of(501L), context.evidence().stream().map(e -> e.evidenceId()).toList());
    }

    @Test
    @DisplayName("学习前置上下文：沿能力、领域和知识点路径查询前置知识")
    void getLearningPrerequisiteContext_followsAbilityDomainKnowledgeNodePath() {
        KgGraphNode ability = createNode(61L, "ABILITY:61", "ABILITY", 61L, "Spring Cloud");
        KgGraphNode knowledgeNode = createNode(701L, "KNOWLEDGE_NODE:701", "KNOWLEDGE_NODE", 701L, "服务治理");
        KgGraphNode prerequisiteNode = createNode(702L, "KNOWLEDGE_NODE:702", "KNOWLEDGE_NODE", 702L, "分布式基础");

        KgGraphEdge belongsToDomain = createEdge(601L, "belongs", "ABILITY:61", "KNOWLEDGE_DOMAIN:88",
                "BELONGS_TO_DOMAIN", "{}", BigDecimal.ONE);
        KgGraphEdge domainHasKnowledge = createEdge(602L, "contains", "KNOWLEDGE_DOMAIN:88", "KNOWLEDGE_NODE:701",
                "HAS_KNOWLEDGE_NODE", "{}", BigDecimal.ONE);
        KgGraphEdge prerequisite = createEdge(603L, "prerequisite", "KNOWLEDGE_NODE:702", "KNOWLEDGE_NODE:701",
                "PREREQUISITE_OF", "{\"graphVersion\":\"KGV_TEST\",\"sourceRefs\":[\"kg:KNOWLEDGE_NODE:701\"]}", BigDecimal.ONE);

        when(graphNodeMapper.selectList(any()))
                .thenReturn(List.of(ability))
                .thenReturn(List.of(knowledgeNode, prerequisiteNode));
        when(graphEdgeMapper.selectList(any()))
                .thenReturn(List.of(belongsToDomain))
                .thenReturn(List.of(domainHasKnowledge))
                .thenReturn(List.of(prerequisite));

        var context = knowledgeGraphQueryService.getLearningPrerequisiteContext(List.of(61L));

        assertEquals(1, context.prerequisites().size());
        var item = context.prerequisites().get(0);
        assertEquals(61L, item.abilityId());
        assertEquals(702L, item.prerequisiteAbilityId());
        assertEquals("分布式基础", item.prerequisiteAbilityName());
    }

    // ===================== 辅助方法 =====================

    private KgGraphNode createNode(Long id, String key, String type, Long refId, String label) {
        KgGraphNode node = new KgGraphNode();
        node.setId(id);
        node.setNodeKey(key);
        node.setNodeType(type);
        node.setRefId(refId);
        node.setLabel(label);
        node.setStatus("ACTIVE");
        node.setWeightValue(BigDecimal.ONE);
        return node;
    }

    private KgGraphEdge createEdge(Long id, String key, String source, String target, String type,
                                    String metadataJson, BigDecimal weight) {
        KgGraphEdge edge = new KgGraphEdge();
        edge.setId(id);
        edge.setEdgeKey(key);
        edge.setSourceNodeKey(source);
        edge.setTargetNodeKey(target);
        edge.setEdgeType(type);
        edge.setMetadataJson(metadataJson);
        edge.setWeightValue(weight);
        return edge;
    }

    /**
     * 桩：4 个能力需求 + 4 个员工能力 → SATISFIED, LEVEL_GAP, MISSING, BONUS
     */
    private void stubMatchGraph() {
        KgGraphNode empNode = createNode(100L, "EMPLOYEE:7", "EMPLOYEE", 7L, "Lin");
        KgGraphNode postNode2 = createNode(101L, "POST:9", "POST", 9L, "Java高级");

        KgGraphNode ab1 = createNode(10L, "ABILITY:10", "ABILITY", 10L, "Java");
        ab1.setMetadataJson("{\"tagCode\":\"SKILL-JAVA\"}");
        KgGraphNode ab2 = createNode(11L, "ABILITY:11", "ABILITY", 11L, "Kafka");
        ab2.setMetadataJson("{\"tagCode\":\"SKILL-KAFKA\"}");
        KgGraphNode ab3 = createNode(12L, "ABILITY:12", "ABILITY", 12L, "Kubernetes");
        ab3.setMetadataJson("{\"tagCode\":\"SKILL-K8S\"}");
        KgGraphNode ab4 = createNode(13L, "ABILITY:13", "ABILITY", 13L, "English");
        ab4.setMetadataJson("{\"tagCode\":\"SKILL-EN\"}");

        // 4 条岗位要求：核心必须、必须、必须、加分
        KgGraphEdge req1 = createEdge(1L, "POST:9-REQUIRES-ABILITY:10", "POST:9", "ABILITY:10",
                "REQUIRES", "{\"isRequired\":1,\"isCore\":1,\"minRequiredLevel\":3}", new BigDecimal("90"));
        KgGraphEdge req2 = createEdge(2L, "POST:9-REQUIRES-ABILITY:11", "POST:9", "ABILITY:11",
                "REQUIRES", "{\"isRequired\":1,\"minRequiredLevel\":3}", new BigDecimal("80"));
        KgGraphEdge req3 = createEdge(3L, "POST:9-REQUIRES-ABILITY:12", "POST:9", "ABILITY:12",
                "REQUIRES", "{\"isRequired\":1,\"minRequiredLevel\":2}", new BigDecimal("70"));
        KgGraphEdge req4 = createEdge(4L, "POST:9-REQUIRES-ABILITY:13", "POST:9", "ABILITY:13",
                "REQUIRES", "{\"isRequired\":0}", new BigDecimal("30"));

        // 员工能力：Java(满足)、Kafka(等级不足)、English(加分有)
        KgGraphEdge emp1 = createEdge(5L, "EMPLOYEE:7-HAS_ABILITY-ABILITY:10", "EMPLOYEE:7", "ABILITY:10",
                "HAS_ABILITY", "{\"masteryLevel\":4}", BigDecimal.ONE);
        KgGraphEdge emp2 = createEdge(6L, "EMPLOYEE:7-HAS_ABILITY-ABILITY:11", "EMPLOYEE:7", "ABILITY:11",
                "HAS_ABILITY", "{\"masteryLevel\":2}", BigDecimal.ONE);
        KgGraphEdge emp4 = createEdge(7L, "EMPLOYEE:7-HAS_ABILITY-ABILITY:13", "EMPLOYEE:7", "ABILITY:13",
                "HAS_ABILITY", "{\"masteryLevel\":5}", BigDecimal.ONE);

        // 第一次 selectOne: employee node, 第二次: post node
        when(graphNodeMapper.selectOne(any()))
                .thenReturn(empNode)
                .thenReturn(postNode2);

        // REQUIRES 边查询
        when(graphEdgeMapper.selectList(any()))
                .thenReturn(List.of(req1, req2, req3, req4))  // requirements
                .thenReturn(List.of(emp1, emp2, emp4))        // employee abilities
                .thenReturn(List.of());                        // SUPPORTED_BY edges (无证据)

        when(graphNodeMapper.selectList(any()))
                .thenReturn(List.of(abilityNode, ab1, ab2, ab3, ab4)); // ability nodes
    }

    /**
     * 桩：8 条证据边（6 条已审核 + 2 条已拒绝）
     */
    private void stubEvidenceEdges(int total) {
        KgGraphNode abilityNode31 = createNode(200L, "ABILITY:31", "ABILITY", 31L, "Spring Boot");
        when(graphNodeMapper.selectOne(any())).thenReturn(abilityNode31);

        List<KgGraphEdge> allEvidence = new ArrayList<>();
        for (int i = 1; i <= total; i++) {
            String reviewStatus = i <= 6 ? "VERIFIED" : "REJECTED";
            KgGraphNode evNode = createNode(300L + i, "EVIDENCE:" + i, "EVIDENCE", (long) i, "证据" + i);
            KgGraphEdge evEdge = createEdge(200L + i, "ABILITY:31-SUPPORTED_BY-EVIDENCE:" + i,
                    "ABILITY:31", "EVIDENCE:" + i, "SUPPORTED_BY",
                    "{\"evidenceStatus\":\"" + reviewStatus + "\",\"sourceRefs\":[\"ref" + i + "\"]}",
                    new BigDecimal(90 - i));
            allEvidence.add(evEdge);
        }

        // selectOne for ability node
        // selectList: evidence edges, then evidence nodes
        when(graphEdgeMapper.selectList(any())).thenReturn(allEvidence);
        List<KgGraphNode> evidenceNodes = new ArrayList<>();
        for (int i = 1; i <= total; i++) {
            evidenceNodes.add(createNode(300L + i, "EVIDENCE:" + i, "EVIDENCE", (long) i, "证据" + i));
        }
        when(graphNodeMapper.selectList(any())).thenReturn(evidenceNodes);
    }
}
