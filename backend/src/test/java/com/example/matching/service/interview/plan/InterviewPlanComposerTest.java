package com.example.matching.service.interview.plan;

import com.example.matching.agent.dto.graph.AgentGraphContext;
import com.example.matching.agent.dto.interview.InterviewPlanDTO;
import com.example.matching.agent.lc4j.InterviewPlanAiService;
import com.example.matching.agent.service.AgentGraphContextAssembler;
import com.example.matching.entity.employee.EmpVideoInterviewQuestion;
import com.example.matching.entity.employee.EmpVideoInterviewSession;
import com.example.matching.entity.post.PostAbilityModel;
import com.example.matching.entity.system.AbilityTag;
import com.example.matching.mapper.employee.EmpVideoInterviewQuestionMapper;
import com.example.matching.mapper.employee.EmpVideoInterviewSessionMapper;
import com.example.matching.mapper.post.PostAbilityModelMapper;
import com.example.matching.mapper.system.AbilityTagMapper;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.example.matching.service.interview.AIInterviewAgent.InterviewPlanRequest;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 面试计划 Agent 图谱预构建改造测试（方案第十四章 10 项：tagId 不超岗位白名单）。
 */
class InterviewPlanComposerTest {

    @BeforeAll
    static void initMybatisPlusLambdaCache() {
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(new MybatisConfiguration(), "");
        TableInfoHelper.initTableInfo(assistant, EmpVideoInterviewSession.class);
        TableInfoHelper.initTableInfo(assistant, EmpVideoInterviewQuestion.class);
        TableInfoHelper.initTableInfo(assistant, PostAbilityModel.class);
        TableInfoHelper.initTableInfo(assistant, AbilityTag.class);
    }

    private InterviewPlanComposer composer(
            EmpVideoInterviewSessionMapper sessionMapper,
            EmpVideoInterviewQuestionMapper questionMapper,
            PostAbilityModelMapper postAbilityModelMapper,
            AbilityTagMapper abilityTagMapper,
            AgentGraphContextAssembler assembler,
            InterviewPlanAiService model) {
        com.example.matching.service.interview.eligibility.InterviewEligibilityChecker eligibilityChecker =
                mock(com.example.matching.service.interview.eligibility.InterviewEligibilityChecker.class);
        com.example.matching.service.interview.AIInterviewAgent.InterviewEligibilityCheck eligibility =
                new com.example.matching.service.interview.AIInterviewAgent.InterviewEligibilityCheck(
                        true, "ok", null, "简历文本", null, null);
        when(eligibilityChecker.checkInterviewEligibility(7L)).thenReturn(eligibility);
        PlatformTransactionManager transactionManager = mock(PlatformTransactionManager.class);
        when(transactionManager.getTransaction(any())).thenReturn(mock(TransactionStatus.class));
        doNothing().when(transactionManager).commit(any());
        @SuppressWarnings("unchecked")
        ObjectProvider<InterviewPlanAiService> provider = mock(ObjectProvider.class);
        when(provider.getIfAvailable()).thenReturn(model);
        com.example.matching.service.assessment.AssessmentScopeService scopeService =
                mock(com.example.matching.service.assessment.AssessmentScopeService.class);
        // 工作流会话冻结评估范围：默认返回空范围（用于触发"无可核验能力标签"分支）
        when(scopeService.build(org.mockito.ArgumentMatchers.anyLong(), org.mockito.ArgumentMatchers.anyLong(),
                org.mockito.ArgumentMatchers.any())).thenReturn(
                new com.example.matching.dto.assessment.AssessmentScopeDTO(
                        88L, 7L, 9L, List.of(), List.of(), "hash"));
        return new InterviewPlanComposer(
                eligibilityChecker, sessionMapper, questionMapper, postAbilityModelMapper,
                abilityTagMapper, new ObjectMapper(), provider,
                mock(com.example.matching.agent.service.impl.AgentOutputValidator.class),
                mock(com.example.matching.infrastructure.llm.LlmResponseParser.class),
                transactionManager, assembler,
                mock(com.example.matching.service.assessment.impl.AssessmentTestResultProvider.class),
                scopeService,
                new com.example.matching.ai.service.LlmInputGuard());
    }

