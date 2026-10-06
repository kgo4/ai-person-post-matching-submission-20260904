package com.example.matching.service.learning.impl;

import com.example.matching.agent.dto.LearningPathAgentRequest;
import com.example.matching.agent.dto.LearningPathAgentResult;
import com.example.matching.agent.service.LearningPathAgentService;
import com.example.matching.common.exception.BusinessException;
import com.example.matching.dto.learning.LearningPathGenerateRequest;
import com.example.matching.dto.learning.LearningPathPlanVO;
import com.example.matching.dto.learning.LearningPathStepVO;
import com.example.matching.entity.learning.LearningAssessmentItem;
import com.example.matching.entity.learning.LearningPathPlan;
import com.example.matching.entity.learning.LearningPathStep;
import com.example.matching.entity.learning.LearningProgressLog;
import com.example.matching.entity.learning.LearningProjectSubmission;
import com.example.matching.entity.learning.LearningProjectTask;
import com.example.matching.entity.learning.LearningResource;
import com.example.matching.mapper.learning.LearningAssessmentItemMapper;
import com.example.matching.mapper.learning.LearningPathPlanMapper;
import com.example.matching.mapper.learning.LearningPathStepMapper;
import com.example.matching.mapper.learning.LearningProjectSubmissionMapper;
import com.example.matching.mapper.learning.LearningProjectTaskMapper;
import com.example.matching.mapper.learning.LearningProgressLogMapper;
import com.example.matching.mapper.learning.LearningResourceMapper;
import com.example.matching.port.matching.MatchingQueryPort;
import com.example.matching.port.post.PostQueryPort;
import com.example.matching.port.tag.TagQueryPort;
import com.example.matching.port.talent.TalentQueryPort;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.*;

