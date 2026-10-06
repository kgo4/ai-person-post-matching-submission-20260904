package com.example.matching.service.kg.support;

import com.example.matching.config.RabbitMQConfig;
import com.example.matching.event.GraphChangeSetQueuedEvent;
import com.example.matching.service.common.EventOutboxDispatcher;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * 图谱变更集发布器 — 使用 Outbox 模式保证可靠投递。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class GraphChangeSetPublisher {

    private final EventOutboxDispatcher outboxDispatcher;

    @TransactionalEventListener(phase = TransactionPhase.BEFORE_COMMIT, fallbackExecution = true)
    public void publishBeforeCommit(GraphChangeSetQueuedEvent event) {
        outboxDispatcher.enqueue("KG_GRAPH_CHANGE_SET",
                RabbitMQConfig.MATCHING_EXCHANGE, "kg.graph.change.execute", event);
        log.info("图谱变更集已写入Outbox: {}", event.changeCode());
    }
}
