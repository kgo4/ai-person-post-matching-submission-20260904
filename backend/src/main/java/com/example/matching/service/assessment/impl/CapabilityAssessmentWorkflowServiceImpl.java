package com.example.matching.service.assessment.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.example.matching.common.enums.StageRunStatusEnum;
import com.example.matching.common.enums.StageTypeEnum;
import com.example.matching.common.enums.WorkflowStatusEnum;
import com.example.matching.config.RabbitMQConfig;
import com.example.matching.entity.workflow.CapabilityStageLifecycleEventLog;
import com.example.matching.entity.workflow.PersonCapabilityStageRun;
import com.example.matching.entity.workflow.PersonCapabilityWorkflow;
import com.example.matching.entity.employee.EmpAiTest;
import com.example.matching.mapper.employee.EmpAiTestMapper;
import com.example.matching.mapper.workflow.CapabilityStageLifecycleEventLogMapper;
import com.example.matching.mapper.workflow.PersonCapabilityStageRunMapper;
import com.example.matching.mapper.workflow.PersonCapabilityWorkflowMapper;
import com.example.matching.service.assessment.CapabilityAssessmentWorkflowService;
import com.example.matching.service.assessment.AssessmentAgentArtifactService;
import com.example.matching.dto.assessment.AgentMessageEnvelope;
import com.example.matching.service.common.EventOutboxDispatcher;
import com.example.matching.listener.AiTestTaskPayload;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 人员能力评估工作流服务实现
 * <p>
 * 负责流程状态机、阶段依赖、幂等、重试、状态查询。
 *
 * @author system
 */
@Slf4j
@Service
public class CapabilityAssessmentWorkflowServiceImpl implements CapabilityAssessmentWorkflowService {

    private static final int MAX_STAGE_FAILURE_MESSAGE_LENGTH = 1000;

    /** 评估阶段任务路由键 */
    public static final String ASSESSMENT_STAGE_ROUTING_KEY = "capability.assessment.stage.execute";

    /** 阶段前置依赖：stageType -> 前置 stageType */
    private static final Map<String, String> STAGE_PREREQUISITES = Map.of(
            StageTypeEnum.RESUME_CLAIM_EXTRACTION.getCode(), StageTypeEnum.RESUME_PARSE.getCode(),
            StageTypeEnum.AI_TEST_GENERATION.getCode(), StageTypeEnum.RESUME_CLAIM_EXTRACTION.getCode(),
            StageTypeEnum.AI_TEST_EVALUATION.getCode(), StageTypeEnum.AI_TEST_GENERATION.getCode(),
            StageTypeEnum.AI_INTERVIEW.getCode(), StageTypeEnum.AI_TEST_EVALUATION.getCode(),
            StageTypeEnum.AGGREGATE_HARNESS_AND_LEVEL_CONFIRMATION.getCode(), StageTypeEnum.AI_INTERVIEW.getCode()
    );

    /** 终态集合 */
    private static final Set<String> TERMINAL_STATUSES = Set.of(
            WorkflowStatusEnum.COMPLETED.getCode(),
            WorkflowStatusEnum.FAILED.getCode(),
            WorkflowStatusEnum.CANCELLED.getCode()
    );

    private final PersonCapabilityWorkflowMapper workflowMapper;
    private final PersonCapabilityStageRunMapper stageRunMapper;
    private final CapabilityStageLifecycleEventLogMapper eventLogMapper;
    private final EventOutboxDispatcher outboxDispatcher;
    private final EmpAiTestMapper empAiTestMapper;
    private AssessmentAgentArtifactService artifactService;

    @org.springframework.beans.factory.annotation.Autowired(required = false)
    void setArtifactService(AssessmentAgentArtifactService artifactService) {
        this.artifactService = artifactService;
    }

