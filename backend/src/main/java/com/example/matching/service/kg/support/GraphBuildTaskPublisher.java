package com.example.matching.service.kg.support;

import com.example.matching.config.RabbitMQConfig;
import com.example.matching.event.GraphBuildQueuedEvent;
import com.example.matching.service.common.EventOutboxDispatcher;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * 图谱构建任务发布器 — 在业务事务提交前将消息写入 Outbox。
 * 若 Outbox 写入失败，业务事务回滚，保证消息不丢失。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class GraphBuildTaskPublisher {

    private final EventOutboxDispatcher outboxDispatcher;

    @TransactionalEventListener(phase = TransactionPhase.BEFORE_COMMIT)
    public void publishBeforeCommit(GraphBuildQueuedEvent event) {
        outboxDispatcher.enqueue("KG_GRAPH_BUILD",
                RabbitMQConfig.MATCHING_EXCHANGE, "kg.graph.build.execute", event);
        log.info("图谱构建任务已写入Outbox: {}", event);
    }
}
