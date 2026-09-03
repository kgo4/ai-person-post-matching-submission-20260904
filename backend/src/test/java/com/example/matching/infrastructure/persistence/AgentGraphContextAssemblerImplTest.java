package com.example.matching.infrastructure.persistence;

import com.example.matching.agent.dto.graph.AgentGraphContext;
import com.example.matching.entity.ability.PersonAbilityProfile;
import com.example.matching.entity.contest.ContestEvidenceItem;
import com.example.matching.entity.employee.EmpAbility;
import com.example.matching.entity.employee.EmpEmployee;
import com.example.matching.entity.employee.EmpVideoInterviewQuestion;
import com.example.matching.entity.employee.EmpVideoInterviewSession;
import com.example.matching.entity.kg.KgGraphNode;
import com.example.matching.entity.post.PostAbilityModel;
import com.example.matching.entity.post.PostPost;
import com.example.matching.entity.system.AbilityTag;
import com.example.matching.mapper.ability.PersonAbilityProfileMapper;
import com.example.matching.mapper.contest.ContestEvidenceItemMapper;
import com.example.matching.mapper.employee.EmpAbilityMapper;
import com.example.matching.mapper.employee.EmpEmployeeMapper;
import com.example.matching.mapper.employee.EmpVideoInterviewQuestionMapper;
import com.example.matching.mapper.employee.EmpVideoInterviewSessionMapper;
import com.example.matching.mapper.interview.InterviewFollowUpQuestionMapper;
import com.example.matching.mapper.kg.KgGraphEdgeMapper;
import com.example.matching.mapper.kg.KgGraphNodeMapper;
import com.example.matching.mapper.post.PostAbilityModelMapper;
import com.example.matching.mapper.post.PostPostMapper;
import com.example.matching.mapper.system.AbilityTagMapper;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Agent 图谱预构建装配器测试（方案第十四章 1-8 项）。
 */
class AgentGraphContextAssemblerImplTest {

    private AgentGraphContextAssemblerImpl assembler;
    private EmpEmployeeMapper empEmployeeMapper;
    private PostPostMapper postPostMapper;
    private PostAbilityModelMapper postAbilityModelMapper;
    private com.example.matching.port.employee.EmployeeAbilityReadPort employeeAbilityReadPort;
    private ContestEvidenceItemMapper evidenceItemMapper;
    private AbilityTagMapper abilityTagMapper;
    private KgGraphNodeMapper graphNodeMapper;
    private KgGraphEdgeMapper graphEdgeMapper;

    @BeforeAll
    static void initMybatisPlusLambdaCache() {
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(new MybatisConfiguration(), "");
        TableInfoHelper.initTableInfo(assistant, EmpAbility.class);
        TableInfoHelper.initTableInfo(assistant, PersonAbilityProfile.class);
        TableInfoHelper.initTableInfo(assistant, ContestEvidenceItem.class);
        TableInfoHelper.initTableInfo(assistant, PostAbilityModel.class);
        TableInfoHelper.initTableInfo(assistant, AbilityTag.class);
        TableInfoHelper.initTableInfo(assistant, KgGraphNode.class);
        TableInfoHelper.initTableInfo(assistant, com.example.matching.entity.kg.KgGraphEdge.class);
        TableInfoHelper.initTableInfo(assistant, EmpEmployee.class);
        TableInfoHelper.initTableInfo(assistant, PostPost.class);
    }

    @BeforeEach
    void setUp() {
        empEmployeeMapper = mock(EmpEmployeeMapper.class);
        postPostMapper = mock(PostPostMapper.class);
        postAbilityModelMapper = mock(PostAbilityModelMapper.class);
        employeeAbilityReadPort = mock(com.example.matching.port.employee.EmployeeAbilityReadPort.class);
        evidenceItemMapper = mock(ContestEvidenceItemMapper.class);
        abilityTagMapper = mock(AbilityTagMapper.class);
        graphNodeMapper = mock(KgGraphNodeMapper.class);
        graphEdgeMapper = mock(KgGraphEdgeMapper.class);
        assembler = new AgentGraphContextAssemblerImpl(
                empEmployeeMapper, postPostMapper, postAbilityModelMapper,
                employeeAbilityReadPort, evidenceItemMapper, abilityTagMapper,
                graphNodeMapper, graphEdgeMapper,
                mock(EmpVideoInterviewSessionMapper.class),
                mock(EmpVideoInterviewQuestionMapper.class),
                mock(InterviewFollowUpQuestionMapper.class),
                new ObjectMapper(), new SimpleMeterRegistry());
    }

