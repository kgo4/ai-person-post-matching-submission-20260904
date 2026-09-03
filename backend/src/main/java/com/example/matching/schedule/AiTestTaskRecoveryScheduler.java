package com.example.matching.schedule;

import com.example.matching.config.RabbitMQConfig;
import com.example.matching.entity.employee.EmpAiTest;
import com.example.matching.event.CapabilityStageLifecycleEvent;
import com.example.matching.listener.AiTestTaskPayload;
import com.example.matching.mapper.employee.EmpAiTestMapper;
import com.example.matching.port.assessment.CapabilityStageLifecycleEventPublisher;
import com.example.matching.service.common.EventOutboxDispatcher;
import com.example.matching.service.assessment.AssessmentAgentArtifactService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;

/**
 * AI 测试任务僵尸恢复扫描器
 * <p>
 * 每分钟扫描超过两分钟的 PROCESSING 任务：
 * <ul>
 *   <li>重试次数未达上限：回到 PENDING 并递增次数后重新投递</li>
 *   <li>重试次数已达上限：进入 FAILED，不再自动投递</li>
 * </ul>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AiTestTaskRecoveryScheduler {

    /** PROCESSING 超过该时长视为僵尸 */
    private static final long STALE_MINUTES = 2;

    /** 最大自动重试次数（与 EmpAiTestMapper 的 retry_count < 3 一致） */
    private static final int MAX_RETRY_COUNT = 3;

    private final EmpAiTestMapper empAiTestMapper;
    private final EventOutboxDispatcher outboxDispatcher;
    private final CapabilityStageLifecycleEventPublisher lifecycleEventPublisher;
    private AssessmentAgentArtifactService artifactService;

    @org.springframework.beans.factory.annotation.Autowired(required = false)
    void setArtifactService(AssessmentAgentArtifactService value) { this.artifactService = value; }

    @org.springframework.beans.factory.annotation.Autowired(required = false)
    private ScheduledTaskRunner taskRunner;

    @Scheduled(fixedDelay = 60_000, initialDelay = 60_000)
    public void recoverZombieTasks() {
        runScheduled("ai_test_task_recovery", this::recoverZombieTasksInternal);
    }

    private void recoverZombieTasksInternal() {
        LocalDateTime before = LocalDateTime.now().minusMinutes(STALE_MINUTES);
        Exception failure = null;

        try {
            List<EmpAiTest> generationZombies = empAiTestMapper.selectZombieGeneration(before);
            for (EmpAiTest test : generationZombies) {
                recoverGeneration(test);
            }
        } catch (Exception e) {
            failure = e;
        }

        try {
            List<EmpAiTest> evaluationZombies = empAiTestMapper.selectZombieEvaluation(before);
            for (EmpAiTest test : evaluationZombies) {
                recoverEvaluation(test);
            }
        } catch (Exception e) {
            if (failure != null) {
                failure.addSuppressed(e);
            } else {
                failure = e;
            }
        }
        if (failure != null) {
            throw new IllegalStateException("AI 测试僵尸任务恢复失败", failure);
        }
    }

    private void runScheduled(String taskName, Runnable task) {
        if (taskRunner != null) {
            taskRunner.run(taskName, task);
            return;
        }
        try {
            task.run();
        } catch (Exception e) {
            log.error("AI 测试僵尸任务恢复失败", e);
        }
    }

    private void recoverGeneration(EmpAiTest test) {
        int retryCount = test.getRetryCount() != null ? test.getRetryCount() : 0;
        if (retryCount >= MAX_RETRY_COUNT) {
            int updated = empAiTestMapper.failGeneration(test.getId(), "ZOMBIE_RECOVERY",
                    "处理超时且重试次数已耗尽");
            if (updated == 1) {
                publishFinalFailure(test, "AI_TEST_GENERATION", "AI_TEST_GENERATION_ZOMBIE");
                log.error("AI测试题目生成僵尸恢复失败（重试耗尽），标记 FAILED: testId={}", test.getId());
            }
            return;
        }

        int updated = empAiTestMapper.recoverGeneration(test.getId(), "ZOMBIE_RECOVERY", "处理超时，自动恢复重试");
        if (updated != 1) {
            log.debug("AI测试题目生成状态已变化，跳过恢复: testId={}", test.getId());
            return;
        }
        enqueue(test, "ai.test.generate", new AiTestTaskPayload("GENERATE", test.getId()));
        log.warn("AI测试题目生成僵尸恢复并重新投递: testId={}, retryCount={}", test.getId(), retryCount + 1);
    }

    private void recoverEvaluation(EmpAiTest test) {
        int retryCount = test.getRetryCount() != null ? test.getRetryCount() : 0;
        if (retryCount >= MAX_RETRY_COUNT) {
            int updated = empAiTestMapper.failEvaluation(test.getId(), "ZOMBIE_RECOVERY",
                    "处理超时且重试次数已耗尽");
            if (updated == 1) {
                publishFinalFailure(test, "AI_TEST_EVALUATION", "AI_TEST_EVALUATION_ZOMBIE");
                log.error("AI测试评分僵尸恢复失败（重试耗尽），标记 FAILED: testId={}", test.getId());
            }
            return;
        }

        int updated = empAiTestMapper.recoverEvaluation(test.getId(), "ZOMBIE_RECOVERY", "处理超时，自动恢复重试");
        if (updated != 1) {
            log.debug("AI测试评分状态已变化，跳过恢复: testId={}", test.getId());
            return;
        }
        enqueue(test, "ai.test.evaluate", new AiTestTaskPayload("EVALUATE", test.getId()));
        log.warn("AI测试评分僵尸恢复并重新投递: testId={}, retryCount={}", test.getId(), retryCount + 1);
    }

    private void enqueue(EmpAiTest test, String routingKey, AiTestTaskPayload payload) {
        if (test.getWorkflowId() != null && artifactService != null) {
            var envelope = artifactService.storePayload(test.getWorkflowId(), null, "AI_TEST_TASK", payload, null, null);
            outboxDispatcher.enqueue("AI_TEST", RabbitMQConfig.MATCHING_EXCHANGE, routingKey, envelope);
        } else {
            outboxDispatcher.enqueue("AI_TEST", RabbitMQConfig.MATCHING_EXCHANGE, routingKey, payload);
        }
    }

    private void publishFinalFailure(EmpAiTest test, String stageType, String errorCode) {
        if (test.getWorkflowId() == null) {
            return;
        }
        lifecycleEventPublisher.publish(CapabilityStageLifecycleEvent.failedFinal(
                test.getWorkflowId(), null, stageType, "AI_TEST", test.getId(),
                errorCode, "AI测试任务处理超时且重试次数已耗尽"));
    }
}
