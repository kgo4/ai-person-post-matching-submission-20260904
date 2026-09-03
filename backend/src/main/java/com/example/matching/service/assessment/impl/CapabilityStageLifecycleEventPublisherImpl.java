package com.example.matching.service.assessment.impl;

import com.example.matching.config.RabbitMQConfig;
import com.example.matching.event.CapabilityStageLifecycleEvent;
import com.example.matching.port.assessment.CapabilityStageLifecycleEventPublisher;
import com.example.matching.service.common.EventOutboxDispatcher;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;

/**
 * 能力评估阶段生命周期事件发布器实现
 * <p>
 * 双通道发布：
 * <ol>
 *   <li>Outbox 持久化后投递 RabbitMQ（可靠链路，最终一致性）；</li>
 *   <li>进程内 Spring 事件加速（协调器 AFTER_COMMIT 同步处理，eventId 幂等）。</li>
 * </ol>
 * 进程内事件只作加速，不承担最终一致性。
 *
 * @author system
 */
@Slf4j
@Service
public class CapabilityStageLifecycleEventPublisherImpl implements CapabilityStageLifecycleEventPublisher {

    /** 生命周期事件路由键 */
    public static final String LIFECYCLE_ROUTING_KEY = "capability.assessment.lifecycle.execute";

    private final EventOutboxDispatcher outboxDispatcher;
    private final ApplicationEventPublisher applicationEventPublisher;

    public CapabilityStageLifecycleEventPublisherImpl(
            EventOutboxDispatcher outboxDispatcher,
            ApplicationEventPublisher applicationEventPublisher) {
        this.outboxDispatcher = outboxDispatcher;
        this.applicationEventPublisher = applicationEventPublisher;
    }

    @Override
    public void publish(CapabilityStageLifecycleEvent event) {
        if (event == null || event.eventId() == null) {
            log.warn("生命周期事件为空或缺少 eventId，丢弃");
            return;
        }
        // 可靠链路：业务事务内写入 Outbox，由调度器投递 RabbitMQ
        outboxDispatcher.enqueue("CAPABILITY_STAGE_LIFECYCLE",
                RabbitMQConfig.MATCHING_EXCHANGE, LIFECYCLE_ROUTING_KEY, event);
        // 进程内加速：协调器 AFTER_COMMIT 监听，eventId 幂等，重复无害
        applicationEventPublisher.publishEvent(event);
        log.info("生命周期事件已发布: eventId={}, workflowId={}, stage={}, type={}",
                event.eventId(), event.workflowId(), event.stageType(), event.eventType());
    }
}