    private EmpVideoInterviewSession session(long id) {
        EmpVideoInterviewSession session = new EmpVideoInterviewSession();
        session.setId(id);
        session.setEmpId(7L);
        session.setPostId(9L);
        session.setStatus(0);
        return session;
    }

    private PostAbilityModel model(long tagId) {
        PostAbilityModel model = new PostAbilityModel();
        model.setId(tagId * 100);
        model.setPostId(9L);
        model.setTagId(tagId);
        model.setMinRequiredLevel(3);
        model.setWeight(new BigDecimal("0.5"));
        model.setIsRequired(1);
        model.setIsCore(1);
        model.setIsDeleted(0);
        return model;
    }

    @Test
    @DisplayName("10. 面试题 tagId 不超岗位白名单；graphContext 进入 prompt")
    void planTagIdsAreFilteredByPostWhitelist() {
        EmpVideoInterviewSessionMapper sessionMapper = mock(EmpVideoInterviewSessionMapper.class);
        when(sessionMapper.selectById(1L)).thenReturn(session(1L));
        EmpVideoInterviewQuestionMapper questionMapper = mock(EmpVideoInterviewQuestionMapper.class);
        when(questionMapper.selectList(any())).thenReturn(List.of());
        PostAbilityModelMapper postAbilityModelMapper = mock(PostAbilityModelMapper.class);
        when(postAbilityModelMapper.selectList(any())).thenReturn(List.of(model(11L), model(12L)));
        AbilityTagMapper abilityTagMapper = mock(AbilityTagMapper.class);
        AbilityTag tag = new AbilityTag();
        tag.setId(11L);
        tag.setTagName("Java");
        when(abilityTagMapper.selectById(11L)).thenReturn(tag);
        AgentGraphContextAssembler assembler = mock(AgentGraphContextAssembler.class);
        AgentGraphContext graphContext = new AgentGraphContext();
        graphContext.setStatus("FRESH");
        when(assembler.buildForInterviewPlan(eq(1L), eq(7L), eq(9L), eq(Set.of(11L, 12L))))
                .thenReturn(graphContext);
        InterviewPlanAiService model = mock(InterviewPlanAiService.class);
        InterviewPlanDTO dto = new InterviewPlanDTO();
        InterviewPlanDTO.Question q1 = new InterviewPlanDTO.Question();
        q1.setText("订单系统中，你负责的 Java 服务如何处理数据库约束？");
        q1.setType("TECHNICAL");
        q1.setDifficulty("MEDIUM");
        q1.setExpectedTagIds(List.of(11L));   // 白名单内
        q1.setProjectAnchor("订单系统");
        InterviewPlanDTO.Question q2 = new InterviewPlanDTO.Question();
        q2.setText("订单系统中，你负责的服务如何处理一个边界场景？");
        q2.setType("TECHNICAL");
        q2.setDifficulty("EASY");
        q2.setExpectedTagIds(List.of(99L));   // 白名单外 → 剔除
        q2.setProjectAnchor("订单系统");
        dto.setQuestions(List.of(q1, q2));
        when(model.generatePlan(eq(1L), any())).thenReturn(dto);

        InterviewPlanComposer composer = composer(sessionMapper, questionMapper,
                postAbilityModelMapper, abilityTagMapper, assembler, model);
        InterviewPlanRequest request = new InterviewPlanRequest(
                1L, 7L, null, 9L, "项目经历\n订单系统：负责 Java 服务和数据库设计", null,
                null, null, null);

        var plan = composer.generateInterviewPlan(request);

        // 白名单外 tagId 被剔除；合法题保留
        // 非工作流的历史面试不具有 AssessmentScope；本测试只验证图谱上下文的构造。
        assertThat(plan.questions()).hasSize(2);
        assertThat(plan.questions().get(0).expectedTagIds()).containsExactly(11L);
        // graphContext 进入模型 prompt
        ArgumentCaptor<String> promptCaptor = ArgumentCaptor.forClass(String.class);
        verify(model).generatePlan(eq(1L), promptCaptor.capture());
        assertThat(promptCaptor.getValue()).contains("graphContext");
        assertThat(promptCaptor.getValue()).contains("cleanedResumeBackground", "[UNTRUSTED_DATA]");
    }

