package com.example.matching.service.kg.build;

import com.example.matching.entity.kg.KgGraphNode;
import com.example.matching.entity.kg.KnowledgeDomain;
import com.example.matching.entity.kg.KnowledgeNode;
import com.example.matching.entity.rag.RagKnowledgeDocument;
import com.example.matching.mapper.kg.KnowledgeDomainMapper;
import com.example.matching.mapper.kg.KnowledgeNodeMapper;
import com.example.matching.port.contest.ContestQueryPort;
import com.example.matching.port.evolution.EvolutionQueryPort;
import com.example.matching.port.learning.LearningQueryPort;
import com.example.matching.port.post.PostQueryPort;
import com.example.matching.port.tag.TagQueryPort;
import com.example.matching.port.talent.TalentQueryPort;
import com.example.matching.service.kg.support.KnowledgeNodeDependencyResolver;
import com.example.matching.service.rag.KnowledgeDocumentService;
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

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link GraphNodeProjectionService} 单元测试。
 *
 * <p>逐类节点投影（岗位/岗位族/技能点/能力/员工/证据/RAG/学习资源/演化事件/
 * 学习计划/步骤/项目任务/知识域/知识节点）的正常路径与空集合、实体缺失、
 * 分页、异常降级分支。Neo4j 写入器与全部端口均为 mock。
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class GraphNodeProjectionServiceTest {

    @Mock private PostQueryPort postQueryPort;
    @Mock private TagQueryPort tagQueryPort;
    @Mock private TalentQueryPort talentQueryPort;
    @Mock private LearningQueryPort learningQueryPort;
    @Mock private KnowledgeDocumentService knowledgeDocumentService;
    @Mock private ContestQueryPort contestQueryPort;
    @Mock private EvolutionQueryPort evolutionQueryPort;
    @Mock private KnowledgeDomainMapper knowledgeDomainMapper;
    @Mock private KnowledgeNodeMapper knowledgeNodeMapper;
    @Mock private KnowledgeNodeDependencyResolver knowledgeNodeDependencyResolver;
    @Mock private GraphSnapshotWriter snapshotWriter;

    private GraphNodeProjectionService service;

    /**
     * MyBatis-Plus 的 LambdaQueryWrapper 需要实体已登记表信息，
     * 否则会抛 "can not find lambda cache for this entity"（纯单测无 Spring 上下文）。
     */
    @BeforeAll
    static void initMybatisPlusLambdaCache() {
        var cfg = new com.baomidou.mybatisplus.core.MybatisConfiguration();
        var assistant = new org.apache.ibatis.builder.MapperBuilderAssistant(cfg, "");
        com.baomidou.mybatisplus.core.metadata.TableInfoHelper.initTableInfo(assistant, KnowledgeDomain.class);
        com.baomidou.mybatisplus.core.metadata.TableInfoHelper.initTableInfo(assistant, KnowledgeNode.class);
    }

    @BeforeEach
    void setUp() {
        service = new GraphNodeProjectionService(
                postQueryPort, tagQueryPort, talentQueryPort, learningQueryPort,
                knowledgeDocumentService, contestQueryPort, evolutionQueryPort,
                knowledgeDomainMapper, knowledgeNodeMapper, knowledgeNodeDependencyResolver,
                new ObjectMapper(), snapshotWriter);
        // 默认所有空集合 + 关键写入器字符串方法返回值
        when(postQueryPort.listActivePosts(anyInt())).thenReturn(Collections.emptyList());
        when(postQueryPort.listActivePrototypes(anyInt())).thenReturn(Collections.emptyList());
        when(postQueryPort.listActivePostAbilityModels(anyInt())).thenReturn(Collections.emptyList());
        when(tagQueryPort.listActiveTags(anyInt())).thenReturn(Collections.emptyList());
        when(talentQueryPort.listAbilitiesPaginated(anyInt(), anyInt())).thenReturn(Collections.emptyList());
        when(talentQueryPort.listEmployeesPaginated(anyInt(), anyInt())).thenReturn(Collections.emptyList());
        when(contestQueryPort.listEvidencePaginated(anyInt(), anyInt())).thenReturn(Collections.emptyList());
        when(knowledgeDocumentService.listAllActiveDocuments(anyInt())).thenReturn(Collections.emptyList());
        when(learningQueryPort.listActiveResources(anyInt())).thenReturn(Collections.emptyList());
        when(evolutionQueryPort.listAllTasks(anyInt())).thenReturn(Collections.emptyList());
        when(learningQueryPort.listPlansPaginated(anyInt(), anyInt())).thenReturn(Collections.emptyList());
        when(learningQueryPort.listStepsPaginated(anyInt(), anyInt())).thenReturn(Collections.emptyList());
        when(learningQueryPort.listProjectTasksPaginated(anyInt(), anyInt())).thenReturn(Collections.emptyList());
        when(knowledgeDomainMapper.selectList(any())).thenReturn(Collections.emptyList());
        when(knowledgeNodeMapper.selectList(any())).thenReturn(Collections.emptyList());
        when(snapshotWriter.generateNodeKey(any(), any())).thenAnswer(inv -> inv.getArgument(0) + ":" + inv.getArgument(1));
        when(snapshotWriter.toJson(any())).thenReturn("{}");
        when(knowledgeNodeDependencyResolver.parsePrerequisiteIds(any())).thenReturn(List.of());
    }

    /** 捕获所有写入节点，方便断言节点类型/键/标签 */
    @SuppressWarnings("unchecked")
    private List<KgGraphNode> capturedNodes() {
        ArgumentCaptor<List<KgGraphNode>> captor = ArgumentCaptor.forClass(List.class);
        verify(snapshotWriter, org.mockito.Mockito.atLeast(0)).batchInsertNodes(captor.capture());
        List<KgGraphNode> all = new ArrayList<>();
        captor.getAllValues().forEach(all::addAll);
        return all;
    }

    private static long countType(List<KgGraphNode> nodes, String type) {
        return nodes.stream().filter(n -> type.equals(n.getNodeType())).count();
    }

    // ==================== 空集合：全流水线仍要返回完整 counter ====================

    @Test
    @DisplayName("projectNodes：所有数据源为空 → 15 类节点计数均为 0")
    void projectNodes_allEmpty() {
        Map<String, Integer> counter = service.projectNodes(
                new GraphBuildContext("KGV1", "2026-01-01T00:00:00"));

        assertEquals(15, counter.size());
        assertTrue(counter.values().stream().allMatch(v -> v == 0), "空数据源不应产生任何节点: " + counter);
    }

    @Test
    @DisplayName("projectNodes：证据分页抛异常 → EVIDENCE 计 0，其余阶段继续执行")
    void projectNodes_evidenceFailureDegrades() {
        when(contestQueryPort.listEvidencePaginated(anyInt(), anyInt()))
                .thenThrow(new RuntimeException("db down"));
        when(postQueryPort.listActivePosts(anyInt())).thenReturn(List.of(
                new PostQueryPort.PostDTO(1L, "Java", "P1", "P5", 2L, 1, "jd")));

        Map<String, Integer> counter = service.projectNodes(
                new GraphBuildContext("KGV1", "2026-01-01T00:00:00"));

        assertEquals(0, counter.get("EVIDENCE"));
        assertEquals(1, counter.get("POST"), "证据失败不应阻断岗位节点投影");
    }

    // ==================== 岗位 / 岗位族 / 技能点 / 技术栈 ====================

    @Test
    @DisplayName("岗位节点：非空列表 → 逐条落库，计数正确，metadata 序列化")
    void buildPostNodes_writesAll() {
        when(postQueryPort.listActivePosts(anyInt())).thenReturn(List.of(
                new PostQueryPort.PostDTO(1L, "Java后端", "P001", "P5", 2L, 1, "jd"),
                new PostQueryPort.PostDTO(2L, "前端", "P002", "P4", 3L, 1, "jd")));

        Map<String, Integer> counter = service.projectNodes(
                new GraphBuildContext("KGV1", "2026-01-01T00:00:00"));

        assertEquals(2, counter.get("POST"));
        List<KgGraphNode> nodes = capturedNodes();
        assertEquals(2, countType(nodes, "POST"));
        assertEquals("Java后端", nodes.stream().filter(n -> "POST".equals(n.getNodeType()))
                .findFirst().orElseThrow().getLabel());
    }

    @Test
    @DisplayName("岗位族节点：原型列表非空 → 落库并计数")
    void buildPostFamilyNodes_writesAll() {
        when(postQueryPort.listActivePrototypes(anyInt())).thenReturn(List.of(
                new PostQueryPort.PostPrototypeDTO(1L, "后端原型", "互联网", "研发", "desc")));

        Map<String, Integer> counter = service.projectNodes(
                new GraphBuildContext("KGV1", "2026-01-01T00:00:00"));

        assertEquals(1, counter.get("POST_FAMILY"));
    }

    @Test
    @DisplayName("技能点：跳过 id/abilityName 缺失的模型；相同 techStack 只建一个技术栈节点")
    void buildPostCapabilityNodes_skipsInvalidAndDedupsStacks() {
        when(postQueryPort.listActivePostAbilityModels(anyInt())).thenReturn(List.of(
                new PostQueryPort.PostAbilityDTO(1L, 100L, 5L, 3, new BigDecimal("20"), 1, 0,
                        "v1", null, "Java", "JVM", null),
                new PostQueryPort.PostAbilityDTO(2L, 100L, 6L, 3, new BigDecimal("10"), 0, 0,
                        "v1", null, "GC", "JVM", null),
                // 无效：id 为 null
                new PostQueryPort.PostAbilityDTO(null, 100L, 6L, 3, BigDecimal.ONE, 0, 0,
                        "v1", null, "无效", "JVM", null),
                // 无效：abilityName 空白
                new PostQueryPort.PostAbilityDTO(4L, 100L, 6L, 3, BigDecimal.ONE, 0, 0,
                        "v1", null, "  ", "JVM", null),
                // techStack 为空 → 归入「通用工程能力」
                new PostQueryPort.PostAbilityDTO(5L, 100L, 7L, 2, null, 0, 0,
                        "v1", null, "排错", null, null)));

        Map<String, Integer> counter = service.projectNodes(
                new GraphBuildContext("KGV1", "2026-01-01T00:00:00"));

        assertEquals(3, counter.get("POST_SKILL_POINT"));
        assertEquals(2, counter.get("POST_TECH_STACK"), "JVM + 通用工程能力 共两个技术栈");
    }

    // ==================== 能力节点 ====================

    @Test
    @DisplayName("能力节点：标签正式名优先；无 tagId 的员工能力另建 ABILITY_FACT")
    void buildAbilityNodes_formalNamesAndFacts() {
        when(tagQueryPort.listActiveTags(anyInt())).thenReturn(List.of(
                new TagQueryPort.TagDTO(10L, "Java(旧名)", "T10", "TECH", null, 2, null, null, "SYS", null, null, null)));
        when(talentQueryPort.listAbilitiesPaginated(eq(1), anyInt())).thenReturn(List.of(
                new TalentQueryPort.EmployeeAbilityDTO(1L, 55L, 10L, 4, "AI_TEST", BigDecimal.ONE, null, null, "Java"),
                // 无 tagId → 生成 ABILITY_FACT 节点
                new TalentQueryPort.EmployeeAbilityDTO(2L, 56L, null, 3, "MANUAL", null, null, null, "Kafka")));

        Map<String, Integer> counter = service.projectNodes(
                new GraphBuildContext("KGV1", "2026-01-01T00:00:00"));

        // 1 个 ABILITY 节点（正式名 Java 覆盖旧名）+ 1 个 ABILITY_FACT 节点
        assertEquals(2, counter.get("ABILITY"));
        List<KgGraphNode> nodes = capturedNodes();
        assertEquals(1, countType(nodes, "ABILITY_FACT"));
        assertEquals("Java", nodes.stream().filter(n -> "ABILITY".equals(n.getNodeType()))
                .findFirst().orElseThrow().getLabel());
    }

    @Test
    @DisplayName("能力节点：分页满页时继续翻页")
    void buildAbilityNodes_paginates() {
        List<TalentQueryPort.EmployeeAbilityDTO> fullPage = new ArrayList<>();
        for (int i = 0; i < 500; i++) {
            fullPage.add(new TalentQueryPort.EmployeeAbilityDTO((long) i, 1L, null, 1, "M", null, null, null, "A" + i));
        }
        when(talentQueryPort.listAbilitiesPaginated(eq(1), anyInt())).thenReturn(fullPage);
        when(talentQueryPort.listAbilitiesPaginated(eq(2), anyInt())).thenReturn(List.of(
                new TalentQueryPort.EmployeeAbilityDTO(999L, 1L, null, 1, "M", null, null, null, "Last")));

        service.projectNodes(new GraphBuildContext("KGV1", "2026-01-01T00:00:00"));

        verify(talentQueryPort).listAbilitiesPaginated(eq(2), anyInt());
    }

    // ==================== 员工 / 证据 ====================

    @Test
    @DisplayName("员工节点：分页聚合总数")
    void buildEmployeeNodes_paginatesAndCounts() {
        when(talentQueryPort.listEmployeesPaginated(eq(1), anyInt())).thenReturn(List.of(
                new TalentQueryPort.EmployeeDTO(1L, "张三", "E1", 1, "P5", 2L, 3L, 1),
                new TalentQueryPort.EmployeeDTO(2L, "李四", "E2", 0, "P4", 2L, 3L, 1)));

        Map<String, Integer> counter = service.projectNodes(
                new GraphBuildContext("KGV1", "2026-01-01T00:00:00"));

        assertEquals(2, counter.get("EMPLOYEE"));
    }

    @Test
    @DisplayName("证据节点：正常数据 → 落库并计数")
    void buildEvidenceNodes_success() {
        when(contestQueryPort.listEvidencePaginated(eq(1), anyInt())).thenReturn(List.of(
                new ContestQueryPort.ContestEvidenceDTO(1L, "JD_IMPORT", 2L, "标题", "文本",
                        "ABILITY_TAG", 10L, "Java", 10L, BigDecimal.ONE, BigDecimal.ONE,
                        "VERIFIED", null)));

        Map<String, Integer> counter = service.projectNodes(
                new GraphBuildContext("KGV1", "2026-01-01T00:00:00"));

        assertEquals(1, counter.get("EVIDENCE"));
    }

    // ==================== RAG / 学习资源 / 演化事件 ====================

    @Test
    @DisplayName("RAG 文档节点：落库并计数")
    void buildRagDocumentNodes_success() {
        RagKnowledgeDocument doc = new RagKnowledgeDocument();
        doc.setId(1L);
        doc.setTitle("材料");
        doc.setSourceType("JD_IMPORT");
        doc.setSourceRefId(2L);
        doc.setChunkCount(5);
        when(knowledgeDocumentService.listAllActiveDocuments(anyInt())).thenReturn(List.of(doc));

        Map<String, Integer> counter = service.projectNodes(
                new GraphBuildContext("KGV1", "2026-01-01T00:00:00"));

        assertEquals(1, counter.get("RAG_DOCUMENT"));
    }

    @Test
    @DisplayName("学习资源节点：落库并计数")
    void buildLearningResourceNodes_success() {
        when(learningQueryPort.listActiveResources(anyInt())).thenReturn(List.of(
                new LearningQueryPort.LearningResourceDTO(1L, "R1", "Java", 10L, "课程", "COURSE",
                        3, "http://x", "d", "慕课", 1)));

        Map<String, Integer> counter = service.projectNodes(
                new GraphBuildContext("KGV1", "2026-01-01T00:00:00"));

        assertEquals(1, counter.get("LEARNING_RESOURCE"));
    }

    @Test
    @DisplayName("演化事件节点：落库并计数")
    void buildEvolutionEventNodes_success() {
        when(evolutionQueryPort.listAllTasks(anyInt())).thenReturn(List.of(
                new EvolutionQueryPort.EvolutionTaskDTO(1L, "T1", "任务", 100L, "DONE", "软件开发")));

        Map<String, Integer> counter = service.projectNodes(
                new GraphBuildContext("KGV1", "2026-01-01T00:00:00"));

        assertEquals(1, counter.get("EVOLUTION_EVENT"));
    }

    // ==================== 学习计划 / 步骤 / 项目任务 ====================

    @Test
    @DisplayName("学习计划节点：落库并聚合计数")
    void buildLearningPlanNodes_success() {
        when(learningQueryPort.listPlansPaginated(eq(1), anyInt())).thenReturn(List.of(
                new LearningQueryPort.LearningPathPlanDTO(1L, 10L, 20L, 30L, "计划", "ACTIVE",
                        BigDecimal.valueOf(60), BigDecimal.valueOf(90))));

        Map<String, Integer> counter = service.projectNodes(
                new GraphBuildContext("KGV1", "2026-01-01T00:00:00"));

        assertEquals(1, counter.get("LEARNING_PLAN"));
    }

    @Test
    @DisplayName("学习步骤节点：落库并聚合计数")
    void buildLearningStepNodes_success() {
        when(learningQueryPort.listStepsPaginated(eq(1), anyInt())).thenReturn(List.of(
                new LearningQueryPort.LearningPathStepDTO(1L, 10L, 5L, "Java", 1, 3,
                        "LEVEL", "HIGH", "步骤", "PENDING")));

        Map<String, Integer> counter = service.projectNodes(
                new GraphBuildContext("KGV1", "2026-01-01T00:00:00"));

        assertEquals(1, counter.get("LEARNING_STEP"));
    }

    @Test
    @DisplayName("项目任务节点：落库并聚合计数")
    void buildProjectTaskNodes_success() {
        when(learningQueryPort.listProjectTasksPaginated(eq(1), anyInt())).thenReturn(List.of(
                new LearningQueryPort.LearningProjectTaskDTO(1L, 10L, 20L, 5L, "任务", "HARD", "DONE")));

        Map<String, Integer> counter = service.projectNodes(
                new GraphBuildContext("KGV1", "2026-01-01T00:00:00"));

        assertEquals(1, counter.get("PROJECT_TASK"));
    }

    // ==================== 知识域 / 知识节点 ====================

    @Test
    @DisplayName("知识域节点：落库并计数")
    void buildKnowledgeDomainNodes_success() {
        KnowledgeDomain domain = new KnowledgeDomain();
        domain.setId(1L);
        domain.setDomainName("后端");
        domain.setDomainCode("BE");
        when(knowledgeDomainMapper.selectList(any())).thenReturn(List.of(domain));

        Map<String, Integer> counter = service.projectNodes(
                new GraphBuildContext("KGV1", "2026-01-01T00:00:00"));

        assertEquals(1, counter.get("KNOWLEDGE_DOMAIN"));
    }

    @Test
    @DisplayName("知识节点：落库并计数，先修 ID 走依赖解析器")
    void buildKnowledgeNodeNodes_success() {
        KnowledgeNode node = new KnowledgeNode();
        node.setId(1L);
        node.setNodeName("JVM");
        node.setDomainId(2L);
        node.setParentId(3L);
        node.setNodeLevel(2);
        node.setNodeDescription("描述");
        when(knowledgeNodeMapper.selectList(any())).thenReturn(List.of(node));
        when(knowledgeNodeDependencyResolver.parsePrerequisiteIds(any())).thenReturn(List.of(7L, 8L));

        Map<String, Integer> counter = service.projectNodes(
                new GraphBuildContext("KGV1", "2026-01-01T00:00:00"));

        assertEquals(1, counter.get("KNOWLEDGE_NODE"));
        verify(knowledgeNodeDependencyResolver).parsePrerequisiteIds(node);
    }

    // ==================== 全量数据：所有阶段都被触发 ====================

    @Test
    @DisplayName("projectNodes：全量数据 → 15 类节点全部非 0，且都调用写入器")
    void projectNodes_fullPipeline() {
        when(postQueryPort.listActivePosts(anyInt())).thenReturn(List.of(
                new PostQueryPort.PostDTO(1L, "Java", "P1", "P5", 2L, 1, "jd")));
        when(postQueryPort.listActivePrototypes(anyInt())).thenReturn(List.of(
                new PostQueryPort.PostPrototypeDTO(1L, "原型", "IT", "研发", "d")));
        when(postQueryPort.listActivePostAbilityModels(anyInt())).thenReturn(List.of(
                new PostQueryPort.PostAbilityDTO(1L, 1L, 5L, 3, BigDecimal.ONE, 1, 0, "v1", null, "Java", "JVM", null)));
        when(tagQueryPort.listActiveTags(anyInt())).thenReturn(List.of(
                new TagQueryPort.TagDTO(5L, "Java", "T", "TECH", null, 2, null, null, "SYS", null, null, null)));
        when(talentQueryPort.listAbilitiesPaginated(eq(1), anyInt())).thenReturn(List.of(
                new TalentQueryPort.EmployeeAbilityDTO(1L, 1L, 5L, 3, "M", null, null, null, "Java")));
        when(talentQueryPort.listEmployeesPaginated(eq(1), anyInt())).thenReturn(List.of(
                new TalentQueryPort.EmployeeDTO(1L, "张三", "E1", 1, "P5", 2L, 3L, 1)));
        when(contestQueryPort.listEvidencePaginated(eq(1), anyInt())).thenReturn(List.of(
                new ContestQueryPort.ContestEvidenceDTO(1L, "JD", 2L, "t", "x", "ABILITY_TAG", 5L, "Java", 5L,
                        BigDecimal.ONE, BigDecimal.ONE, "VERIFIED", null)));
        RagKnowledgeDocument doc = new RagKnowledgeDocument();
        doc.setId(1L);
        when(knowledgeDocumentService.listAllActiveDocuments(anyInt())).thenReturn(List.of(doc));
        when(learningQueryPort.listActiveResources(anyInt())).thenReturn(List.of(
                new LearningQueryPort.LearningResourceDTO(1L, "R", "Java", 5L, "t", "COURSE", 3, "u", "d", "p", 1)));
        when(evolutionQueryPort.listAllTasks(anyInt())).thenReturn(List.of(
                new EvolutionQueryPort.EvolutionTaskDTO(1L, "T", "t", 1L, "DONE", "d")));
        when(learningQueryPort.listPlansPaginated(eq(1), anyInt())).thenReturn(List.of(
                new LearningQueryPort.LearningPathPlanDTO(1L, 1L, 1L, 1L, "p", "ACTIVE", BigDecimal.ONE, BigDecimal.TEN)));
        when(learningQueryPort.listStepsPaginated(eq(1), anyInt())).thenReturn(List.of(
                new LearningQueryPort.LearningPathStepDTO(1L, 1L, 1L, "a", 1, 2, "L", "H", "s", "PENDING")));
        when(learningQueryPort.listProjectTasksPaginated(eq(1), anyInt())).thenReturn(List.of(
                new LearningQueryPort.LearningProjectTaskDTO(1L, 1L, 1L, 1L, "t", "HARD", "DONE")));
        KnowledgeDomain domain = new KnowledgeDomain();
        domain.setId(1L);
        domain.setDomainName("D");
        domain.setDomainCode("C");
        when(knowledgeDomainMapper.selectList(any())).thenReturn(List.of(domain));
        KnowledgeNode kn = new KnowledgeNode();
        kn.setId(1L);
        when(knowledgeNodeMapper.selectList(any())).thenReturn(List.of(kn));

        Map<String, Integer> counter = service.projectNodes(
                new GraphBuildContext("KGV1", "2026-01-01T00:00:00"));

        assertEquals(15, counter.size());
        assertTrue(counter.values().stream().allMatch(v -> v > 0), "全量数据下每类都应 >0: " + counter);
    }

    @Test
    @DisplayName("证据阶段写入失败 → 捕获异常并计 0，不向外抛")
    void buildEvidenceNodes_swallowsWriteFailure() {
        when(contestQueryPort.listEvidencePaginated(eq(1), anyInt())).thenReturn(List.of(
                new ContestQueryPort.ContestEvidenceDTO(1L, "JD", 2L, "t", "x", "ABILITY_TAG", 5L, "Java", 5L,
                        BigDecimal.ONE, BigDecimal.ONE, "VERIFIED", null)));
        // 仅非空列表写入失败（空列表真实实现会短路，服务仍会照常调用）
        doAnswer(inv -> {
            List<?> nodes = inv.getArgument(0);
            if (nodes != null && !nodes.isEmpty()) {
                throw new RuntimeException("neo4j down");
            }
            return null;
        }).when(snapshotWriter).batchInsertNodes(any());

        Map<String, Integer> counter = service.projectNodes(
                new GraphBuildContext("KGV1", "2026-01-01T00:00:00"));

        assertEquals(0, counter.get("EVIDENCE"));
    }
}