    private EmpEmployee emp(long id, LocalDateTime updated) {
        EmpEmployee emp = new EmpEmployee();
        emp.setId(id);
        emp.setRealName("张三");
        emp.setUpdatedTime(updated);
        return emp;
    }

    private PostPost post(long id, LocalDateTime updated) {
        PostPost post = new PostPost();
        post.setId(id);
        post.setPostName("Java工程师");
        post.setUpdatedTime(updated);
        return post;
    }

    private PostAbilityModel model(long tagId, int requiredLevel, BigDecimal weight,
                                   boolean required, boolean core) {
        PostAbilityModel model = new PostAbilityModel();
        model.setId(tagId * 100);
        model.setPostId(200L);
        model.setTagId(tagId);
        model.setMinRequiredLevel(requiredLevel);
        model.setWeight(weight);
        model.setIsRequired(required ? 1 : 0);
        model.setIsCore(core ? 1 : 0);
        model.setIsDeleted(0);
        return model;
    }

    private EmpAbility empAbility(long id, long tagId, int level) {
        EmpAbility ability = new EmpAbility();
        ability.setId(id);
        ability.setEmpId(100L);
        ability.setTagId(tagId);
        ability.setMasteryLevel(level);
        ability.setIsDeleted(0);
        return ability;
    }

    private ContestEvidenceItem evidence(long id, long targetRefId, long tagId, String status) {
        ContestEvidenceItem item = new ContestEvidenceItem();
        item.setId(id);
        item.setTargetType("EMP_ABILITY");
        item.setTargetRefId(targetRefId);
        item.setTagId(tagId);
        item.setSourceText("项目证据文本" + id);
        item.setEvidenceStatus(status);
        item.setCredibilityScore(new BigDecimal("0.9"));
        item.setIsDeleted(0);
        return item;
    }

    private void stubBasics() {
        when(empEmployeeMapper.selectById(100L)).thenReturn(emp(100L, LocalDateTime.now().minusHours(1)));
        when(postPostMapper.selectById(200L)).thenReturn(post(200L, LocalDateTime.now().minusHours(1)));
        when(abilityTagMapper.selectList(any())).thenReturn(List.of(
                tag(11L, "Java"), tag(12L, "Spring"), tag(13L, "Kubernetes")));
        when(employeeAbilityReadPort.loadAuthoritativeAbilities(java.util.List.of(100L)))
                .thenReturn(java.util.Map.of(100L, java.util.List.of(
                        new com.example.matching.dto.matching.MatchingAbilitySnapshot(
                                1L, 11L, "Java", 4, null, "EMP_ABILITY", null, null),
                        new com.example.matching.dto.matching.MatchingAbilitySnapshot(
                                2L, 12L, "Spring", 2, null, "EMP_ABILITY", null, null),
                        new com.example.matching.dto.matching.MatchingAbilitySnapshot(
                                3L, 13L, "Kubernetes", 1, null, "EMP_ABILITY", null, null))));
    }

    private AbilityTag tag(long id, String name) {
        AbilityTag tag = new AbilityTag();
        tag.setId(id);
        tag.setTagName(name);
        return tag;
    }

