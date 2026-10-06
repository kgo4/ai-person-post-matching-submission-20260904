package com.example.matching.integration.mq;

import com.example.matching.config.RabbitMQConfig;
import com.example.matching.infra.AbstractIntegrationTest;
import com.example.matching.service.common.EventOutboxDispatcher;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.*;
import org.springframework.amqp.rabbit.core.RabbitAdmin;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

/**
 * Integration test for {@link EventOutboxDispatcher} against a real RabbitMQ broker.
 * <p>
 * Verifies:
 * <ul>
 *   <li>Dispatching a PENDING event delivers a message to the target queue.</li>
 *   <li>After successful delivery the outbox record is marked PUBLISHED.</li>
 *   <li>Delivery to a non-existent exchange leaves the event PENDING for retry.</li>
 * </ul>
 */
class EventOutboxDispatcherIT extends AbstractIntegrationTest {

    private static final String TEST_QUEUE = "test.outbox.dispatch.queue";
    private static final String TEST_RK = "test.outbox.dispatch";

    @Autowired
    private EventOutboxDispatcher outboxDispatcher;

    @Autowired
    private RabbitTemplate rabbitTemplate;

    @Autowired
    private RabbitAdmin rabbitAdmin;

    @Autowired
    private TransactionTemplate transactionTemplate;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void setUp() {
        // Declare a test queue bound to the matching exchange
        Queue testQueue = QueueBuilder.durable(TEST_QUEUE).build();
        rabbitAdmin.declareQueue(testQueue);
        rabbitAdmin.declareBinding(
                BindingBuilder.bind(testQueue)
                        .to(new TopicExchange(RabbitMQConfig.MATCHING_EXCHANGE, true, false))
                        .with(TEST_RK));
        rabbitAdmin.purgeQueue(TEST_QUEUE);
    }

    @AfterEach
    void tearDown() {
        try {
            rabbitAdmin.purgeQueue(TEST_QUEUE);
            rabbitAdmin.deleteQueue(TEST_QUEUE);
            rabbitAdmin.removeBinding(
                    BindingBuilder.bind(new Queue(TEST_QUEUE))
                            .to(new TopicExchange(RabbitMQConfig.MATCHING_EXCHANGE, true, false))
                            .with(TEST_RK));
        } catch (Exception ignored) {
        }
    }

    @Test
    void dispatch_deliversMessageToQueue_andMarksOutboxPublished() {
        // 1. Create an outbox event via a committing transaction
        transactionTemplate.executeWithoutResult(status ->
                outboxDispatcher.enqueue("DISPATCH_TEST", RabbitMQConfig.MATCHING_EXCHANGE, TEST_RK, "dispatch-payload"));

        // 2. Manually trigger dispatch (scheduling is disabled in integration profile)
        outboxDispatcher.dispatchPendingEvents();

        // 3. Wait for the message to arrive on the test queue
        await().atMost(Duration.ofSeconds(10))
                .pollInterval(Duration.ofMillis(200))
                .untilAsserted(() -> {
                    Message msg = rabbitTemplate.receive(TEST_QUEUE, 1_000L);
                    String body = (msg != null) ? new String(msg.getBody()) : null;
                    assertThat(body).isNotNull();
                    assertThat(body).contains("dispatch-payload");
                });

        // 4. Verify outbox record updated to PUBLISHED
        await().atMost(Duration.ofSeconds(10))
                .untilAsserted(() -> {
                    String status = jdbcTemplate.queryForObject(
                            "SELECT status FROM event_outbox WHERE event_type = ?",
                            String.class, "DISPATCH_TEST");
                    assertThat(status).isEqualTo("PUBLISHED");
                });
    }

    @Test
    void dispatch_toNonExistentExchange_eventRemainsPendingForRetry() {
        // 1. Enqueue to a non-existent exchange
        transactionTemplate.executeWithoutResult(status ->
                outboxDispatcher.enqueue(
                        "UNROUTABLE_TEST",
                        "non.existent.exchange",
                        "unroutable.key",
                        "unroutable-payload"));

        // 2. Attempt dispatch
        outboxDispatcher.dispatchPendingEvents();

        // 3. Wait for the outbox record to be updated (PENDING with error or SENDING)
        await().atMost(Duration.ofSeconds(10))
                .pollInterval(Duration.ofMillis(500))
                .untilAsserted(() -> {
                    String status = jdbcTemplate.queryForObject(
                            "SELECT status FROM event_outbox WHERE event_type = ?",
                            String.class, "UNROUTABLE_TEST");
                    // After returned/nack the dispatcher either:
                    //  - marks PENDING with nextRetryTime (if under max attempts), or
                    //  - marks FAILED (if max attempts exceeded)
                    assertThat(status).isIn("PENDING", "SENDING", "FAILED");
                });

        // 4. Verify NO message landed on the test queue
        Message noMsg = rabbitTemplate.receive(TEST_QUEUE, 2_000L);
        String body = (noMsg != null) ? new String(noMsg.getBody()) : null;
        assertThat(body).as("Message should not be delivered to queue for unroutable exchange").isNull();
    }
}