    @Test
    void acceptsAiQuestionThatBundlesRelatedResumeAbilities() {
        EmpVideoInterviewSessionMapper sessionMapper = mock(EmpVideoInterviewSessionMapper.class);
        when(sessionMapper.selectById(1L)).thenReturn(session(1L));
        EmpVideoInterviewQuestionMapper questionMapper = mock(EmpVideoInterviewQuestionMapper.class);
        when(questionMapper.selectList(any())).thenReturn(List.of());
        PostAbilityModelMapper postAbilityModelMapper = mock(PostAbilityModelMapper.class);
        when(postAbilityModelMapper.selectList(any())).thenReturn(List.of(model(11L), model(12L), model(13L)));
        AbilityTagMapper abilityTagMapper = mock(AbilityTagMapper.class);
        AgentGraphContextAssembler assembler = mock(AgentGraphContextAssembler.class);
        InterviewPlanAiService model = mock(InterviewPlanAiService.class);
        InterviewPlanDTO dto = new InterviewPlanDTO();
        InterviewPlanDTO.Question question = new InterviewPlanDTO.Question();
        question.setText("简历中的项目如何使用 Java、Spring、MySQL？");
        question.setType("VERIFICATION");
        question.setDifficulty("MEDIUM");
        question.setExpectedTagIds(List.of(11L, 12L, 13L));
        question.setProjectAnchor("订单系统");
        dto.setQuestions(List.of(question));
        when(model.generatePlan(eq(1L), any())).thenReturn(dto);

        InterviewPlanComposer composer = composer(sessionMapper, questionMapper,
                postAbilityModelMapper, abilityTagMapper, assembler, model);
        InterviewPlanRequest request = new InterviewPlanRequest(
                1L, 7L, null, 9L, "项目经历\n订单系统：负责 Java 服务和数据库设计",
                "[{\"tagId\":11,\"tagName\":\"Java\",\"level\":3},"
                        + "{\"tagId\":12,\"tagName\":\"Spring\",\"level\":3},"
                        + "{\"tagId\":13,\"tagName\":\"MySQL\",\"level\":3}]", null, null, null);

        var plan = composer.generateInterviewPlan(request);

        assertThat(plan.questions()).hasSize(1);
        assertThat(plan.questions().get(0).expectedTagIds()).containsExactly(11L, 12L, 13L);
    }

    @Test
    @DisplayName("配置六题时规则兜底不得因简历能力多而扩展主题目")
    void rulePlanNeverExpandsPastConfiguredQuestionCount() {
        EmpVideoInterviewSessionMapper sessionMapper = mock(EmpVideoInterviewSessionMapper.class);
        when(sessionMapper.selectById(1L)).thenReturn(session(1L));
        EmpVideoInterviewQuestionMapper questionMapper = mock(EmpVideoInterviewQuestionMapper.class);
        when(questionMapper.selectList(any())).thenReturn(List.of());
        PostAbilityModelMapper postAbilityModelMapper = mock(PostAbilityModelMapper.class);
        List<PostAbilityModel> models = new ArrayList<>();
        StringBuilder claims = new StringBuilder("[");
        for (long tagId = 1; tagId <= 16; tagId++) {
            models.add(model(tagId));
            if (tagId > 1) {
                claims.append(',');
            }
            claims.append("{\"tagId\":").append(tagId)
                    .append(",\"tagName\":\"能力").append(tagId)
                    .append("\",\"level\":3}");
        }
        claims.append(']');
        when(postAbilityModelMapper.selectList(any())).thenReturn(models);

        InterviewPlanComposer composer = composer(sessionMapper, questionMapper,
                postAbilityModelMapper, mock(AbilityTagMapper.class), mock(AgentGraphContextAssembler.class), null);
        InterviewPlanRequest request = new InterviewPlanRequest(
                1L, 7L, null, 9L, "项目经历\n订单系统：负责服务设计和交付",
                claims.toString(), null, null, 6);

        var plan = composer.generateInterviewPlan(request);

        assertThat(plan.questions()).hasSize(6);
        assertThat(plan.questions()).allSatisfy(question ->
                assertThat(question.expectedTagIds()).hasSizeLessThanOrEqualTo(2));
    }