    @Test
    @DisplayName("1. 匹配子图包含员工/岗位/能力/证据/关系/预计算差距")
    void buildForMatching_containsNodesEdgesAndFacts() {
        stubBasics();
        when(postAbilityModelMapper.selectList(any())).thenReturn(List.of(
                model(11L, 3, new BigDecimal("0.5"), true, true),
                model(12L, 3, new BigDecimal("0.3"), true, false),
                model(13L, 2, new BigDecimal("0.2"), false, false)));
        when(evidenceItemMapper.selectList(any())).thenReturn(List.of(
                evidence(50L, 1L, 11L, "VERIFIED")));
        when(graphNodeMapper.selectList(any())).thenReturn(List.of());
        when(graphEdgeMapper.selectList(any())).thenReturn(List.of());

        AgentGraphContext ctx = assembler.buildForMatching(100L, 200L);

        assertThat(ctx.getStatus()).isEqualTo("UNAVAILABLE"); // 图谱节点缺失
        // 业务事实仍完整：节点/匹配/差距/证据不依赖图谱状态
        assertThat(ctx.getNodes()).extracting("nodeKey")
                .contains("EMPLOYEE:100", "POST:200", "ABILITY:11", "ABILITY:12", "ABILITY:13", "EVIDENCE:50");
        assertThat(ctx.getEdges()).extracting("edgeType")
                .contains("REQUIRES", "HAS_ABILITY", "SUPPORTED_BY");
        assertThat(ctx.getAbilityMatches()).hasSize(3);
        // 缺口只统计必填能力：12(2<3) 必填为缺口；13 非必填（加分项）不构成缺口
        assertThat(ctx.getGaps()).hasSize(1);
        assertThat(ctx.getGaps()).extracting("abilityTagId").containsExactly(12L);
        assertThat(ctx.getVerifiedEvidence()).hasSize(1);
        assertThat(ctx.getAllowedAbilityTagIds()).containsExactlyInAnyOrder(11L, 12L, 13L);
        assertThat(ctx.getAllowedSourceRefs()).contains(
                "fact:EMP_ABILITY:1", "fact:POST_ABILITY_MODEL:1100", "fact:EVIDENCE:50");
    }

    @Test
    @DisplayName("2. 满足/等级不足/缺失/加分状态计算正确")
    void buildForMatching_matchStates() {
        stubBasics();
        when(postAbilityModelMapper.selectList(any())).thenReturn(List.of(
                model(11L, 4, new BigDecimal("0.5"), true, true),   // 员工4=4 SATISFIED
                model(12L, 4, new BigDecimal("0.3"), true, false),  // 员工2<4 LEVEL_GAP
                model(13L, 2, new BigDecimal("0.2"), false, false))); // 非必填 BONUS
        when(graphNodeMapper.selectList(any())).thenReturn(List.of());
        when(graphEdgeMapper.selectList(any())).thenReturn(List.of());

        AgentGraphContext ctx = assembler.buildForMatching(100L, 200L);

        assertThat(ctx.getAbilityMatches()).extracting("matchState")
                .containsExactlyInAnyOrder("SATISFIED", "LEVEL_GAP", "BONUS");
        // 必填缺失：员工没有 tagId=99 的能力
        assertThat(ctx.getGaps()).extracting("abilityTagId").containsExactlyInAnyOrder(12L);
    }

    @Test
    @DisplayName("3+4. 未审核证据与其他员工证据不进入子图")
    void buildForMatching_filtersEvidence() {
        stubBasics();
        when(postAbilityModelMapper.selectList(any())).thenReturn(List.of(
                model(11L, 3, new BigDecimal("0.5"), true, true)));
        // PENDING 证据、其他员工能力(targetRefId=999)证据
        when(evidenceItemMapper.selectList(any())).thenReturn(List.of(
                evidence(50L, 1L, 11L, "VERIFIED"),
                evidence(51L, 1L, 11L, "PENDING"),
                evidence(52L, 999L, 11L, "VERIFIED")));
        when(graphNodeMapper.selectList(any())).thenReturn(List.of());
        when(graphEdgeMapper.selectList(any())).thenReturn(List.of());

        AgentGraphContext ctx = assembler.buildForMatching(100L, 200L);

        assertThat(ctx.getVerifiedEvidence()).hasSize(1);
        assertThat(ctx.getVerifiedEvidence().get(0).getEvidenceId()).isEqualTo(50L);
        assertThat(ctx.getNodes()).extracting("nodeKey").contains("EVIDENCE:50")
                .doesNotContain("EVIDENCE:51", "EVIDENCE:52");
    }