    @org.springframework.beans.factory.annotation.Autowired
    public CapabilityAssessmentWorkflowServiceImpl(
            PersonCapabilityWorkflowMapper workflowMapper,
            PersonCapabilityStageRunMapper stageRunMapper,
            CapabilityStageLifecycleEventLogMapper eventLogMapper,
            EventOutboxDispatcher outboxDispatcher,
            EmpAiTestMapper empAiTestMapper) {
        this.workflowMapper = workflowMapper;
        this.stageRunMapper = stageRunMapper;
        this.eventLogMapper = eventLogMapper;
        this.outboxDispatcher = outboxDispatcher;
        this.empAiTestMapper = empAiTestMapper;
    }

    /**
     * Compatibility constructor for lightweight integration fixtures that do not
     * exercise concrete AI-task retry handling.
     */
    public CapabilityAssessmentWorkflowServiceImpl(
            PersonCapabilityWorkflowMapper workflowMapper,
            PersonCapabilityStageRunMapper stageRunMapper,
            CapabilityStageLifecycleEventLogMapper eventLogMapper,
            EventOutboxDispatcher outboxDispatcher) {
        this(workflowMapper, stageRunMapper, eventLogMapper, outboxDispatcher, null);
    }

    @Override
    @Transactional
    public PersonCapabilityWorkflow getOrCreateActiveWorkflow(Long empId, Long operatorId) {
        PersonCapabilityWorkflow existing = getActiveWorkflow(empId);
        if (existing != null) {
            return existing;
        }
        PersonCapabilityWorkflow workflow = new PersonCapabilityWorkflow();
        workflow.setEmpId(empId);
        workflow.setStatus(WorkflowStatusEnum.RESUME_REQUIRED.getCode());
        workflow.setWorkflowVersion(1);
        workflow.setStartedAt(LocalDateTime.now());
        workflow.setCreatedBy(operatorId);
        try {
            workflowMapper.insert(workflow);
        } catch (DuplicateKeyException e) {
            // 历史唯一索引兜底已移除（终态 COMPLETED 纳入唯一会阻断员工二次评估完成）。
            // 并发重复创建概率极低，这里仍保留重查逻辑作为防御。
            PersonCapabilityWorkflow raced = getActiveWorkflow(empId);
            if (raced != null) {
                return raced;
            }
            throw e;
        }
        log.info("创建人员能力评估工作流: workflowId={}, empId={}", workflow.getId(), empId);
        return workflow;
    }

    @Override
    public PersonCapabilityWorkflow getActiveWorkflow(Long empId) {
        return workflowMapper.selectOne(new LambdaQueryWrapper<PersonCapabilityWorkflow>()
                .eq(PersonCapabilityWorkflow::getEmpId, empId)
                // 人工 Harness 审核属于独立治理门户。历史 REVIEW_REQUIRED 工作流
                // 不得阻止员工发起新的简历/测试/面试评估批次。
                // A failed assessment is recoverable. Returning it prevents a page refresh
                // from silently creating an empty workflow and losing the recovery context.
                .notIn(PersonCapabilityWorkflow::getStatus,
                        WorkflowStatusEnum.COMPLETED.getCode(), WorkflowStatusEnum.CANCELLED.getCode(),
                        WorkflowStatusEnum.REVIEW_REQUIRED.getCode())
                .orderByDesc(PersonCapabilityWorkflow::getId)
                .last("LIMIT 1"));
    }

    @Override
    public PersonCapabilityWorkflow getWorkflow(Long workflowId) {
        PersonCapabilityWorkflow workflow = workflowMapper.selectById(workflowId);
        if (workflow == null) {
            throw new IllegalArgumentException("工作流不存在: " + workflowId);
        }
        return workflow;
    }

    @Override
    public void bindPost(Long workflowId, Long postId) {
        workflowMapper.update(null, new LambdaUpdateWrapper<PersonCapabilityWorkflow>()
                .eq(PersonCapabilityWorkflow::getId, workflowId)
                .set(PersonCapabilityWorkflow::getPostId, postId)
                .set(PersonCapabilityWorkflow::getUpdatedTime, LocalDateTime.now()));
        log.info("工作流绑定目标岗位: workflowId={}, postId={}", workflowId, postId);
    }

