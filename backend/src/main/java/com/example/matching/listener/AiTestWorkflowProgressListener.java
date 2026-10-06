package com.example.matching.listener;

import com.example.matching.common.enums.StageLifecycleEventType;
import com.example.matching.event.AiTestQuestionsGeneratedEvent;
import com.example.matching.event.AiTestQuestionsGenerationFailedEvent;
import com.example.matching.event.CapabilityStageLifecycleEvent;
import com.example.matching.port.assessment.CapabilityStageLifecycleEventPublisher;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * 同步工作流测试题目生成的真实进度（生命周期事件转发）
 * <p>
 * 不再直接调用工作流状态推进，而是将题目生成结果转换为统一的
 * CapabilityStageLifecycleEvent，由协调器依据状态转换表推进。
 *
 * @author system
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AiTestWorkflowProgressListener {

    private final CapabilityStageLifecycleEventPublisher lifecycleEventPublisher;

    @EventListener
    public void onQuestionsGenerated(AiTestQuestionsGeneratedEvent event) {
        if (event.workflowId() == null) {
            return;
        }
        try {
            // 题目生成成功：AI_TEST_GENERATION 阶段成功 -> 协调器推进 TEST_GENERATING -> TEST_IN_PROGRESS
            lifecycleEventPublisher.publish(CapabilityStageLifecycleEvent.of(
                    event.workflowId(), null, "AI_TEST_GENERATION",
                    "AI_TEST", event.testId(), StageLifecycleEventType.TASK_SUCCEEDED, null, null));
        } catch (Exception e) {
            log.error("发布测试题目生成完成生命周期事件失败: workflowId={}, testId={}",
                    event.workflowId(), event.testId(), e);
        }
    }

    @EventListener
    public void onQuestionsGenerationFailed(AiTestQuestionsGenerationFailedEvent event) {
        if (event.workflowId() == null) {
            return;
        }
        try {
            // 题目生成最终失败：协调器将工作流置 FAILED 并写入 failedReason
            lifecycleEventPublisher.publish(CapabilityStageLifecycleEvent.failedFinal(
                    event.workflowId(), null, "AI_TEST_GENERATION",
                    "AI_TEST", event.testId(), "AI_TEST_GENERATION_FAILED", event.errorMessage()));
        } catch (Exception e) {
            log.error("发布测试题目生成失败生命周期事件失败: workflowId={}, testId={}",
                    event.workflowId(), event.testId(), e);
        }
    }
}