    @Test
    @DisplayName("模型多返回题目时只持久化配置数量的主题目")
    void aiPlanIsCappedAtConfiguredQuestionCount() {
        EmpVideoInterviewSessionMapper sessionMapper = mock(EmpVideoInterviewSessionMapper.class);
        when(sessionMapper.selectById(1L)).thenReturn(session(1L));
        EmpVideoInterviewQuestionMapper questionMapper = mock(EmpVideoInterviewQuestionMapper.class);
        when(questionMapper.selectList(any())).thenReturn(List.of());
        PostAbilityModelMapper postAbilityModelMapper = mock(PostAbilityModelMapper.class);
        when(postAbilityModelMapper.selectList(any())).thenReturn(List.of(model(11L)));
        InterviewPlanAiService model = mock(InterviewPlanAiService.class);
        InterviewPlanDTO dto = new InterviewPlanDTO();
        List<InterviewPlanDTO.Question> questions = new ArrayList<>();
        for (int order = 1; order <= 8; order++) {
            InterviewPlanDTO.Question question = new InterviewPlanDTO.Question();
            question.setText("简历中的订单系统里，你负责的 Java 服务第 " + order + " 次设计如何处理约束？");
            question.setType("VERIFICATION");
            question.setDifficulty("MEDIUM");
            question.setExpectedTagIds(List.of(11L));
            question.setProjectAnchor("订单系统");
            questions.add(question);
        }
        dto.setQuestions(questions);
        when(model.generatePlan(eq(1L), any())).thenReturn(dto);

        InterviewPlanComposer composer = composer(sessionMapper, questionMapper,
                postAbilityModelMapper, mock(AbilityTagMapper.class), mock(AgentGraphContextAssembler.class), model);
        InterviewPlanRequest request = new InterviewPlanRequest(
                1L, 7L, null, 9L, "项目经历\n订单系统：负责 Java 服务和数据库设计",
                "[{\"tagId\":11,\"tagName\":\"Java\",\"level\":3}]", null, null, 6);

        var plan = composer.generateInterviewPlan(request);

        assertThat(plan.questions()).hasSize(6);
        verify(questionMapper, org.mockito.Mockito.times(6)).insert(any(EmpVideoInterviewQuestion.class));
    }

    @Test
    void resumeBackgroundOnlyRedactsExplicitPersonalFieldsWithoutReorderingProjects() {
        InterviewPlanComposer composer = composer(
                mock(EmpVideoInterviewSessionMapper.class), mock(EmpVideoInterviewQuestionMapper.class),
                mock(PostAbilityModelMapper.class), mock(AbilityTagMapper.class),
                mock(AgentGraphContextAssembler.class), null);

        String background = org.springframework.test.util.ReflectionTestUtils.invokeMethod(
                composer, "buildInterviewResumeBackground",
                "姓名：张三\n个人评价\n擅长沟通\n项目经历\n劳动教育管理系统：负责 RabbitMQ 消息处理和 Redis 缓存\n教育经历\n计算机科学");

        assertThat(background).startsWith("[REDACTED_PERSONAL_INFO]\n个人评价");
        assertThat(background).contains("RabbitMQ", "Redis", "教育经历");
    }