    @Override
    public boolean transition(Long workflowId, String expectStatus, String targetStatus, String currentStage) {
        int updated = workflowMapper.update(null, new LambdaUpdateWrapper<PersonCapabilityWorkflow>()
                .eq(PersonCapabilityWorkflow::getId, workflowId)
                .eq(PersonCapabilityWorkflow::getStatus, expectStatus)
                .set(PersonCapabilityWorkflow::getStatus, targetStatus)
                .set(PersonCapabilityWorkflow::getCurrentStage, currentStage)
                .set(PersonCapabilityWorkflow::getUpdatedTime, LocalDateTime.now()));
        if (updated == 1) {
            log.info("工作流状态推进: workflowId={}, {} -> {} (stage={})",
                    workflowId, expectStatus, targetStatus, currentStage);
            return true;
        }
        log.warn("工作流状态CAS失败: workflowId={}, expect={}, actual={}",
                workflowId, expectStatus, currentStage);
        return false;
    }

    @Override
    @Transactional
    public PersonCapabilityStageRun createStageRun(Long workflowId, String stageType, String inputHash,
                                                   String inputSnapshotJson, String sourceRefType, Long sourceRefId) {
        // 幂等：同工作流+阶段+输入哈希 已存在则返回
        PersonCapabilityStageRun existing = stageRunMapper.selectOne(new LambdaQueryWrapper<PersonCapabilityStageRun>()
                .eq(PersonCapabilityStageRun::getWorkflowId, workflowId)
                .eq(PersonCapabilityStageRun::getStageType, stageType)
                .eq(PersonCapabilityStageRun::getInputHash, inputHash)
                .last("LIMIT 1"));
        if (existing != null) {
            return existing;
        }
        PersonCapabilityStageRun stageRun = new PersonCapabilityStageRun();
        stageRun.setWorkflowId(workflowId);
        stageRun.setStageType(stageType);
        stageRun.setStatus("PENDING");
        stageRun.setInputHash(inputHash);
        stageRun.setInputSnapshotJson(inputSnapshotJson);
        stageRun.setSourceRefType(sourceRefType);
        stageRun.setSourceRefId(sourceRefId);
        stageRun.setAttemptCount(0);
        try {
            stageRunMapper.insert(stageRun);
        } catch (DuplicateKeyException e) {
            // 并发插入：唯一索引兜底
            PersonCapabilityStageRun raced = stageRunMapper.selectOne(new LambdaQueryWrapper<PersonCapabilityStageRun>()
                    .eq(PersonCapabilityStageRun::getWorkflowId, workflowId)
                    .eq(PersonCapabilityStageRun::getStageType, stageType)
                    .eq(PersonCapabilityStageRun::getInputHash, inputHash)
                    .last("LIMIT 1"));
            if (raced != null) {
                return raced;
            }
            throw e;
        }
        return stageRun;
    }

    @Override
    public boolean claimStageRun(Long stageRunId) {
        int updated = stageRunMapper.update(null, new LambdaUpdateWrapper<PersonCapabilityStageRun>()
                .eq(PersonCapabilityStageRun::getId, stageRunId)
                .eq(PersonCapabilityStageRun::getStatus, "PENDING")
                .set(PersonCapabilityStageRun::getStatus, "RUNNING")
                .set(PersonCapabilityStageRun::getStartedAt, LocalDateTime.now()));
        return updated == 1;
    }

