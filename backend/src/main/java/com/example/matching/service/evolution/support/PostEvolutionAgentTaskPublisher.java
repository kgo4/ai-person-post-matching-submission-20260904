package com.example.matching.service.evolution.support;

import com.example.matching.config.RabbitMQConfig;
import com.example.matching.event.PostEvolutionAgentQueuedEvent;
import com.example.matching.service.common.EventOutboxDispatcher;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * 岗位演化 Agent 任务发布器 — 使用 Outbox 模式保证可靠投递。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PostEvolutionAgentTaskPublisher {

    private static final String ROUTING_KEY = "post.evolution.agent.execute";

    private final EventOutboxDispatcher outboxDispatcher;

    @TransactionalEventListener(phase = TransactionPhase.BEFORE_COMMIT)
    public void publishBeforeCommit(PostEvolutionAgentQueuedEvent event) {
        outboxDispatcher.enqueue("POST_EVOLUTION_AGENT",
                RabbitMQConfig.MATCHING_EXCHANGE, ROUTING_KEY, event);
        log.info("岗位演化 Agent 任务已写入Outbox: taskId={}", event.taskId());
    }
}
