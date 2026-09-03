package com.example.matching.infrastructure.llm.memory;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.data.message.*;
import dev.langchain4j.memory.ChatMemory;
import dev.langchain4j.memory.chat.MessageWindowChatMemory;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Redis-backed {@link ChatMemoryProvider} for distributed interview sessions.
 *
 * <p>Stores chat messages per session in Redis with TTL, allowing multi-instance
 * deployments to share interview conversation state.</p>
 *
 * <p>Consistency model:</p>
 * <ul>
 *   <li>Within an instance: per-session {@link ChatMemory} cache + synchronized
 *       add/persist, so concurrent adds on the same session are serialized.</li>
 *   <li>Across instances: persisted state carries a version; writes use a Lua
 *       compare-and-set. On conflict the local copy is reloaded from Redis and
 *       the pending message re-applied, so concurrent writers converge instead
 *       of blindly last-write-wins.</li>
 *   <li>Runtime Redis failures degrade gracefully: the in-memory session
 *       continues and persistence retries on the next add.</li>
 * </ul>
 */
@Slf4j
@Component
@ConditionalOnProperty(name = "chat.memory.provider", havingValue = "redis")
public class RedisChatMemoryProvider implements ChatMemoryProvider {

    private static final int MAX_MESSAGES = 20;
    private static final Duration TTL = Duration.ofHours(24);
    private static final String KEY_PREFIX = "chat:memory:";
    private static final long TTL_SECONDS = TTL.getSeconds();

    /**
     * 版本 CAS 写：ARGV[1]=新状态JSON ARGV[2]=期望版本 ARGV[3]=TTL秒
     * 返回 1=写入成功，0=版本冲突。
     * <p>兼容旧格式：存量 key 是纯 JSON 数组（无 version 字段）时，
     * 仅当期望版本为 0 时允许覆盖升级为新格式——首次写入即完成格式迁移。
     */
    private static final RedisScript<Long> CAS_SCRIPT = new DefaultRedisScript<>(
            "local current = redis.call('GET', KEYS[1]) "
                    + "if current == false then "
                    + "  redis.call('SET', KEYS[1], ARGV[1], 'EX', ARGV[3]) "
                    + "  return 1 "
                    + "end "
                    + "local state = cjson.decode(current) "
                    + "if state.version == nil then "
                    + "  if tonumber(ARGV[2]) == 0 then "
                    + "    redis.call('SET', KEYS[1], ARGV[1], 'EX', ARGV[3]) "
                    + "    return 1 "
                    + "  end "
                    + "  return 0 "
                    + "end "
                    + "if tonumber(state.version) == tonumber(ARGV[2]) then "
                    + "  redis.call('SET', KEYS[1], ARGV[1], 'EX', ARGV[3]) "
                    + "  return 1 "
                    + "end "
                    + "return 0",
            Long.class);

    private final StringRedisTemplate stringRedisTemplate;
    private final ObjectMapper objectMapper;
    private final ConcurrentHashMap<String, ChatMemory> redisSessions = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, ChatMemory> localFallback = new ConcurrentHashMap<>();
    private final boolean useRedis;

    public RedisChatMemoryProvider(ObjectProvider<StringRedisTemplate> templateProvider,
                                   ObjectMapper objectMapper) {
        this.stringRedisTemplate = templateProvider.getIfAvailable();
        this.objectMapper = objectMapper;
        this.useRedis = this.stringRedisTemplate != null;
        if (!useRedis) {
            log.warn("Redis 不可用，ChatMemory 降级为本地 Caffeine");
        }
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
        if (useRedis) {
            return redisSessions.computeIfAbsent(memoryKey, this::loadOrCreateRedisMemory);
        }
        return localFallback.computeIfAbsent(memoryKey, this::createMemory);
    }

    @Override
    public void clear(Long sessionId) {
        if (sessionId == null) return;
        // 清理该 session 的全部 stage memory 与裸 key
        String bareKey = String.valueOf(sessionId);
        String suffix = ":" + bareKey;
        redisSessions.keySet().removeIf(key -> key.equals(bareKey) || key.endsWith(suffix));
        localFallback.keySet().removeIf(key -> key.equals(bareKey) || key.endsWith(suffix));
        if (useRedis) {
            try {
                java.util.Set<String> stageKeys = stringRedisTemplate.keys(KEY_PREFIX + "*" + suffix);
                if (stageKeys != null && !stageKeys.isEmpty()) {
                    stringRedisTemplate.delete(stageKeys);
                }
                stringRedisTemplate.delete(KEY_PREFIX + bareKey);
            } catch (Exception e) {
                log.warn("删除 Redis 聊天记忆失败: sessionId={}", sessionId, e);
            }
        }
    }