    @Override
    public void markStageSucceeded(Long stageRunId, String outputSnapshotJson) {
        PersonCapabilityStageRun stageRun = stageRunMapper.selectById(stageRunId);
        if (stageRun == null) {
            return;
        }
        int updated = stageRunMapper.update(null, new LambdaUpdateWrapper<PersonCapabilityStageRun>()
                .eq(PersonCapabilityStageRun::getId, stageRunId)
                .in(PersonCapabilityStageRun::getStatus, "RUNNING", "PENDING", "FAILED_RETRYABLE")
                .set(PersonCapabilityStageRun::getStatus, "SUCCEEDED")
                .set(PersonCapabilityStageRun::getOutputSnapshotJson, outputSnapshotJson)
                .set(PersonCapabilityStageRun::getCompletedAt, LocalDateTime.now())
                .set(PersonCapabilityStageRun::getUpdatedTime, LocalDateTime.now()));
        if (updated == 1) {
            log.info("阶段运行成功: stageRunId={}, type={}", stageRunId, stageRun.getStageType());
        }
    }

    @Override
    public void markStageFailed(Long stageRunId, String failureCode, String failureMessage, boolean finalFailure) {
        PersonCapabilityStageRun stageRun = stageRunMapper.selectById(stageRunId);
        if (stageRun == null) {
            return;
        }
        String targetStatus = finalFailure ? "FAILED_FINAL" : "FAILED_RETRYABLE";
        String persistedMessage = normalizeFailureMessage(failureMessage);
        int updated = stageRunMapper.update(null, new LambdaUpdateWrapper<PersonCapabilityStageRun>()
                .eq(PersonCapabilityStageRun::getId, stageRunId)
                .set(PersonCapabilityStageRun::getStatus, targetStatus)
                .set(PersonCapabilityStageRun::getFailureCode, failureCode)
                .set(PersonCapabilityStageRun::getFailureMessage, persistedMessage)
                .set(PersonCapabilityStageRun::getCompletedAt, LocalDateTime.now())
                .set(PersonCapabilityStageRun::getUpdatedTime, LocalDateTime.now()));
        if (updated == 1) {
            log.warn("阶段运行失败: stageRunId={}, type={}, final={}, code={}",
                    stageRunId, stageRun.getStageType(), finalFailure, failureCode);
            if (finalFailure) {
                failWorkflow(stageRun.getWorkflowId(), "阶段[" + stageRun.getStageType() + "]最终失败: " + persistedMessage);
            }
        }
    }

    @Override
    public void failWorkflow(Long workflowId, String reason) {
        workflowMapper.update(null, new LambdaUpdateWrapper<PersonCapabilityWorkflow>()
                .eq(PersonCapabilityWorkflow::getId, workflowId)
                .notIn(PersonCapabilityWorkflow::getStatus,
                        WorkflowStatusEnum.COMPLETED.getCode(), WorkflowStatusEnum.CANCELLED.getCode())
                .set(PersonCapabilityWorkflow::getStatus, WorkflowStatusEnum.RECOVERY_REQUIRED.getCode())
                .set(PersonCapabilityWorkflow::getFailedReason, reason)
                .set(PersonCapabilityWorkflow::getCompletedAt, null)
                .set(PersonCapabilityWorkflow::getUpdatedTime, LocalDateTime.now()));
    }

    @Override
    public void completeWorkflow(Long workflowId) {
        workflowMapper.update(null, new LambdaUpdateWrapper<PersonCapabilityWorkflow>()
                .eq(PersonCapabilityWorkflow::getId, workflowId)
                .set(PersonCapabilityWorkflow::getStatus, WorkflowStatusEnum.COMPLETED.getCode())
                .set(PersonCapabilityWorkflow::getCurrentStage, StageTypeEnum.AGGREGATE_HARNESS_AND_LEVEL_CONFIRMATION.getCode())
                .set(PersonCapabilityWorkflow::getCompletedAt, LocalDateTime.now())
                .set(PersonCapabilityWorkflow::getUpdatedTime, LocalDateTime.now()));
        log.info("工作流完成: workflowId={}", workflowId);
    }

    @Override
    public PersonCapabilityStageRun getLatestStageRun(Long workflowId, String stageType) {
        return stageRunMapper.selectOne(new LambdaQueryWrapper<PersonCapabilityStageRun>()
                .eq(PersonCapabilityStageRun::getWorkflowId, workflowId)
                .eq(PersonCapabilityStageRun::getStageType, stageType)
                .orderByDesc(PersonCapabilityStageRun::getId)
                .last("LIMIT 1"));
    }

