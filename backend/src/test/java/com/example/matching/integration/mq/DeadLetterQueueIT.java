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
 * Verifies Dead Letter Queue (DLQ) routing for failed message consumption.
 * <p>
 * Uses a dedicated test queue whose listener always throws, causing messages
 * to be rejected and routed to the production DLQ (via the DLX configured on
 * the queue).  This avoids interfering with production queues that have a
 * 7-day TTL before dead-lettering.
 */
@Import(MqTestHelperService.class)
class DeadLetterQueueIT extends AbstractIntegrationTest {

    @Autowired
    private RabbitTemplate rabbitTemplate;

    @Autowired
    private RabbitAdmin rabbitAdmin;

    @Autowired
    private MqTestHelperService testHelper;

    @BeforeEach
    void setUp() {
        testHelper.purgeTestQueues(rabbitAdmin);
        // Also purge the production DLQ so previous test runs don't interfere
        try {
            rabbitAdmin.purgeQueue(RabbitMQConfig.DEAD_LETTER_QUEUE);
        } catch (Exception ignored) {
        }
    }

    @AfterEach
    void tearDown() {
        testHelper.purgeTestQueues(rabbitAdmin);
    }

    @Test
    void rejectedMessage_reachesDeadLetterQueue() {
        // 1. Publish a message to the DLQ trigger queue.
        //    The MqTestHelperService.failingConsumer always throws -> message is rejected.
        //    The queue's DLX routing sends it to the production DLQ.
        rabbitTemplate.convertAndSend(
                RabbitMQConfig.MATCHING_EXCHANGE,
                MqTestHelperService.DLQ_TRIGGER_RK,
                "dlq-test-payload");

        // 2. Wait for the message to appear in the dead letter queue
        await().atMost(Duration.ofSeconds(15))
                .pollInterval(Duration.ofMillis(300))
                .untilAsserted(() -> {
                    Message message = rabbitTemplate.receive(RabbitMQConfig.DEAD_LETTER_QUEUE, 2_000);
                    assertThat(message)
                            .as("Dead letter queue should contain the rejected message")
                            .isNotNull();

                    // Verify body
                    String body = new String(message.getBody());
                    assertThat(body).contains("dlq-test-payload");

                    // Verify the message was dead-lettered from our trigger queue
                    List<Map<String, Object>> xDeath = (List<Map<String, Object>>)
                            message.getMessageProperties().getHeaders().get("x-death");
                    assertThat(xDeath).isNotEmpty();
                    assertThat(xDeath.get(0).get("queue"))
                            .isEqualTo(MqTestHelperService.DLQ_TRIGGER_QUEUE);
                    assertThat(xDeath.get(0).get("reason")).isEqualTo("rejected");

                    // Verify original routing key is preserved in x-death
                    List<String> routingKeys = (List<String>) xDeath.get(0).get("routing-keys");
                    assertThat(routingKeys).contains(MqTestHelperService.DLQ_TRIGGER_RK);
                });
    }

    @Test
    void dlqMessage_carriesOriginalExchangeInfo() {
        rabbitTemplate.convertAndSend(
                RabbitMQConfig.MATCHING_EXCHANGE,
                MqTestHelperService.DLQ_TRIGGER_RK,
                "exchange-info-payload");

        await().atMost(Duration.ofSeconds(15))
                .pollInterval(Duration.ofMillis(300))
                .untilAsserted(() -> {
                    Message message = rabbitTemplate.receive(RabbitMQConfig.DEAD_LETTER_QUEUE, 2_000);
                    assertThat(message).isNotNull();

                    List<Map<String, Object>> xDeath = (List<Map<String, Object>>)
                            message.getMessageProperties().getHeaders().get("x-death");
                    assertThat(xDeath).isNotEmpty();

                    // The exchange that originally published the message
                    assertThat(xDeath.get(0).get("exchange"))
                            .isEqualTo(RabbitMQConfig.MATCHING_EXCHANGE);
                });
    }
}