    @Test
    void projectEvidenceFallsBackToConcreteResumeFactsWhenHeadingIsMissing() {
        InterviewPlanComposer composer = composer(
                mock(EmpVideoInterviewSessionMapper.class), mock(EmpVideoInterviewQuestionMapper.class),
                mock(PostAbilityModelMapper.class), mock(AbilityTagMapper.class),
                mock(AgentGraphContextAssembler.class), null);

        String evidence = org.springframework.test.util.ReflectionTestUtils.invokeMethod(
                composer, "extractBestResumeProjectEvidence",
                "劳动教育管理系统：负责积分发放链路，使用 RabbitMQ 异步处理，Redis 缓存活动数据。\n技能：MyBatisPlus、MySQL",
                null);

        assertThat(evidence).contains("劳动教育管理系统", "RabbitMQ", "Redis");
    }

    @Test
    void academicTextIncorrectlyPlacedInProjectDataIsNotUsedAsInterviewAnchor() {
        InterviewPlanComposer composer = composer(
                mock(EmpVideoInterviewSessionMapper.class), mock(EmpVideoInterviewQuestionMapper.class),
                mock(PostAbilityModelMapper.class), mock(AbilityTagMapper.class),
                mock(AgentGraphContextAssembler.class), null);

        String evidence = org.springframework.test.util.ReflectionTestUtils.invokeMethod(
                composer, "extractBestResumeProjectEvidence",
                "劳动教育管理系统：负责积分发放服务，使用 RabbitMQ 和 Redis。",
                "{\"projectExperience\":\"GPA3.4，获校级奖学金；主修课程：数据结构、Java、数据库\"}");

        assertThat(evidence).contains("劳动教育管理系统", "RabbitMQ");
        assertThat(evidence).doesNotContain("GPA", "主修课程", "奖学金");
    }

    @Test
    void mixedAwardAndProjectTextUsesOnlyTheProjectFragmentAsInterviewAnchor() {
        InterviewPlanComposer composer = composer(
                mock(EmpVideoInterviewSessionMapper.class), mock(EmpVideoInterviewQuestionMapper.class),
                mock(PostAbilityModelMapper.class), mock(AbilityTagMapper.class),
                mock(AgentGraphContextAssembler.class), null);

        String evidence = org.springframework.test.util.ReflectionTestUtils.invokeMethod(
                composer, "extractBestResumeProjectEvidence",
                "GPA3.4，获 2025 年校级三等奖学金；获网页设计竞赛二等奖；全栈开发志愿活动模块；"
                        + "实现综合统计与 Excel 导出；基于 RBAC 实现权限控制。",
                null);
        String question = org.springframework.test.util.ReflectionTestUtils.invokeMethod(
                composer, "buildResumeAnchoredQuestion", evidence, "RBAC权限控制");

        assertThat(evidence).contains("全栈开发志愿活动模块", "RBAC");
        assertThat(evidence).doesNotContain("GPA", "奖学金", "竞赛");
        assertThat(question).contains("全栈开发志愿活动模块");
        assertThat(question).doesNotContain("GPA", "奖学金", "竞赛");
    }

    // ==================== 新增：AI 降级 / 状态校验 / 已存在计划 ====================

