package com.example.matching.service.interview;

import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class InterviewWebSocketTicketServiceTest {

    @SuppressWarnings("unchecked")
    private static StringRedisTemplate mockRedisTemplate() {
        StringRedisTemplate template = mock(StringRedisTemplate.class);
        ValueOperations<String, String> ops = mock(ValueOperations.class);
        Map<String, String> store = new ConcurrentHashMap<>();

        when(template.opsForValue()).thenReturn(ops);
        doAnswer(inv -> {
            String key = inv.getArgument(0);
            String value = inv.getArgument(1);
            store.put(key, value);
            return null;
        }).when(ops).set(anyString(), anyString(), any());

        when(ops.get(anyString())).thenAnswer(inv -> store.get(inv.getArgument(0)));
        when(ops.getAndDelete(anyString())).thenAnswer(inv -> store.remove(inv.getArgument(0)));
        when(template.delete(anyString())).thenAnswer(inv -> store.remove(inv.getArgument(0)) != null);

        return template;
    }

    @Test
    void issuedTicketCanBeConsumedOnceForMatchingSession() {
        InterviewWebSocketTicketService service = new InterviewWebSocketTicketService(
                mockRedisTemplate(),
                new com.fasterxml.jackson.databind.ObjectMapper(),
                Clock.fixed(Instant.parse("2026-05-20T00:00:00Z"), ZoneOffset.UTC)
        );

        var issued = service.issue(9L, 100L);

        assertThat(service.consume(issued.ticket(), 9L)).isNotNull();
        assertThat(service.consume(issued.ticket(), 9L)).isNull();
    }

    @Test
    void ticketCannotBeConsumedForDifferentSession() {
        InterviewWebSocketTicketService service = new InterviewWebSocketTicketService(
                mockRedisTemplate(),
                new com.fasterxml.jackson.databind.ObjectMapper(),
                Clock.fixed(Instant.parse("2026-05-20T00:00:00Z"), ZoneOffset.UTC)
        );

        var issued = service.issue(9L, 100L);

        assertThat(service.consume(issued.ticket(), 10L)).isNull();
    }

    @Test
    void legacyJsonPayloadWithoutTypeMetadataCanStillBeConsumed() {
        StringRedisTemplate template = mockRedisTemplate();
        InterviewWebSocketTicketService service = new InterviewWebSocketTicketService(
                template,
                new com.fasterxml.jackson.databind.ObjectMapper(),
                Clock.fixed(Instant.parse("2026-05-20T00:00:00Z"), ZoneOffset.UTC)
        );

        String ticket = "legacy-ticket";
        when(template.opsForValue().getAndDelete("ws:ticket:" + ticket))
                .thenReturn("{\"sessionId\":9,\"userId\":100,\"expiresAt\":9999999999999}");

        assertThat(service.consume(ticket, 9L)).isNotNull();
    }
}
