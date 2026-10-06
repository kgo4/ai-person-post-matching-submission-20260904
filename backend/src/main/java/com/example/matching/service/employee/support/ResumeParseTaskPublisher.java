package com.example.matching.service.employee.support;

import com.example.matching.config.RabbitMQConfig;
import com.example.matching.event.ResumeParseQueuedEvent;
import com.example.matching.service.common.EventOutboxDispatcher;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * 简历解析任务发布器 — 在业务事务提交前写入 Outbox。
 * 若 Outbox 写入失败，业务事务回滚，保证消息不丢失。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ResumeParseTaskPublisher {

    private static final String RESUME_PARSE_ROUTING_KEY = "resume.parse.execute";

    private final EventOutboxDispatcher outboxDispatcher;

    @TransactionalEventListener(phase = TransactionPhase.BEFORE_COMMIT)
    public void publishBeforeCommit(ResumeParseQueuedEvent event) {
        outboxDispatcher.enqueue("RESUME_PARSE",
                RabbitMQConfig.MATCHING_EXCHANGE, RESUME_PARSE_ROUTING_KEY, event.parseId());
        log.info("简历解析任务已写入Outbox: parseId={}", event.parseId());
    }
}
