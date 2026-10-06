package com.example.matching.listener;

import com.example.matching.config.RabbitMQConfig;
import com.example.matching.event.CapabilityStageLifecycleEvent;
import com.example.matching.service.assessment.CapabilityAssessmentLifecycleCoordinator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

/**
 * 能力评估生命周期事件消费者
 * <p>
 * 消费 Outbox 投递的生命周期事件（capability.assessment.lifecycle.execute），
 * 转交 CapabilityAssessmentLifecycleCoordinator 统一推进状态。
 * 协调器内部按 eventId 幂等去重，重复消息不会重复推进。
 *
 * @author system
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class CapabilityStageLifecycleListener {

    private final CapabilityAssessmentLifecycleCoordinator coordinator;

    @RabbitListener(queues = RabbitMQConfig.CAPABILITY_ASSESSMENT_LIFECYCLE_QUEUE)
    public void onLifecycleEvent(CapabilityStageLifecycleEvent event) {
        if (event == null || event.eventId() == null) {
            log.warn("能力评估生命周期事件参数为空，跳过");
            return;
        }
        try {
            coordinator.handle(event);
        } catch (Exception e) {
            log.error("能力评估生命周期事件处理异常: eventId={}, error={}", event.eventId(), e.getMessage(), e);
        }
    }
}