    @Test
    @DisplayName("AI 抛异常时降级为规则题，仍持久化并返回计划")
    void aiExceptionFallsBackToRuleBasedQuestions() {
        EmpVideoInterviewSessionMapper sessionMapper = mock(EmpVideoInterviewSessionMapper.class);
        when(sessionMapper.selectById(1L)).thenReturn(session(1L));
        EmpVideoInterviewQuestionMapper questionMapper = mock(EmpVideoInterviewQuestionMapper.class);
        when(questionMapper.selectList(any())).thenReturn(List.of());
        PostAbilityModelMapper postAbilityModelMapper = mock(PostAbilityModelMapper.class);
        when(postAbilityModelMapper.selectList(any())).thenReturn(List.of(model(11L)));
        InterviewPlanAiService model = mock(InterviewPlanAiService.class);
        when(model.generatePlan(eq(1L), any())).thenThrow(new RuntimeException("LLM 连接失败"));

        InterviewPlanComposer composer = composer(sessionMapper, questionMapper,
                postAbilityModelMapper, mock(AbilityTagMapper.class), mock(AgentGraphContextAssembler.class), model);
        InterviewPlanRequest request = new InterviewPlanRequest(
                1L, 7L, null, 9L, "项目经历\n订单系统：负责 Java 服务和数据库设计",
                "[{\"tagId\":11,\"tagName\":\"Java\",\"level\":3}]", null, null, 6);

        var plan = composer.generateInterviewPlan(request);

        // 降级后仍生成规则题并持久化
        assertThat(plan.questions()).isNotEmpty();
        verify(questionMapper, org.mockito.Mockito.atLeastOnce())
                .insert(any(EmpVideoInterviewQuestion.class));
    }

    @Test
    @DisplayName("AI 返回非法 JSON（空 questions）时降级为规则题")
    void aiReturnsEmptyQuestionsFallsBackToRules() {
        EmpVideoInterviewSessionMapper sessionMapper = mock(EmpVideoInterviewSessionMapper.class);
        when(sessionMapper.selectById(1L)).thenReturn(session(1L));
        EmpVideoInterviewQuestionMapper questionMapper = mock(EmpVideoInterviewQuestionMapper.class);
        when(questionMapper.selectList(any())).thenReturn(List.of());
        PostAbilityModelMapper postAbilityModelMapper = mock(PostAbilityModelMapper.class);
        when(postAbilityModelMapper.selectList(any())).thenReturn(List.of(model(11L)));
        InterviewPlanAiService model = mock(InterviewPlanAiService.class);
        // questions 为空 → convertPlanFromDto 返回 null → 降级
        InterviewPlanDTO dto = new InterviewPlanDTO();
        dto.setQuestions(List.of());
        when(model.generatePlan(eq(1L), any())).thenReturn(dto);

        InterviewPlanComposer composer = composer(sessionMapper, questionMapper,
                postAbilityModelMapper, mock(AbilityTagMapper.class), mock(AgentGraphContextAssembler.class), model);
        InterviewPlanRequest request = new InterviewPlanRequest(
                1L, 7L, null, 9L, "项目经历\n订单系统：负责 Java 服务和数据库设计",
                "[{\"tagId\":11,\"tagName\":\"Java\",\"level\":3}]", null, null, 6);

        var plan = composer.generateInterviewPlan(request);

        assertThat(plan.questions()).isNotEmpty();
    }

    @Test
    @DisplayName("配置题数越界时被规范到 [3,10] 区间")
    void questionCountIsNormalizedIntoAllowedRange() {
        EmpVideoInterviewSessionMapper sessionMapper = mock(EmpVideoInterviewSessionMapper.class);
        when(sessionMapper.selectById(1L)).thenReturn(session(1L));
        EmpVideoInterviewQuestionMapper questionMapper = mock(EmpVideoInterviewQuestionMapper.class);
        when(questionMapper.selectList(any())).thenReturn(List.of());
        PostAbilityModelMapper postAbilityModelMapper = mock(PostAbilityModelMapper.class);
        when(postAbilityModelMapper.selectList(any())).thenReturn(List.of(model(11L)));
        InterviewPlanComposer composer = composer(sessionMapper, questionMapper,
                postAbilityModelMapper, mock(AbilityTagMapper.class), mock(AgentGraphContextAssembler.class), null);
        // 请求 100 题 → 上限 10；无 AI 服务直接走规则兜底
        StringBuilder claims = new StringBuilder("[");
        for (long tagId = 1; tagId <= 20; tagId++) {
            if (tagId > 1) claims.append(',');
            claims.append("{\"tagId\":").append(tagId).append(",\"tagName\":\"能力").append(tagId)
                    .append("\",\"level\":3}");
        }
        claims.append(']');
        InterviewPlanRequest request = new InterviewPlanRequest(
                1L, 7L, null, 9L, "项目经历\n订单系统：负责服务设计和交付",
                claims.toString(), null, null, 100);

        var plan = composer.generateInterviewPlan(request);

        assertThat(plan.questions()).hasSizeLessThanOrEqualTo(10);
        assertThat(plan.questions()).isNotEmpty();
    }