    @Test
    @DisplayName("无系统标签的正式能力不应中断标签图谱上下文")
    void buildForMatching_ignoresUntaggedAbilitiesForTagGraph() {
        stubBasics();
        when(postAbilityModelMapper.selectList(any())).thenReturn(List.of(
                model(11L, 3, new BigDecimal("0.5"), true, true)));
        when(employeeAbilityReadPort.loadAuthoritativeAbilities(java.util.List.of(100L)))
                .thenReturn(java.util.Map.of(100L, java.util.List.of(
                        new com.example.matching.dto.matching.MatchingAbilitySnapshot(
                                1L, 11L, "Java", 4, null, "EMP_ABILITY", null, null),
                        new com.example.matching.dto.matching.MatchingAbilitySnapshot(
                                2L, null, "接口自动化测试", 3, null, "EMP_ABILITY", null, null))));
        when(evidenceItemMapper.selectList(any())).thenReturn(List.of());
        when(graphNodeMapper.selectList(any())).thenReturn(List.of());
        when(graphEdgeMapper.selectList(any())).thenReturn(List.of());

        AgentGraphContext ctx = assembler.buildForMatching(100L, 200L);

        assertThat(ctx.getAbilityMatches()).hasSize(1);
        assertThat(ctx.getAbilityMatches().get(0).getAbilityTagId()).isEqualTo(11L);
    }

    @Test
    @DisplayName("8. 权威能力：融合画像优先，回退 emp_ability")
    void buildForMatching_profileFusedPreferred() {
        EmpEmployee emp = emp(100L, LocalDateTime.now().minusHours(1));
        when(empEmployeeMapper.selectById(100L)).thenReturn(emp);
        when(postPostMapper.selectById(200L)).thenReturn(post(200L, LocalDateTime.now().minusHours(1)));
        when(abilityTagMapper.selectList(any())).thenReturn(List.of(tag(11L, "Java")));
        when(postAbilityModelMapper.selectList(any())).thenReturn(List.of(
                model(11L, 3, new BigDecimal("0.5"), true, true)));
        // 画像 APPROVED finalLevel=5（端口语义）；emp_ability masteryLevel=2
        when(employeeAbilityReadPort.loadAuthoritativeAbilities(java.util.List.of(100L)))
                .thenReturn(java.util.Map.of(100L, java.util.List.of(
                        new com.example.matching.dto.matching.MatchingAbilitySnapshot(
                                1L, 11L, "Java", 5, null, "PROFILE_FUSED", null, null))));
        when(graphNodeMapper.selectList(any())).thenReturn(List.of());
        when(graphEdgeMapper.selectList(any())).thenReturn(List.of());

        AgentGraphContext ctx = assembler.buildForMatching(100L, 200L);

        assertThat(ctx.getAbilityMatches().get(0).getEmployeeLevel()).isEqualTo(5);
        assertThat(ctx.getAbilityMatches().get(0).getMatchState()).isEqualTo("SATISFIED");
    }

    @Test
    @DisplayName("6. 图谱不可用时业务继续（UNAVAILABLE 状态 + 事实仍返回）")
    void buildForMatching_unavailableStillReturnsFacts() {
        when(empEmployeeMapper.selectById(100L)).thenReturn(emp(100L, LocalDateTime.now()));
        when(postPostMapper.selectById(200L)).thenReturn(post(200L, LocalDateTime.now()));
        when(postAbilityModelMapper.selectList(any())).thenReturn(List.of(
                model(11L, 3, new BigDecimal("0.5"), true, true)));
        when(abilityTagMapper.selectList(any())).thenReturn(List.of(tag(11L, "Java")));
        when(employeeAbilityReadPort.loadAuthoritativeAbilities(java.util.List.of(100L)))
                .thenReturn(java.util.Map.of(100L, java.util.List.of(
                        new com.example.matching.dto.matching.MatchingAbilitySnapshot(
                                1L, 11L, "Java", 4, null, "EMP_ABILITY", null, null))));
        when(graphNodeMapper.selectList(any())).thenReturn(List.of()); // 图谱缺失
        when(graphEdgeMapper.selectList(any())).thenReturn(List.of());

        AgentGraphContext ctx = assembler.buildForMatching(100L, 200L);

        assertThat(ctx.getStatus()).isEqualTo("UNAVAILABLE");
        assertThat(ctx.getAbilityMatches()).hasSize(1);
        assertThat(ctx.getNodes()).isNotEmpty();
    }

