package com.example.matching.service.learning;

import com.example.matching.common.exception.BusinessException;
import com.example.matching.dto.learning.LearningOutcomeContextResponse;
import com.example.matching.dto.learning.LearningPathPlanVO;
import com.example.matching.dto.learning.LearningPathStepVO;
import com.example.matching.entity.employee.EmpEmployee;
import com.example.matching.entity.learning.EmpLearningOutcomeSubmission;
import com.example.matching.entity.learning.LearningAssessmentItem;
import com.example.matching.entity.learning.LearningProjectSubmission;
import com.example.matching.entity.learning.LearningProjectTask;
import com.example.matching.mapper.employee.EmpEmployeeMapper;
import com.example.matching.mapper.learning.EmpLearningOutcomeSubmissionMapper;
import com.example.matching.mapper.learning.LearningProjectSubmissionMapper;
import com.example.matching.mapper.learning.LearningProjectTaskMapper;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.*;

/**
 * {@link LearningOutcomeContextQueryService} 单元测试。
 *
 * <p>只读查询入口，覆盖：参数/存在性校验、计划缺失与查询异常降级、能力步骤匹配
 * （能力名 / tagId / 均未命中）、评估题过滤，以及项目材料排序与标题/查询异常降级。
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class LearningOutcomeContextQueryServiceTest {

    @Mock private EmpLearningOutcomeSubmissionMapper submissionMapper;
    @Mock private EmpEmployeeMapper empEmployeeMapper;
    @Mock private LearningPathPlanService learningPathPlanService;
    @Mock private LearningAssessmentService learningAssessmentService;
    @Mock private LearningProjectSubmissionMapper projectSubmissionMapper;
    @Mock private LearningProjectTaskMapper projectTaskMapper;

    private LearningOutcomeContextQueryService service;

    @BeforeAll
    static void initMpCache() {
        var cfg = new com.baomidou.mybatisplus.core.MybatisConfiguration();
        com.baomidou.mybatisplus.core.metadata.TableInfoHelper.initTableInfo(
                new org.apache.ibatis.builder.MapperBuilderAssistant(cfg, ""), LearningProjectSubmission.class);
    }

    @BeforeEach
    void setUp() {
        service = new LearningOutcomeContextQueryService(
                submissionMapper, empEmployeeMapper, learningPathPlanService,
                learningAssessmentService, projectSubmissionMapper, projectTaskMapper);
    }

    private EmpLearningOutcomeSubmission submission(Long matchingRecordId, String abilityName, Long tagId) {
        EmpLearningOutcomeSubmission s = new EmpLearningOutcomeSubmission();
        s.setId(1L);
        s.setEmpId(100L);
        s.setMatchingRecordId(matchingRecordId);
        s.setAbilityName(abilityName);
        s.setTagId(tagId);
        s.setBeforeLevel(1);
        s.setConfirmedLevel(3);
        return s;
    }

    private LearningPathStepVO step(Long id, String abilityName, Long tagId) {
        LearningPathStepVO v = new LearningPathStepVO();
        v.setId(id);
        v.setAbilityName(abilityName);
        v.setAbilityTagId(tagId);
        v.setCurrentLevel(1);
        v.setTargetLevel(3);
        v.setStatus("IN_PROGRESS");
        v.setEvidenceStatus("PENDING");
        v.setResourceCount(2);
        return v;
    }

    private LearningPathPlanVO plan(Long id, List<LearningPathStepVO> steps) {
        LearningPathPlanVO p = new LearningPathPlanVO();
        p.setId(id);
        p.setPlanTitle("学习计划");
        p.setPlanStatus("ACTIVE");
        p.setPostName("Java工程师");
        p.setTotalStepCount(steps.size());
        p.setCompletedStepCount(0);
        p.setSteps(steps);
        return p;
    }

    // ==================== 参数与存在性校验 ====================

    @Test
    @DisplayName("getContext：submissionId 为 null → 抛 400")
    void getContext_nullId() {
        BusinessException ex = assertThrows(BusinessException.class, () -> service.getContext(null));
        assertEquals(400, ex.getCode());
    }

    @Test
    @DisplayName("getContext：提交单不存在 → 抛 404")
    void getContext_notFound() {
        when(submissionMapper.selectById(1L)).thenReturn(null);
        BusinessException ex = assertThrows(BusinessException.class, () -> service.getContext(1L));
        assertEquals(404, ex.getCode());
    }

    // ==================== 计划解析 ====================

    @Test
    @DisplayName("getContext：无学习计划 → 计划相关字段为 null、steps 为空")
    void getContext_noPlan() {
        when(submissionMapper.selectById(1L)).thenReturn(submission(null, "Kafka", 5L));
        when(empEmployeeMapper.selectById(100L)).thenReturn(employee("张三"));

        LearningOutcomeContextResponse resp = service.getContext(1L);

        assertNull(resp.planId());
        assertNull(resp.postName());
        assertTrue(resp.steps().isEmpty());
        assertNull(resp.appliedStepId());
        assertEquals(1L, resp.submissionId());
        assertEquals("张三", resp.empName());
    }

    @Test
    @DisplayName("getContext：计划查询抛异常 → 按无计划降级")
    void getContext_planQueryThrows() {
        when(submissionMapper.selectById(1L)).thenReturn(submission(10L, "Kafka", 5L));
        when(learningPathPlanService.getByMatchingRecord(10L)).thenThrow(new RuntimeException("db down"));

        LearningOutcomeContextResponse resp = service.getContext(1L);

        assertNull(resp.planId());
        assertTrue(resp.steps().isEmpty());
    }

    @Test
    @DisplayName("getContext：能力名匹配到步骤 → appliedStepId 命中并携带该步评估题")
    void getContext_matchByAbilityName() {
        when(submissionMapper.selectById(1L)).thenReturn(submission(10L, "Kafka", 5L));
        when(learningPathPlanService.getByMatchingRecord(10L))
                .thenReturn(plan(1L, List.of(step(11L, "Kafka", 5L), step(12L, "Redis", 6L))));
        LearningAssessmentItem item = new LearningAssessmentItem();
        item.setId(50L);
        item.setStepId(11L);
        item.setQuestionType("TECHNICAL");
        item.setScore(90);
        LearningAssessmentItem other = new LearningAssessmentItem();
        other.setId(51L);
        other.setStepId(12L);
        when(learningAssessmentService.getAssessmentsByPlan(1L)).thenReturn(List.of(item, other));

        LearningOutcomeContextResponse resp = service.getContext(1L);

        assertEquals(11L, resp.appliedStepId());
        assertEquals(2, resp.steps().size());
        assertTrue(resp.steps().get(0).applied());
        assertFalse(resp.steps().get(1).applied());
        // 只回本次申请步骤的评估题
        assertEquals(1, resp.assessments().size());
        assertEquals(50L, resp.assessments().get(0).id());
    }

    @Test
    @DisplayName("getContext：能力名对不上但 tagId 命中 → 按标签兜底定位")
    void getContext_matchByTagId() {
        when(submissionMapper.selectById(1L)).thenReturn(submission(10L, "Kafka消息", 6L));
        when(learningPathPlanService.getByMatchingRecord(10L))
                .thenReturn(plan(1L, List.of(step(11L, "Kafka", 5L), step(12L, "Redis", 6L))));

        LearningOutcomeContextResponse resp = service.getContext(1L);

        assertEquals(12L, resp.appliedStepId());
    }

    @Test
    @DisplayName("getContext：能力名与 tagId 均未命中 → appliedStepId 为 null、评估题为空")
    void getContext_noMatch() {
        when(submissionMapper.selectById(1L)).thenReturn(submission(10L, "Unknown", 99L));
        when(learningPathPlanService.getByMatchingRecord(10L))
                .thenReturn(plan(1L, List.of(step(11L, "Kafka", 5L))));

        LearningOutcomeContextResponse resp = service.getContext(1L);

        assertNull(resp.appliedStepId());
        assertTrue(resp.assessments().isEmpty());
    }

    @Test
    @DisplayName("getContext：能力名为空白 → 仅尝试 tagId 匹配")
    void getContext_blankAbilityName() {
        when(submissionMapper.selectById(1L)).thenReturn(submission(10L, "   ", 5L));
        when(learningPathPlanService.getByMatchingRecord(10L))
                .thenReturn(plan(1L, List.of(step(11L, "Kafka", 5L))));

        LearningOutcomeContextResponse resp = service.getContext(1L);
        assertEquals(11L, resp.appliedStepId());
    }

    @Test
    @DisplayName("getContext：评估题查询抛异常 → 按空列表降级")
    void getContext_assessmentQueryThrows() {
        when(submissionMapper.selectById(1L)).thenReturn(submission(10L, "Kafka", 5L));
        when(learningPathPlanService.getByMatchingRecord(10L))
                .thenReturn(plan(1L, List.of(step(11L, "Kafka", 5L))));
        when(learningAssessmentService.getAssessmentsByPlan(1L)).thenThrow(new RuntimeException("db down"));

        LearningOutcomeContextResponse resp = service.getContext(1L);
        assertTrue(resp.assessments().isEmpty());
    }

    @Test
    @DisplayName("getContext：员工查询失败 → empName 为 null，不影响主流程")
    void getContext_empLookupFails() {
        when(submissionMapper.selectById(1L)).thenReturn(submission(null, "Kafka", null));
        when(empEmployeeMapper.selectById(100L)).thenThrow(new RuntimeException("db down"));

        LearningOutcomeContextResponse resp = service.getContext(1L);
        assertNull(resp.empName());
        assertEquals(1L, resp.submissionId());
    }

    @Test
    @DisplayName("getContext：员工不存在 → empName 为 null")
    void getContext_empMissing() {
        when(submissionMapper.selectById(1L)).thenReturn(submission(null, "Kafka", null));
        when(empEmployeeMapper.selectById(100L)).thenReturn(null);

        assertNull(service.getContext(1L).empName());
    }

    // ==================== 项目材料 ====================

    @Test
    @DisplayName("getContext：项目材料按「属于本次申请步骤」排前，且回填任务标题")
    void getContext_projectMaterialsSorted() {
        when(submissionMapper.selectById(1L)).thenReturn(submission(10L, "Kafka", 5L));
        when(learningPathPlanService.getByMatchingRecord(10L))
                .thenReturn(plan(1L, List.of(step(11L, "Kafka", 5L), step(12L, "Redis", 6L))));

        LearningProjectSubmission s1 = projectSubmission(100L, 12L, 71L, LocalDateTime.now());
        LearningProjectSubmission s2 = projectSubmission(101L, 11L, 70L, LocalDateTime.now().minusDays(1));
        when(projectSubmissionMapper.selectList(any())).thenReturn(List.of(s1, s2));
        LearningProjectTask task = new LearningProjectTask();
        task.setId(70L);
        task.setTaskTitle("Kafka 实战");
        when(projectTaskMapper.selectBatchIds(anyList())).thenReturn(List.of(task));

        LearningOutcomeContextResponse resp = service.getContext(1L);

        assertEquals(2, resp.projectMaterials().size());
        // 属于本次申请步骤(11)的材料被排到最前
        assertEquals(101L, resp.projectMaterials().get(0).submissionId());
        assertTrue(resp.projectMaterials().get(0).belongsToAppliedStep());
        assertEquals("Kafka 实战", resp.projectMaterials().get(0).taskTitle());
        assertEquals("Kafka", resp.projectMaterials().get(0).stepAbilityName());
        assertFalse(resp.projectMaterials().get(1).belongsToAppliedStep());
    }

    @Test
    @DisplayName("getContext：无计划或无员工 → 项目材料为空")
    void getContext_noMaterialsWhenNoPlan() {
        when(submissionMapper.selectById(1L)).thenReturn(submission(null, "Kafka", 5L));

        LearningOutcomeContextResponse resp = service.getContext(1L);
        assertTrue(resp.projectMaterials().isEmpty());
    }

    @Test
    @DisplayName("getContext：材料查询抛异常 → 按空列表降级不抛")
    void getContext_materialQueryThrows() {
        when(submissionMapper.selectById(1L)).thenReturn(submission(10L, "Kafka", 5L));
        when(learningPathPlanService.getByMatchingRecord(10L))
                .thenReturn(plan(1L, List.of(step(11L, "Kafka", 5L))));
        when(projectSubmissionMapper.selectList(any())).thenThrow(new RuntimeException("db down"));

        LearningOutcomeContextResponse resp = service.getContext(1L);
        assertTrue(resp.projectMaterials().isEmpty());
    }

    @Test
    @DisplayName("getContext：材料为空列表 → 直接返回空")
    void getContext_emptyMaterials() {
        when(submissionMapper.selectById(1L)).thenReturn(submission(10L, "Kafka", 5L));
        when(learningPathPlanService.getByMatchingRecord(10L))
                .thenReturn(plan(1L, List.of(step(11L, "Kafka", 5L))));
        when(projectSubmissionMapper.selectList(any())).thenReturn(List.of());

        assertTrue(service.getContext(1L).projectMaterials().isEmpty());
    }

    @Test
    @DisplayName("getContext：任务标题查询抛异常 → 材料仍返回，标题缺失")
    void getContext_taskTitleQueryThrows() {
        when(submissionMapper.selectById(1L)).thenReturn(submission(10L, "Kafka", 5L));
        when(learningPathPlanService.getByMatchingRecord(10L))
                .thenReturn(plan(1L, List.of(step(11L, "Kafka", 5L))));
        when(projectSubmissionMapper.selectList(any()))
                .thenReturn(List.of(projectSubmission(101L, 11L, 70L, LocalDateTime.now())));
        when(projectTaskMapper.selectBatchIds(anyList())).thenThrow(new RuntimeException("db down"));

        LearningOutcomeContextResponse resp = service.getContext(1L);
        assertEquals(1, resp.projectMaterials().size());
        assertNull(resp.projectMaterials().get(0).taskTitle());
    }

    @Test
    @DisplayName("getContext：材料 taskId 为 null → 标题为 null，不因空标题而失败")
    void getContext_materialWithoutTaskId() {
        when(submissionMapper.selectById(1L)).thenReturn(submission(10L, "Kafka", 5L));
        when(learningPathPlanService.getByMatchingRecord(10L))
                .thenReturn(plan(1L, List.of(step(11L, "Kafka", 5L))));
        // 同时给一条有 taskId 的材料，确保标题映射为可变 Map（覆盖 null taskId 的 get 分支）
        when(projectSubmissionMapper.selectList(any())).thenReturn(List.of(
                projectSubmission(101L, 11L, 70L, LocalDateTime.now()),
                projectSubmission(100L, 11L, null, LocalDateTime.now())));
        LearningProjectTask task = new LearningProjectTask();
        task.setId(70L);
        task.setTaskTitle("Kafka 实战");
        when(projectTaskMapper.selectBatchIds(anyList())).thenReturn(List.of(task));

        LearningOutcomeContextResponse resp = service.getContext(1L);

        assertEquals(2, resp.projectMaterials().size());
        LearningOutcomeContextResponse.ProjectMaterialBrief noTask = resp.projectMaterials().stream()
                .filter(m -> m.submissionId().equals(100L)).findFirst().orElseThrow();
        assertNull(noTask.taskTitle());
    }

    private EmpEmployee employee(String realName) {
        EmpEmployee e = new EmpEmployee();
        e.setId(100L);
        e.setRealName(realName);
        return e;
    }

    private LearningProjectSubmission projectSubmission(Long id, Long stepId, Long taskId, LocalDateTime created) {
        LearningProjectSubmission s = new LearningProjectSubmission();
        s.setId(id);
        s.setPlanId(1L);
        s.setEmpId(100L);
        s.setStepId(stepId);
        s.setTaskId(taskId);
        s.setRepoUrl("http://repo");
        s.setSubmissionText("说明");
        s.setCreatedTime(created);
        return s;
    }
}