    @Test
    @DisplayName("会话不存在时抛 BusinessException 404")
    void missingSessionThrowsBusinessException() {
        EmpVideoInterviewSessionMapper sessionMapper = mock(EmpVideoInterviewSessionMapper.class);
        when(sessionMapper.selectById(1L)).thenReturn(null);
        InterviewPlanComposer composer = composer(sessionMapper, mock(EmpVideoInterviewQuestionMapper.class),
                mock(PostAbilityModelMapper.class), mock(AbilityTagMapper.class),
                mock(AgentGraphContextAssembler.class), null);
        InterviewPlanRequest request = new InterviewPlanRequest(
                1L, 7L, null, 9L, "简历", "[]", null, null, 6);

        org.assertj.core.api.Assertions.assertThatThrownBy(() -> composer.generateInterviewPlan(request))
                .isInstanceOf(com.example.matching.common.exception.BusinessException.class)
                .hasMessageContaining("Interview session not found");
    }

    @Test
    @DisplayName("会话已生成题目时抛 PlanAlreadyExistsException")
    void existingQuestionsThrowsPlanAlreadyExists() {
        EmpVideoInterviewSessionMapper sessionMapper = mock(EmpVideoInterviewSessionMapper.class);
        EmpVideoInterviewSession existing = session(1L);
        existing.setStatus(1);
        when(sessionMapper.selectById(1L)).thenReturn(existing);
        EmpVideoInterviewQuestionMapper questionMapper = mock(EmpVideoInterviewQuestionMapper.class);
        EmpVideoInterviewQuestion persisted = new EmpVideoInterviewQuestion();
        persisted.setId(50L);
        persisted.setSessionId(1L);
        persisted.setQuestionOrder(1);
        persisted.setQuestionText("已存在的题目");
        persisted.setExpectedTagsJson("[11]");
        when(questionMapper.selectList(any())).thenReturn(List.of(persisted));
        InterviewPlanComposer composer = composer(sessionMapper, questionMapper,
                mock(PostAbilityModelMapper.class), mock(AbilityTagMapper.class),
                mock(AgentGraphContextAssembler.class), null);
        InterviewPlanRequest request = new InterviewPlanRequest(
                1L, 7L, null, 9L, "简历", "[]", null, null, 6);

        var ex = org.assertj.core.api.Assertions.catchThrowable(() -> composer.generateInterviewPlan(request));
        assertThat(ex).isInstanceOf(InterviewPlanComposer.PlanAlreadyExistsException.class);
        assertThat(((InterviewPlanComposer.PlanAlreadyExistsException) ex).existingPlan.questions()).hasSize(1);
    }

    @Test
    @DisplayName("简历无可核验能力声明时规则计划为空且不报错")
    void noResumeClaimsProducesEmptyRulePlan() {
        EmpVideoInterviewSessionMapper sessionMapper = mock(EmpVideoInterviewSessionMapper.class);
        when(sessionMapper.selectById(1L)).thenReturn(session(1L));
        EmpVideoInterviewQuestionMapper questionMapper = mock(EmpVideoInterviewQuestionMapper.class);
        when(questionMapper.selectList(any())).thenReturn(List.of());
        PostAbilityModelMapper postAbilityModelMapper = mock(PostAbilityModelMapper.class);
        when(postAbilityModelMapper.selectList(any())).thenReturn(List.of(model(11L)));
        InterviewPlanComposer composer = composer(sessionMapper, questionMapper,
                postAbilityModelMapper, mock(AbilityTagMapper.class), mock(AgentGraphContextAssembler.class), null);
        // 空声明 + 无 AI 服务 → 规则题无主题目
        InterviewPlanRequest request = new InterviewPlanRequest(
                1L, 7L, null, 9L, "项目经历\n订单系统：负责服务设计", "[]", null, null, 6);

        var plan = composer.generateInterviewPlan(request);

        assertThat(plan.questions()).isEmpty();
    }