    @Override
    @Transactional
    public void retryStage(Long workflowId, String stageType, Long operatorId) {
        PersonCapabilityWorkflow workflow = getWorkflow(workflowId);
        if (WorkflowStatusEnum.COMPLETED.getCode().equals(workflow.getStatus())) {
            throw new IllegalStateException("工作流已完成，不可重试: " + workflowId);
        }
        // 找到该阶段最近一次失败运行
        PersonCapabilityStageRun lastFailed = stageRunMapper.selectOne(new LambdaQueryWrapper<PersonCapabilityStageRun>()
                .eq(PersonCapabilityStageRun::getWorkflowId, workflowId)
                .eq(PersonCapabilityStageRun::getStageType, stageType)
                .in(PersonCapabilityStageRun::getStatus, "FAILED_RETRYABLE", "FAILED_FINAL")
                .orderByDesc(PersonCapabilityStageRun::getId)
                .last("LIMIT 1"));
        if (lastFailed == null) {
            // Repair an already-created retry run whose concrete AI task was not published.
            PersonCapabilityStageRun pending = stageRunMapper.selectOne(new LambdaQueryWrapper<PersonCapabilityStageRun>()
                    .eq(PersonCapabilityStageRun::getWorkflowId, workflowId)
                    .eq(PersonCapabilityStageRun::getStageType, stageType)
                    .in(PersonCapabilityStageRun::getStatus, "PENDING", "RUNNING")
                    .orderByDesc(PersonCapabilityStageRun::getId)
                    .last("LIMIT 1"));
            if (pending != null) {
                resetFailedAiTestForRetry(pending);
                dispatchRetryTask(pending);
                log.info("Repaired undispatched assessment task: workflowId={}, stage={}, stageRunId={}",
                        workflowId, stageType, pending.getId());
                return;
            }
            throw new IllegalStateException("未找到可重试的失败阶段: workflowId=" + workflowId + ", stage=" + stageType);
        }
        // 失败运行保持历史，新 StageRun 使用新输入哈希（追加尝试序号避免与唯一键冲突）
        int attempt = (lastFailed.getAttemptCount() == null ? 0 : lastFailed.getAttemptCount()) + 1;
        String newHash = hashInput(lastFailed.getInputHash(), String.valueOf(attempt), String.valueOf(System.nanoTime()));
        PersonCapabilityStageRun newRun = createStageRun(workflowId, stageType,
                newHash, lastFailed.getInputSnapshotJson(),
                lastFailed.getSourceRefType(), lastFailed.getSourceRefId());
        newRun.setAttemptCount(attempt);
        stageRunMapper.updateById(newRun);
        resetFailedAiTestForRetry(newRun);
        // 不再直接推进工作流：工作流若为 FAILED，由协调器依据 USER_ACTION_STARTED 事件恢复
        // （业务服务只创建新阶段运行并投递任务）
        dispatchRetryTask(newRun);
        log.info("重试失败阶段，已创建新阶段运行并投递任务: workflowId={}, stage={}, attempt={}",
                workflowId, stageType, attempt);
    }

    @Override
    public void assertStagePrerequisite(Long workflowId, String stageType) {
        String prerequisite = STAGE_PREREQUISITES.get(stageType);
        if (prerequisite == null) {
            return; // 初始阶段无前置
        }
        Long count = stageRunMapper.selectCount(new LambdaQueryWrapper<PersonCapabilityStageRun>()
                .eq(PersonCapabilityStageRun::getWorkflowId, workflowId)
                .eq(PersonCapabilityStageRun::getStageType, prerequisite)
                .eq(PersonCapabilityStageRun::getStatus, "SUCCEEDED"));
        if (count == null || count == 0) {
            throw new IllegalStateException("前置阶段未完成: " + prerequisite + "，无法进入 " + stageType);
        }
    }