    @Test
    @DisplayName("7. 图谱陈旧时状态 STALE，不采用旧关系结论")
    void buildForMatching_staleWhenGraphOlderThanFacts() {
        LocalDateTime factTime = LocalDateTime.now().minusHours(1);
        when(empEmployeeMapper.selectById(100L)).thenReturn(emp(100L, factTime));
        when(postPostMapper.selectById(200L)).thenReturn(post(200L, factTime));
        when(postAbilityModelMapper.selectList(any())).thenReturn(List.of(
                model(11L, 3, new BigDecimal("0.5"), true, true)));
        when(abilityTagMapper.selectList(any())).thenReturn(List.of(tag(11L, "Java")));
        when(employeeAbilityReadPort.loadAuthoritativeAbilities(java.util.List.of(100L)))
                .thenReturn(java.util.Map.of(100L, java.util.List.of(
                        new com.example.matching.dto.matching.MatchingAbilitySnapshot(
                                1L, 11L, "Java", 4, null, "EMP_ABILITY", null, null))));
        // 图谱节点陈旧（updatedTime 早于事实更新时间）
        KgGraphNode abilityNode = new KgGraphNode();
        abilityNode.setNodeKey("ABILITY:11");
        abilityNode.setNodeType("ABILITY");
        abilityNode.setRefId(11L);
        abilityNode.setLabel("Java");
        abilityNode.setUpdatedTime(factTime.minusDays(2));
        when(graphNodeMapper.selectList(any())).thenReturn(List.of(abilityNode));
        when(graphEdgeMapper.selectList(any())).thenReturn(List.of());

        AgentGraphContext ctx = assembler.buildForMatching(100L, 200L);

        assertThat(ctx.getStatus()).isEqualTo("STALE");
        assertThat(ctx.isUsable()).isFalse();
    }

    @Test
    @DisplayName("5. 图谱超限时稳定截断（保留核心必填）")
    void buildForMatching_truncatesDeterministically() {
        stubBasics();
        List<PostAbilityModel> models = new java.util.ArrayList<>();
        for (long i = 1; i <= 40; i++) {
            models.add(model(i, 3, new BigDecimal("0.1"), true, i <= 2));
        }
        when(postAbilityModelMapper.selectList(any())).thenReturn(models);
        // 40 个 tag 的标签
        java.util.List<AbilityTag> tags = new java.util.ArrayList<>();
        for (long i = 1; i <= 40; i++) {
            tags.add(tag(i, "能力" + i));
        }
        when(abilityTagMapper.selectList(any())).thenReturn(tags);
        java.util.List<com.example.matching.dto.matching.MatchingAbilitySnapshot> abilitySnapshots =
                new java.util.ArrayList<>();
        for (long i = 1; i <= 40; i++) {
            abilitySnapshots.add(new com.example.matching.dto.matching.MatchingAbilitySnapshot(
                    i, i, "能力" + i, 1, null, "EMP_ABILITY", null, null));
        }
        when(employeeAbilityReadPort.loadAuthoritativeAbilities(java.util.List.of(100L)))
                .thenReturn(java.util.Map.of(100L, abilitySnapshots));
        when(graphNodeMapper.selectList(any())).thenReturn(List.of());
        when(graphEdgeMapper.selectList(any())).thenReturn(List.of());

        AgentGraphContext ctx = assembler.buildForMatching(100L, 200L);

        // 岗位能力限 30：核心必填保留
        assertThat(ctx.getAbilityMatches()).hasSize(30);
        assertThat(ctx.getAbilityMatches().get(0).isCore()).isTrue();
        assertThat(ctx.getNodes()).extracting("nodeKey")
                .contains("ABILITY:1", "ABILITY:2");
    }

