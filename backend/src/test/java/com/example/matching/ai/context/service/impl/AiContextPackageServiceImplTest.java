package com.example.matching.ai.context.service.impl;

import com.example.matching.ai.context.dto.AiContextAbilityDTO;
import com.example.matching.ai.context.dto.AiContextPackageDTO;
import com.example.matching.ai.context.dto.AiContextSourceRefDTO;
import com.example.matching.ai.context.service.AiContextCompressorService;
import com.example.matching.ai.context.service.AiContextSnapshotService;
import com.example.matching.ai.context.service.AiContextSourceRefService;
import com.example.matching.common.exception.BusinessException;
import com.example.matching.dto.matching.MatchingAbilitySnapshot;
import com.example.matching.entity.contest.ContestEvidenceItem;
import com.example.matching.entity.employee.EmpEmployee;
import com.example.matching.entity.matching.MatchingRecord;
import com.example.matching.entity.post.PostAbilityModel;
import com.example.matching.entity.post.PostPost;
import com.example.matching.entity.system.AbilityTag;
import com.example.matching.mapper.contest.ContestEvidenceItemMapper;
import com.example.matching.mapper.employee.EmpEmployeeMapper;
import com.example.matching.mapper.matching.MatchingRecordMapper;
import com.example.matching.mapper.post.PostAbilityModelMapper;
import com.example.matching.mapper.post.PostPostMapper;
import com.example.matching.mapper.system.AbilityTagMapper;
import com.example.matching.port.employee.EmployeeAbilityReadPort;
import com.example.matching.service.kg.KnowledgeGraphQueryService;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.*;

