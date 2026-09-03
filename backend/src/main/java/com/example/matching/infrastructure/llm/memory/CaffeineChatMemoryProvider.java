package com.example.matching.infrastructure.llm.memory;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.RemovalCause;
import dev.langchain4j.memory.ChatMemory;
import dev.langchain4j.memory.chat.MessageWindowChatMemory;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.time.Duration;

/**
 * Caffeine-based implementation of {@link ChatMemoryProvider}.
 * <p>
 * Configuration:
 * - Maximum 500 concurrent sessions
 * - 24-hour expiry after last access
 * - 20-message window per session
 * - Explicit clear on session completion
 * <p>
 * NOTE: This provider is NOT distributed-safe. For multi-instance deployments,
 * {@link RedisChatMemoryProvider} should be used instead via
 * {@code chat.memory.provider=redis} configuration.
 * <p>
 * IMPORTANT: Memory contents are NEVER logged. Only session IDs are logged
 * for operational debugging.
 * <p>
 * M1：内存按 stage key 隔离（{@code INTERVIEW_PLAN:{sessionId}} 等），
 * 同一 session 的不同面试阶段不共享消息；会话结束时清理全部 stage。
 */
@Slf4j
@Component
@ConditionalOnProperty(name = "chat.memory.provider", havingValue = "caffeine", matchIfMissing = true)
public class CaffeineChatMemoryProvider implements ChatMemoryProvider {

    private static final int MAX_MESSAGES = 20;
    private static final int MAX_SESSIONS = 500;
    private static final Duration EXPIRY = Duration.ofHours(24);

    private final Cache<String, ChatMemory> memoryCache;

    public CaffeineChatMemoryProvider() {
        this.memoryCache = Caffeine.newBuilder()
                .maximumSize(MAX_SESSIONS)
                .expireAfterAccess(EXPIRY)
                .removalListener(this::onRemoval)
                .build();
    }

    @Override
    public ChatMemory getMemory(Long sessionId) {
        if (sessionId == null) {
            throw new IllegalArgumentException("Session ID must not be null");
        }
        return getMemory(String.valueOf(sessionId));
    }

    @Override
    public ChatMemory getMemory(String memoryKey) {
        if (memoryKey == null || memoryKey.isBlank()) {
            throw new IllegalArgumentException("Memory key must not be blank");
        }
        return memoryCache.get(memoryKey, this::createMemory);
    }

    @Override
    public void clear(Long sessionId) {
        if (sessionId == null) {
            return;
        }
        // 清理该 session 的全部 stage memory 与裸 key
        String bareKey = String.valueOf(sessionId);
        String suffix = ":" + bareKey;
        memoryCache.asMap().keySet().removeIf(key -> key.equals(bareKey) || key.endsWith(suffix));
        log.debug("Cleared all stage memories for session: {}", sessionId);
    }

    @Override
    public boolean hasMemory(Long sessionId) {
        return sessionId != null && hasMemory(String.valueOf(sessionId));
    }

    @Override
    public boolean hasMemory(String memoryKey) {
        return memoryKey != null && memoryCache.asMap().containsKey(memoryKey);
    }

    private ChatMemory createMemory(String memoryKey) {
        log.debug("Creating chat memory for key: {}", memoryKey);
        return MessageWindowChatMemory.builder()
                .id(memoryKey)
                .maxMessages(MAX_MESSAGES)
                .build();
    }

    private void onRemoval(String memoryKey, ChatMemory memory, RemovalCause cause) {
        if (cause.wasEvicted()) {
            log.debug("Chat memory evicted for key: {}, cause: {}", memoryKey, cause);
        }
    }
}