    @Test
    @DisplayName("AI 题目 projectAnchor 不可回溯时拒绝该题并降级")
    void aiQuestionWithNonTraceableAnchorIsRejected() {
        EmpVideoInterviewSessionMapper sessionMapper = mock(EmpVideoInterviewSessionMapper.class);
        when(sessionMapper.selectById(1L)).thenReturn(session(1L));
        EmpVideoInterviewQuestionMapper questionMapper = mock(EmpVideoInterviewQuestionMapper.class);
        when(questionMapper.selectList(any())).thenReturn(List.of());
        PostAbilityModelMapper postAbilityModelMapper = mock(PostAbilityModelMapper.class);
        when(postAbilityModelMapper.selectList(any())).thenReturn(List.of(model(11L)));
        InterviewPlanAiService model = mock(InterviewPlanAiService.class);
        InterviewPlanDTO dto = new InterviewPlanDTO();
        InterviewPlanDTO.Question q = new InterviewPlanDTO.Question();
        q.setText("订单系统中，你负责的 Java 服务如何设计？");
        q.setType("VERIFICATION");
        q.setDifficulty("MEDIUM");
        q.setExpectedTagIds(List.of(11L));
        q.setProjectAnchor("不存在的项目");
        dto.setQuestions(List.of(q));
        when(model.generatePlan(eq(1L), any())).thenReturn(dto);

        InterviewPlanComposer composer = composer(sessionMapper, questionMapper,
                postAbilityModelMapper, mock(AbilityTagMapper.class), mock(AgentGraphContextAssembler.class), model);
        InterviewPlanRequest request = new InterviewPlanRequest(
                1L, 7L, null, 9L, "项目经历\n订单系统：负责 Java 服务和数据库设计",
                "[{\"tagId\":11,\"tagName\":\"Java\",\"level\":3}]", null, null, 6);

        var plan = composer.generateInterviewPlan(request);

        // 拒绝 AI 计划 → 规则题兜底
        assertThat(plan.questions()).isNotEmpty();
        assertThat(plan.questions()).noneSatisfy(question ->
                assertThat(question.text()).contains("不存在的项目"));
    }

    @Test
    @DisplayName("工作流会话缺 scope 时抛 BusinessException（无法核验能力标签）")
    void workflowSessionWithoutScopeThrows() {
        EmpVideoInterviewSessionMapper sessionMapper = mock(EmpVideoInterviewSessionMapper.class);
        EmpVideoInterviewSession workflowSession = session(1L);
        workflowSession.setWorkflowId(88L);
        when(sessionMapper.selectById(1L)).thenReturn(workflowSession);
        EmpVideoInterviewQuestionMapper questionMapper = mock(EmpVideoInterviewQuestionMapper.class);
        when(questionMapper.selectList(any())).thenReturn(List.of());
        PostAbilityModelMapper postAbilityModelMapper = mock(PostAbilityModelMapper.class);
        when(postAbilityModelMapper.selectList(any())).thenReturn(List.of());
        InterviewPlanComposer composer = composer(sessionMapper, questionMapper,
                postAbilityModelMapper, mock(AbilityTagMapper.class), mock(AgentGraphContextAssembler.class), null);
        InterviewPlanRequest request = new InterviewPlanRequest(
                1L, 7L, null, 9L, "简历", "[]", null, null, 6);

        org.assertj.core.api.Assertions.assertThatThrownBy(() -> composer.generateInterviewPlan(request))
                .isInstanceOf(com.example.matching.common.exception.BusinessException.class);
    }
}
