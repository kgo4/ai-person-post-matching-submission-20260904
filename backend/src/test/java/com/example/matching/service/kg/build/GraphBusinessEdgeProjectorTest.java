package com.example.matching.service.kg.build;

import com.example.matching.entity.kg.KgGraphEdge;
import com.example.matching.entity.rag.RagKnowledgeDocument;
import com.example.matching.port.contest.ContestQueryPort;
import com.example.matching.port.evolution.EvolutionQueryPort;
import com.example.matching.port.learning.LearningQueryPort;
import com.example.matching.port.matching.MatchingQueryPort;
import com.example.matching.port.post.PostQueryPort;
import com.example.matching.port.talent.TalentQueryPort;
import com.example.matching.service.rag.KnowledgeDocumentService;
import com.fasterxml.jackson.databind.ObjectMapper;
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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;

/**
 * {@link GraphBusinessEdgeProjector} 单元测试。
 *
 * <p>覆盖岗位要求边、技能点边、员工能力边、岗位族要求边、证据支撑边（各 targetType 分支）、
 * RAG 派生边（各 sourceType 分支）、学习资源推荐边、演化事件边、匹配边；含空集合、
 * 实体缺失、异常降级与 ":null" 键过滤分支。全部端口与写入器均 mock。
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class GraphBusinessEdgeProjectorTest {

    @Mock private PostQueryPort postQueryPort;
    @Mock private TalentQueryPort talentQueryPort;
    @Mock private LearningQueryPort learningQueryPort;
    @Mock private MatchingQueryPort matchingQueryPort;
    @Mock private KnowledgeDocumentService knowledgeDocumentService;
    @Mock private ContestQueryPort contestQueryPort;
    @Mock private EvolutionQueryPort evolutionQueryPort;
    @Mock private GraphSnapshotWriter snapshotWriter;

    private GraphBusinessEdgeProjector projector;

    private static final GraphBuildContext CTX = new GraphBuildContext("KGV_TEST", "2026-01-01T00:00:00");

    @BeforeEach
    void setUp() {
        projector = new GraphBusinessEdgeProjector(
                postQueryPort, talentQueryPort, learningQueryPort, matchingQueryPort,
                knowledgeDocumentService, contestQueryPort, evolutionQueryPort, snapshotWriter,
                new ObjectMapper());
        when(snapshotWriter.generateNodeKey(any(), any()))
                .thenAnswer(inv -> inv.getArgument(0) + ":" + inv.getArgument(1));
        when(snapshotWriter.generateEdgeKey(any(), any(), any()))
                .thenAnswer(inv -> inv.getArgument(0) + "_" + inv.getArgument(1) + "_" + inv.getArgument(2));
        // 默认空集合
        when(postQueryPort.listActivePostAbilityModels(anyInt())).thenReturn(Collections.emptyList());
        when(postQueryPort.listAllPrototypeTags()).thenReturn(Collections.emptyList());
        when(talentQueryPort.listAbilitiesPaginated(anyInt(), anyInt())).thenReturn(Collections.emptyList());
        when(contestQueryPort.listAllEvidence(anyInt())).thenReturn(Collections.emptyList());
        when(knowledgeDocumentService.listAllActiveDocuments(anyInt())).thenReturn(Collections.emptyList());
        when(learningQueryPort.listActiveResources(anyInt())).thenReturn(Collections.emptyList());
        when(evolutionQueryPort.listAllTasks(anyInt())).thenReturn(Collections.emptyList());
        when(matchingQueryPort.listRecordsPaginated(anyInt(), anyInt())).thenReturn(Collections.emptyList());
    }

    @SuppressWarnings("unchecked")
    private List<KgGraphEdge> capturedEdges() {
        ArgumentCaptor<List<KgGraphEdge>> captor = ArgumentCaptor.forClass(List.class);
        org.mockito.Mockito.verify(snapshotWriter, org.mockito.Mockito.atLeast(0))
                .batchInsertEdges(captor.capture(), any());
        List<KgGraphEdge> all = new ArrayList<>();
        captor.getAllValues().forEach(all::addAll);
        return all;
    }

    private static Map<String, Integer> counter() {
        return new LinkedHashMap<>();
    }

    // ==================== 岗位要求边 ====================

    @Test
    @DisplayName("岗位要求边：空模型列表 → REQUIRES 与 HAS_SKILL_POINT 均为 0")
    void postRequiresAbility_empty() {
        Map<String, Integer> c = counter();
        projector.buildPostRequiresAbilityEdges(c, CTX);

        assertEquals(0, c.get("REQUIRES"));
        assertEquals(0, c.get("HAS_SKILL_POINT"));
    }

    @Test
    @DisplayName("岗位要求边：有效模型 → 生成 REQUIRES + HAS_SKILL_POINT 边；无效模型被跳过")
    void postRequiresAbility_successAndSkipsInvalid() {
        when(postQueryPort.listActivePostAbilityModels(anyInt())).thenReturn(List.of(
                new PostQueryPort.PostAbilityDTO(1L, 100L, 5L, 3, new BigDecimal("20"), 1, 1,
                        "v1", null, "Java", "JVM", null),
                // 无效：id 为 null
                new PostQueryPort.PostAbilityDTO(null, 100L, 5L, 3, BigDecimal.ONE, 1, 0,
                        "v1", null, "X", null, null),
                // 无效：postId 为 null
                new PostQueryPort.PostAbilityDTO(3L, null, 5L, 3, BigDecimal.ONE, 1, 0,
                        "v1", null, "Y", null, null),
                // 无效：abilityName 空白
                new PostQueryPort.PostAbilityDTO(4L, 100L, 5L, 3, BigDecimal.ONE, 1, 0,
                        "v1", null, "  ", null, null)));

        Map<String, Integer> c = counter();
        projector.buildPostRequiresAbilityEdges(c, CTX);

        assertEquals(1, c.get("REQUIRES"));
        assertEquals(1, c.get("HAS_SKILL_POINT"));
        List<KgGraphEdge> edges = capturedEdges();
        assertEquals(2, edges.size());
        assertTrue(edges.stream().anyMatch(e -> "REQUIRES".equals(e.getEdgeType())));
        assertTrue(edges.stream().anyMatch(e -> "HAS_SKILL_POINT".equals(e.getEdgeType())));
    }

    @Test
    @DisplayName("岗位要求边：techStack 为空时归入「通用工程能力」技术栈")
    void postRequiresAbility_defaultStack() {
        when(postQueryPort.listActivePostAbilityModels(anyInt())).thenReturn(List.of(
                new PostQueryPort.PostAbilityDTO(1L, 100L, 5L, 3, BigDecimal.ONE, 0, 0,
                        "v1", null, "Java", null, null)));

        Map<String, Integer> c = counter();
        projector.buildPostRequiresAbilityEdges(c, CTX);

        assertTrue(capturedEdges().stream()
                .anyMatch(e -> "POST_TECH_STACK:通用工程能力".equals(e.getSourceNodeKey())));
    }

    // ==================== 员工能力边 ====================

    @Test
    @DisplayName("员工能力边：有效记录生成 HAS_ABILITY_FACT，无效记录跳过；空集合计 0")
    void employeeHasAbility_emptyAndSuccess() {
        Map<String, Integer> empty = counter();
        projector.buildEmployeeHasAbilityEdges(empty, CTX);
        assertEquals(0, empty.get("HAS_ABILITY"));

        when(talentQueryPort.listAbilitiesPaginated(eq(1), anyInt())).thenReturn(List.of(
                new TalentQueryPort.EmployeeAbilityDTO(1L, 55L, 10L, 4, "AI_TEST", BigDecimal.ONE, null, null, "Java"),
                // 无效：id 为 null
                new TalentQueryPort.EmployeeAbilityDTO(null, 55L, 10L, 4, "M", null, null, null, "X"),
                // 无效：abilityName 空白
                new TalentQueryPort.EmployeeAbilityDTO(3L, 55L, 10L, 4, "M", null, null, null, " ")));

        Map<String, Integer> c = counter();
        projector.buildEmployeeHasAbilityEdges(c, CTX);

        assertEquals(1, c.get("HAS_ABILITY"));
        assertTrue(capturedEdges().stream().anyMatch(e -> "HAS_ABILITY_FACT".equals(e.getEdgeType())));
    }

    @Test
    @DisplayName("员工能力边：满页时翻页累加")
    void employeeHasAbility_paginates() {
        List<TalentQueryPort.EmployeeAbilityDTO> full = new ArrayList<>();
        for (int i = 0; i < 500; i++) {
            full.add(new TalentQueryPort.EmployeeAbilityDTO((long) i, 1L, null, 1, "M", null, null, null, "A" + i));
        }
        when(talentQueryPort.listAbilitiesPaginated(eq(1), anyInt())).thenReturn(full);
        when(talentQueryPort.listAbilitiesPaginated(eq(2), anyInt())).thenReturn(List.of(
                new TalentQueryPort.EmployeeAbilityDTO(999L, 1L, null, 1, "M", null, null, null, "Last")));

        Map<String, Integer> c = counter();
        projector.buildEmployeeHasAbilityEdges(c, CTX);

        assertEquals(501, c.get("HAS_ABILITY"));
    }

    // ==================== 岗位族要求边 ====================

    @Test
    @DisplayName("岗位族要求边：空 / 非空都能正确计数")
    void postFamilyRequiresAbility() {
        Map<String, Integer> empty = counter();
        projector.buildPostFamilyRequiresAbilityEdges(empty, CTX);
        assertEquals(0, empty.get("POST_FAMILY_REQUIRES"));

        when(postQueryPort.listAllPrototypeTags()).thenReturn(List.of(
                new PostQueryPort.PostPrototypeTagDTO(1L, 2L, 3L)));

        Map<String, Integer> c = counter();
        projector.buildPostFamilyRequiresAbilityEdges(c, CTX);
        assertEquals(1, c.get("POST_FAMILY_REQUIRES"));
    }

    // ==================== 证据支撑边 ====================

    @Test
    @DisplayName("证据支撑边：空列表 → SUPPORTED_BY=0；查询异常 → 降级 0 且不抛")
    void evidenceSupportedBy_emptyAndFailure() {
        Map<String, Integer> empty = counter();
        projector.buildEvidenceSupportedByEdges(empty, CTX);
        assertEquals(0, empty.get("SUPPORTED_BY"));

        when(contestQueryPort.listAllEvidence(anyInt())).thenThrow(new RuntimeException("db down"));
        Map<String, Integer> failed = counter();
        projector.buildEvidenceSupportedByEdges(failed, CTX);
        assertEquals(0, failed.get("SUPPORTED_BY"));
    }

    private ContestQueryPort.ContestEvidenceDTO tagEvidence(String targetType, Long targetRefId, Long tagId) {
        return new ContestQueryPort.ContestEvidenceDTO(1L, "JD", 2L, "标题", "文本", targetType, targetRefId,
                "Java", tagId, BigDecimal.ONE, BigDecimal.ONE, "VERIFIED", null);
    }

    @Test
    @DisplayName("证据支撑边：ABILITY_TAG → 指向能力节点")
    void evidenceSupportedBy_abilityTag() {
        when(contestQueryPort.listAllEvidence(anyInt())).thenReturn(List.of(tagEvidence("ABILITY_TAG", 10L, null)));

        Map<String, Integer> c = counter();
        projector.buildEvidenceSupportedByEdges(c, CTX);

        assertEquals(1, c.get("SUPPORTED_BY"));
        assertEquals("ABILITY:10", capturedEdges().get(0).getSourceNodeKey());
    }

    @Test
    @DisplayName("证据支撑边：EMP_ABILITY 命中实体且带 tagId → 员工 + 能力两条边")
    void evidenceSupportedBy_empAbilityWithTagId() {
        when(contestQueryPort.listAllEvidence(anyInt())).thenReturn(List.of(
                tagEvidence("EMP_ABILITY", 7L, null)));
        when(talentQueryPort.getEmpAbilityById(7L)).thenReturn(
                new TalentQueryPort.EmployeeAbilityDTO(7L, 55L, 10L, 4, "M", null, null, null, "Java"));

        Map<String, Integer> c = counter();
        projector.buildEvidenceSupportedByEdges(c, CTX);

        assertEquals(2, c.get("SUPPORTED_BY"));
    }

    @Test
    @DisplayName("证据支撑边：EMP_ABILITY 命中实体但无 tagId → 员工 + EMP_ABILITY: 共两条边")
    void evidenceSupportedBy_empAbilityWithoutTagId() {
        when(contestQueryPort.listAllEvidence(anyInt())).thenReturn(List.of(
                tagEvidence("EMP_ABILITY", 7L, null)));
        when(talentQueryPort.getEmpAbilityById(7L)).thenReturn(
                new TalentQueryPort.EmployeeAbilityDTO(7L, 55L, null, 4, "M", null, null, null, "Kafka"));

        Map<String, Integer> c = counter();
        projector.buildEvidenceSupportedByEdges(c, CTX);

        assertEquals(2, c.get("SUPPORTED_BY"));
        assertTrue(capturedEdges().stream().anyMatch(e -> "EMP_ABILITY:7".equals(e.getSourceNodeKey())));
        assertTrue(capturedEdges().stream().anyMatch(e -> "EMPLOYEE:55".equals(e.getSourceNodeKey())));
    }

    @Test
    @DisplayName("证据支撑边：EMP_ABILITY 实体缺失但有 tagId → 回退 item.tagId 节点")
    void evidenceSupportedBy_empAbilityFallbackTagId() {
        when(contestQueryPort.listAllEvidence(anyInt())).thenReturn(List.of(
                tagEvidence("EMP_ABILITY", 7L, 12L)));
        when(talentQueryPort.getEmpAbilityById(7L)).thenReturn(null);

        Map<String, Integer> c = counter();
        projector.buildEvidenceSupportedByEdges(c, CTX);

        assertEquals(1, c.get("SUPPORTED_BY"));
        assertEquals("ABILITY:12", capturedEdges().get(0).getSourceNodeKey());
    }

    @Test
    @DisplayName("证据支撑边：EMP_ABILITY 实体缺失且无 tagId → 不产生边")
    void evidenceSupportedBy_empAbilityNothing() {
        when(contestQueryPort.listAllEvidence(anyInt())).thenReturn(List.of(
                tagEvidence("EMP_ABILITY", 7L, null)));
        when(talentQueryPort.getEmpAbilityById(7L)).thenReturn(null);

        Map<String, Integer> c = counter();
        projector.buildEvidenceSupportedByEdges(c, CTX);

        assertEquals(0, c.get("SUPPORTED_BY"));
    }

    @Test
    @DisplayName("证据支撑边：POST_ABILITY_MODEL 命中模型 → 岗位/技能点/能力三条边")
    void evidenceSupportedBy_postAbilityModel() {
        when(contestQueryPort.listAllEvidence(anyInt())).thenReturn(List.of(
                tagEvidence("POST_ABILITY_MODEL", 9L, null)));
        when(postQueryPort.getPostAbilityModelById(9L)).thenReturn(
                new PostQueryPort.PostAbilityDTO(9L, 100L, 5L, 3, BigDecimal.ONE, 1, 0, "v1", null, "Java"));

        Map<String, Integer> c = counter();
        projector.buildEvidenceSupportedByEdges(c, CTX);

        assertEquals(3, c.get("SUPPORTED_BY"));
    }

    @Test
    @DisplayName("证据支撑边：POST_ABILITY_MODEL 模型缺失但有 tagId → 回退能力节点")
    void evidenceSupportedBy_postAbilityModelFallback() {
        when(contestQueryPort.listAllEvidence(anyInt())).thenReturn(List.of(
                tagEvidence("POST_ABILITY_MODEL", 9L, 11L)));
        when(postQueryPort.getPostAbilityModelById(9L)).thenReturn(null);

        Map<String, Integer> c = counter();
        projector.buildEvidenceSupportedByEdges(c, CTX);

        assertEquals(1, c.get("SUPPORTED_BY"));
        assertEquals("ABILITY:11", capturedEdges().get(0).getSourceNodeKey());
    }

    @Test
    @DisplayName("证据支撑边：未知 targetType → 兜底指向 POST 节点")
    void evidenceSupportedBy_defaultTargetType() {
        when(contestQueryPort.listAllEvidence(anyInt())).thenReturn(List.of(
                tagEvidence("MATCHING_RECORD", 100L, null)));

        Map<String, Integer> c = counter();
        projector.buildEvidenceSupportedByEdges(c, CTX);

        assertEquals(1, c.get("SUPPORTED_BY"));
        assertEquals("POST:100", capturedEdges().get(0).getSourceNodeKey());
    }

    @Test
    @DisplayName("appendEvidenceSupportEdges：item.id 为 null → 直接返回；targetRefId 为 null → 无兜底")
    void appendEvidenceSupportEdges_nullGuards() {
        List<KgGraphEdge> edges = new ArrayList<>();
        projector.appendEvidenceSupportEdges(edges, new ContestQueryPort.ContestEvidenceDTO(
                null, "JD", 2L, "t", "x", "ABILITY_TAG", 10L, "Java", null,
                null, null, null, null));
        assertTrue(edges.isEmpty());

        projector.appendEvidenceSupportEdges(edges, new ContestQueryPort.ContestEvidenceDTO(
                1L, "JD", 2L, "t", "x", "MATCHING_RECORD", null, "Java", null,
                null, null, null, null));
        assertTrue(edges.isEmpty());
    }

    @Test
    @DisplayName("addSupportedByEdge：业务节点键为 null 或以 :null 结尾 → 不生成边")
    void addSupportedByEdge_nullKeys() {
        List<KgGraphEdge> edges = new ArrayList<>();
        ContestQueryPort.ContestEvidenceDTO item = tagEvidence("ABILITY_TAG", 10L, null);

        projector.addSupportedByEdge(edges, null, item);
        projector.addSupportedByEdge(edges, "POST:null", item);
        assertTrue(edges.isEmpty());

        projector.addSupportedByEdge(edges, "POST:1", item);
        assertEquals(1, edges.size());
    }

    // ==================== RAG 派生边 ====================

    @Test
    @DisplayName("RAG 派生边：空 / 异常 → DERIVED_FROM 计 0")
    void ragDerivedFrom_emptyAndFailure() {
        Map<String, Integer> empty = counter();
        projector.buildRagDocumentDerivedFromEdges(empty, CTX);
        assertEquals(0, empty.get("DERIVED_FROM"));

        when(knowledgeDocumentService.listAllActiveDocuments(anyInt()))
                .thenThrow(new RuntimeException("db down"));
        Map<String, Integer> failed = counter();
        projector.buildRagDocumentDerivedFromEdges(failed, CTX);
        assertEquals(0, failed.get("DERIVED_FROM"));
    }

    private RagKnowledgeDocument doc(Long id, String sourceType, Long sourceRefId) {
        RagKnowledgeDocument d = new RagKnowledgeDocument();
        d.setId(id);
        d.setSourceType(sourceType);
        d.setSourceRefId(sourceRefId);
        return d;
    }

    @Test
    @DisplayName("RAG 派生边：JD_IMPORT / ABILITY_TAG / POST_PROTOTYPE 各指向对应节点")
    void ragDerivedFrom_sourceTypes() {
        when(knowledgeDocumentService.listAllActiveDocuments(anyInt())).thenReturn(List.of(
                doc(1L, "JD_IMPORT", 100L),
                doc(2L, "ABILITY_TAG", 10L),
                doc(3L, "POST_PROTOTYPE", 20L)));

        Map<String, Integer> c = counter();
        projector.buildRagDocumentDerivedFromEdges(c, CTX);

        assertEquals(3, c.get("DERIVED_FROM"));
    }

    @Test
    @DisplayName("RAG 派生边：EMP_ABILITY 命中实体 → 指向员工；实体缺失 → 无边")
    void ragDerivedFrom_empAbility() {
        when(knowledgeDocumentService.listAllActiveDocuments(anyInt())).thenReturn(List.of(
                doc(1L, "EMP_ABILITY", 7L)));
        when(talentQueryPort.getEmpAbilityById(7L)).thenReturn(
                new TalentQueryPort.EmployeeAbilityDTO(7L, 55L, null, 4, "M", null, null, null, "Java"));

        Map<String, Integer> c = counter();
        projector.buildRagDocumentDerivedFromEdges(c, CTX);
        assertEquals(1, c.get("DERIVED_FROM"));

        when(talentQueryPort.getEmpAbilityById(7L)).thenReturn(null);
        Map<String, Integer> second = counter();
        projector.buildRagDocumentDerivedFromEdges(second, CTX);
        assertEquals(0, second.get("DERIVED_FROM"));
    }

    @Test
    @DisplayName("RAG 派生边：id 或 sourceRefId 缺失 → 跳过；未知 sourceType → 跳过")
    void ragDerivedFrom_guards() {
        when(knowledgeDocumentService.listAllActiveDocuments(anyInt())).thenReturn(List.of(
                doc(null, "JD_IMPORT", 100L),
                doc(2L, "JD_IMPORT", null),
                doc(3L, "UNKNOWN", 100L)));

        Map<String, Integer> c = counter();
        projector.buildRagDocumentDerivedFromEdges(c, CTX);

        assertEquals(0, c.get("DERIVED_FROM"));
    }

    @Test
    @DisplayName("addDerivedFromEdge：业务键为 null 或 :null → 不生成边")
    void addDerivedFromEdge_nullKeys() {
        List<KgGraphEdge> edges = new ArrayList<>();
        projector.addDerivedFromEdge(edges, "RAG_DOCUMENT:1", null);
        projector.addDerivedFromEdge(edges, "RAG_DOCUMENT:1", "POST:null");
        assertTrue(edges.isEmpty());

        projector.addDerivedFromEdge(edges, "RAG_DOCUMENT:1", "POST:1");
        assertEquals(1, edges.size());
    }

    // ==================== 学习资源推荐边 ====================

    @Test
    @DisplayName("学习资源推荐边：无 tagId 的资源被跳过，有 tagId 的生成 RECOMMENDS")
    void learningResourceRecommends() {
        when(learningQueryPort.listActiveResources(anyInt())).thenReturn(List.of(
                new LearningQueryPort.LearningResourceDTO(1L, "R1", "Java", 10L, "课程", "COURSE",
                        3, "u", "d", "p", 1),
                new LearningQueryPort.LearningResourceDTO(2L, "R2", "无标签", null, "课程", "COURSE",
                        3, "u", "d", "p", 1)));

        Map<String, Integer> c = counter();
        projector.buildLearningResourceRecommendsEdges(c, CTX);

        assertEquals(1, c.get("RECOMMENDS"));
    }

    // ==================== 演化事件边 ====================

    @Test
    @DisplayName("演化事件边：无 postId 的任务被跳过；空 / 异常 → EVOLVED_TO 计 0")
    void evolutionEventEdges() {
        Map<String, Integer> empty = counter();
        projector.buildEvolutionEventEdges(empty, CTX);
        assertEquals(0, empty.get("EVOLVED_TO"));

        when(evolutionQueryPort.listAllTasks(anyInt())).thenReturn(List.of(
                new EvolutionQueryPort.EvolutionTaskDTO(1L, "T1", "任务", 100L, "DONE", "d"),
                new EvolutionQueryPort.EvolutionTaskDTO(2L, "T2", "无岗位", null, "DONE", "d")));

        Map<String, Integer> c = counter();
        projector.buildEvolutionEventEdges(c, CTX);
        assertEquals(1, c.get("EVOLVED_TO"));

        when(evolutionQueryPort.listAllTasks(anyInt())).thenThrow(new RuntimeException("db down"));
        Map<String, Integer> failed = counter();
        projector.buildEvolutionEventEdges(failed, CTX);
        assertEquals(0, failed.get("EVOLVED_TO"));
    }

    // ==================== 匹配边 ====================

    @Test
    @DisplayName("匹配边：正常记录生成 MATCHED_WITH；空 / 异常 → 计 0")
    void matchedWithEdges() {
        Map<String, Integer> empty = counter();
        projector.buildMatchedWithEdges(empty, CTX);
        assertEquals(0, empty.get("MATCHED_WITH"));

        when(matchingQueryPort.listRecordsPaginated(eq(1), anyInt())).thenReturn(List.of(
                new MatchingQueryPort.MatchingRecordDTO(1L, 55L, 100L, "Java", BigDecimal.valueOf(80),
                        BigDecimal.valueOf(78), null, null, null, null, "v1", 2, 3, null, null)));

        Map<String, Integer> c = counter();
        projector.buildMatchedWithEdges(c, CTX);
        assertEquals(1, c.get("MATCHED_WITH"));

        when(matchingQueryPort.listRecordsPaginated(eq(1), anyInt())).thenThrow(new RuntimeException("db down"));
        Map<String, Integer> failed = counter();
        projector.buildMatchedWithEdges(failed, CTX);
        assertEquals(0, failed.get("MATCHED_WITH"));
    }

    @Test
    @DisplayName("匹配边：满页时翻页累加")
    void matchedWithEdges_paginates() {
        List<MatchingQueryPort.MatchingRecordDTO> full = new ArrayList<>();
        for (int i = 0; i < 500; i++) {
            full.add(new MatchingQueryPort.MatchingRecordDTO((long) i, 1L, 2L, "p", BigDecimal.ONE, BigDecimal.ONE,
                    null, null, null, null, "v1", 1, 1, null, null));
        }
        when(matchingQueryPort.listRecordsPaginated(eq(1), anyInt())).thenReturn(full);
        when(matchingQueryPort.listRecordsPaginated(eq(2), anyInt())).thenReturn(Collections.emptyList());

        Map<String, Integer> c = counter();
        projector.buildMatchedWithEdges(c, CTX);

        assertEquals(500, c.get("MATCHED_WITH"));
    }

    // ==================== EVALUATED_BY ====================

    @Test
    @DisplayName("EVALUATED_BY：固定写 0")
    void evaluatedBy() {
        Map<String, Integer> c = counter();
        projector.buildEvaluatedByEdges(c);
        assertEquals(0, c.get("EVALUATED_BY"));
    }

    // ==================== metadata 序列化失败降级 ====================

    @Test
    @DisplayName("边元数据序列化失败 → 回退 {}，不影响边生成")
    void toJsonSerializationFailureDegrades() {
        GraphBusinessEdgeProjector failing = new GraphBusinessEdgeProjector(
                postQueryPort, talentQueryPort, learningQueryPort, matchingQueryPort,
                knowledgeDocumentService, contestQueryPort, evolutionQueryPort, snapshotWriter,
                new ObjectMapper() {
                    @Override
                    public String writeValueAsString(Object value) {
                        throw new IllegalStateException("boom");
                    }
                });
        when(postQueryPort.listActivePostAbilityModels(anyInt())).thenReturn(List.of(
                new PostQueryPort.PostAbilityDTO(1L, 100L, 5L, 3, BigDecimal.ONE, 1, 0, "v1", null, "Java", "JVM", null)));

        Map<String, Integer> c = counter();
        failing.buildPostRequiresAbilityEdges(c, CTX);

        assertEquals(1, c.get("REQUIRES"));
        assertTrue(capturedEdges().stream().allMatch(e -> "{}".equals(e.getMetadataJson())));
    }
}
