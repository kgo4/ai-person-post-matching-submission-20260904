package com.example.matching.service.assessment;

import com.example.matching.common.enums.WorkflowStatusEnum;
import com.example.matching.entity.workflow.PersonCapabilityStageRun;
import com.example.matching.entity.workflow.PersonCapabilityWorkflow;
import com.example.matching.entity.employee.EmpAiTest;
import com.example.matching.mapper.employee.EmpAiTestMapper;
import com.example.matching.mapper.workflow.CapabilityStageLifecycleEventLogMapper;
import com.example.matching.mapper.workflow.PersonCapabilityStageRunMapper;
import com.example.matching.mapper.workflow.PersonCapabilityWorkflowMapper;
import com.example.matching.service.assessment.impl.CapabilityAssessmentWorkflowServiceImpl;
import com.example.matching.service.common.EventOutboxDispatcher;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 能力评估工作流状态机测试
 * <p>
 * 覆盖：活跃工作流唯一、阶段运行幂等、CAS 抢占、前置依赖校验、失败重试恢复。
 */
class CapabilityAssessmentWorkflowServiceTest {

    private PersonCapabilityWorkflowMapper workflowMapper;
    private PersonCapabilityStageRunMapper stageRunMapper;
    private CapabilityStageLifecycleEventLogMapper eventLogMapper;
    private EventOutboxDispatcher outboxDispatcher;
    private EmpAiTestMapper empAiTestMapper;
    private CapabilityAssessmentWorkflowService service;

    @BeforeEach
    void setUp() {
        workflowMapper = mock(PersonCapabilityWorkflowMapper.class);
        stageRunMapper = mock(PersonCapabilityStageRunMapper.class);
        eventLogMapper = mock(CapabilityStageLifecycleEventLogMapper.class);
        outboxDispatcher = mock(EventOutboxDispatcher.class);
        empAiTestMapper = mock(EmpAiTestMapper.class);
        service = new CapabilityAssessmentWorkflowServiceImpl(
                workflowMapper, stageRunMapper, eventLogMapper, outboxDispatcher, empAiTestMapper);
        // 初始化 MyBatis-Plus TableInfo（LambdaQueryWrapper 需要实体缓存）
        com.baomidou.mybatisplus.core.MybatisConfiguration configuration =
                new com.baomidou.mybatisplus.core.MybatisConfiguration();
        com.baomidou.mybatisplus.core.metadata.TableInfoHelper.initTableInfo(
                new org.apache.ibatis.builder.MapperBuilderAssistant(configuration, ""),
                PersonCapabilityWorkflow.class);
        com.baomidou.mybatisplus.core.metadata.TableInfoHelper.initTableInfo(
                new org.apache.ibatis.builder.MapperBuilderAssistant(configuration, ""),
                PersonCapabilityStageRun.class);
    }

    @Test
    void getOrCreateActiveWorkflow_reusesExistingActiveWorkflow() {
        PersonCapabilityWorkflow existing = new PersonCapabilityWorkflow();
        existing.setId(10L);
        existing.setEmpId(1L);
        existing.setStatus(WorkflowStatusEnum.RESUME_EVIDENCE_READY.getCode());
        when(workflowMapper.selectOne(any())).thenReturn(existing);

        PersonCapabilityWorkflow result = service.getOrCreateActiveWorkflow(1L, 9L);

        assertThat(result.getId()).isEqualTo(10L);
        verify(workflowMapper, times(0)).insert(any(com.example.matching.entity.workflow.PersonCapabilityWorkflow.class));
    }

    @Test
    void createStageRun_isIdempotentByInputHash() {
        PersonCapabilityStageRun existing = new PersonCapabilityStageRun();
        existing.setId(100L);
        existing.setWorkflowId(1L);
        existing.setStageType("AGGREGATE_HARNESS");
        existing.setStatus("PENDING");
        when(stageRunMapper.selectOne(any())).thenReturn(existing);

        PersonCapabilityStageRun result = service.createStageRun(
                1L, "AGGREGATE_HARNESS", "hash-1", "{}", null, null);

        assertThat(result.getId()).isEqualTo(100L);
        verify(stageRunMapper, times(0)).insert(any(PersonCapabilityStageRun.class));
    }

    @Test
    void claimStageRun_casOnlyOnce() {
        when(stageRunMapper.update(any(), any())).thenReturn(1, 0);

        assertThat(service.claimStageRun(100L)).isTrue();
        assertThat(service.claimStageRun(100L)).isFalse();
    }

    @Test
    void startNextStage_rejectsWhenPrerequisiteMissing() {
        PersonCapabilityWorkflow workflow = new PersonCapabilityWorkflow();
        workflow.setId(1L);
        workflow.setEmpId(1L);
        workflow.setStatus(WorkflowStatusEnum.RESUME_EVIDENCE_READY.getCode());
        when(workflowMapper.selectById(1L)).thenReturn(workflow);
        // 前置 RESUME_CLAIM_EXTRACTION 无 SUCCEEDED 记录
        when(stageRunMapper.selectCount(any())).thenReturn(0L);

        assertThatThrownBy(() -> service.startNextStage(
                1L, "AI_TEST_GENERATION", "h", "{}", null))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("前置阶段未完成");
        verify(outboxDispatcher, times(0)).enqueue(anyString(), anyString(), anyString(), anyLong());
    }