/**
 * {@link LearningPathPlanServiceImpl} 单元测试。
 *
 * <p>覆盖两条生成链路（确定性规则 / AI 增强 + 降级）、资源回填、步骤状态流转
 * 与计划 VO 组装等分支。
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class LearningPathPlanServiceImplTest {

    @Mock private LearningPathPlanMapper planMapper;
    @Mock private LearningPathStepMapper stepMapper;
    @Mock private LearningProjectTaskMapper projectTaskMapper;
    @Mock private LearningProjectSubmissionMapper submissionMapper;
    @Mock private LearningProgressLogMapper progressLogMapper;
    @Mock private MatchingQueryPort matchingQueryPort;
    @Mock private PostQueryPort postQueryPort;
    @Mock private TalentQueryPort talentQueryPort;
    @Mock private TagQueryPort tagQueryPort;
    @Mock private LearningPathAgentService learningPathAgentService;
    @Mock private LearningAssessmentItemMapper assessmentItemMapper;
    @Mock private LearningResourceMapper resourceMapper;

    @InjectMocks
    private LearningPathPlanServiceImpl service;

    private MatchingQueryPort.MatchingRecordDTO record;

    /**
     * MyBatis-Plus 的 LambdaQueryWrapper/UpdateWrapper 需要实体已登记表信息，
     * 否则会抛 "can not find lambda cache for this entity"（纯单测无 Spring 上下文）。
     */
    @BeforeAll
    static void initMybatisPlusLambdaCache() {
        com.baomidou.mybatisplus.core.MybatisConfiguration cfg =
                new com.baomidou.mybatisplus.core.MybatisConfiguration();
        com.baomidou.mybatisplus.core.metadata.TableInfoHelper.initTableInfo(
                new org.apache.ibatis.builder.MapperBuilderAssistant(cfg, ""), LearningPathPlan.class);
        com.baomidou.mybatisplus.core.metadata.TableInfoHelper.initTableInfo(
                new org.apache.ibatis.builder.MapperBuilderAssistant(cfg, ""), LearningPathStep.class);
        com.baomidou.mybatisplus.core.metadata.TableInfoHelper.initTableInfo(
                new org.apache.ibatis.builder.MapperBuilderAssistant(cfg, ""), LearningProjectTask.class);
        com.baomidou.mybatisplus.core.metadata.TableInfoHelper.initTableInfo(
                new org.apache.ibatis.builder.MapperBuilderAssistant(cfg, ""), LearningProjectSubmission.class);
        com.baomidou.mybatisplus.core.metadata.TableInfoHelper.initTableInfo(
                new org.apache.ibatis.builder.MapperBuilderAssistant(cfg, ""), LearningResource.class);
    }

    @BeforeEach
    void setUp() {
        record = new MatchingQueryPort.MatchingRecordDTO(
                10L, 100L, 200L, "Java后端工程师",
                new BigDecimal("70.00"), new BigDecimal("68.00"),
                null, null, null, null, "v1", 1, 1, null, LocalDateTime.now());
        // insert 时回填主键，便于后续 getPlan
        doAnswer(inv -> {
            LearningPathPlan p = inv.getArgument(0);
            if (p.getId() == null) p.setId(1L);
            return 1;
        }).when(planMapper).insert(any(LearningPathPlan.class));
    }

    private LearningPathGenerateRequest req(boolean useAi, boolean tasks) {
        LearningPathGenerateRequest r = new LearningPathGenerateRequest();
        r.setMatchingRecordId(10L);
        r.setUseAi(useAi);
        r.setIncludeProjectTasks(tasks);
        return r;
    }

    /** 组装一个可用的 plan + steps，供 getPlan/assemblePlanVO 使用 */
    private void stubExistingPlan(Long planId, List<LearningPathStep> steps) {
        LearningPathPlan plan = new LearningPathPlan();
        plan.setId(planId);
        plan.setEmpId(100L);
        plan.setPostId(200L);
        plan.setMatchingRecordId(10L);
        plan.setPlanTitle("人岗匹配学习路径 - Java后端工程师");
        plan.setPlanStatus("ACTIVE");
        plan.setIsDeleted(0);
        when(planMapper.selectById(planId)).thenReturn(plan);
        when(stepMapper.selectList(any())).thenReturn(steps);
        when(projectTaskMapper.selectList(any())).thenReturn(Collections.emptyList());
        when(submissionMapper.selectCount(any())).thenReturn(0L);
    }

    // ==================== 确定性规则模式 ====================

    @Test
    @DisplayName("规则模式：已有计划且未强制重生成 → 直接返回已有计划")
    void generate_returnsExistingPlan() {
        LearningPathGenerateRequest r = req(false, false);
        // 未强制重生成时先走 getByMatchingRecord（selectOne），命中即短路返回
        LearningPathPlan existing = new LearningPathPlan();
        existing.setId(1L);
        existing.setIsDeleted(0);
        existing.setMatchingRecordId(10L);
        when(planMapper.selectOne(any())).thenReturn(existing);
        when(stepMapper.selectList(any())).thenReturn(Collections.emptyList());
        when(projectTaskMapper.selectList(any())).thenReturn(Collections.emptyList());
        when(submissionMapper.selectCount(any())).thenReturn(0L);

        LearningPathPlanVO vo = service.generateFromMatchingRecord(r);

        assertNotNull(vo);
        assertEquals(1L, vo.getId());
        verify(planMapper, never()).insert(any(LearningPathPlan.class));
    }

    @Test
    @DisplayName("规则模式：强制重生成 → 重新计算并插入步骤")
    void generate_forceRegenerate_deterministic() {
        LearningPathGenerateRequest r = req(false, true);
        r.setForceRegenerate(true);
        r.setTargetScore(new BigDecimal("88.00"));
        when(matchingQueryPort.getById(10L)).thenReturn(record);
        when(postQueryPort.listRequirementsByPostId(200L)).thenReturn(List.of(
                new PostQueryPort.PostAbilityDTO(1L, 200L, 5L, 4, new BigDecimal("1.0"), 1, 1, "v1", null, "Java", null, null),
                new PostQueryPort.PostAbilityDTO(2L, 200L, 6L, 3, new BigDecimal("0.5"), 0, 0, "v1", null, "MySQL", null, null)));
        when(talentQueryPort.listAbilitiesByEmpId(100L)).thenReturn(List.of(
                new TalentQueryPort.EmployeeAbilityDTO(1L, 100L, 5L, 2, "AI_TEST", null, null, null, "Java")));
        when(tagQueryPort.batchGetTags(anyList())).thenReturn(List.of(
                new TagQueryPort.TagDTO(5L, "Java", "java", "SKILL", null, 3, null, null, "SYS", "Java语言", null, null),
                new TagQueryPort.TagDTO(6L, "MySQL", "mysql", "SKILL", null, 3, null, null, "SYS", "数据库", null, null)));
        when(resourceMapper.selectList(any())).thenReturn(Collections.emptyList());
        stubExistingPlan(1L, Collections.emptyList());

        LearningPathPlanVO vo = service.generateFromMatchingRecord(r);

        assertNotNull(vo);
        verify(planMapper).insert(any(LearningPathPlan.class));
        verify(stepMapper, atLeastOnce()).insert(any(LearningPathStep.class));
        verify(projectTaskMapper, atLeastOnce()).insert(any(LearningProjectTask.class));
        verify(progressLogMapper).insert(any(LearningProgressLog.class));
    }

    @Test
    @DisplayName("规则模式：目标分未传 → 当前分 +15 且不超过 100")
    void generate_targetScoreComputed() {
        LearningPathGenerateRequest r = req(false, false);
        r.setForceRegenerate(true);
        MatchingQueryPort.MatchingRecordDTO high = new MatchingQueryPort.MatchingRecordDTO(
                10L, 100L, 200L, "岗位A", new BigDecimal("95.00"), null,
                null, null, null, null, "v1", 1, 1, null, LocalDateTime.now());
        when(matchingQueryPort.getById(10L)).thenReturn(high);
        when(postQueryPort.listRequirementsByPostId(200L)).thenReturn(List.of(
                new PostQueryPort.PostAbilityDTO(1L, 200L, 5L, 4, BigDecimal.ONE, 1, 1, "v1", null, "Redis", null, null)));
        when(talentQueryPort.listAbilitiesByEmpId(100L)).thenReturn(Collections.emptyList());
        when(resourceMapper.selectList(any())).thenReturn(Collections.emptyList());
        stubExistingPlan(1L, Collections.emptyList());

        service.generateFromMatchingRecord(r);

        ArgumentCaptor<LearningPathPlan> cap = ArgumentCaptor.forClass(LearningPathPlan.class);
        verify(planMapper, atLeastOnce()).insert(cap.capture());
        assertEquals(new BigDecimal("100"), cap.getValue().getTargetScore());
    }

    @Test
    @DisplayName("规则模式：匹配记录不存在 → 抛 BusinessException")
    void generate_recordNotFound() {
        LearningPathGenerateRequest r = req(false, false);
        r.setForceRegenerate(true);
        when(matchingQueryPort.getById(10L)).thenReturn(null);

        assertThrows(BusinessException.class, () -> service.generateFromMatchingRecord(r));
    }

    @Test
    @DisplayName("规则模式：岗位能力模型为空 → 抛 BusinessException")
    void generate_emptyPostRequirements() {
        LearningPathGenerateRequest r = req(false, false);
        r.setForceRegenerate(true);
        when(matchingQueryPort.getById(10L)).thenReturn(record);
        when(postQueryPort.listRequirementsByPostId(200L)).thenReturn(Collections.emptyList());

        assertThrows(BusinessException.class, () -> service.generateFromMatchingRecord(r));
    }

    @Test
    @DisplayName("规则模式：无能力差距且非核心 → 抛「已满足要求」")
    void generate_noGap() {
        LearningPathGenerateRequest r = req(false, false);
        r.setForceRegenerate(true);
        when(matchingQueryPort.getById(10L)).thenReturn(record);
        when(postQueryPort.listRequirementsByPostId(200L)).thenReturn(List.of(
                new PostQueryPort.PostAbilityDTO(1L, 200L, 5L, 3, BigDecimal.ONE, 1, 0, "v1", null, "Java", null, null)));
        when(talentQueryPort.listAbilitiesByEmpId(100L)).thenReturn(List.of(
                new TalentQueryPort.EmployeeAbilityDTO(1L, 100L, 5L, 4, "AI_TEST", null, null, null, "Java")));
        when(resourceMapper.selectList(any())).thenReturn(Collections.emptyList());

        BusinessException ex = assertThrows(BusinessException.class, () -> service.generateFromMatchingRecord(r));
        assertTrue(String.valueOf(ex.getMessage()).contains("已满足"));
    }

    // ==================== AI 增强模式 ====================

    @Test
    @DisplayName("AI 模式：Agent 返回有效步骤 → 落库步骤/任务/评估题")
    void generate_aiMode_success() {
        LearningPathGenerateRequest r = req(true, true);
        r.setForceRegenerate(true);
        when(matchingQueryPort.getById(10L)).thenReturn(record);
        when(resourceMapper.selectList(any())).thenReturn(Collections.emptyList());

        LearningPathAgentResult agent = new LearningPathAgentResult();
        agent.setSummary("AI 生成的路径摘要");
        agent.setFallbackUsed(false);
        LearningPathAgentResult.LearningStepSuggestion s1 = new LearningPathAgentResult.LearningStepSuggestion();
        s1.setAbilityName("Kafka"); s1.setCurrentLevel(0); s1.setTargetLevel(3); s1.setPriority("HIGH");
        agent.setSteps(new ArrayList<>(List.of(s1)));
        LearningPathAgentResult.ProjectTaskSuggestion pt = new LearningPathAgentResult.ProjectTaskSuggestion();
        pt.setTitle("Kafka 实践"); pt.setDifficulty("HARD");
        agent.setProjectTasks(new ArrayList<>(List.of(pt)));
        LearningPathAgentResult.AssessmentSuggestion as = new LearningPathAgentResult.AssessmentSuggestion();
        as.setAbilityTagId(5L); as.setQuestionText("Kafka 分区原理？");
        agent.setAssessments(new ArrayList<>(List.of(as)));
        when(learningPathAgentService.preview(any(LearningPathAgentRequest.class))).thenReturn(agent);

        doAnswer(inv -> {
            LearningPathStep st = inv.getArgument(0);
            if (st.getId() == null) st.setId(50L);
            return 1;
        }).when(stepMapper).insert(any(LearningPathStep.class));
        stubExistingPlan(1L, Collections.emptyList());

        LearningPathPlanVO vo = service.generateFromMatchingRecord(r);

        assertNotNull(vo);
        verify(assessmentItemMapper, atLeastOnce()).insert(any(LearningAssessmentItem.class));
        verify(projectTaskMapper, atLeastOnce()).insert(any(LearningProjectTask.class));
        ArgumentCaptor<LearningPathPlan> cap = ArgumentCaptor.forClass(LearningPathPlan.class);
        verify(planMapper, atLeastOnce()).updateById(cap.capture());
        assertEquals("AI 生成的路径摘要", cap.getValue().getAiSummary());
    }

    @Test
    @DisplayName("AI 模式：Agent 无步骤 → 降级为规则计划")
    void generate_aiMode_fallsBackToRules() {
        LearningPathGenerateRequest r = req(true, false);
        r.setForceRegenerate(true);
        when(matchingQueryPort.getById(10L)).thenReturn(record);
        when(postQueryPort.listRequirementsByPostId(200L)).thenReturn(List.of(
                new PostQueryPort.PostAbilityDTO(1L, 200L, 5L, 4, BigDecimal.ONE, 1, 1, "v1", null, "Java", null, null)));
        when(talentQueryPort.listAbilitiesByEmpId(100L)).thenReturn(Collections.emptyList());
        when(resourceMapper.selectList(any())).thenReturn(Collections.emptyList());

        LearningPathAgentResult agent = new LearningPathAgentResult();
        agent.setSteps(new ArrayList<>());
        agent.setProjectTasks(new ArrayList<>());
        agent.setAssessments(new ArrayList<>());
        agent.setSummary("空结果");
        when(learningPathAgentService.preview(any())).thenReturn(agent);
        stubExistingPlan(1L, Collections.emptyList());

        LearningPathPlanVO vo = service.generateFromMatchingRecord(r);

        assertNotNull(vo);
        ArgumentCaptor<LearningPathPlan> cap = ArgumentCaptor.forClass(LearningPathPlan.class);
        verify(planMapper, atLeastOnce()).updateById(cap.capture());
        assertEquals(0, cap.getValue().getGeneratedByAi());
        assertTrue(cap.getValue().getAiSummary().contains("规则降级"));
    }

    @Test
    @DisplayName("AI 模式：Agent 抛异常 → 空结果降级")
    void generate_aiMode_agentThrows() {
        LearningPathGenerateRequest r = req(true, false);
        r.setForceRegenerate(true);
        when(matchingQueryPort.getById(10L)).thenReturn(record);
        when(postQueryPort.listRequirementsByPostId(200L)).thenReturn(List.of(
                new PostQueryPort.PostAbilityDTO(1L, 200L, 5L, 4, BigDecimal.ONE, 1, 1, "v1", null, "Java", null, null)));
        when(talentQueryPort.listAbilitiesByEmpId(100L)).thenReturn(Collections.emptyList());
        when(resourceMapper.selectList(any())).thenReturn(Collections.emptyList());
        when(learningPathAgentService.preview(any())).thenThrow(new RuntimeException("LLM down"));
        stubExistingPlan(1L, Collections.emptyList());

        LearningPathPlanVO vo = service.generateFromMatchingRecord(r);

        assertNotNull(vo);
        verify(planMapper, atLeastOnce()).updateById(any(LearningPathPlan.class));
    }

    @Test
    @DisplayName("AI 模式：Agent 回退标记 → 摘要加「降级方案」前缀")
    void generate_aiMode_fallbackPrefix() {
        LearningPathGenerateRequest r = req(true, false);
        r.setForceRegenerate(true);
        when(matchingQueryPort.getById(10L)).thenReturn(record);
        when(resourceMapper.selectList(any())).thenReturn(Collections.emptyList());

        LearningPathAgentResult agent = new LearningPathAgentResult();
        agent.setSummary("部分降级说明");
        agent.setFallbackUsed(true);
        LearningPathAgentResult.LearningStepSuggestion s1 = new LearningPathAgentResult.LearningStepSuggestion();
        s1.setAbilityName("Docker"); s1.setCurrentLevel(1); s1.setTargetLevel(3);
        agent.setSteps(new ArrayList<>(List.of(s1)));
        when(learningPathAgentService.preview(any())).thenReturn(agent);
        stubExistingPlan(1L, Collections.emptyList());

        service.generateFromMatchingRecord(r);

        ArgumentCaptor<LearningPathPlan> cap = ArgumentCaptor.forClass(LearningPathPlan.class);
        verify(planMapper, atLeastOnce()).updateById(cap.capture());
        assertTrue(cap.getValue().getAiSummary().startsWith("[降级方案]"));
    }

    @Test
    @DisplayName("AI 模式：AI 返回的 resourceId 不在候选中 → 回退服务端匹配资源")
    void generate_aiMode_resourceIdFallback() {
        LearningPathGenerateRequest r = req(true, false);
        r.setForceRegenerate(true);
        when(matchingQueryPort.getById(10L)).thenReturn(record);

        LearningResource res = new LearningResource();
        res.setId(900L); res.setAbilityName("Redis"); res.setTitle("Redis实战");
        res.setResourceType("COURSE"); res.setStatus(1); res.setDifficultyLevel(3);
        when(resourceMapper.selectList(any())).thenReturn(List.of(res));

        LearningPathAgentResult agent = new LearningPathAgentResult();
        agent.setSummary("s");
        LearningPathAgentResult.LearningStepSuggestion s1 = new LearningPathAgentResult.LearningStepSuggestion();
        s1.setAbilityName("Redis"); s1.setCurrentLevel(0); s1.setTargetLevel(3);
        s1.setResourceId(7777L); // 不存在
        agent.setSteps(new ArrayList<>(List.of(s1)));
        when(learningPathAgentService.preview(any())).thenReturn(agent);
        stubExistingPlan(1L, Collections.emptyList());

        service.generateFromMatchingRecord(r);

        ArgumentCaptor<LearningPathStep> cap = ArgumentCaptor.forClass(LearningPathStep.class);
        verify(stepMapper, atLeastOnce()).insert(cap.capture());
        assertEquals(900L, cap.getValue().getResourceId());
    }

    // ==================== 查询 / 状态流转 / 资源回填 ====================

    @Test
    @DisplayName("getPlan：不存在或已删除 → 抛 BusinessException")
    void getPlan_notFound() {
        when(planMapper.selectById(99L)).thenReturn(null);
        assertThrows(BusinessException.class, () -> service.getPlan(99L));

        LearningPathPlan deleted = new LearningPathPlan();
        deleted.setId(98L);
        deleted.setIsDeleted(1);
        when(planMapper.selectById(98L)).thenReturn(deleted);
        assertThrows(BusinessException.class, () -> service.getPlan(98L));
    }

    @Test
    @DisplayName("getByMatchingRecord：找不到 → 返回 null；找到 → 组装 VO")
    void getByMatchingRecord_bothPaths() {
        when(planMapper.selectOne(any())).thenReturn(null);
        assertNull(service.getByMatchingRecord(10L));

        LearningPathPlan plan = new LearningPathPlan();
        plan.setId(1L); plan.setIsDeleted(0); plan.setMatchingRecordId(10L);
        when(planMapper.selectOne(any())).thenReturn(plan);
        when(stepMapper.selectList(any())).thenReturn(Collections.emptyList());
        when(projectTaskMapper.selectList(any())).thenReturn(Collections.emptyList());
        when(submissionMapper.selectCount(any())).thenReturn(2L);

        LearningPathPlanVO vo = service.getByMatchingRecord(10L);
        assertNotNull(vo);
        assertEquals(2, vo.getPendingSubmissionCount());
    }

    @Test
    @DisplayName("pagePlans：带全部过滤条件分页")
    @SuppressWarnings("unchecked")
    void pagePlans_withFilters() {
        Page<LearningPathPlan> page = new Page<>(1, 10);
        LearningPathPlan plan = new LearningPathPlan();
        plan.setId(1L); plan.setIsDeleted(0);
        Page<LearningPathPlan> result = new Page<>(1, 10);
        result.setRecords(List.of(plan));
        when(planMapper.selectPage(any(), any())).thenReturn(result);
        when(stepMapper.selectList(any())).thenReturn(Collections.emptyList());
        when(projectTaskMapper.selectList(any())).thenReturn(Collections.emptyList());
        when(submissionMapper.selectCount(any())).thenReturn(0L);

        IPage<LearningPathPlanVO> out = service.pagePlans(page, 100L, 200L, "ACTIVE");

        assertNotNull(out);
        assertEquals(1, out.getRecords().size());
    }

    @Test
    @DisplayName("updateStepStatus：步骤不存在 → 抛异常")
    void updateStepStatus_notFound() {
        when(stepMapper.selectById(5L)).thenReturn(null);
        assertThrows(BusinessException.class, () -> service.updateStepStatus(5L, "COMPLETED"));
    }

    @Test
    @DisplayName("updateStepStatus：全部完成后计划置 COMPLETED")
    void updateStepStatus_completesPlan() {
        LearningPathStep step = new LearningPathStep();
        step.setId(5L); step.setPlanId(1L); step.setIsDeleted(0);
        when(stepMapper.selectById(5L)).thenReturn(step);
        LearningPathPlan plan = new LearningPathPlan();
        plan.setId(1L); plan.setEmpId(100L); plan.setIsDeleted(0);
        when(planMapper.selectById(1L)).thenReturn(plan);
        when(stepMapper.selectCount(any())).thenReturn(0L);

        service.updateStepStatus(5L, "COMPLETED");

        assertEquals("COMPLETED", plan.getPlanStatus());
        verify(planMapper).updateById(plan);
    }

    @Test
    @DisplayName("refreshResourceBindings：无步骤 → 返回 0")
    void refreshResourceBindings_empty() {
        when(stepMapper.selectList(any())).thenReturn(Collections.emptyList());
        assertEquals(0, service.refreshResourceBindings(1L));
    }

    @Test
    @DisplayName("refreshResourceBindings：有匹配资源 → 回填；无资源 → 清空")
    void refreshResourceBindings_updates() {
        LearningPathStep withRes = new LearningPathStep();
        withRes.setId(1L); withRes.setPlanId(1L); withRes.setAbilityName("Java");
        withRes.setCurrentLevel(1); withRes.setTargetLevel(3); withRes.setIsDeleted(0);
        LearningPathStep noRes = new LearningPathStep();
        noRes.setId(2L); noRes.setPlanId(1L); noRes.setAbilityName("UnknownAbility");
        noRes.setCurrentLevel(0); noRes.setTargetLevel(3); noRes.setIsDeleted(0);
        when(stepMapper.selectList(any())).thenReturn(List.of(withRes, noRes));

        LearningResource r1 = new LearningResource();
        r1.setId(11L); r1.setAbilityName("Java"); r1.setTitle("Java课程");
        r1.setUrl("http://x"); r1.setResourceType("COURSE"); r1.setStatus(1); r1.setDifficultyLevel(3);
        when(resourceMapper.selectList(any())).thenReturn(List.of(r1));

        int n = service.refreshResourceBindings(1L);

        // 服务内部用 LambdaUpdateWrapper 提交更新（不修改传入实体），因此断言 update 被调了两次
        assertEquals(2, n);
        verify(stepMapper, times(2)).update(isNull(), any());
    }

    @Test
    @DisplayName("refreshAllResourceBindings：按 planId 汇总更新数")
    void refreshAllResourceBindings_iterates() {
        LearningPathStep p1 = new LearningPathStep();
        p1.setPlanId(1L);
        LearningPathStep p2 = new LearningPathStep();
        p2.setPlanId(2L);
        // 第一次 selectList 返回两个待处理的 planId；refreshResourceBindings 内部的 selectList 返回空 → 计 0
        when(stepMapper.selectList(any())).thenReturn(List.of(p1, p2), Collections.emptyList());

        int total = service.refreshAllResourceBindings();

        assertEquals(0, total);
    }

    @Test
    @DisplayName("refreshAllResourceBindings：内部回填有更新时累加")
    void refreshAllResourceBindings_accumulates() {
        LearningPathStep p1 = new LearningPathStep();
        p1.setPlanId(1L);

        LearningPathStep inner = new LearningPathStep();
        inner.setId(9L); inner.setPlanId(1L); inner.setAbilityName("Java");
        inner.setCurrentLevel(1); inner.setTargetLevel(3); inner.setIsDeleted(0);

        LearningResource res = new LearningResource();
        res.setId(11L); res.setAbilityName("Java"); res.setTitle("Java课程");
        res.setResourceType("COURSE"); res.setStatus(1); res.setDifficultyLevel(3);

        // 外层 selectList → 一个 planId；内层 selectList → 一个步骤
        when(stepMapper.selectList(any())).thenReturn(List.of(p1), List.of(inner));
        when(resourceMapper.selectList(any())).thenReturn(List.of(res));

        int total = service.refreshAllResourceBindings();

        assertEquals(1, total);
    }

    @Test
    @DisplayName("assemblePlanVO：统计已完成步骤数")
    void assemblePlanVO_countsCompleted() {
        LearningPathStep s1 = new LearningPathStep();
        s1.setId(1L); s1.setPlanId(1L); s1.setStatus("COMPLETED"); s1.setIsDeleted(0); s1.setSortOrder(0);
        LearningPathStep s2 = new LearningPathStep();
        s2.setId(2L); s2.setPlanId(1L); s2.setStatus("PENDING"); s2.setIsDeleted(0); s2.setSortOrder(1);

        LearningPathPlan plan = new LearningPathPlan();
        plan.setId(1L); plan.setIsDeleted(0);
        when(planMapper.selectById(1L)).thenReturn(plan);
        when(stepMapper.selectList(any())).thenReturn(List.of(s1, s2));
        when(projectTaskMapper.selectList(any())).thenReturn(Collections.emptyList());
        when(submissionMapper.selectCount(any())).thenReturn(0L);

        LearningPathPlanVO vo = service.getPlan(1L);

        assertEquals(2, vo.getTotalStepCount());
        assertEquals(1, vo.getCompletedStepCount());
    }

    @Test
    @DisplayName("assembleStepVO：携带项目任务与最新提交状态")
    void assembleStepVO_withTasksAndSubmission() {
        LearningPathStep s1 = new LearningPathStep();
        s1.setId(1L); s1.setPlanId(1L); s1.setStatus("PENDING"); s1.setIsDeleted(0); s1.setSortOrder(0);
        LearningPathPlan plan = new LearningPathPlan();
        plan.setId(1L); plan.setIsDeleted(0);
        when(planMapper.selectById(1L)).thenReturn(plan);
        when(stepMapper.selectList(any())).thenReturn(List.of(s1));

        LearningProjectTask task = new LearningProjectTask();
        task.setId(7L); task.setPlanId(1L); task.setStepId(1L); task.setIsDeleted(0);
        when(projectTaskMapper.selectList(any())).thenReturn(List.of(task));
        LearningProjectSubmission sub = new LearningProjectSubmission();
        sub.setId(77L); sub.setReviewStatus("PENDING");
        when(submissionMapper.selectOne(any())).thenReturn(sub);
        when(submissionMapper.selectCount(any())).thenReturn(1L);

        LearningPathPlanVO vo = service.getPlan(1L);

        assertEquals(1, vo.getProjectTaskCount());
        LearningPathStepVO stepVO = vo.getSteps().get(0);
        assertEquals(1, stepVO.getProjectTasks().size());
        assertEquals(77L, stepVO.getProjectTasks().get(0).getLatestSubmissionId());
        assertEquals("PENDING", stepVO.getProjectTasks().get(0).getLatestSubmissionStatus());
    }

    @Test
    @DisplayName("buildPlanTitle：postName 为空 → 用「岗位#id」")
    void buildPlanTitle_nullPostName() {
        LearningPathGenerateRequest r = req(false, false);
        r.setForceRegenerate(true);
        MatchingQueryPort.MatchingRecordDTO noName = new MatchingQueryPort.MatchingRecordDTO(
                10L, 100L, 200L, null, new BigDecimal("50.00"), null,
                null, null, null, null, "v1", 1, 1, null, LocalDateTime.now());
        when(matchingQueryPort.getById(10L)).thenReturn(noName);
        when(postQueryPort.listRequirementsByPostId(200L)).thenReturn(List.of(
                new PostQueryPort.PostAbilityDTO(1L, 200L, 5L, 4, BigDecimal.ONE, 1, 1, "v1", null, "Go", null, null)));
        when(talentQueryPort.listAbilitiesByEmpId(100L)).thenReturn(Collections.emptyList());
        when(resourceMapper.selectList(any())).thenReturn(Collections.emptyList());
        stubExistingPlan(1L, Collections.emptyList());

        service.generateFromMatchingRecord(r);

        ArgumentCaptor<LearningPathPlan> cap = ArgumentCaptor.forClass(LearningPathPlan.class);
        verify(planMapper, atLeastOnce()).insert(cap.capture());
        assertTrue(cap.getValue().getPlanTitle().contains("岗位#200"));
    }

    @Test
    @DisplayName("规则模式：includeProjectTasks=false 时不生成项目任务")
    void generate_withoutProjectTasks() {
        LearningPathGenerateRequest r = req(false, false);
        r.setForceRegenerate(true);
        when(matchingQueryPort.getById(10L)).thenReturn(record);
        when(postQueryPort.listRequirementsByPostId(200L)).thenReturn(List.of(
                new PostQueryPort.PostAbilityDTO(1L, 200L, 5L, 4, BigDecimal.ONE, 1, 1, "v1", null, "Java", null, null)));
        when(talentQueryPort.listAbilitiesByEmpId(100L)).thenReturn(Collections.emptyList());
        when(resourceMapper.selectList(any())).thenReturn(Collections.emptyList());
        stubExistingPlan(1L, Collections.emptyList());

        service.generateFromMatchingRecord(r);

        verify(projectTaskMapper, never()).insert(any(LearningProjectTask.class));
    }

    @Test
    @DisplayName("规则模式：有匹配资源时步骤绑定主资源")
    void generate_bindsResources() {
        LearningPathGenerateRequest r = req(false, false);
        r.setForceRegenerate(true);
        when(matchingQueryPort.getById(10L)).thenReturn(record);
        when(postQueryPort.listRequirementsByPostId(200L)).thenReturn(List.of(
                new PostQueryPort.PostAbilityDTO(1L, 200L, 5L, 4, BigDecimal.ONE, 1, 1, "v1", null, "Java", null, null)));
        when(talentQueryPort.listAbilitiesByEmpId(100L)).thenReturn(Collections.emptyList());
        LearningResource res = new LearningResource();
        res.setId(321L); res.setAbilityName("Java"); res.setTitle("Java 进阶");
        res.setUrl("http://java"); res.setResourceType("COURSE"); res.setStatus(1); res.setDifficultyLevel(3);
        when(resourceMapper.selectList(any())).thenReturn(List.of(res));
        stubExistingPlan(1L, Collections.emptyList());

        service.generateFromMatchingRecord(r);

        ArgumentCaptor<LearningPathStep> cap = ArgumentCaptor.forClass(LearningPathStep.class);
        verify(stepMapper, atLeastOnce()).insert(cap.capture());
        assertEquals(321L, cap.getValue().getResourceId());
        assertEquals(1, cap.getValue().getResourceCount());
    }
}
