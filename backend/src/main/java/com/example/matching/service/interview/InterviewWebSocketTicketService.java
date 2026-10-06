package com.example.matching.service.interview;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Issues short-lived, single-use tickets for browser WebSocket handshakes.
 * <p>
 * Redis 可用时使用字符串 JSON 存储，避免依赖 polymorphic type metadata。
 */
@Slf4j
@Service
public class InterviewWebSocketTicketService {

    private static final String TICKET_KEY_PREFIX = "ws:ticket:";
    private static final long TICKET_TTL_SECONDS = 120;

    private final SecureRandom secureRandom = new SecureRandom();
    private final Clock clock;
    private final StringRedisTemplate stringRedisTemplate;
    private final ObjectMapper objectMapper;
    private final Map<String, TicketRecord> inMemoryStore = new ConcurrentHashMap<>();
    private final boolean useRedis;

    @Autowired
    public InterviewWebSocketTicketService(
            @Autowired(required = false) StringRedisTemplate stringRedisTemplate,
            ObjectMapper objectMapper
    ) {
        this(stringRedisTemplate, objectMapper, Clock.systemUTC());
    }

    InterviewWebSocketTicketService(StringRedisTemplate stringRedisTemplate, ObjectMapper objectMapper, Clock clock) {
        this.stringRedisTemplate = stringRedisTemplate;
        this.objectMapper = objectMapper;
        this.clock = clock;
        this.useRedis = stringRedisTemplate != null;
        if (!useRedis) {
            log.warn("Redis 不可用，WebSocket Ticket 降级为内存存储");
        }
    }

    public IssuedTicket issue(Long sessionId, Long userId) {
        byte[] randomBytes = new byte[32];
        secureRandom.nextBytes(randomBytes);
        String ticket = Base64.getUrlEncoder().withoutPadding().encodeToString(randomBytes);
        Instant expiresAt = clock.instant().plusSeconds(TICKET_TTL_SECONDS);

        TicketRecord record = new TicketRecord(sessionId, userId, expiresAt.toEpochMilli());

        if (useRedis) {
            stringRedisTemplate.opsForValue().set(
                    TICKET_KEY_PREFIX + ticket,
                    serialize(record),
                    Duration.ofSeconds(TICKET_TTL_SECONDS)
            );
        } else {
            inMemoryStore.put(ticket, record);
        }

        return new IssuedTicket(ticket, expiresAt.toEpochMilli());
    }

    /**
     * 消费一次性票据。
     *
     * @return 校验通过时返回票据中携带的 userId（若票据无 userId 字段则返回 0 表示系统身份）；
     *         校验失败返回 null（票据无效/已使用/已过期/sessionId 不匹配）。
     */
    public Long consume(String ticket, Long sessionId) {
        if (ticket == null || ticket.isBlank() || sessionId == null) {
            return null;
        }

        TicketRecord record;
        if (useRedis) {
            String key = TICKET_KEY_PREFIX + ticket;
            // 原子 GET+DELETE：防止并发请求重复消费同一 ticket
            String payload = stringRedisTemplate.opsForValue().getAndDelete(key);
            if (payload == null) return null;
            record = parse(payload);
            if (record == null) return null;
        } else {
            record = inMemoryStore.remove(ticket);
            if (record == null) return null;
        }

        if (record.sessionId().equals(sessionId)
                && record.expiresAt() > clock.instant().toEpochMilli()) {
            return record.userId() != null ? record.userId() : 0L;
        }
        return null;
    }

    public record IssuedTicket(String ticket, Long expiresAt) {
    }

    private record TicketRecord(Long sessionId, Long userId, Long expiresAt) {
    }

    private String serialize(TicketRecord record) {
        try {
            return objectMapper.writeValueAsString(record);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to serialize WebSocket ticket", e);
        }
    }

    private TicketRecord parse(String payload) {
        try {
            JsonNode node = objectMapper.readTree(payload);
            Long sessionId = node.hasNonNull("sessionId") ? node.get("sessionId").asLong() : null;
            Long userId = node.hasNonNull("userId") ? node.get("userId").asLong() : null;
            Long expiresAt = node.hasNonNull("expiresAt") ? node.get("expiresAt").asLong() : null;
            if (sessionId == null || expiresAt == null) {
                return null;
            }
            return new TicketRecord(sessionId, userId, expiresAt);
        } catch (Exception e) {
            log.warn("Failed to parse websocket ticket payload: {}", e.getMessage());
            return null;
        }
    }
}