    @Override
    public boolean hasMemory(Long sessionId) {
        return sessionId != null && hasMemory(String.valueOf(sessionId));
    }

    @Override
    public boolean hasMemory(String memoryKey) {
        if (memoryKey == null) return false;
        if (useRedis) {
            if (redisSessions.containsKey(memoryKey)) {
                return true;
            }
            try {
                return Boolean.TRUE.equals(stringRedisTemplate.hasKey(KEY_PREFIX + memoryKey));
            } catch (Exception e) {
                log.warn("Redis 聊天记忆存在性检查失败: memoryKey={}", memoryKey, e);
                return false;
            }
        }
        return localFallback.containsKey(memoryKey);
    }

    private ChatMemory loadOrCreateRedisMemory(String memoryKey) {
        LoadedState loaded = loadState(memoryKey);
        if (loaded != null) {
            return new SerializableChatMemory(memoryKey, restoreMemory(memoryKey, loaded.messages()),
                    this, loaded.version());
        }
        return new SerializableChatMemory(memoryKey, createMemory(memoryKey), this, 0);
    }

    private MessageWindowChatMemory restoreMemory(String memoryKey, List<ChatMessageDto> dtos) {
        MessageWindowChatMemory memory = MessageWindowChatMemory.builder()
                .id(memoryKey).maxMessages(MAX_MESSAGES).build();
        for (ChatMessageDto dto : dtos) {
            memory.add(dto.toMessage());
        }
        return memory;
    }

    private ChatMemory createMemory(String memoryKey) {
        return MessageWindowChatMemory.builder()
                .id(memoryKey).maxMessages(MAX_MESSAGES).build();
    }

    /**
     * 从 Redis 读取 {version, messages}；key 不存在返回 null。
     * <p>兼容旧格式：存量 key 若为纯 JSON 数组（升级前的格式），
     * 原地迁移为 {version:0, messages:[...]} 并返回 v0 状态，
     * 迁移写失败也不阻塞会话——Lua CAS 会在下次写入时按 v0 覆盖升级。
     */
    private LoadedState loadState(String memoryKey) {
        String key = KEY_PREFIX + memoryKey;
        try {
            String json = stringRedisTemplate.opsForValue().get(key);
            if (json == null) {
                return null;
            }
            try {
                return objectMapper.readValue(json, LoadedState.class);
            } catch (Exception legacyFormat) {
                List<ChatMessageDto> legacy = objectMapper.readValue(json,
                        new TypeReference<List<ChatMessageDto>>() {});
                migrateLegacyKey(key, legacy);
                return new LoadedState(0, legacy);
            }
        } catch (Exception e) {
            log.warn("读取/反序列化 Redis 聊天记忆失败: memoryKey={}", memoryKey, e);
            return null;
        }
    }

    /** 将旧格式（纯消息数组）key 原地迁移为 {version:0, messages:[...]}。 */
    private void migrateLegacyKey(String key, List<ChatMessageDto> legacy) {
        try {
            String migrated = objectMapper.writeValueAsString(new PersistedState(0, legacy));
            stringRedisTemplate.opsForValue().set(key, migrated, TTL);
            log.info("Redis 聊天记忆已从旧格式迁移到 {version,messages}: key={}", key);
        } catch (Exception e) {
            log.warn("Redis 聊天记忆旧格式迁移失败，将由 Lua CAS 在下次写入时升级: key={}", key, e);
        }
    }

    /**
     * 版本 CAS 持久化。expectedVersion 与 Redis 当前版本一致才写入。
     *
     * @return true=写入成功（含 key 不存在时的首次写入）；false=版本冲突
     */
    boolean persistWithCas(String memoryKey, SerializableChatMemory memory, int expectedVersion) {
        if (!useRedis) return true;
        List<ChatMessage> messages = memory.messages();
        if (messages.isEmpty()) {
            try {
                stringRedisTemplate.delete(KEY_PREFIX + memoryKey);
            } catch (Exception e) {
                log.warn("删除 Redis 聊天记忆失败: memoryKey={}", memoryKey, e);
            }
            memory.version = 0;
            return true;
        }
        String key = KEY_PREFIX + memoryKey;
        try {
            List<ChatMessageDto> dtos = new ArrayList<>();
            for (ChatMessage msg : messages) {
                dtos.add(ChatMessageDto.from(msg));
            }
            String json = objectMapper.writeValueAsString(new PersistedState(expectedVersion + 1, dtos));
            Long result = stringRedisTemplate.execute(CAS_SCRIPT, List.of(key),
                    json, String.valueOf(expectedVersion), String.valueOf(TTL_SECONDS));
            if (result != null && result == 1L) {
                memory.version = expectedVersion + 1;
                return true;
            }
            return false;
        } catch (Exception e) {
            log.error("序列化/写入 Redis 聊天记忆失败: memoryKey={}", memoryKey, e);
            return false;
        }
    }

