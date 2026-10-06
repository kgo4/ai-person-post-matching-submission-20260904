package com.example.matching.integration.mq;

import com.example.matching.config.RabbitMQConfig;
import org.springframework.amqp.core.*;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.rabbit.core.RabbitAdmin;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;

/**
 * Shared MQ test infrastructure for integration tests.
 * <p>
 * Provides a failing listener for DLQ routing tests, and utility methods
 * for declaring / cleaning up test-only queues.
 */
@TestConfiguration
public class MqTestHelperService {

    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(MqTestHelperService.class);

    // ── Test queue names (unique to avoid collisions with production queues) ──
    public static final String DLQ_TRIGGER_QUEUE = "test.dlq.trigger.queue";
    public static final String DLQ_TRIGGER_RK = "test.dlq.trigger";

    public static final String FAST_RETRY_1_QUEUE = "test.retry.fast.1.queue";
    public static final String FAST_RETRY_2_QUEUE = "test.retry.fast.2.queue";
    public static final String FAST_RETRY_FINAL_QUEUE = "test.retry.fast.final.queue";
    public static final String FAST_DLQ_TRIGGER_QUEUE = "test.dlq.fast.trigger.queue";
    public static final String FAST_DLQ_TRIGGER_RK = "test.dlq.fast.trigger";

    // ── Queue declaration beans (auto-declared at context startup) ────────────

    @Bean
    public Queue dlqTriggerQueue() {
        return QueueBuilder.durable(DLQ_TRIGGER_QUEUE)
                .deadLetterExchange(RabbitMQConfig.DEAD_LETTER_EXCHANGE)
                .deadLetterRoutingKey("dead.letter")
                .build();
    }

    @Bean
    public Binding dlqTriggerBinding() {
        return BindingBuilder.bind(dlqTriggerQueue())
                .to(new TopicExchange(RabbitMQConfig.MATCHING_EXCHANGE, true, false))
                .with(DLQ_TRIGGER_RK);
    }

    // ── Failing listener: rejects every message -> routes to DLQ ──────────────

    @RabbitListener(queues = DLQ_TRIGGER_QUEUE)
    public void failingConsumer(String body) {
        log.warn("[TEST] Rejecting message from {}: {}", DLQ_TRIGGER_QUEUE, body);
        throw new RuntimeException("Simulated processing failure for DLQ test");
    }

    // ── Utility: purge test queues (call in @BeforeEach / @AfterEach) ─────────

    public void purgeTestQueues(RabbitAdmin rabbitAdmin) {
        for (String queue : new String[]{
                DLQ_TRIGGER_QUEUE, FAST_RETRY_1_QUEUE, FAST_RETRY_2_QUEUE,
                FAST_RETRY_FINAL_QUEUE, FAST_DLQ_TRIGGER_QUEUE}) {
            try {
                rabbitAdmin.purgeQueue(queue);
            } catch (Exception ignored) {
                // queue may not exist yet
            }
        }
    }

    // ── Utility: declare fast retry queue chain for retry-flow tests ──────────

    /**
     * Declares a 3-queue retry chain with short TTLs:
     * <pre>
     *   fastRetry1 (1s TTL) -> matching.exchange / resume.parse.execute
     *     -> fastRetry2 (2s TTL) -> matching.exchange / resume.parse.execute
     *       -> fastRetryFinal
     * </pre>
     * Messages sent to {@code fastRetry1} flow through the chain and arrive
     * at {@code fastRetryFinal} after ~3 seconds total.
     */
    public void declareFastRetryChain(AmqpAdmin amqpAdmin) {
        // Fast retry 1: 1s TTL, dead-letters to matching.exchange
        Queue retry1 = QueueBuilder.durable(FAST_RETRY_1_QUEUE)
                .deadLetterExchange(RabbitMQConfig.MATCHING_EXCHANGE)
                .deadLetterRoutingKey("test.retry.fast.2")
                .ttl(1_000)
                .build();
        amqpAdmin.declareQueue(retry1);
        amqpAdmin.declareBinding(BindingBuilder.bind(retry1)
                .to(new TopicExchange(RabbitMQConfig.MATCHING_EXCHANGE, true, false))
                .with("test.retry.fast.1"));

        // Fast retry 2: 2s TTL, dead-letters to matching.exchange
        Queue retry2 = QueueBuilder.durable(FAST_RETRY_2_QUEUE)
                .deadLetterExchange(RabbitMQConfig.MATCHING_EXCHANGE)
                .deadLetterRoutingKey("test.retry.fast.final")
                .ttl(2_000)
                .build();
        amqpAdmin.declareQueue(retry2);
        amqpAdmin.declareBinding(BindingBuilder.bind(retry2)
                .to(new TopicExchange(RabbitMQConfig.MATCHING_EXCHANGE, true, false))
                .with("test.retry.fast.2"));

        // Fast retry final: destination queue
        Queue finalQ = QueueBuilder.durable(FAST_RETRY_FINAL_QUEUE).build();
        amqpAdmin.declareQueue(finalQ);
        amqpAdmin.declareBinding(BindingBuilder.bind(finalQ)
                .to(new TopicExchange(RabbitMQConfig.MATCHING_EXCHANGE, true, false))
                .with("test.retry.fast.final"));
    }

    /**
     * Declares a single fast DLQ trigger queue (1s TTL) that dead-letters to
     * the production DLQ exchange.  Useful for testing the DLQ routing in ~1s
     * instead of waiting for the production 7-day TTL.
     */
    public void declareFastDlqTrigger(AmqpAdmin amqpAdmin) {
        Queue fastDlq = QueueBuilder.durable(FAST_DLQ_TRIGGER_QUEUE)
                .deadLetterExchange(RabbitMQConfig.DEAD_LETTER_EXCHANGE)
                .deadLetterRoutingKey("dead.letter")
                .ttl(1_000)
                .build();
        amqpAdmin.declareQueue(fastDlq);
        // Bind to matching.exchange so messages enter via normal routing;
        // on TTL expiry they dead-letter to the production DLQ.
        amqpAdmin.declareBinding(BindingBuilder.bind(fastDlq)
                .to(new TopicExchange(RabbitMQConfig.MATCHING_EXCHANGE, true, false))
                .with(FAST_DLQ_TRIGGER_RK));
    }

    // ── Utility: clean up test queues (call in @AfterAll) ─────────────────────

    public void cleanupTestQueues(AmqpAdmin amqpAdmin) {
        for (String queue : new String[]{
                DLQ_TRIGGER_QUEUE, FAST_RETRY_1_QUEUE, FAST_RETRY_2_QUEUE,
                FAST_RETRY_FINAL_QUEUE, FAST_DLQ_TRIGGER_QUEUE}) {
            try {
                amqpAdmin.purgeQueue(queue);
                amqpAdmin.deleteQueue(queue);
            } catch (Exception ignored) {
            }
        }
        // DLQ trigger queue is auto-declared by @RabbitListener; delete bindings too
        try {
            amqpAdmin.removeBinding(BindingBuilder
                    .bind(new Queue(DLQ_TRIGGER_QUEUE))
                    .to(new TopicExchange(RabbitMQConfig.MATCHING_EXCHANGE, true, false))
                    .with(DLQ_TRIGGER_RK));
        } catch (Exception ignored) {
        }
    }
}
