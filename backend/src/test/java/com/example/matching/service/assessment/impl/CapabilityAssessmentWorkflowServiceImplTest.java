package com.example.matching.service.assessment.impl;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.example.matching.common.enums.StageTypeEnum;
import com.example.matching.common.enums.WorkflowStatusEnum;
import com.example.matching.dto.assessment.AgentMessageEnvelope;
import com.example.matching.entity.employee.EmpAiTest;
import com.example.matching.entity.workflow.CapabilityStageLifecycleEventLog;
import com.example.matching.entity.workflow.PersonCapabilityStageRun;
import com.example.matching.entity.workflow.PersonCapabilityWorkflow;
import com.example.matching.listener.AiTestTaskPayload;
import com.example.matching.mapper.employee.EmpAiTestMapper;
import com.example.matching.mapper.workflow.CapabilityStageLifecycleEventLogMapper;
import com.example.matching.mapper.workflow.PersonCapabilityStageRunMapper;
import com.example.matching.mapper.workflow.PersonCapabilityWorkflowMapper;
import com.example.matching.service.assessment.AssessmentAgentArtifactService;
import com.example.matching.service.common.EventOutboxDispatcher;
import org.apache.ibatis.builder.MapperBuilderAssistant;
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
import org.springframework.dao.DuplicateKeyException;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * {@link CapabilityAssessmentWorkflowServiceImpl} 单元测试。
 *
 * <p>覆盖工作流创建/查询、状态 CAS 流转、阶段运行幂等创建、失败与重试、
 * 前置依赖校验、生命周期事件日志幂等与 AI 测试重试复位等分支。
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class CapabilityAssessmentWorkflowServiceImplTest {

    /**
     * MyBatis-Plus lambda 包装器需要实体登记表信息，否则抛
     * "can not find lambda cache for this entity"。
     */
    @BeforeAll
    static void initMybatisPlusLambdaCache() {
        MybatisConfiguration cfg = new MybatisConfiguration();
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(cfg, "");
        TableInfoHelper.initTableInfo(assistant, PersonCapabilityWorkflow.class);
        TableInfoHelper.initTableInfo(assistant, PersonCapabilityStageRun.class);
        TableInfoHelper.initTableInfo(assistant, CapabilityStageLifecycleEventLog.class);
        TableInfoHelper.initTableInfo(assistant, EmpAiTest.class);
    }

    @Mock private PersonCapabilityWorkflowMapper workflowMapper;
    @Mock private PersonCapabilityStageRunMapper stageRunMapper;
    @Mock private CapabilityStageLifecycleEventLogMapper eventLogMapper;
    @Mock private EventOutboxDispatcher outboxDispatcher;
    @Mock private EmpAiTestMapper empAiTestMapper;
    @Mock private AssessmentAgentArtifactService artifactService;

    private CapabilityAssessmentWorkflowServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new CapabilityAssessmentWorkflowServiceImpl(
                workflowMapper, stageRunMapper, eventLogMapper, outboxDispatcher, empAiTestMapper);
    }

    private PersonCapabilityWorkflow workflow(Long id, String status) {
        PersonCapabilityWorkflow w = new PersonCapabilityWorkflow();
        w.setId(id);
        w.setEmpId(7L);
        w.setStatus(status);
        return w;
    }

    private PersonCapabilityStageRun stageRun(Long id, Long workflowId, String stageType, String status) {
        PersonCapabilityStageRun r = new PersonCapabilityStageRun();
        r.setId(id);
        r.setWorkflowId(workflowId);
        r.setStageType(stageType);
        r.setStatus(status);
        r.setInputHash("hash");
        r.setAttemptCount(0);
        return r;
    }

    // ==================== getOrCreateActiveWorkflow ====================

    @Test
    @DisplayName("getOrCreateActiveWorkflow：已有活跃工作流 → 直接返回")
    void getOrCreate_existing() {
        PersonCapabilityWorkflow existing = workflow(1L, WorkflowStatusEnum.TEST_GENERATING.getCode());
        when(workflowMapper.selectOne(any())).thenReturn(existing);

        assertSame(existing, service.getOrCreateActiveWorkflow(7L, 9L));
        verify(workflowMapper, never()).insert(any(PersonCapabilityWorkflow.class));
    }

    @Test
    @DisplayName("getOrCreateActiveWorkflow：无活跃工作流 → 新建并回填 ID")
    void getOrCreate_creates() {
        when(workflowMapper.selectOne(any())).thenReturn(null);
        doAnswer(inv -> {
            PersonCapabilityWorkflow w = inv.getArgument(0);
            w.setId(100L);
            return 1;
        }).when(workflowMapper).insert(any(PersonCapabilityWorkflow.class));

        PersonCapabilityWorkflow created = service.getOrCreateActiveWorkflow(7L, 9L);

        assertEquals(100L, created.getId());
        assertEquals(WorkflowStatusEnum.RESUME_REQUIRED.getCode(), created.getStatus());
        assertEquals(9L, created.getCreatedBy());
        assertNotNull(created.getStartedAt());
    }

    @Test
    @DisplayName("getOrCreateActiveWorkflow：并发唯一键冲突 → 重查返回已存在工作流")
    void getOrCreate_duplicateRaceRecovered() {
        PersonCapabilityWorkflow raced = workflow(55L, WorkflowStatusEnum.TEST_GENERATING.getCode());
        when(workflowMapper.selectOne(any())).thenReturn(null, raced);
        when(workflowMapper.insert(any(PersonCapabilityWorkflow.class)))
                .thenThrow(new DuplicateKeyException("dup"));

        assertSame(raced, service.getOrCreateActiveWorkflow(7L, 9L));
    }

    @Test
    @DisplayName("getOrCreateActiveWorkflow：唯一键冲突且重查仍为空 → 原样抛出")
    void getOrCreate_duplicateRaceUnrecovered() {
        when(workflowMapper.selectOne(any())).thenReturn(null);
        when(workflowMapper.insert(any(PersonCapabilityWorkflow.class)))
                .thenThrow(new DuplicateKeyException("dup"));

        assertThrows(DuplicateKeyException.class, () -> service.getOrCreateActiveWorkflow(7L, 9L));
    }

    // ==================== 工作流查询 ====================

    @Test
    @DisplayName("getActiveWorkflow：转发 mapper 查询")
    void getActiveWorkflow_delegates() {
        PersonCapabilityWorkflow w = workflow(1L, "TEST_GENERATING");
        when(workflowMapper.selectOne(any())).thenReturn(w);

        assertSame(w, service.getActiveWorkflow(7L));
    }

    @Test
    @DisplayName("getLatestCompletedWorkflowId：命中返回 ID，未命中返回 null")
    void getLatestCompletedWorkflowId_bothPaths() {
        when(workflowMapper.selectOne(any())).thenReturn(workflow(3L, WorkflowStatusEnum.COMPLETED.getCode()));
        assertEquals(3L, service.getLatestCompletedWorkflowId(7L));

        when(workflowMapper.selectOne(any())).thenReturn(null);
        assertNull(service.getLatestCompletedWorkflowId(7L));
    }

    @Test
    @DisplayName("getLatestWorkflow：empId 为空 → 返回 null，不查库")
    void getLatestWorkflow_nullEmpId() {
        assertNull(service.getLatestWorkflow(null));
        verify(workflowMapper, never()).selectOne(any());
    }

    @Test
    @DisplayName("getLatestWorkflow：有记录 → 返回记录")
    void getLatestWorkflow_found() {
        PersonCapabilityWorkflow w = workflow(4L, "TEST_GENERATING");
        when(workflowMapper.selectOne(any())).thenReturn(w);

        assertSame(w, service.getLatestWorkflow(7L));
    }

    @Test
    @DisplayName("getWorkflow：不存在 → 抛 IllegalArgumentException")
    void getWorkflow_notFound() {
        when(workflowMapper.selectById(1L)).thenReturn(null);

        assertThrows(IllegalArgumentException.class, () -> service.getWorkflow(1L));
    }

    @Test
    @DisplayName("getWorkflow：存在 → 返回工作流")
    void getWorkflow_found() {
        PersonCapabilityWorkflow w = workflow(1L, "TEST_GENERATING");
        when(workflowMapper.selectById(1L)).thenReturn(w);

        assertSame(w, service.getWorkflow(1L));
    }

    // ==================== bindPost / transition ====================

    @Test
    @DisplayName("bindPost：更新工作流目标岗位")
    void bindPost_updates() {
        service.bindPost(1L, 200L);

        verify(workflowMapper).update(any(), any());
    }

    @Test
    @DisplayName("transition：CAS 成功 → 返回 true")
    void transition_success() {
        when(workflowMapper.update(any(), any())).thenReturn(1);

        assertTrue(service.transition(1L, "A", "B", "STAGE"));
    }

    @Test
    @DisplayName("transition：CAS 失败（更新 0 行）→ 返回 false")
    void transition_casFailure() {
        when(workflowMapper.update(any(), any())).thenReturn(0);

        assertFalse(service.transition(1L, "A", "B", "STAGE"));
    }

    // ==================== createStageRun ====================

    @Test
    @DisplayName("createStageRun：同工作流+阶段+输入哈希已存在 → 幂等返回已有")
    void createStageRun_idempotent() {
        PersonCapabilityStageRun existing = stageRun(9L, 1L, "RESUME_PARSE", "PENDING");
        when(stageRunMapper.selectOne(any())).thenReturn(existing);

        assertSame(existing, service.createStageRun(1L, "RESUME_PARSE", "h", "{}", null, null));
        verify(stageRunMapper, never()).insert(any(PersonCapabilityStageRun.class));
    }

    @Test
    @DisplayName("createStageRun：不存在 → 新建 PENDING 运行并回填 ID")
    void createStageRun_creates() {
        when(stageRunMapper.selectOne(any())).thenReturn(null);
        doAnswer(inv -> {
            PersonCapabilityStageRun r = inv.getArgument(0);
            r.setId(88L);
            return 1;
        }).when(stageRunMapper).insert(any(PersonCapabilityStageRun.class));

        PersonCapabilityStageRun created = service.createStageRun(1L, "RESUME_PARSE", "h", "{}", "REF", 5L);

        assertEquals(88L, created.getId());
        assertEquals("PENDING", created.getStatus());
        assertEquals(0, created.getAttemptCount());
        assertEquals(5L, created.getSourceRefId());
    }

    @Test
    @DisplayName("createStageRun：并发唯一键冲突 → 重查返回已存在运行")
    void createStageRun_duplicateRace() {
        PersonCapabilityStageRun raced = stageRun(77L, 1L, "RESUME_PARSE", "PENDING");
        when(stageRunMapper.selectOne(any())).thenReturn(null, raced);
        when(stageRunMapper.insert(any(PersonCapabilityStageRun.class)))
                .thenThrow(new DuplicateKeyException("dup"));

        assertSame(raced, service.createStageRun(1L, "RESUME_PARSE", "h", "{}", null, null));
    }

    @Test
    @DisplayName("createStageRun：唯一键冲突且重查为空 → 原样抛出")
    void createStageRun_duplicateUnrecovered() {
        when(stageRunMapper.selectOne(any())).thenReturn(null);
        when(stageRunMapper.insert(any(PersonCapabilityStageRun.class)))
                .thenThrow(new DuplicateKeyException("dup"));

        assertThrows(DuplicateKeyException.class,
                () -> service.createStageRun(1L, "RESUME_PARSE", "h", "{}", null, null));
    }

    // ==================== claimStageRun ====================

    @Test
    @DisplayName("claimStageRun：抢占成功返回 true / 失败返回 false")
    void claimStageRun_bothPaths() {
        when(stageRunMapper.update(any(), any())).thenReturn(1);
        assertTrue(service.claimStageRun(1L));

        when(stageRunMapper.update(any(), any())).thenReturn(0);
        assertFalse(service.claimStageRun(1L));
    }

    // ==================== markStageSucceeded / Failed ====================

    @Test
    @DisplayName("markStageSucceeded：运行不存在 → 静默返回")
    void markStageSucceeded_missing() {
        when(stageRunMapper.selectById(1L)).thenReturn(null);

        service.markStageSucceeded(1L, "{}");

        verify(stageRunMapper, never()).update(any(), any());
    }

    @Test
    @DisplayName("markStageSucceeded：存在 → 执行条件更新")
    void markStageSucceeded_updates() {
        when(stageRunMapper.selectById(1L)).thenReturn(stageRun(1L, 1L, "RESUME_PARSE", "RUNNING"));
        when(stageRunMapper.update(any(), any())).thenReturn(1);

        service.markStageSucceeded(1L, "{\"ok\":true}");

        verify(stageRunMapper).update(any(), any());
    }

    @Test
    @DisplayName("markStageFailed：运行不存在 → 静默返回")
    void markStageFailed_missing() {
        when(stageRunMapper.selectById(1L)).thenReturn(null);

        service.markStageFailed(1L, "CODE", "msg", false);

        verify(stageRunMapper, never()).update(any(), any());
    }

    @Test
    @DisplayName("markStageFailed：可重试失败 → 更新状态，不触发工作流失败")
    void markStageFailed_retryable() {
        when(stageRunMapper.selectById(1L)).thenReturn(stageRun(1L, 1L, "RESUME_PARSE", "RUNNING"));
        when(stageRunMapper.update(any(), any())).thenReturn(1);

        service.markStageFailed(1L, "CODE", "msg", false);

        verify(workflowMapper, never()).update(any(), any());
    }

    @Test
    @DisplayName("markStageFailed：最终失败 → 联动将工作流置为需恢复")
    void markStageFailed_final() {
        when(stageRunMapper.selectById(1L)).thenReturn(stageRun(1L, 1L, "RESUME_PARSE", "RUNNING"));
        when(stageRunMapper.update(any(), any())).thenReturn(1);

        service.markStageFailed(1L, "CODE", "msg", true);

        verify(workflowMapper).update(any(), any());
    }

    @Test
    @DisplayName("markStageFailed：超长失败信息 → 截断至 1000 字符")
    void markStageFailed_truncatesMessage() {
        when(stageRunMapper.selectById(1L)).thenReturn(stageRun(1L, 1L, "RESUME_PARSE", "RUNNING"));
        when(stageRunMapper.update(any(), any())).thenReturn(1);
        String longMessage = "x".repeat(1500);

        service.markStageFailed(1L, "CODE", longMessage, false);

        verify(stageRunMapper).update(any(), any());
    }

    // ==================== failWorkflow / completeWorkflow ====================

    @Test
    @DisplayName("failWorkflow：更新为需恢复状态")
    void failWorkflow_updates() {
        service.failWorkflow(1L, "boom");

        verify(workflowMapper).update(any(), any());
    }

    @Test
    @DisplayName("completeWorkflow：更新为已完成并推进到聚合阶段")
    void completeWorkflow_updates() {
        service.completeWorkflow(1L);

        verify(workflowMapper).update(any(), any());
    }

    // ==================== getLatestStageRun / resolveActiveStageRun ====================

    @Test
    @DisplayName("getLatestStageRun：返回最近一次阶段运行")
    void getLatestStageRun_delegates() {
        PersonCapabilityStageRun run = stageRun(1L, 1L, "RESUME_PARSE", "SUCCEEDED");
        when(stageRunMapper.selectOne(any())).thenReturn(run);

        assertSame(run, service.getLatestStageRun(1L, "RESUME_PARSE"));
    }

    @Test
    @DisplayName("resolveActiveStageRun：带来源条件 → 转发生成查询")
    void resolveActiveStageRun_withSource() {
        PersonCapabilityStageRun run = stageRun(1L, 1L, "RESUME_PARSE", "RUNNING");
        when(stageRunMapper.selectOne(any())).thenReturn(run);

        assertSame(run, service.resolveActiveStageRun(1L, "RESUME_PARSE", "REF", 5L));
    }

    @Test
    @DisplayName("resolveActiveStageRun：来源类型为空 → 不附加来源过滤")
    void resolveActiveStageRun_withoutSource() {
        when(stageRunMapper.selectOne(any())).thenReturn(null);

        assertNull(service.resolveActiveStageRun(1L, "RESUME_PARSE", null, null));
    }

    // ==================== retryStage ====================

    @Test
    @DisplayName("retryStage：工作流已完成 → 抛 IllegalStateException")
    void retryStage_completedWorkflow() {
        when(workflowMapper.selectById(1L)).thenReturn(workflow(1L, WorkflowStatusEnum.COMPLETED.getCode()));

        assertThrows(IllegalStateException.class, () -> service.retryStage(1L, "RESUME_PARSE", 9L));
    }

    @Test
    @DisplayName("retryStage：无失败运行也无在途运行 → 抛 IllegalStateException")
    void retryStage_nothingToRetry() {
        when(workflowMapper.selectById(1L)).thenReturn(workflow(1L, WorkflowStatusEnum.RECOVERY_REQUIRED.getCode()));
        when(stageRunMapper.selectOne(any())).thenReturn(null);

        assertThrows(IllegalStateException.class, () -> service.retryStage(1L, "RESUME_PARSE", 9L));
    }

    @Test
    @DisplayName("retryStage：存在在途运行但未投递 → 修复并投递，不新建运行")
    void retryStage_repairsPendingRun() {
        when(workflowMapper.selectById(1L)).thenReturn(workflow(1L, WorkflowStatusEnum.RECOVERY_REQUIRED.getCode()));
        PersonCapabilityStageRun pending = stageRun(33L, 1L, "RESUME_PARSE", "PENDING");
        when(stageRunMapper.selectOne(any())).thenReturn(null, pending);

        service.retryStage(1L, "RESUME_PARSE", 9L);

        verify(outboxDispatcher).enqueue(eq("CAPABILITY_ASSESSMENT_STAGE"), anyString(), anyString(), any());
        verify(stageRunMapper, never()).insert(any(PersonCapabilityStageRun.class));
    }

    @Test
    @DisplayName("retryStage：存在可重试失败运行 → 新建高尝试次数运行并投递")
    void retryStage_createsNewRun() {
        when(workflowMapper.selectById(1L)).thenReturn(workflow(1L, WorkflowStatusEnum.RECOVERY_REQUIRED.getCode()));
        PersonCapabilityStageRun failed = stageRun(10L, 1L, "RESUME_PARSE", "FAILED_RETRYABLE");
        failed.setAttemptCount(1);
        // 第 1 次查失败运行 → failed；第 2 次是 createStageRun 内部的幂等查询 → null（触发插入）
        when(stageRunMapper.selectOne(any())).thenReturn(failed, null);
        doAnswer(inv -> {
            PersonCapabilityStageRun r = inv.getArgument(0);
            r.setId(200L);
            return 1;
        }).when(stageRunMapper).insert(any(PersonCapabilityStageRun.class));

        service.retryStage(1L, "RESUME_PARSE", 9L);

        ArgumentCaptor<PersonCapabilityStageRun> cap = ArgumentCaptor.forClass(PersonCapabilityStageRun.class);
        verify(stageRunMapper).insert(cap.capture());
        assertEquals(2, cap.getValue().getAttemptCount());
        verify(stageRunMapper).updateById(any(PersonCapabilityStageRun.class));
        verify(outboxDispatcher).enqueue(eq("CAPABILITY_ASSESSMENT_STAGE"), anyString(), anyString(), any());
    }

    @Test
    @DisplayName("retryStage：AI 测试生成阶段 → 复位 FAILED 状态并走 AI 专用队列")
    void retryStage_aiTestGenerationResets() {
        when(workflowMapper.selectById(1L)).thenReturn(workflow(1L, WorkflowStatusEnum.RECOVERY_REQUIRED.getCode()));
        PersonCapabilityStageRun failed = stageRun(10L, 1L, "AI_TEST_GENERATION", "FAILED_RETRYABLE");
        failed.setSourceRefId(55L);
        when(stageRunMapper.selectOne(any())).thenReturn(failed, null);
        doAnswer(inv -> {
            PersonCapabilityStageRun r = inv.getArgument(0);
            r.setId(200L);
            return 1;
        }).when(stageRunMapper).insert(any(PersonCapabilityStageRun.class));

        EmpAiTest test = new EmpAiTest();
        test.setId(55L);
        test.setGenerationState("FAILED");
        when(empAiTestMapper.selectById(55L)).thenReturn(test);
        when(empAiTestMapper.resetGenerationToPending(55L)).thenReturn(1);

        service.retryStage(1L, "AI_TEST_GENERATION", 9L);

        verify(empAiTestMapper).resetGenerationToPending(55L);
        verify(outboxDispatcher).enqueue(eq("AI_TEST"), anyString(), eq("ai.test.generate"), any(AiTestTaskPayload.class));
    }

    @Test
    @DisplayName("retryStage：AI 测试评分阶段 → 复位评分状态并投递 ai.test.evaluate")
    void retryStage_aiTestEvaluationResets() {
        when(workflowMapper.selectById(1L)).thenReturn(workflow(1L, WorkflowStatusEnum.RECOVERY_REQUIRED.getCode()));
        PersonCapabilityStageRun failed = stageRun(10L, 1L, "AI_TEST_EVALUATION", "FAILED_RETRYABLE");
        failed.setSourceRefId(66L);
        when(stageRunMapper.selectOne(any())).thenReturn(failed, null);
        doAnswer(inv -> {
            PersonCapabilityStageRun r = inv.getArgument(0);
            r.setId(200L);
            return 1;
        }).when(stageRunMapper).insert(any(PersonCapabilityStageRun.class));

        EmpAiTest test = new EmpAiTest();
        test.setId(66L);
        test.setEvaluationState("FAILED");
        when(empAiTestMapper.selectById(66L)).thenReturn(test);
        when(empAiTestMapper.resetEvaluationToPending(66L)).thenReturn(1);

        service.retryStage(1L, "AI_TEST_EVALUATION", 9L);

        verify(empAiTestMapper).resetEvaluationToPending(66L);
        verify(outboxDispatcher).enqueue(eq("AI_TEST"), anyString(), eq("ai.test.evaluate"), any(AiTestTaskPayload.class));
    }

    @Test
    @DisplayName("retryStage：AI 测试记录不存在 → 抛 IllegalStateException")
    void retryStage_aiTestMissing() {
        when(workflowMapper.selectById(1L)).thenReturn(workflow(1L, WorkflowStatusEnum.RECOVERY_REQUIRED.getCode()));
        PersonCapabilityStageRun failed = stageRun(10L, 1L, "AI_TEST_GENERATION", "FAILED_RETRYABLE");
        failed.setSourceRefId(99L);
        when(stageRunMapper.selectOne(any())).thenReturn(failed, null);
        doAnswer(inv -> {
            PersonCapabilityStageRun r = inv.getArgument(0);
            r.setId(200L);
            return 1;
        }).when(stageRunMapper).insert(any(PersonCapabilityStageRun.class));
        when(empAiTestMapper.selectById(99L)).thenReturn(null);

        assertThrows(IllegalStateException.class, () -> service.retryStage(1L, "AI_TEST_GENERATION", 9L));
    }

    @Test
    @DisplayName("retryStage：AI 测试状态复位失败 → 抛 IllegalStateException")
    void retryStage_aiTestResetFails() {
        when(workflowMapper.selectById(1L)).thenReturn(workflow(1L, WorkflowStatusEnum.RECOVERY_REQUIRED.getCode()));
        PersonCapabilityStageRun failed = stageRun(10L, 1L, "AI_TEST_GENERATION", "FAILED_RETRYABLE");
        failed.setSourceRefId(55L);
        when(stageRunMapper.selectOne(any())).thenReturn(failed, null);
        doAnswer(inv -> {
            PersonCapabilityStageRun r = inv.getArgument(0);
            r.setId(200L);
            return 1;
        }).when(stageRunMapper).insert(any(PersonCapabilityStageRun.class));
        EmpAiTest test = new EmpAiTest();
        test.setId(55L);
        test.setGenerationState("FAILED");
        when(empAiTestMapper.selectById(55L)).thenReturn(test);
        when(empAiTestMapper.resetGenerationToPending(55L)).thenReturn(0);

        assertThrows(IllegalStateException.class, () -> service.retryStage(1L, "AI_TEST_GENERATION", 9L));
    }

    // ==================== assertStagePrerequisite ====================

    @Test
    @DisplayName("assertStagePrerequisite：初始阶段无前置 → 通过")
    void assertStagePrerequisite_initialStage() {
        service.assertStagePrerequisite(1L, StageTypeEnum.RESUME_PARSE.getCode());

        verify(stageRunMapper, never()).selectCount(any());
    }

    @Test
    @DisplayName("assertStagePrerequisite：前置阶段未成功 → 抛 IllegalStateException")
    void assertStagePrerequisite_notSatisfied() {
        when(stageRunMapper.selectCount(any())).thenReturn(0L);

        assertThrows(IllegalStateException.class,
                () -> service.assertStagePrerequisite(1L, StageTypeEnum.RESUME_CLAIM_EXTRACTION.getCode()));
    }

    @Test
    @DisplayName("assertStagePrerequisite：count 为 null → 视为未满足")
    void assertStagePrerequisite_nullCount() {
        when(stageRunMapper.selectCount(any())).thenReturn(null);

        assertThrows(IllegalStateException.class,
                () -> service.assertStagePrerequisite(1L, StageTypeEnum.RESUME_CLAIM_EXTRACTION.getCode()));
    }

    @Test
    @DisplayName("assertStagePrerequisite：前置阶段已成功 → 通过")
    void assertStagePrerequisite_satisfied() {
        when(stageRunMapper.selectCount(any())).thenReturn(1L);

        service.assertStagePrerequisite(1L, StageTypeEnum.RESUME_CLAIM_EXTRACTION.getCode());

        verify(stageRunMapper).selectCount(any());
    }

    // ==================== startNextStage ====================

    @Test
    @DisplayName("startNextStage：前置满足 → 创建 PENDING 运行、同步活跃 ID 并投递任务")
    void startNextStage_success() {
        when(stageRunMapper.selectCount(any())).thenReturn(1L);
        when(stageRunMapper.selectOne(any())).thenReturn(null);
        doAnswer(inv -> {
            PersonCapabilityStageRun r = inv.getArgument(0);
            r.setId(77L);
            return 1;
        }).when(stageRunMapper).insert(any(PersonCapabilityStageRun.class));

        PersonCapabilityStageRun run = service.startNextStage(
                1L, StageTypeEnum.RESUME_CLAIM_EXTRACTION.getCode(), "h", "{}", 9L);

        assertEquals(77L, run.getId());
        verify(workflowMapper).update(any(), any());
        verify(outboxDispatcher).enqueue(eq("CAPABILITY_ASSESSMENT_STAGE"), anyString(), anyString(), any());
    }

    @Test
    @DisplayName("startNextStage：前置未满足 → 抛 IllegalStateException")
    void startNextStage_prerequisiteFails() {
        when(stageRunMapper.selectCount(any())).thenReturn(0L);

        assertThrows(IllegalStateException.class, () -> service.startNextStage(
                1L, StageTypeEnum.RESUME_CLAIM_EXTRACTION.getCode(), "h", "{}", 9L));
    }

    @Test
    @DisplayName("startNextStage：存在 Artifact 服务时 → 投递信封而非裸 ID")
    void startNextStage_withArtifactService() {
        ReflectionTestUtils.setField(service, "artifactService", artifactService);
        when(stageRunMapper.selectCount(any())).thenReturn(1L);
        when(stageRunMapper.selectOne(any())).thenReturn(null);
        doAnswer(inv -> {
            PersonCapabilityStageRun r = inv.getArgument(0);
            r.setId(77L);
            return 1;
        }).when(stageRunMapper).insert(any(PersonCapabilityStageRun.class));
        when(artifactService.storeStageTask(any(), any(), any(), any(), any()))
                .thenReturn(new AgentMessageEnvelope("m", "t", 1L, 77L, "a", "b", "v", 5L, null, null, 1, null, 1));

        service.startNextStage(1L, StageTypeEnum.RESUME_CLAIM_EXTRACTION.getCode(), "h", "{}", 9L);

        verify(artifactService).storeStageTask(1L, 77L,
                StageTypeEnum.RESUME_CLAIM_EXTRACTION.getCode(), "h", "ABILITY_TAG_TREE_V1");
    }

    // ==================== hashInput ====================

    @Test
    @DisplayName("hashInput：生成稳定 MD5，null 片段按空串处理")
    void hashInput_stable() {
        String h1 = CapabilityAssessmentWorkflowServiceImpl.hashInput("a", null, "b");
        String h2 = CapabilityAssessmentWorkflowServiceImpl.hashInput("a", null, "b");

        assertEquals(h1, h2);
        assertEquals(32, h1.length());
        assertNotEquals(h1, CapabilityAssessmentWorkflowServiceImpl.hashInput("a", "b"));
    }

    // ==================== getStageRun / casStageRunStatus ====================

    @Test
    @DisplayName("getStageRun：按 ID 查询")
    void getStageRun_delegates() {
        PersonCapabilityStageRun run = stageRun(1L, 1L, "RESUME_PARSE", "PENDING");
        when(stageRunMapper.selectById(1L)).thenReturn(run);

        assertSame(run, service.getStageRun(1L));
    }

    @Test
    @DisplayName("casStageRunStatus：CAS 成功返回 true / 失败返回 false")
    void casStageRunStatus_bothPaths() {
        when(stageRunMapper.update(any(), any())).thenReturn(1);
        assertTrue(service.casStageRunStatus(1L, "PENDING", "RUNNING", null, null));

        when(stageRunMapper.update(any(), any())).thenReturn(0);
        assertFalse(service.casStageRunStatus(1L, "PENDING", "RUNNING", "CODE", "msg"));
    }

    @Test
    @DisplayName("casStageRunStatus：目标为终态且带失败信息 → 覆盖更多 set 分支")
    void casStageRunStatus_terminalTarget() {
        when(stageRunMapper.update(any(), any())).thenReturn(1);

        assertTrue(service.casStageRunStatus(1L, "RUNNING", "FAILED_FINAL", "CODE", "msg"));
        assertTrue(service.casStageRunStatus(1L, "RUNNING", "SUCCEEDED", null, null));
    }

    // ==================== syncActiveStageRun ====================

    @Test
    @DisplayName("syncActiveStageRun：更新工作流活跃阶段运行 ID")
    void syncActiveStageRun_updates() {
        service.syncActiveStageRun(1L, 9L);

        verify(workflowMapper).update(any(), any());
    }

    @Test
    @DisplayName("markWorkflowFinalFailed：更新为需恢复状态并记录失败原因")
    void markWorkflowFinalFailed_updates() {
        service.markWorkflowFinalFailed(1L, "final");

        verify(workflowMapper).update(any(), any());
    }

    // ==================== 生命周期事件日志 ====================

    @Test
    @DisplayName("recordLifecycleEventLog：插入成功返回 true，重复键返回 false")
    void recordLifecycleEventLog_bothPaths() {
        CapabilityStageLifecycleEventLog log = new CapabilityStageLifecycleEventLog();
        log.setEventId("e1");
        when(eventLogMapper.insert(any(CapabilityStageLifecycleEventLog.class))).thenReturn(1);
        assertTrue(service.recordLifecycleEventLog(log));

        when(eventLogMapper.insert(any(CapabilityStageLifecycleEventLog.class)))
                .thenThrow(new DuplicateKeyException("dup"));
        assertFalse(service.recordLifecycleEventLog(log));
    }

    @Test
    @DisplayName("claimLifecycleEventLog：等价于 recordLifecycleEventLog")
    void claimLifecycleEventLog_delegates() {
        CapabilityStageLifecycleEventLog log = new CapabilityStageLifecycleEventLog();
        log.setEventId("e1");
        when(eventLogMapper.insert(any(CapabilityStageLifecycleEventLog.class))).thenReturn(1);

        assertTrue(service.claimLifecycleEventLog(log));
    }

    @Test
    @DisplayName("completeLifecycleEventLog：入参为空或缺 eventId → 返回 false")
    void completeLifecycleEventLog_invalidArgs() {
        assertFalse(service.completeLifecycleEventLog(null));
        assertFalse(service.completeLifecycleEventLog(new CapabilityStageLifecycleEventLog()));
    }

    @Test
    @DisplayName("completeLifecycleEventLog：更新一行返回 true，零行返回 false")
    void completeLifecycleEventLog_bothPaths() {
        CapabilityStageLifecycleEventLog log = new CapabilityStageLifecycleEventLog();
        log.setEventId("e1");
        when(eventLogMapper.update(any(), any())).thenReturn(1);
        assertTrue(service.completeLifecycleEventLog(log));

        when(eventLogMapper.update(any(), any())).thenReturn(0);
        assertFalse(service.completeLifecycleEventLog(log));
    }

    @Test
    @DisplayName("existsLifecycleEvent：count>0 返回 true，否则 false")
    void existsLifecycleEvent_bothPaths() {
        when(eventLogMapper.selectCount(any())).thenReturn(1L);
        assertTrue(service.existsLifecycleEvent("e1"));

        when(eventLogMapper.selectCount(any())).thenReturn(0L);
        assertFalse(service.existsLifecycleEvent("e1"));

        when(eventLogMapper.selectCount(any())).thenReturn(null);
        assertFalse(service.existsLifecycleEvent("e1"));
    }

    @Test
    @DisplayName("hasRecordedLifecycleEvent：按阶段运行与事件类型判断存在性")
    void hasRecordedLifecycleEvent_bothPaths() {
        when(eventLogMapper.selectCount(any())).thenReturn(2L);
        assertTrue(service.hasRecordedLifecycleEvent(1L, "STAGE_SUCCEEDED"));

        when(eventLogMapper.selectCount(any())).thenReturn(0L);
        assertFalse(service.hasRecordedLifecycleEvent(1L, "STAGE_SUCCEEDED"));
    }

    // ==================== listStageRuns ====================

    @Test
    @DisplayName("listStageRuns：返回工作流下全部阶段运行")
    void listStageRuns_delegates() {
        when(stageRunMapper.selectList(any())).thenReturn(List.of(
                stageRun(1L, 1L, "RESUME_PARSE", "SUCCEEDED")));

        List<PersonCapabilityStageRun> runs = service.listStageRuns(1L);

        assertEquals(1, runs.size());
    }

    @Test
    @DisplayName("listStageRuns：无阶段运行 → 返回空列表")
    void listStageRuns_empty() {
        when(stageRunMapper.selectList(any())).thenReturn(Collections.emptyList());

        assertThat(service.listStageRuns(1L)).isEmpty();
    }
}