    /**
     * 版本冲突收敛：从 Redis 重新加载最新状态，替换本地副本，
     * 再补回冲突时待写入的消息并重试一次。
     * <p>Redis 不可读（故障）或 key 已被删除时保留本地副本，
     * 等待 Redis 恢复后由下一次 persist 写入，避免故障期间清空会话。
     */
    void reconcileAfterConflict(String memoryKey, SerializableChatMemory memory, ChatMessage pending) {
        LoadedState state = loadState(memoryKey);
        if (state == null) {
            log.warn("记忆冲突且无法读取 Redis 最新状态，保留本地副本: memoryKey={}", memoryKey);
            return;
        }
        memory.delegate.clear();
        for (ChatMessageDto dto : state.messages()) {
            memory.delegate.add(dto.toMessage());
        }
        memory.version = state.version();
        memory.delegate.add(pending);
        if (!persistWithCas(memoryKey, memory, memory.version)) {
            log.warn("记忆冲突收敛重试仍失败，保留本地副本: memoryKey={}", memoryKey);
        }
    }

    /**
     * Transparently persists chat memory to Redis on every add() call.
     */
    static class SerializableChatMemory implements ChatMemory {
        private final String memoryKey;
        private final ChatMemory delegate;
        private final RedisChatMemoryProvider provider;
        private int version;

        SerializableChatMemory(String memoryKey, ChatMemory delegate,
                               RedisChatMemoryProvider provider, int version) {
            this.memoryKey = memoryKey;
            this.delegate = delegate;
            this.provider = provider;
            this.version = version;
        }

        @Override
        public Object id() { return delegate.id(); }

        @Override
        public void add(ChatMessage message) {
            // 同一会话内的 add+persist 串行化，避免实例内交错写
            synchronized (delegate) {
                delegate.add(message);
                if (!provider.persistWithCas(memoryKey, this, version)) {
                    provider.reconcileAfterConflict(memoryKey, this, message);
                }
            }
        }

        @Override
        public List<ChatMessage> messages() { return delegate.messages(); }

        @Override
        public void clear() {
            synchronized (delegate) {
                delegate.clear();
                provider.persistWithCas(memoryKey, this, version);
            }
        }
    }

    record PersistedState(int version, List<ChatMessageDto> messages) {
    }

    record LoadedState(int version, List<ChatMessageDto> messages) {
    }

    /**
     * Serialization DTO for LangChain4j ChatMessage types.
     * Preserves tool calls (id/name/arguments) and tool-execution ids so
     * multi-turn tool-calling conversations survive a Redis round-trip.
     */
    record ChatMessageDto(String type, String text, String name,
                          String toolExecutionId, String toolName,
                          List<ToolCallDto> toolCalls) {
        static ChatMessageDto from(ChatMessage msg) {
            String type = msg.type().name();
            String text = null;
            String name = null;
            String toolExecutionId = null;
            String toolName = null;
            List<ToolCallDto> toolCalls = null;
            if (msg instanceof AiMessage ai) {
                text = ai.text();
                if (ai.toolExecutionRequests() != null && !ai.toolExecutionRequests().isEmpty()) {
                    toolCalls = ai.toolExecutionRequests().stream()
                            .map(t -> new ToolCallDto(t.id(), t.name(), t.arguments()))
                            .toList();
                }
            } else if (msg instanceof UserMessage user) {
                text = user.singleText();
                name = user.name();
            } else if (msg instanceof SystemMessage sys) {
                text = sys.text();
            } else if (msg instanceof ToolExecutionResultMessage tool) {
                text = tool.text();
                toolExecutionId = tool.id();
                toolName = tool.toolName();
            }
            return new ChatMessageDto(type, text, name, toolExecutionId, toolName, toolCalls);
        }

        ChatMessage toMessage() {
            String msgText = text != null ? text : "";
            return switch (type) {
                case "AI" -> toolCalls == null || toolCalls.isEmpty()
                        ? new AiMessage(msgText)
                        : new AiMessage(msgText, toolCalls.stream()
                                .map(tc -> ToolExecutionRequest.builder()
                                        .id(tc.id()).name(tc.name()).arguments(tc.arguments())
                                        .build())
                                .toList());
                case "USER" -> name != null
                        ? UserMessage.from(name, msgText)
                        : UserMessage.from(msgText);
                case "SYSTEM" -> SystemMessage.from(msgText);
                case "TOOL_EXECUTION_RESULT" -> new ToolExecutionResultMessage(
                        toolExecutionId, toolName != null ? toolName : "", msgText);
                default -> UserMessage.from(msgText);
            };
        }

        record ToolCallDto(String id, String name, String arguments) {
        }
    }
}