    @Override
    @Transactional
    public PersonCapabilityStageRun startNextStage(Long workflowId, String stageType, String inputHash,
                                                   String inputSnapshotJson, Long operatorId) {
        assertStagePrerequisite(workflowId, stageType);
        // 仅创建阶段运行（PENDING）并投递任务；工作流状态由协调器依据 TASK_CLAIMED 事件推进
        PersonCapabilityStageRun stageRun = createStageRun(workflowId, stageType,
                inputHash, inputSnapshotJson, null, null);
        // 更新工作流活跃阶段运行
        workflowMapper.update(null, new LambdaUpdateWrapper<PersonCapabilityWorkflow>()
                .eq(PersonCapabilityWorkflow::getId, workflowId)
                .set(PersonCapabilityWorkflow::getActiveStageRunId, stageRun.getId())
                .set(PersonCapabilityWorkflow::getUpdatedTime, LocalDateTime.now()));
        dispatchStageTask(stageRun);
        return stageRun;
    }

    /**
     * 通过 Outbox 投递阶段执行任务（不阻塞、可靠投递）。
     */
    /**
     * Retry concrete AI work on its own queue. A stage-only retry cannot regenerate
     * test questions when the capability-stage queue is delayed or dead-lettered.
     */
    private void dispatchRetryTask(PersonCapabilityStageRun stageRun) {
        if ("AI_TEST_GENERATION".equals(stageRun.getStageType())
                || "AI_TEST_EVALUATION".equals(stageRun.getStageType())) {
            String taskType = "AI_TEST_GENERATION".equals(stageRun.getStageType())
                    ? "GENERATE" : "EVALUATE";
            String routingKey = "AI_TEST_GENERATION".equals(stageRun.getStageType())
                    ? "ai.test.generate" : "ai.test.evaluate";
            AiTestTaskPayload payload = new AiTestTaskPayload(taskType, stageRun.getSourceRefId());
            if (artifactService != null) {
                AgentMessageEnvelope envelope = artifactService.storePayload(stageRun.getWorkflowId(), stageRun.getId(),
                        "AI_TEST_TASK", payload, null, null);
                outboxDispatcher.enqueue("AI_TEST", RabbitMQConfig.MATCHING_EXCHANGE, routingKey, envelope);
            } else {
                outboxDispatcher.enqueue("AI_TEST", RabbitMQConfig.MATCHING_EXCHANGE, routingKey, payload);
            }
            log.info("AI test retry task queued: stageRunId={}, type={}, testId={}",
                    stageRun.getId(), taskType, stageRun.getSourceRefId());
            return;
        }
        dispatchStageTask(stageRun);
    }

    private void resetFailedAiTestForRetry(PersonCapabilityStageRun stageRun) {
        boolean generation = "AI_TEST_GENERATION".equals(stageRun.getStageType());
        boolean evaluation = "AI_TEST_EVALUATION".equals(stageRun.getStageType());
        if (!generation && !evaluation) {
            return;
        }
        if (empAiTestMapper == null) {
            throw new IllegalStateException("AI测试重试依赖未配置: empAiTestMapper");
        }
        if (stageRun.getSourceRefId() == null) {
            throw new IllegalStateException("AI测试重试缺少测试记录: stageRunId=" + stageRun.getId());
        }
        EmpAiTest test = empAiTestMapper.selectById(stageRun.getSourceRefId());
        if (test == null) {
            throw new IllegalStateException("AI测试重试记录不存在: testId=" + stageRun.getSourceRefId());
        }
        String taskState = generation ? test.getGenerationState() : test.getEvaluationState();
        if (!"FAILED".equals(taskState)) {
            return;
        }
        int updated = generation
                ? empAiTestMapper.resetGenerationToPending(test.getId())
                : empAiTestMapper.resetEvaluationToPending(test.getId());
        if (updated != 1) {
            throw new IllegalStateException("AI测试重试状态复位失败: testId=" + test.getId());
        }
        log.info("AI test retry state reset: stageRunId={}, testId={}, type={}",
                stageRun.getId(), test.getId(), generation ? "GENERATE" : "EVALUATE");
    }

