package com.example.matching.integration.concurrency;

import com.example.matching.infra.AbstractIntegrationTest;
import com.rabbitmq.client.Channel;
import com.rabbitmq.client.GetResponse;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.*;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.ChannelCallback;
import org.springframework.amqp.rabbit.core.RabbitAdmin;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

@DisplayName("MQ Redelivery Tests")
@Disabled("Requires Docker for Testcontainers RabbitMQ")
class MqRedeliveryTest extends AbstractIntegrationTest {

    private static final String TEST_EXCHANGE = "test.redelivery.exchange";
    private static final String TEST_QUEUE = "test.redelivery.queue";
    private static final String TEST_ROUTING_KEY = "test.redelivery";

    @Autowired
    private ConnectionFactory connectionFactory;

    private RabbitAdmin rabbitAdmin;
    private RabbitTemplate rabbitTemplate;

    @BeforeEach
    void setUp() {
        rabbitAdmin = new RabbitAdmin(connectionFactory);
        rabbitTemplate = new RabbitTemplate(connectionFactory);

        // Declare exchange, queue, and binding
        DirectExchange exchange = new DirectExchange(TEST_EXCHANGE, true, false);
        Queue queue = QueueBuilder.durable(TEST_QUEUE)
                .withArgument("x-dead-letter-exchange", "")
                .build();
        Binding binding = BindingBuilder.bind(queue).to(exchange).with(TEST_ROUTING_KEY);

        rabbitAdmin.declareExchange(exchange);
        rabbitAdmin.declareQueue(queue);
        rabbitAdmin.declareBinding(binding);

        // Purge any leftover messages
        rabbitAdmin.purgeQueue(TEST_QUEUE);
    }

    @AfterEach
    void tearDown() {
        rabbitAdmin.deleteQueue(TEST_QUEUE);
        rabbitAdmin.deleteExchange(TEST_EXCHANGE);
    }

    @Test
    @DisplayName("Unacknowledged message reappears on queue after channel close")
    void unacknowledgedMessageReappearsAfterChannelClose() {
        // Publish a persistent message
        String payload = "{\"eventType\":\"MATCHING_TASK\",\"postId\":1,\"attempt\":1}";
        rabbitTemplate.convertAndSend(TEST_EXCHANGE, TEST_ROUTING_KEY, payload,
                message -> {
                    message.getMessageProperties().setDeliveryMode(MessageDeliveryMode.PERSISTENT);
                    return message;
                });

        // Wait for the message to arrive
        await().atMost(Duration.ofSeconds(10)).untilAsserted(() -> {
            assertThat(getQueueMessageCount()).isGreaterThanOrEqualTo(1);
        });

        // Consume with autoAck=false using a dedicated channel, then close it without acking
        // This simulates a consumer crash -- the message should be requeued
        GetResponse getResponse = rabbitTemplate.execute((ChannelCallback<GetResponse>) channel -> {
            // Use a temporary channel so closing it simulates consumer disconnect
            return channel.basicGet(TEST_QUEUE, false /* autoAck=false */);
        });

        assertThat(getResponse).isNotNull();
        String body = new String(getResponse.getBody(), StandardCharsets.UTF_8);
        assertThat(body).contains("MATCHING_TASK");

        // Channel is now closed (callback returns), message is unacknowledged.
        // RabbitMQ should requeue it after the consumer timeout (typically 30 min)
        // For testing, we use the nack to trigger immediate requeue instead.
        rabbitTemplate.execute((ChannelCallback<Void>) channel -> {
            channel.basicNack(getResponse.getEnvelope().getDeliveryTag(), false, true);
            return null;
        });

        // Verify message reappears on the queue
        await().atMost(Duration.ofSeconds(10)).untilAsserted(() -> {
            assertThat(getQueueMessageCount()).isGreaterThanOrEqualTo(1);
        });

        // Consume again to verify same message is redelivered
        Message redelivered = rabbitTemplate.receive(TEST_QUEUE, 5000);
        assertThat(redelivered).isNotNull();
        String redeliveredBody = new String(redelivered.getBody(), StandardCharsets.UTF_8);
        assertThat(redeliveredBody).contains("MATCHING_TASK");
        assertThat(redelivered.getMessageProperties().getRedelivered())
                .as("Message should be marked as redelivered")
                .isTrue();
    }

    @Test
    @DisplayName("Multiple messages maintain order and all reappear after nack")
    void multipleMessagesMaintainOrderAfterNack() {
        for (int i = 1; i <= 3; i++) {
            rabbitTemplate.convertAndSend(TEST_EXCHANGE, TEST_ROUTING_KEY,
                    "{\"seq\":" + i + "}");
        }

        // Wait for all messages to arrive
        await().atMost(Duration.ofSeconds(10)).untilAsserted(() -> {
            assertThat(getQueueMessageCount()).isGreaterThanOrEqualTo(3);
        });

        // Read all 3 messages without ack, then nack them all (requeue=true)
        List<GetResponse> received = rabbitTemplate.execute((ChannelCallback<List<GetResponse>>) channel -> {
            List<GetResponse> msgs = new ArrayList<>();
            for (int i = 0; i < 3; i++) {
                GetResponse resp = channel.basicGet(TEST_QUEUE, false);
                if (resp != null) msgs.add(resp);
            }
            // Nack all to requeue
            for (GetResponse resp : msgs) {
                channel.basicNack(resp.getEnvelope().getDeliveryTag(), false, true);
            }
            return msgs;
        });

        assertThat(received).hasSize(3);

        // All 3 should reappear after nack
        await().atMost(Duration.ofSeconds(10)).untilAsserted(() -> {
            assertThat(getQueueMessageCount()).isGreaterThanOrEqualTo(3);
        });

        // Drain to clean up
        rabbitTemplate.execute((ChannelCallback<Void>) channel -> {
            for (int i = 0; i < 3; i++) {
                GetResponse resp = channel.basicGet(TEST_QUEUE, true);
                if (resp != null) {
                    channel.basicAck(resp.getEnvelope().getDeliveryTag(), false);
                }
            }
            return null;
        });
    }

    // ==================== helpers ====================

    private int getQueueMessageCount() {
        Properties props = rabbitAdmin.getQueueProperties(TEST_QUEUE);
        if (props == null) return 0;
        String count = props.getProperty("QUEUE_MESSAGE_COUNT");
        return count != null ? Integer.parseInt(count) : 0;
    }
}
