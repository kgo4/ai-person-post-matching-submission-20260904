package com.example.matching.service.common;

import com.example.matching.config.RabbitMQConfig;
import com.example.matching.service.system.SysOperationLogService;
import com.rabbitmq.client.AMQP;
import com.rabbitmq.client.Channel;
import com.rabbitmq.client.Envelope;
import com.rabbitmq.client.GetResponse;
import com.rabbitmq.client.ReturnListener;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.rabbit.core.ChannelCallback;
import org.springframework.amqp.rabbit.core.RabbitTemplate;

import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class DlqReplayServiceTest {

    private RabbitTemplate rabbitTemplate;
    private Channel channel;
    private DlqReplayService service;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        rabbitTemplate = mock(RabbitTemplate.class);
        channel = mock(Channel.class);
        service = new DlqReplayService(rabbitTemplate, mock(SysOperationLogService.class));
        when(rabbitTemplate.execute(any(ChannelCallback.class))).thenAnswer(invocation ->
                ((ChannelCallback<Object>) invocation.getArgument(0)).doInRabbit(channel));
    }

    @Test
    void replayEnablesPublisherConfirmsBeforeAcknowledgingDlqMessage() throws Exception {
        GetResponse message = message(Map.of(
                "x-first-death-exchange", RabbitMQConfig.MATCHING_EXCHANGE,
                "x-first-death-routing-key", "ai.test.generate"));
        when(channel.basicGet(RabbitMQConfig.DEAD_LETTER_QUEUE, false)).thenReturn(message).thenReturn(null);

        assertThat(service.replay(1)).isEqualTo(1);

        verify(channel).confirmSelect();
        verify(channel).waitForConfirmsOrDie(5000);
        verify(channel).basicAck(message.getEnvelope().getDeliveryTag(), false);
    }

    @Test
    void replayWithoutOriginalRouteKeepsMessageInDlq() throws Exception {
        GetResponse message = message(Map.of());
        when(channel.basicGet(RabbitMQConfig.DEAD_LETTER_QUEUE, false)).thenReturn(message).thenReturn(null);

        assertThat(service.replay(1)).isZero();

        verify(channel, never()).basicPublish(any(), any(), any(), any());
        verify(channel).basicNack(message.getEnvelope().getDeliveryTag(), false, true);
        verify(channel, never()).basicAck(message.getEnvelope().getDeliveryTag(), false);
    }

    @Test
    void replayOfUnroutableMessageKeepsOriginalDlqMessage() throws Exception {
        GetResponse message = message(Map.of(
                "x-first-death-exchange", RabbitMQConfig.MATCHING_EXCHANGE,
                "x-first-death-routing-key", "missing.route"));
        when(channel.basicGet(RabbitMQConfig.DEAD_LETTER_QUEUE, false)).thenReturn(message).thenReturn(null);
        AtomicReference<ReturnListener> returnListener = new AtomicReference<>();
        org.mockito.Mockito.doAnswer(invocation -> {
            returnListener.set(invocation.getArgument(0));
            return null;
        }).when(channel).addReturnListener(org.mockito.ArgumentMatchers.any(ReturnListener.class));
        org.mockito.Mockito.doAnswer(invocation -> {
            returnListener.get().handleReturn(312, "NO_ROUTE", "matching.exchange", "missing.route",
                    message.getProps(), message.getBody());
            return null;
        }).when(channel).basicPublish(org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.eq(true),
                org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any());

        assertThat(service.replay(1)).isZero();

        verify(channel).addReturnListener(org.mockito.ArgumentMatchers.any(ReturnListener.class));
        verify(channel).basicNack(message.getEnvelope().getDeliveryTag(), false, true);
        verify(channel, never()).basicAck(message.getEnvelope().getDeliveryTag(), false);
    }

    private GetResponse message(Map<String, Object> headers) {
        return new GetResponse(
                new Envelope(7L, false, RabbitMQConfig.DEAD_LETTER_EXCHANGE, "dead.letter"),
                new AMQP.BasicProperties.Builder().messageId("dlq-7").headers(headers).build(),
                "payload".getBytes(java.nio.charset.StandardCharsets.UTF_8),
                0);
    }
}