/**
 * {@link AiContextPackageServiceImpl} 单元测试。
 *
 * <p>覆盖四条构建链路（匹配 / 员工 / 岗位 / 学习路径）、能力与岗位要求的名称回退、
 * 能力差距与优先级计算、风险信号触发条件、图谱摘要异常降级、证据截断与上限。
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class AiContextPackageServiceImplTest {

    @Mock private MatchingRecordMapper matchingRecordMapper;
    @Mock private EmpEmployeeMapper empEmployeeMapper;
    @Mock private EmployeeAbilityReadPort employeeAbilityReadPort;
    @Mock private PostPostMapper postPostMapper;
    @Mock private PostAbilityModelMapper postAbilityModelMapper;
    @Mock private AbilityTagMapper abilityTagMapper;
    @Mock private ContestEvidenceItemMapper evidenceItemMapper;
    @Mock private AiContextSourceRefService sourceRefService;
    @Mock private AiContextCompressorService compressorService;
    @Mock private AiContextSnapshotService snapshotService;
    @Mock private KnowledgeGraphQueryService knowledgeGraphQueryService;

    private AiContextPackageServiceImpl service;

    /**
     * MyBatis-Plus 的 LambdaQueryWrapper 需要实体已登记表信息，
     * 否则会抛 "can not find lambda cache for this entity"（纯单测无 Spring 上下文）。
     */
    @BeforeAll
    static void initMybatisPlusLambdaCache() {
        var cfg = new com.baomidou.mybatisplus.core.MybatisConfiguration();
        var assistant = new org.apache.ibatis.builder.MapperBuilderAssistant(cfg, "");
        com.baomidou.mybatisplus.core.metadata.TableInfoHelper.initTableInfo(assistant, MatchingRecord.class);
        com.baomidou.mybatisplus.core.metadata.TableInfoHelper.initTableInfo(assistant, EmpEmployee.class);
        com.baomidou.mybatisplus.core.metadata.TableInfoHelper.initTableInfo(assistant, PostPost.class);
        com.baomidou.mybatisplus.core.metadata.TableInfoHelper.initTableInfo(assistant, PostAbilityModel.class);
        com.baomidou.mybatisplus.core.metadata.TableInfoHelper.initTableInfo(assistant, AbilityTag.class);
        com.baomidou.mybatisplus.core.metadata.TableInfoHelper.initTableInfo(assistant, ContestEvidenceItem.class);
    }

    @BeforeEach
    void setUp() {
        service = new AiContextPackageServiceImpl(
                matchingRecordMapper, empEmployeeMapper, employeeAbilityReadPort, postPostMapper,
                postAbilityModelMapper, abilityTagMapper, evidenceItemMapper,
                sourceRefService, compressorService, snapshotService, knowledgeGraphQueryService);

        when(sourceRefService.fromPostAbilityModel(any(PostAbilityModel.class), any()))
                .thenAnswer(inv -> sourceRef("fact:POST_ABILITY_MODEL:" + System.nanoTime()));
        when(sourceRefService.fromEvidence(any(ContestEvidenceItem.class)))
                .thenAnswer(inv -> sourceRef("evidence:CONTEST_EVIDENCE:" + System.nanoTime()));
        when(sourceRefService.fromMatchingRecord(any(MatchingRecord.class)))
                .thenAnswer(inv -> sourceRef("matching:MATCHING_RECORD:10"));
        when(compressorService.compress(any(AiContextPackageDTO.class))).thenAnswer(inv -> inv.getArgument(0));
        when(knowledgeGraphQueryService.getAbilityGapPath(anyLong(), anyLong())).thenReturn(null);
    }

    private static AiContextSourceRefDTO sourceRef(String ref) {
        AiContextSourceRefDTO dto = new AiContextSourceRefDTO();
        dto.setRef(ref);
        return dto;
    }

    private MatchingAbilitySnapshot snapshot(Long abilityId, Long tagId, String name, Integer level,
                                             String sourceType, BigDecimal confidence) {
        return new MatchingAbilitySnapshot(abilityId, tagId, name, level, confidence, sourceType,
                BigDecimal.ONE, LocalDate.now());
    }

    /** 只有 abilityId、没有 tagId 的能力：会触发证据查询，但不会触发公共证据补充。 */
    private MatchingAbilitySnapshot ability(Long abilityId, String name, Integer level) {
        return snapshot(abilityId, null, name, level, "MANUAL", null);
    }

    /**
     * 构造「按能力ID查证据」的返回值。真实 MyBatis 返回的是可变 ArrayList，
     * 而服务内会对该返回值 addAll 补充公共证据，所以这里必须也是可变的。
     */
    private List<ContestEvidenceItem> evidenceList(ContestEvidenceItem... items) {
        return new ArrayList<>(List.of(items));
    }

    private ContestEvidenceItem evidence(long id, String sourceType) {
        ContestEvidenceItem item = new ContestEvidenceItem();
        item.setId(id);
        item.setSourceType(sourceType);
        return item;
    }

    private PostAbilityModel requirement(Long id, Long postId, Long tagId, String name, Integer minLevel,
                                         Integer isRequired, Integer isCore) {
        PostAbilityModel m = new PostAbilityModel();
        m.setId(id);
        m.setPostId(postId);
        m.setTagId(tagId);
        m.setAbilityName(name);
        m.setMinRequiredLevel(minLevel);
        m.setWeight(new BigDecimal("1.00"));
        m.setIsRequired(isRequired);
        m.setIsCore(isCore);
        return m;
    }

    private AbilityTag tag(Long id, String name) {
        AbilityTag t = new AbilityTag();
        t.setId(id);
        t.setTagName(name);
        return t;
    }

    private MatchingRecord record(Long empId, Long postId) {
        MatchingRecord r = new MatchingRecord();
        r.setId(10L);
        r.setEmpId(empId);
        r.setPostId(postId);
        r.setFinalMatchScore(new BigDecimal("80.00"));
        return r;
    }

    // ==================== buildForMatching ====================

    @Test
    @DisplayName("buildForMatching：全链路正常 → 组装能力/要求/差距/证据/风险/图谱摘要")
    void buildForMatching_happyPath() {
        MatchingRecord record = record(100L, 200L);
        record.setPostModelScore(new BigDecimal("70.00"));
        record.setVectorScore(new BigDecimal("65.00"));
        record.setLlmScore(new BigDecimal("60.00"));
        record.setModelQualityCoefficient(new BigDecimal("50.00"));
        record.setFeedbackCalibration(new BigDecimal("9.00"));
        when(matchingRecordMapper.selectById(10L)).thenReturn(record);

        EmpEmployee emp = new EmpEmployee();
        emp.setId(100L);
        emp.setRealName("张三");
        emp.setEmpCode("E100");
        emp.setLevel("L4");
        when(empEmployeeMapper.selectById(100L)).thenReturn(emp);

        PostPost post = new PostPost();
        post.setId(200L);
        post.setPostName("Java工程师");
        post.setPostCode("P200");
        post.setPostLevel("P6");
        when(postPostMapper.selectById(200L)).thenReturn(post);

        // 能力：tagId=5 有正式能力记录；tagId=null 走 abilityId；tagId=7 无名称 → 跳过
        List<MatchingAbilitySnapshot> snapshots = new ArrayList<>();
        snapshots.add(snapshot(1L, 5L, null, 2, "EMP_ABILITY", new BigDecimal("0.8")));
        snapshots.add(snapshot(2L, null, "无标签能力", 3, "PROFILE_FUSED", null));
        snapshots.add(snapshot(3L, 7L, null, 4, null, null));
        snapshots.add(snapshot(null, null, null, 4, "EMP_ABILITY", null));
        when(employeeAbilityReadPort.loadAuthoritativeAbilities(List.of(100L)))
                .thenReturn(Map.of(100L, snapshots));

        // 岗位要求：一条有 tagId，一条只有 abilityName，一条完全空白名称
        PostAbilityModel req1 = requirement(11L, 200L, 5L, null, 4, 1, 1);
        PostAbilityModel req2 = requirement(12L, 200L, 9L, "无标签能力", 3, 0, 0);
        PostAbilityModel req3 = requirement(13L, 200L, 8L, "  ", 3, 0, 0);
        when(postAbilityModelMapper.selectList(any())).thenReturn(List.of(req1, req2, req3));

        when(abilityTagMapper.selectList(any())).thenReturn(List.of(tag(5L, "Java")));

        ContestEvidenceItem evidence = new ContestEvidenceItem();
        evidence.setId(77L);
        evidence.setEvidenceCode("EV-77");
        evidence.setSourceType("AI_GENERATED");
        evidence.setSourceText("x".repeat(300));
        evidence.setTagId(5L);
        evidence.setCredibilityScore(new BigDecimal("90.00"));
        // 第一次按能力ID查证据；第二次补充公共证据时返回空
        when(evidenceItemMapper.selectList(any()))
                .thenReturn(evidenceList(evidence), evidenceList());

        Map<String, Object> gapPath = new LinkedHashMap<>();
        gapPath.put("nodes", List.of(
                Map.of("nodeType", "ABILITY", "label", "Java"),
                Map.of("nodeType", "EVIDENCE", "label", "证据")));
        gapPath.put("edges", List.of(Map.of("sourceNodeKey", "EMPLOYEE:100", "targetNodeKey", "ABILITY:5")));
        when(knowledgeGraphQueryService.getAbilityGapPath(100L, 200L)).thenReturn(gapPath);

        AiContextPackageDTO out = service.buildForMatching(10L);

        assertNotNull(out);
        assertEquals("MATCHING_ANALYSIS", out.getScenario());
        assertEquals(new BigDecimal("80.00"), out.getMatchScore());
        assertEquals("张三", out.getEmpName());
        assertEquals("Java工程师", out.getPostName());
        // 能力：tag5（tagName 兜底）+ 无标签能力（abilityName），无名能力被跳过
        assertEquals(2, out.getEmployeeAbilities().size());
        assertEquals("Java", out.getEmployeeAbilities().get(0).getAbilityName());
        assertEquals(0, out.getEmployeeAbilities().get(0).getCredibility().compareTo(new BigDecimal("80.00")));
        // 岗位要求：req3 名称为空白被跳过
        assertEquals(2, out.getPostRequirements().size());
        assertTrue(out.getPostRequirements().get(0).getRequired());
        assertTrue(out.getPostRequirements().get(0).getCore());
        // 差距：req1 Java 2<4 且 core → HIGH；req2 等级 3>=3 无差距
        assertEquals(1, out.getGaps().size());
        assertEquals("HIGH", out.getGaps().get(0).getPriority());
        assertEquals(2, out.getGaps().get(0).getGap());
        assertEquals("LEVEL_GAP", out.getGaps().get(0).getGapType());
        // 证据被截断为 200 + "..."
        assertEquals(1, out.getEvidences().size());
        assertEquals(203, out.getEvidences().get(0).getSourceSnippet().length());
        // 评分明细 3 项
        assertEquals(3, out.getScoreBreakdown().size());
        // 风险信号：核心能力缺口 + AI 自证据 + 模型质量低 + 反馈偏差
        assertTrue(out.getRiskSignals().stream().anyMatch(r -> "CORE_ABILITY_GAP".equals(r.getRiskType())));
        assertTrue(out.getRiskSignals().stream().anyMatch(r -> "SELF_EVIDENCE".equals(r.getRiskType())));
        assertTrue(out.getRiskSignals().stream().anyMatch(r -> "POST_MODEL_LOW_QUALITY".equals(r.getRiskType())));
        assertTrue(out.getRiskSignals().stream().anyMatch(r -> "FEEDBACK_BIAS".equals(r.getRiskType())));
        // 图谱摘要
        assertEquals(2, out.getGraphSummary().getNodeCount());
        assertEquals(1, out.getGraphSummary().getEdgeCount());
        assertEquals(1, out.getGraphSummary().getAbilityCount());
        assertEquals(1, out.getGraphSummary().getEvidenceCount());
        assertEquals(List.of("Java"), out.getGraphSummary().getKeyAbilityNodes());
        assertNotNull(out.getSourceRefs());
        assertTrue(out.getSourceRefs().size() >= 3);
        verify(snapshotService).saveSnapshot(any(AiContextPackageDTO.class));
    }

    @Test
    @DisplayName("buildForMatching：匹配记录不存在 → 抛 BusinessException")
    void buildForMatching_recordNotFound() {
        when(matchingRecordMapper.selectById(999L)).thenReturn(null);
        assertThrows(BusinessException.class, () -> service.buildForMatching(999L));
    }

    @Test
    @DisplayName("buildForMatching：仅简历解析来源且无证据 → 弱证据风险 + MISSING 差距")
    void buildForMatching_weakEvidenceAndMissingGap() {
        MatchingRecord record = record(100L, 200L);
        record.setFinalMatchScore(null);
        record.setAiMatchScore(new BigDecimal("55.00"));
        when(matchingRecordMapper.selectById(10L)).thenReturn(record);
        when(empEmployeeMapper.selectById(100L)).thenReturn(null);
        when(postPostMapper.selectById(200L)).thenReturn(null);

        when(employeeAbilityReadPort.loadAuthoritativeAbilities(anyList()))
                .thenReturn(Map.of(100L, List.of(snapshot(1L, null, "Python", 0, "RESUME_PARSE", null))));
        when(postAbilityModelMapper.selectList(any()))
                .thenReturn(List.of(requirement(21L, 200L, null, "Python", 4, 0, 0)));
        when(abilityTagMapper.selectList(any())).thenReturn(Collections.emptyList());
        // 第一次按能力ID查证据；第二次补充公共证据 → 都为空的可变列表
        when(evidenceItemMapper.selectList(any())).thenReturn(evidenceList(), evidenceList());

        AiContextPackageDTO out = service.buildForMatching(10L);

        assertEquals(new BigDecimal("55.00"), out.getMatchScore());
        assertNull(out.getEmpName());
        assertNull(out.getPostName());
        assertEquals(1, out.getGaps().size());
        assertEquals("MISSING", out.getGaps().get(0).getGapType());
        // gap = 4 - 0 = 4 → calculatePriority 中 gap>=2 一律 HIGH
        assertEquals("HIGH", out.getGaps().get(0).getPriority());
        assertTrue(out.getRiskSignals().stream().anyMatch(r -> "WEAK_EVIDENCE".equals(r.getRiskType())));
        // 无评分字段 → 评分明细为空
        assertTrue(out.getScoreBreakdown().isEmpty());
        // gapPath 为 null → 空摘要（未填充）
        assertNotNull(out.getGraphSummary());
        assertNull(out.getGraphSummary().getNodeCount());
    }

    @Test
    @DisplayName("buildForMatching：图数据库查询抛异常 → 图谱摘要降级但不影响主流程")
    void buildForMatching_graphQueryDegrades() {
        when(matchingRecordMapper.selectById(10L)).thenReturn(record(100L, 200L));
        when(empEmployeeMapper.selectById(100L)).thenReturn(null);
        when(postPostMapper.selectById(200L)).thenReturn(null);
        when(employeeAbilityReadPort.loadAuthoritativeAbilities(anyList())).thenReturn(Map.of());
        when(postAbilityModelMapper.selectList(any())).thenReturn(Collections.emptyList());
        when(knowledgeGraphQueryService.getAbilityGapPath(anyLong(), anyLong()))
                .thenThrow(new RuntimeException("neo4j down"));

        AiContextPackageDTO out = service.buildForMatching(10L);

        assertNotNull(out);
        assertNull(out.getGraphSummary().getKeyAbilityNodes());
        assertNull(out.getGraphSummary().getKeyPaths());
        // 查询抛异常 → 摘要保持未填充状态（Integer 字段为 null，而非 0）
        assertNull(out.getGraphSummary().getNodeCount());
        verify(snapshotService).saveSnapshot(any(AiContextPackageDTO.class));
    }

    @Test
    @DisplayName("buildForMatching：证据已达 30 条 → 不再补充公共证据")
    void buildForMatching_evidenceCapSkipsPublicQuery() {
        when(matchingRecordMapper.selectById(10L)).thenReturn(record(100L, 200L));
        when(empEmployeeMapper.selectById(100L)).thenReturn(null);
        when(postPostMapper.selectById(200L)).thenReturn(null);
        when(employeeAbilityReadPort.loadAuthoritativeAbilities(anyList()))
                .thenReturn(Map.of(100L, List.of(snapshot(1L, 5L, "Java", 3, "MANUAL", null))));
        when(postAbilityModelMapper.selectList(any())).thenReturn(Collections.emptyList());
        when(abilityTagMapper.selectList(any())).thenReturn(List.of(tag(5L, "Java")));

        List<ContestEvidenceItem> many = new ArrayList<>();
        for (long i = 0; i < 30; i++) {
            ContestEvidenceItem item = new ContestEvidenceItem();
            item.setId(i);
            item.setSourceType("MANUAL");
            many.add(item);
        }
        when(evidenceItemMapper.selectList(any())).thenReturn(many);

        AiContextPackageDTO out = service.buildForMatching(10L);

        assertEquals(30, out.getEvidences().size());
        verify(evidenceItemMapper, times(1)).selectList(any());
    }

    @Test
    @DisplayName("buildForMatching：同名能力按名称回填差距 → 有事实引用且 gap<2 优先级 MEDIUM")
    void buildForMatching_gapMatchedByAbilityName() {
        when(matchingRecordMapper.selectById(10L)).thenReturn(record(100L, 200L));
        when(empEmployeeMapper.selectById(100L)).thenReturn(null);
        when(postPostMapper.selectById(200L)).thenReturn(null);
        when(employeeAbilityReadPort.loadAuthoritativeAbilities(anyList()))
                .thenReturn(Map.of(100L, List.of(snapshot(55L, null, " 持续 集成 ", 3, "EMP_ABILITY", null))));
        when(postAbilityModelMapper.selectList(any()))
                .thenReturn(List.of(requirement(31L, 200L, null, "持续集成", 4, 0, 0)));
        when(abilityTagMapper.selectList(any())).thenReturn(Collections.emptyList());
        when(evidenceItemMapper.selectList(any())).thenReturn(Collections.emptyList());

        AiContextPackageDTO out = service.buildForMatching(10L);

        assertEquals(1, out.getGaps().size());
        assertEquals(3, out.getGaps().get(0).getCurrentLevel());
        assertEquals("MEDIUM", out.getGaps().get(0).getPriority());
        assertTrue(out.getGaps().get(0).getSourceRefs().contains("fact:EMP_ABILITY:55"));
        assertTrue(out.getGaps().get(0).getSourceRefs().contains("fact:POST_ABILITY_MODEL:31"));
    }

    @Test
    @DisplayName("buildForMatching：核心能力缺口 gap<2 → 无核心缺口风险，优先级 MEDIUM")
    void buildForMatching_coreGapSmallUsesMediumPriority() {
        MatchingRecord record = record(100L, 200L);
        record.setAiScore(new BigDecimal("50.00"));
        when(matchingRecordMapper.selectById(10L)).thenReturn(record);
        when(empEmployeeMapper.selectById(100L)).thenReturn(null);
        when(postPostMapper.selectById(200L)).thenReturn(null);
        when(employeeAbilityReadPort.loadAuthoritativeAbilities(anyList()))
                .thenReturn(Map.of(100L, List.of(snapshot(1L, 5L, "Java", 3, "MANUAL", null))));
        when(postAbilityModelMapper.selectList(any()))
                .thenReturn(List.of(requirement(61L, 200L, 5L, null, 4, 1, 1)));
        when(abilityTagMapper.selectList(any())).thenReturn(List.of(tag(5L, "Java")));
        when(evidenceItemMapper.selectList(any())).thenReturn(Collections.emptyList());

        AiContextPackageDTO out = service.buildForMatching(10L);

        assertEquals(1, out.getGaps().size());
        assertEquals("MEDIUM", out.getGaps().get(0).getPriority());
        assertTrue(out.getRiskSignals().stream().noneMatch(r -> "CORE_ABILITY_GAP".equals(r.getRiskType())));
    }

    @Test
    @DisplayName("buildForMatching：无标签能力（abilityId=null）→ 画像来源引用且置信度为 null")
    void buildForMatching_profileSourceRefWhenNoAbilityId() {
        when(matchingRecordMapper.selectById(10L)).thenReturn(record(100L, 200L));
        when(empEmployeeMapper.selectById(100L)).thenReturn(null);
        when(postPostMapper.selectById(200L)).thenReturn(null);
        when(employeeAbilityReadPort.loadAuthoritativeAbilities(anyList()))
                .thenReturn(Map.of(100L, List.of(snapshot(null, 5L, "Java", 4, null, null))));
        when(postAbilityModelMapper.selectList(any())).thenReturn(Collections.emptyList());
        when(abilityTagMapper.selectList(any())).thenReturn(List.of(tag(5L, "Java")));

        AiContextPackageDTO out = service.buildForMatching(10L);

        AiContextAbilityDTO ability = out.getEmployeeAbilities().get(0);
        // 画像来源没有 emp_ability 记录 ID，快照 sourceType 原样透传（此处为 null）
        assertNull(ability.getSource());
        assertNull(ability.getCredibility());
        assertTrue(out.getSourceRefs().get(0).getRef().startsWith("fact:PERSON_ABILITY_PROFILE:100:5"));
    }

    @Test
    @DisplayName("buildForMatching：图谱节点 label 为空 → 不进入关键能力节点，边摘要拼接 null")
    void buildForMatching_graphNodeNullLabel() {
        when(matchingRecordMapper.selectById(10L)).thenReturn(record(100L, 200L));
        when(empEmployeeMapper.selectById(100L)).thenReturn(null);
        when(postPostMapper.selectById(200L)).thenReturn(null);
        when(employeeAbilityReadPort.loadAuthoritativeAbilities(anyList())).thenReturn(Map.of());
        when(postAbilityModelMapper.selectList(any())).thenReturn(Collections.emptyList());

        Map<String, Object> gapPath = new HashMap<>();
        gapPath.put("nodes", List.of(Map.of("nodeType", "ABILITY", "label", "")));
        gapPath.put("edges", List.of(new HashMap<String, Object>()));
        when(knowledgeGraphQueryService.getAbilityGapPath(anyLong(), anyLong())).thenReturn(gapPath);

        AiContextPackageDTO out = service.buildForMatching(10L);

        // 空字符串 label 非 null，会以空串进入关键能力节点；仅 null label 才被过滤
        assertNotNull(out.getGraphSummary().getKeyAbilityNodes());
        assertEquals(List.of(""), out.getGraphSummary().getKeyAbilityNodes());
        assertEquals(List.of("null -> null"), out.getGraphSummary().getKeyPaths());
    }

    @Test
    @DisplayName("buildForMatching：AI 生成证据未过半 → 不产生自证据风险")
    void buildForMatching_selfEvidenceNotTriggered() {
        when(matchingRecordMapper.selectById(10L)).thenReturn(record(100L, 200L));
        when(empEmployeeMapper.selectById(100L)).thenReturn(null);
        when(postPostMapper.selectById(200L)).thenReturn(null);
        when(employeeAbilityReadPort.loadAuthoritativeAbilities(anyList()))
                .thenReturn(Map.of(100L, List.of(ability(1L, "Java", 3))));
        when(postAbilityModelMapper.selectList(any())).thenReturn(Collections.emptyList());

        when(evidenceItemMapper.selectList(any()))
                .thenReturn(evidenceList(evidence(1L, "MANUAL"), evidence(2L, "AI_GENERATED")));

        AiContextPackageDTO out = service.buildForMatching(10L);

        assertEquals(2, out.getEvidences().size());
        assertTrue(out.getRiskSignals().stream().noneMatch(r -> "SELF_EVIDENCE".equals(r.getRiskType())));
    }

    @Test
    @DisplayName("buildForMatching：证据 sourceText 为 null → 摘要为 null 不抛异常")
    void buildForMatching_nullEvidenceText() {
        when(matchingRecordMapper.selectById(10L)).thenReturn(record(100L, 200L));
        when(empEmployeeMapper.selectById(100L)).thenReturn(null);
        when(postPostMapper.selectById(200L)).thenReturn(null);
        when(employeeAbilityReadPort.loadAuthoritativeAbilities(anyList()))
                .thenReturn(Map.of(100L, List.of(ability(5L, "Java", 3))));
        when(postAbilityModelMapper.selectList(any())).thenReturn(Collections.emptyList());

        when(evidenceItemMapper.selectList(any())).thenReturn(evidenceList(evidence(5L, "MANUAL")));

        AiContextPackageDTO out = service.buildForMatching(10L);

        assertEquals(1, out.getEvidences().size());
        assertNull(out.getEvidences().get(0).getSourceSnippet());
    }

    // ==================== buildForEmployee ====================

    @Test
    @DisplayName("buildForEmployee：正常路径 → 员工资料场景 + 画像来源引用")
    void buildForEmployee_happyPath() {
        EmpEmployee emp = new EmpEmployee();
        emp.setId(100L);
        emp.setRealName("李四");
        emp.setEmpCode("E100");
        emp.setLevel("L5");
        when(empEmployeeMapper.selectById(100L)).thenReturn(emp);
        when(employeeAbilityReadPort.loadAuthoritativeAbilities(List.of(100L)))
                .thenReturn(Map.of(100L, List.of(snapshot(null, 5L, "Java", 4, "PROFILE_FUSED", null))));
        when(abilityTagMapper.selectList(any())).thenReturn(List.of(tag(5L, "Java")));

        AiContextPackageDTO out = service.buildForEmployee(100L);

        assertEquals("EMPLOYEE_PROFILE", out.getScenario());
        assertEquals("李四", out.getEmpName());
        assertEquals(1, out.getEmployeeAbilities().size());
        assertEquals("Java", out.getEmployeeAbilities().get(0).getAbilityName());
        assertTrue(out.getSourceRefs().get(0).getRef().startsWith("fact:PERSON_ABILITY_PROFILE:100:5"));
        verify(snapshotService).saveSnapshot(any(AiContextPackageDTO.class));
    }

    @Test
    @DisplayName("buildForEmployee：员工不存在 → 抛 BusinessException")
    void buildForEmployee_notFound() {
        when(empEmployeeMapper.selectById(404L)).thenReturn(null);
        assertThrows(BusinessException.class, () -> service.buildForEmployee(404L));
    }

    @Test
    @DisplayName("buildForEmployee：无能力标签 → 不查标签表且能力为空")
    void buildForEmployee_noTags() {
        EmpEmployee emp = new EmpEmployee();
        emp.setId(100L);
        when(empEmployeeMapper.selectById(100L)).thenReturn(emp);
        when(employeeAbilityReadPort.loadAuthoritativeAbilities(anyList())).thenReturn(Map.of());

        AiContextPackageDTO out = service.buildForEmployee(100L);

        assertTrue(out.getEmployeeAbilities().isEmpty());
        assertTrue(out.getSourceRefs().isEmpty());
        verify(abilityTagMapper, never()).selectList(any());
    }

    @Test
    @DisplayName("buildForEmployee：能力无名称且无标签 → 跳过，不产生能力项")
    void buildForEmployee_skipsUnnamedAbility() {
        EmpEmployee emp = new EmpEmployee();
        emp.setId(100L);
        when(empEmployeeMapper.selectById(100L)).thenReturn(emp);
        when(employeeAbilityReadPort.loadAuthoritativeAbilities(anyList()))
                .thenReturn(Map.of(100L, List.of(snapshot(1L, 5L, "  ", 2, "MANUAL", null))));
        when(abilityTagMapper.selectList(any())).thenReturn(Collections.emptyList());

        AiContextPackageDTO out = service.buildForEmployee(100L);

        assertTrue(out.getEmployeeAbilities().isEmpty());
    }

    @Test
    @DisplayName("buildForEmployee：能力有 abilityId → 使用 empAbilityFactRef 事实引用")
    void buildForEmployee_usesEmpAbilityFactRef() {
        EmpEmployee emp = new EmpEmployee();
        emp.setId(100L);
        when(empEmployeeMapper.selectById(100L)).thenReturn(emp);
        when(employeeAbilityReadPort.loadAuthoritativeAbilities(anyList()))
                .thenReturn(Map.of(100L, List.of(snapshot(88L, 5L, "Java", 4, "MANUAL",
                        new BigDecimal("0.8")))));
        when(abilityTagMapper.selectList(any())).thenReturn(List.of(tag(5L, "Java")));

        AiContextPackageDTO out = service.buildForEmployee(100L);

        AiContextSourceRefDTO ref = out.getSourceRefs().get(0);
        assertEquals("fact:EMP_ABILITY:88", ref.getRef());
        assertEquals("fact", ref.getRefType());
        assertEquals("88", ref.getRefId());
        assertEquals(0, ref.getConfidenceScore().compareTo(new BigDecimal("80.00")));
        assertEquals("Java 等级 4", ref.getSnippet());
        assertEquals("MANUAL", ref.getSourceType());
    }

    // ==================== buildForPost ====================

    @Test
    @DisplayName("buildForPost：正常路径 → 岗位资料场景 + 要求列表来源引用")
    void buildForPost_happyPath() {
        PostPost post = new PostPost();
        post.setId(200L);
        post.setPostName("数据工程师");
        post.setPostCode("P200");
        post.setPostLevel("P6");
        when(postPostMapper.selectById(200L)).thenReturn(post);
        when(postAbilityModelMapper.selectList(any())).thenReturn(List.of(
                requirement(41L, 200L, 5L, null, 4, 1, 1),
                requirement(42L, 200L, null, "无名", 3, 0, 0)));
        when(abilityTagMapper.selectList(any())).thenReturn(List.of(tag(5L, "SQL")));

        AiContextPackageDTO out = service.buildForPost(200L);

        assertEquals("POST_PROFILE", out.getScenario());
        assertEquals("数据工程师", out.getPostName());
        assertEquals(2, out.getPostRequirements().size());
        assertEquals("SQL", out.getPostRequirements().get(0).getAbilityName());
        assertTrue(out.getPostRequirements().get(0).getCore());
        assertNotNull(out.getPostRequirements().get(0).getSourceRefs());
        assertEquals(2, out.getSourceRefs().size());
        verify(snapshotService).saveSnapshot(any(AiContextPackageDTO.class));
    }

    @Test
    @DisplayName("buildForPost：岗位不存在 → 抛 BusinessException")
    void buildForPost_notFound() {
        when(postPostMapper.selectById(404L)).thenReturn(null);
        assertThrows(BusinessException.class, () -> service.buildForPost(404L));
    }

    @Test
    @DisplayName("buildForPost：无岗位要求 → 空要求列表且不查标签表")
    void buildForPost_noRequirements() {
        PostPost post = new PostPost();
        post.setId(200L);
        when(postPostMapper.selectById(200L)).thenReturn(post);
        when(postAbilityModelMapper.selectList(any())).thenReturn(Collections.emptyList());

        AiContextPackageDTO out = service.buildForPost(200L);

        assertTrue(out.getPostRequirements().isEmpty());
        assertTrue(out.getSourceRefs().isEmpty());
        verify(abilityTagMapper, never()).selectList(any());
    }

    @Test
    @DisplayName("buildForPost：isRequired/isCore 为 null → 标记为 false 而不抛异常")
    void buildForPost_nullFlagsBecomeFalse() {
        PostPost post = new PostPost();
        post.setId(200L);
        when(postPostMapper.selectById(200L)).thenReturn(post);
        when(postAbilityModelMapper.selectList(any()))
                .thenReturn(List.of(requirement(51L, 200L, null, "Go", 3, null, null)));

        AiContextPackageDTO out = service.buildForPost(200L);

        assertFalse(out.getPostRequirements().get(0).getRequired());
        assertFalse(out.getPostRequirements().get(0).getCore());
    }

    // ==================== buildForLearningPath ====================

    @Test
    @DisplayName("buildForLearningPath：复用匹配上下文并改写场景为 LEARNING_PATH")
    void buildForLearningPath_reusesMatchingContext() {
        when(matchingRecordMapper.selectById(10L)).thenReturn(record(100L, 200L));
        when(empEmployeeMapper.selectById(100L)).thenReturn(null);
        when(postPostMapper.selectById(200L)).thenReturn(null);
        when(employeeAbilityReadPort.loadAuthoritativeAbilities(anyList())).thenReturn(Map.of());
        when(postAbilityModelMapper.selectList(any())).thenReturn(Collections.emptyList());

        AiContextPackageDTO out = service.buildForLearningPath(10L);

        assertEquals("LEARNING_PATH", out.getScenario());
        assertEquals(10L, out.getMatchingRecordId());
    }

    // ==================== 静态分组工具 ====================

    @Test
    void keepsEachUntaggedFormalAbilityInAiContext() {
        List<MatchingAbilitySnapshot> snapshots = List.of(
                new MatchingAbilitySnapshot(1L, null, "接口自动化测试", 3,
                        BigDecimal.ONE, "EMP_ABILITY", BigDecimal.ONE, null),
                new MatchingAbilitySnapshot(2L, null, "持续集成测试配置", 4,
                        BigDecimal.ONE, "EMP_ABILITY", BigDecimal.ONE, null));

        assertThat(AiContextPackageServiceImpl.selectContextAbilities(snapshots))
                .extracting(MatchingAbilitySnapshot::abilityName)
                .containsExactlyInAnyOrder("接口自动化测试", "持续集成测试配置");
    }

    @Test
    @DisplayName("selectContextAbilities：空/null 输入 → 返回空列表")
    void selectContextAbilities_emptyInputs() {
        assertTrue(AiContextPackageServiceImpl.selectContextAbilities(null).isEmpty());
        assertTrue(AiContextPackageServiceImpl.selectContextAbilities(Collections.emptyList()).isEmpty());
    }

    @Test
    @DisplayName("selectContextAbilities：同 tag 取最高等级；null 快照被忽略")
    void selectContextAbilities_dedupByTag() {
        List<MatchingAbilitySnapshot> snapshots = new ArrayList<>();
        snapshots.add(snapshot(3L, 5L, "Java", 1, "EMP_ABILITY", BigDecimal.ONE));
        snapshots.add(snapshot(4L, 5L, "Java", 4, "EMP_ABILITY", BigDecimal.ONE));
        snapshots.add(null);

        List<MatchingAbilitySnapshot> selected = AiContextPackageServiceImpl.selectContextAbilities(snapshots);

        assertEquals(1, selected.size());
        assertEquals(4L, selected.get(0).abilityId());
        assertEquals(4, selected.get(0).level());
    }

    @Test
    @DisplayName("selectContextAbilities：同 abilityId 取最高等级")
    void selectContextAbilities_sameAbilityIdKeepsHighest() {
        List<MatchingAbilitySnapshot> snapshots = List.of(
                snapshot(9L, null, "A", 1, "MANUAL", null),
                snapshot(9L, null, "A", 5, "MANUAL", null));

        List<MatchingAbilitySnapshot> selected = AiContextPackageServiceImpl.selectContextAbilities(snapshots);

        assertEquals(1, selected.size());
        assertEquals(5, selected.get(0).level());
    }

    @Test
    @DisplayName("selectContextAbilities：level 为 null 视为 0，不被更高等级覆盖")
    void selectContextAbilities_nullLevelTreatedAsZero() {
        List<MatchingAbilitySnapshot> snapshots = List.of(
                snapshot(1L, 7L, "X", null, "MANUAL", null),
                snapshot(2L, 7L, "X", 0, "MANUAL", null));

        List<MatchingAbilitySnapshot> selected = AiContextPackageServiceImpl.selectContextAbilities(snapshots);

        assertEquals(1, selected.size());
        assertEquals(1L, selected.get(0).abilityId());
    }
}