    private void dispatchStageTask(PersonCapabilityStageRun stageRun) {
        if (artifactService != null) {
            AgentMessageEnvelope envelope = artifactService.storeStageTask(
                    stageRun.getWorkflowId(), stageRun.getId(), stageRun.getStageType(),
                    stageRun.getInputHash(), "ABILITY_TAG_TREE_V1");
            outboxDispatcher.enqueue("CAPABILITY_ASSESSMENT_STAGE",
                    RabbitMQConfig.MATCHING_EXCHANGE, ASSESSMENT_STAGE_ROUTING_KEY, envelope);
            log.info("评估阶段 A2A Artifact 已写入并投递: stageRunId={}, payloadRef={}",
                    stageRun.getId(), envelope.payloadRef());
            return;
        }
        outboxDispatcher.enqueue("CAPABILITY_ASSESSMENT_STAGE",
                RabbitMQConfig.MATCHING_EXCHANGE, ASSESSMENT_STAGE_ROUTING_KEY, stageRun.getId());
        log.info("评估阶段任务已写入Outbox: stageRunId={}, type={}", stageRun.getId(), stageRun.getStageType());
    }

    /**
     * 计算输入哈希（幂等键）。
     */
    public static String hashInput(String... parts) {
        try {
            MessageDigest digest = MessageDigest.getInstance("MD5");
            StringBuilder sb = new StringBuilder();
            for (String part : parts) {
                sb.append(part == null ? "" : part).append('|');
            }
            byte[] bytes = digest.digest(sb.toString().getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(bytes);
        } catch (Exception e) {
            throw new IllegalStateException("输入哈希计算失败", e);
        }
    }

    @Override
    public PersonCapabilityStageRun getStageRun(Long stageRunId) {
        return stageRunMapper.selectById(stageRunId);
    }

    @Override
    public PersonCapabilityStageRun resolveActiveStageRun(Long workflowId, String stageType,
                                                          String sourceRefType, Long sourceRefId) {
        LambdaQueryWrapper<PersonCapabilityStageRun> qw = new LambdaQueryWrapper<PersonCapabilityStageRun>()
                .eq(PersonCapabilityStageRun::getWorkflowId, workflowId)
                .eq(PersonCapabilityStageRun::getStageType, stageType)
                .in(PersonCapabilityStageRun::getStatus,
                        StageRunStatusEnum.ACTIVE_STATUSES.stream().map(StageRunStatusEnum::getCode).toList())
                .orderByDesc(PersonCapabilityStageRun::getId)
                .last("LIMIT 1");
        if (sourceRefType != null) {
            qw.eq(PersonCapabilityStageRun::getSourceRefType, sourceRefType);
            if (sourceRefId != null) {
                qw.eq(PersonCapabilityStageRun::getSourceRefId, sourceRefId);
            }
        }
        return stageRunMapper.selectOne(qw);
    }

    @Override
    public boolean casStageRunStatus(Long stageRunId, String expectStatus, String targetStatus,
                                     String failureCode, String failureMessage) {
        String persistedMessage = normalizeFailureMessage(failureMessage);
        int updated = stageRunMapper.update(null, new LambdaUpdateWrapper<PersonCapabilityStageRun>()
                .eq(PersonCapabilityStageRun::getId, stageRunId)
                .eq(PersonCapabilityStageRun::getStatus, expectStatus)
                .set(PersonCapabilityStageRun::getStatus, targetStatus)
                .set(PersonCapabilityStageRun::getUpdatedTime, LocalDateTime.now())
                .set(StageRunStatusEnum.SUCCEEDED.getCode().equals(targetStatus)
                                || StageRunStatusEnum.FAILED_FINAL.getCode().equals(targetStatus),
                        PersonCapabilityStageRun::getCompletedAt, LocalDateTime.now())
                .set(failureCode != null, PersonCapabilityStageRun::getFailureCode, failureCode)
                .set(failureMessage != null, PersonCapabilityStageRun::getFailureMessage, persistedMessage));
        return updated == 1;
    }

    private String normalizeFailureMessage(String message) {
        if (message == null) {
            return null;
        }
        String normalized = message.trim();
        return normalized.length() <= MAX_STAGE_FAILURE_MESSAGE_LENGTH
                ? normalized
                : normalized.substring(0, MAX_STAGE_FAILURE_MESSAGE_LENGTH);
    }

    @Override
    public void syncActiveStageRun(Long workflowId, Long stageRunId) {
        workflowMapper.update(null, new LambdaUpdateWrapper<PersonCapabilityWorkflow>()
                .eq(PersonCapabilityWorkflow::getId, workflowId)
                .set(PersonCapabilityWorkflow::getActiveStageRunId, stageRunId)
                .set(PersonCapabilityWorkflow::getUpdatedTime, LocalDateTime.now()));
    }

    @Override
    public void markWorkflowFinalFailed(Long workflowId, String failedReason) {
        workflowMapper.update(null, new LambdaUpdateWrapper<PersonCapabilityWorkflow>()
                .eq(PersonCapabilityWorkflow::getId, workflowId)
                .notIn(PersonCapabilityWorkflow::getStatus,
                        WorkflowStatusEnum.COMPLETED.getCode(), WorkflowStatusEnum.CANCELLED.getCode())
                .set(PersonCapabilityWorkflow::getStatus, WorkflowStatusEnum.RECOVERY_REQUIRED.getCode())
                .set(PersonCapabilityWorkflow::getFailedReason, failedReason)
                .set(PersonCapabilityWorkflow::getCompletedAt, null)
                .set(PersonCapabilityWorkflow::getUpdatedTime, LocalDateTime.now()));
    }

    @Override
    public boolean recordLifecycleEventLog(com.example.matching.entity.workflow.CapabilityStageLifecycleEventLog logRecord) {
        try {
            return eventLogMapper.insert(logRecord) == 1;
        } catch (org.springframework.dao.DuplicateKeyException e) {
            // 并发重复：另一消费者已处理
            return false;
        }
    }

    @Override
    public boolean claimLifecycleEventLog(CapabilityStageLifecycleEventLog logRecord) {
        return recordLifecycleEventLog(logRecord);
    }

    @Override
    public boolean completeLifecycleEventLog(CapabilityStageLifecycleEventLog logRecord) {
        if (logRecord == null || logRecord.getEventId() == null) {
            return false;
        }
        return eventLogMapper.update(logRecord, new LambdaUpdateWrapper<CapabilityStageLifecycleEventLog>()
                .eq(CapabilityStageLifecycleEventLog::getEventId, logRecord.getEventId())) == 1;
    }

    @Override
    public boolean existsLifecycleEvent(String eventId) {
        Long count = eventLogMapper.selectCount(new LambdaQueryWrapper<CapabilityStageLifecycleEventLog>()
                .eq(CapabilityStageLifecycleEventLog::getEventId, eventId));
        return count != null && count > 0;
    }

    @Override
    public boolean hasRecordedLifecycleEvent(Long stageRunId, String eventType) {
        Long count = eventLogMapper.selectCount(new LambdaQueryWrapper<CapabilityStageLifecycleEventLog>()
                .eq(CapabilityStageLifecycleEventLog::getStageRunId, stageRunId)
                .eq(CapabilityStageLifecycleEventLog::getEventType, eventType));
        return count != null && count > 0;
    }

    @Override
    public List<PersonCapabilityStageRun> listStageRuns(Long workflowId) {
        return stageRunMapper.selectList(new LambdaQueryWrapper<PersonCapabilityStageRun>()
                .eq(PersonCapabilityStageRun::getWorkflowId, workflowId)
                .orderByAsc(PersonCapabilityStageRun::getId));
    }
}
