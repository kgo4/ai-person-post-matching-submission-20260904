package com.example.matching.integration.mq;

import com.example.matching.config.RabbitMQConfig;
import com.example.matching.infra.AbstractIntegrationTest;
import org.junit.jupiter.api.*;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.core.RabbitAdmin;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;

import java.time.Duration;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

/**
 * Integration test for the resume-parse retry-queue chain.
 * <p>
 * Verifies:
 * <ul>
 *   <li>Production retry queues (30s, 5m, 30m) have correct TTL and DLX configuration.</li>
 *   <li>Retry queue TTL-based dead-letter routing works end-to-end
 *       (tested with fast TTLs to avoid long waits).</li>
 *   <li>After max retries the message ends up in the dead letter queue.</li>
 * </ul>
 */
@Import(MqTestHelperService.class)
class ResumeParseRetryQueueIT extends AbstractIntegrationTest {

    @Autowired
    private RabbitTemplate rabbitTemplate;

    @Autowired
    private RabbitAdmin rabbitAdmin;

    @Autowired
    private MqTestHelperService testHelper;

    @BeforeEach
    void setUp() {
        testHelper.purgeTestQueues(rabbitAdmin);
        testHelper.declareFastRetryChain(rabbitAdmin);
        testHelper.declareFastDlqTrigger(rabbitAdmin);
        try {
            rabbitAdmin.purgeQueue(RabbitMQConfig.DEAD_LETTER_QUEUE);
        } catch (Exception ignored) {
        }
    }

    @AfterEach
    void tearDown() {
        testHelper.cleanupTestQueues(rabbitAdmin);
    }

    // ── Production queue configuration verification ───────────────────────────

    @Test
    void retry30sQueue_hasCorrectTtlAndDlx() {
        var props = rabbitAdmin.getQueueProperties(RabbitMQConfig.RESUME_PARSE_RETRY_30S_QUEUE);
        assertThat(props).isNotNull();
        assertThat(props.get("x-message-ttl")).isEqualTo(30_000);
        assertThat(props.get("x-dead-letter-exchange")).isEqualTo(RabbitMQConfig.MATCHING_EXCHANGE);
        assertThat(props.get("x-dead-letter-routing-key")).isEqualTo("resume.parse.execute");
    }

    @Test
    void retry5mQueue_hasCorrectTtlAndDlx() {
        var props = rabbitAdmin.getQueueProperties(RabbitMQConfig.RESUME_PARSE_RETRY_5M_QUEUE);
        assertThat(props).isNotNull();
        assertThat(props.get("x-message-ttl")).isEqualTo(300_000);
        assertThat(props.get("x-dead-letter-exchange")).isEqualTo(RabbitMQConfig.MATCHING_EXCHANGE);
        assertThat(props.get("x-dead-letter-routing-key")).isEqualTo("resume.parse.execute");
    }

    @Test
    void retry30mQueue_hasCorrectTtlAndDlx() {
        var props = rabbitAdmin.getQueueProperties(RabbitMQConfig.RESUME_PARSE_RETRY_30M_QUEUE);
        assertThat(props).isNotNull();
        assertThat(props.get("x-message-ttl")).isEqualTo(1_800_000);
        assertThat(props.get("x-dead-letter-exchange")).isEqualTo(RabbitMQConfig.MATCHING_EXCHANGE);
        assertThat(props.get("x-dead-letter-routing-key")).isEqualTo("resume.parse.execute");
    }

    @Test
    void resumeParseQueue_hasDlxForFailedMessages() {
        var props = rabbitAdmin.getQueueProperties(RabbitMQConfig.RESUME_PARSE_QUEUE);
        assertThat(props).isNotNull();
        assertThat(props.get("x-dead-letter-exchange")).isEqualTo(RabbitMQConfig.DEAD_LETTER_EXCHANGE);
        assertThat(props.get("x-dead-letter-routing-key")).isEqualTo("dead.letter");
    }

    // ── End-to-end retry flow (fast TTLs, ~6s total) ─────────────────────────

    @Test
    void retryQueue_ttlRouting_deliversMessageToFinalQueue() {
        // Send to the first fast retry queue; it will dead-letter after 1s to
        // matching.exchange/test.retry.fast.2, which routes to the second retry queue.
        // The second queue dead-letters after 2s to test.retry.fast.final.
        rabbitTemplate.convertAndSend(
                RabbitMQConfig.MATCHING_EXCHANGE,
                "test.retry.fast.1",
                "retry-chain-payload");

        // Wait for the message to traverse both retry queues and arrive at the final queue
        await().atMost(Duration.ofSeconds(15))
                .pollInterval(Duration.ofMillis(500))
                .untilAsserted(() -> {
                    Message msg = rabbitTemplate.receive(
                            MqTestHelperService.FAST_RETRY_FINAL_QUEUE, 2_000L);
                    String body = (msg != null) ? new String(msg.getBody()) : null;
                    assertThat(body)
                            .as("Message should arrive at the final queue after TTL-based retries")
                            .contains("retry-chain-payload");
                });
    }

    // ── DLQ routing after max retries (fast TTL, ~2s) ────────────────────────

    @Test
    void afterMaxRetries_messageReachesDeadLetterQueue() {
        // Send to the fast DLQ trigger queue via the matching exchange (1s TTL).
        // On expiry the message dead-letters to the production DLQ exchange / "dead.letter".
        rabbitTemplate.convertAndSend(
                RabbitMQConfig.MATCHING_EXCHANGE,
                MqTestHelperService.FAST_DLQ_TRIGGER_RK,
                "dlq-after-retry-payload");

        await().atMost(Duration.ofSeconds(10))
                .pollInterval(Duration.ofMillis(300))
                .untilAsserted(() -> {
                    Message message = rabbitTemplate.receive(
                            RabbitMQConfig.DEAD_LETTER_QUEUE, 2_000);
                    assertThat(message)
                            .as("Message should reach DLQ after TTL-based retry exhaustion")
                            .isNotNull();
                    String body = new String(message.getBody());
                    assertThat(body).contains("dlq-after-retry-payload");

                    // Verify dead-letter metadata
                    List<Map<String, Object>> xDeath = (List<Map<String, Object>>)
                            message.getMessageProperties().getHeaders().get("x-death");
                    assertThat(xDeath).isNotEmpty();
                    assertThat(xDeath.get(0).get("queue"))
                            .isEqualTo(MqTestHelperService.FAST_DLQ_TRIGGER_QUEUE);
                });
    }
}