    @Test
    void startNextStage_dispatchesOutboxTask() {
        PersonCapabilityWorkflow workflow = new PersonCapabilityWorkflow();
        workflow.setId(1L);
        workflow.setEmpId(1L);
        workflow.setStatus(WorkflowStatusEnum.RESUME_EVIDENCE_READY.getCode());
        when(workflowMapper.selectById(1L)).thenReturn(workflow);
        // 前置阶段已完成
        when(stageRunMapper.selectCount(any())).thenReturn(1L);
        when(stageRunMapper.selectOne(any())).thenReturn(null);
        when(stageRunMapper.insert((PersonCapabilityStageRun) any())).thenAnswer(inv -> {
            PersonCapabilityStageRun run = inv.getArgument(0);
            run.setId(200L);
            return 1;
        });

        PersonCapabilityStageRun stageRun = service.startNextStage(
                1L, "AI_TEST_GENERATION", "h", "{}", null);

        assertThat(stageRun.getId()).isEqualTo(200L);
        verify(outboxDispatcher).enqueue(anyString(), anyString(), anyString(), anyLong());
        // startNextStage 只创建阶段运行并投递任务，不推进工作流状态（由协调器处理 TASK_CLAIMED）
        verify(workflowMapper).update(any(), any());
    }

    @Test
    void retryStage_usesNewInputHashToAvoidUniqueKeyCollision() {
        PersonCapabilityWorkflow workflow = new PersonCapabilityWorkflow();
        workflow.setId(1L);
        workflow.setEmpId(1L);
        workflow.setStatus(WorkflowStatusEnum.FAILED.getCode());
        when(workflowMapper.selectById(1L)).thenReturn(workflow);
        // 最近失败运行（attempt=1，inputHash=old-hash）
        PersonCapabilityStageRun failed = new PersonCapabilityStageRun();
        failed.setId(300L);
        failed.setWorkflowId(1L);
        failed.setStageType("AGGREGATE_HARNESS");
        failed.setStatus("FAILED_FINAL");
        failed.setInputHash("old-hash");
        failed.setInputSnapshotJson("{}");
        failed.setAttemptCount(1);
        when(stageRunMapper.selectOne(any())).thenReturn(failed, null);
        PersonCapabilityStageRun[] captured = new PersonCapabilityStageRun[1];
        when(stageRunMapper.insert((PersonCapabilityStageRun) any())).thenAnswer(inv -> {
            PersonCapabilityStageRun run = inv.getArgument(0);
            captured[0] = run;
            run.setId(301L);
            return 1;
        });
        when(stageRunMapper.updateById((PersonCapabilityStageRun) any())).thenReturn(1);
        when(stageRunMapper.update(any(), any())).thenReturn(1);

        service.retryStage(1L, "AGGREGATE_HARNESS", 9L);

        verify(stageRunMapper).insert(any(PersonCapabilityStageRun.class));
        PersonCapabilityStageRun newRun = captured[0];
        // 新 run 使用新哈希（避免与失败 run 的 uk_workflow_stage_input 冲突）
        assertThat(newRun.getInputHash()).isNotEqualTo("old-hash");
        assertThat(newRun.getAttemptCount()).isEqualTo(2);
        // retryStage 不再直接推进工作流状态：FAILED 恢复由协调器依据 USER_ACTION_STARTED 事件处理
        verify(workflowMapper, never()).update(any(), any());
    }

    @Test
    void retryAiTestGeneration_resetsFailedTaskBeforeRepublishing() {
        PersonCapabilityWorkflow workflow = new PersonCapabilityWorkflow();
        workflow.setId(1L);
        workflow.setStatus(WorkflowStatusEnum.RECOVERY_REQUIRED.getCode());
        when(workflowMapper.selectById(1L)).thenReturn(workflow);

        PersonCapabilityStageRun failed = new PersonCapabilityStageRun();
        failed.setId(300L);
        failed.setWorkflowId(1L);
        failed.setStageType("AI_TEST_GENERATION");
        failed.setStatus("FAILED_FINAL");
        failed.setInputHash("old-hash");
        failed.setInputSnapshotJson("{}");
        failed.setSourceRefType("AI_TEST");
        failed.setSourceRefId(46L);
        when(stageRunMapper.selectOne(any())).thenReturn(failed, null);
        when(stageRunMapper.insert((PersonCapabilityStageRun) any())).thenAnswer(inv -> {
            inv.getArgument(0, PersonCapabilityStageRun.class).setId(301L);
            return 1;
        });

        EmpAiTest test = new EmpAiTest();
        test.setId(46L);
        test.setGenerationState("FAILED");
        when(empAiTestMapper.selectById(46L)).thenReturn(test);
        when(empAiTestMapper.resetGenerationToPending(46L)).thenReturn(1);

        service.retryStage(1L, "AI_TEST_GENERATION", 9L);

        verify(empAiTestMapper).resetGenerationToPending(46L);
        verify(outboxDispatcher).enqueue(org.mockito.ArgumentMatchers.eq("AI_TEST"), anyString(),
                org.mockito.ArgumentMatchers.eq("ai.test.generate"), any());
    }
}