    @Test
    @DisplayName("学习路径子图：白名单=缺口标签，差距与前置关系可用")
    void buildForLearningPath_gapWhitelist() {
        stubBasics();
        when(postAbilityModelMapper.selectList(any())).thenReturn(List.of(
                model(11L, 4, new BigDecimal("0.5"), true, true),
                model(12L, 4, new BigDecimal("0.3"), true, false)));
        when(graphNodeMapper.selectList(any())).thenReturn(List.of());
        when(graphEdgeMapper.selectList(any())).thenReturn(List.of());

        AgentGraphContext ctx = assembler.buildForLearningPath(100L, 200L, Set.of(12L));

        assertThat(ctx.getAllowedAbilityTagIds()).containsExactly(12L);
        assertThat(ctx.getAbilityMatches()).hasSize(1);
        assertThat(ctx.getAbilityMatches().get(0).getAbilityTagId()).isEqualTo(12L);
        assertThat(ctx.getGaps()).extracting("abilityTagId").containsExactly(12L);
    }

    @Test
    void buildForInterviewObservation_parsesNumericExpectedTagIds() {
        EmpVideoInterviewSessionMapper sessionMapper = mock(EmpVideoInterviewSessionMapper.class);
        EmpVideoInterviewQuestionMapper questionMapper = mock(EmpVideoInterviewQuestionMapper.class);
        InterviewFollowUpQuestionMapper followUpMapper = mock(InterviewFollowUpQuestionMapper.class);
        EmpVideoInterviewSession session = new EmpVideoInterviewSession();
        session.setId(7L);
        session.setEmpId(100L);
        session.setPostId(200L);
        session.setSessionName("session");
        when(sessionMapper.selectById(7L)).thenReturn(session);
        EmpVideoInterviewQuestion question = new EmpVideoInterviewQuestion();
        question.setId(70L);
        question.setSessionId(7L);
        question.setQuestionText("Java question");
        question.setExpectedTagsJson("[11,12]");
        when(questionMapper.selectList(any())).thenReturn(List.of(question));
        when(followUpMapper.selectList(any())).thenReturn(List.of());
        AgentGraphContext ctx = new AgentGraphContextAssemblerImpl(
                empEmployeeMapper, postPostMapper, postAbilityModelMapper,
                employeeAbilityReadPort, evidenceItemMapper, abilityTagMapper,
                graphNodeMapper, graphEdgeMapper, sessionMapper, questionMapper,
                followUpMapper, new ObjectMapper(), new SimpleMeterRegistry())
                .buildForInterviewObservation(7L, Set.of(11L, 12L));

        assertThat(ctx.getEdges()).anyMatch(edge -> "INTERVIEW_QUESTION:70".equals(edge.getSourceNodeKey())
                && "ABILITY:11".equals(edge.getTargetNodeKey()));
        assertThat(ctx.getEdges()).anyMatch(edge -> "INTERVIEW_QUESTION:70".equals(edge.getSourceNodeKey())
                && "ABILITY:12".equals(edge.getTargetNodeKey()));
    }

    @Test
    void buildForMatching_marksGraphStaleWhenAnyAbilityNodeIsOld() {
        stubBasics();
        when(postAbilityModelMapper.selectList(any())).thenReturn(List.of(
                model(11L, 3, new BigDecimal("0.5"), true, true),
                model(12L, 3, new BigDecimal("0.5"), true, false)));
        LocalDateTime now = LocalDateTime.now();
        KgGraphNode fresh = new KgGraphNode();
        fresh.setNodeKey("ABILITY:11");
        fresh.setUpdatedTime(now);
        KgGraphNode stale = new KgGraphNode();
        stale.setNodeKey("ABILITY:12");
        stale.setUpdatedTime(now.minusDays(3));
        when(graphNodeMapper.selectList(any())).thenReturn(List.of(fresh, stale));
        when(graphEdgeMapper.selectList(any())).thenReturn(List.of());
        when(empEmployeeMapper.selectById(100L)).thenReturn(emp(100L, now.minusDays(2)));
        when(postPostMapper.selectById(200L)).thenReturn(post(200L, now.minusDays(2)));

        assertThat(assembler.buildForMatching(100L, 200L).getStatus()).isEqualTo("STALE");
    }
}
