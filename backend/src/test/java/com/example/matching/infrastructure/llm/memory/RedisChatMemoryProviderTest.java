package com.example.matching.infrastructure.llm.memory;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ToolExecutionResultMessage;
import dev.langchain4j.data.message.UserMessage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.data.redis.core.script.RedisScript;

import java.time.Duration;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@DisplayName("RedisChatMemoryProvider")
class RedisChatMemoryProviderTest {

    private static final String KEY = "chat:memory:1";

    private StringRedisTemplate template;
    private ValueOperations<String, String> ops;
    private RedisChatMemoryProvider provider;

    @BeforeEach
    void setUp() {
        template = mock(StringRedisTemplate.class);
        ops = mock(ValueOperations.class);
        when(template.opsForValue()).thenReturn(ops);
        when(template.execute(any(RedisScript.class), anyList(), any(Object[].class))).thenReturn(1L);
        @SuppressWarnings("unchecked")
        ObjectProvider<StringRedisTemplate> templateProvider = mock(ObjectProvider.class);
        when(templateProvider.getIfAvailable()).thenReturn(template);
        ObjectMapper mapper = new ObjectMapper().registerModule(new JavaTimeModule());
        provider = new RedisChatMemoryProvider(templateProvider, mapper);
    }

    @Test
    @DisplayName("clear() 必须删除 Redis key，防止旧记忆复活")
    void clearDeletesRedisKey() {
        provider.getMemory(1L).add(UserMessage.from("hello"));

        provider.clear(1L);

        verify(template).delete(KEY);
    }

    @Test
    @DisplayName("clear() 后会话缓存被移除，hasMemory 返回 false")
    void clearRemovesSessionCache() {
        provider.getMemory(1L).add(UserMessage.from("hello"));
        when(template.hasKey(KEY)).thenReturn(false);

        provider.clear(1L);

        assertThat(provider.hasMemory(1L)).isFalse();
    }

    @Test
    @DisplayName("写入携带版本并走 CAS 脚本")
    void persistUsesVersionedCasScript() {
        provider.getMemory(1L).add(UserMessage.from("hello"));

        verify(template).execute(any(RedisScript.class), eq(List.of(KEY)),
                any(String.class), any(String.class), any(String.class));
    }

    @Test
    @DisplayName("工具调用会话经 Redis 往返后 toolCalls 与执行ID完整保留")
    void toolCallConversationSurvivesRedisRoundTrip() {
        var memory = provider.getMemory(1L);
        memory.add(UserMessage.from("请查询员工档案"));
        ToolExecutionRequest toolRequest = ToolExecutionRequest.builder()
                .id("call-1").name("employeeProfile").arguments("{\"empId\":7}").build();
        memory.add(new AiMessage("查询中", List.of(toolRequest)));
        memory.add(new ToolExecutionResultMessage("call-1", "employeeProfile", "{\"name\":\"张三\"}"));

        org.mockito.ArgumentCaptor<Object[]> argsCaptor = org.mockito.ArgumentCaptor.forClass(Object[].class);
        verify(template, times(3)).execute(any(RedisScript.class), eq(List.of(KEY)), argsCaptor.capture());
        String persistedJson = (String) argsCaptor.getAllValues().get(argsCaptor.getAllValues().size() - 1)[0];
        assertThat(persistedJson).contains("call-1");

        when(ops.get(KEY)).thenReturn(persistedJson);
        var restored = freshProvider().getMemory(1L);
        List<dev.langchain4j.data.message.ChatMessage> messages = restored.messages();

        assertThat(messages).hasSize(3);
        AiMessage ai = (AiMessage) messages.get(1);
        assertThat(ai.toolExecutionRequests()).hasSize(1);
        assertThat(ai.toolExecutionRequests().get(0).id()).isEqualTo("call-1");
        assertThat(ai.toolExecutionRequests().get(0).name()).isEqualTo("employeeProfile");
        assertThat(ai.toolExecutionRequests().get(0).arguments()).isEqualTo("{\"empId\":7}");
        ToolExecutionResultMessage toolResult = (ToolExecutionResultMessage) messages.get(2);
        assertThat(toolResult.id()).isEqualTo("call-1");
        assertThat(toolResult.toolName()).isEqualTo("employeeProfile");
        assertThat(toolResult.text()).contains("张三");
    }

    @Test
    @DisplayName("从 Redis 恢复的记忆再次 add 会继续持久化")
    void restoredMemoryPersistsSubsequentAdds() {
        var memory = provider.getMemory(1L);
        memory.add(UserMessage.from("first"));
        org.mockito.ArgumentCaptor<Object[]> argsCaptor = org.mockito.ArgumentCaptor.forClass(Object[].class);
        verify(template).execute(any(RedisScript.class), eq(List.of(KEY)), argsCaptor.capture());
        String persistedJson = (String) argsCaptor.getValue()[0];

        when(ops.get(KEY)).thenReturn(persistedJson);
        var restored = freshProvider().getMemory(1L);
        assertThat(restored.messages()).hasSize(1);

        restored.add(UserMessage.from("second"));
        verify(template, times(2)).execute(any(RedisScript.class), eq(List.of(KEY)), any(Object[].class));
    }

    @Test
    @DisplayName("版本冲突时从 Redis 重载合并，不丢待写入消息")
    void versionConflictReloadsAndMerges() {
        var memory = provider.getMemory(1L);
        memory.add(UserMessage.from("first"));
        org.mockito.ArgumentCaptor<Object[]> argsCaptor = org.mockito.ArgumentCaptor.forClass(Object[].class);
        verify(template).execute(any(RedisScript.class), eq(List.of(KEY)), argsCaptor.capture());
        String persistedJson = (String) argsCaptor.getValue()[0];

        // 模拟另一实例已写入更高版本（含 "remote" 消息），本地写被拒
        when(ops.get(KEY)).thenReturn(
                persistedJson.replace("\"version\":1", "\"version\":2")
                        .replace("\"messages\":[", "\"messages\":[{\"type\":\"USER\",\"text\":\"remote\",\"name\":null,\"toolExecutionId\":null,\"toolName\":null,\"toolCalls\":null},"));
        when(template.execute(any(RedisScript.class), anyList(), any(Object[].class)))
                .thenReturn(0L, 1L);

        memory.add(UserMessage.from("local"));

        var messages = memory.messages();
        assertThat(messages).extracting(m -> ((UserMessage) m).singleText())
                .contains("remote", "local", "first");
    }

    @Test
    @DisplayName("运行时 Redis 故障不抛出异常，会话在内存中继续")
    void redisFailureAtRuntimeDegradesGracefully() {
        doThrow(new RedisConnectionFailureException("redis down"))
                .when(template).execute(any(RedisScript.class), anyList(), any(Object[].class));

        var memory = provider.getMemory(1L);

        memory.add(UserMessage.from("hello"));
        memory.add(UserMessage.from("world"));

        assertThat(memory.messages()).hasSize(2);
    }

    @Test
    @DisplayName("Redis 读取失败时回退为本地新记忆且不崩溃")
    void redisReadFailureCreatesFreshSession() {
        when(ops.get(KEY)).thenThrow(new RedisConnectionFailureException("redis down"));

        var memory = provider.getMemory(1L);
        memory.add(UserMessage.from("fresh"));

        assertThat(memory.messages()).hasSize(1);
    }

    @Test
    @DisplayName("clear() 空记忆直接删 key，不写状态")
    void clearOnEmptyMemoryDeletesKeyOnly() {
        provider.getMemory(1L).clear();

        verify(template).delete(KEY);
        verify(template, never()).execute(any(RedisScript.class), anyList(), any(Object[].class));
    }

    @Test
    @DisplayName("旧格式（纯数组）key 可恢复会话并原地迁移为 {version,messages}")
    void legacyArrayFormatSessionRecoversAndPersists() {
        String legacyJson = "[{\"type\":\"USER\",\"text\":\"legacy-msg\",\"name\":null,"
                + "\"toolExecutionId\":null,\"toolName\":null,\"toolCalls\":null}]";
        when(ops.get(KEY)).thenReturn(legacyJson);

        var memory = provider.getMemory(1L);

        assertThat(memory.messages()).hasSize(1);
        assertThat(((UserMessage) memory.messages().get(0)).singleText()).isEqualTo("legacy-msg");

        org.mockito.ArgumentCaptor<String> migratedCaptor = org.mockito.ArgumentCaptor.forClass(String.class);
        verify(ops).set(eq(KEY), migratedCaptor.capture(), any(Duration.class));
        assertThat(migratedCaptor.getValue()).contains("\"version\":0");
        assertThat(migratedCaptor.getValue()).contains("legacy-msg");

        memory.add(UserMessage.from("new-msg"));
        verify(template).execute(any(RedisScript.class), eq(List.of(KEY)), any(Object[].class));
        assertThat(memory.messages()).hasSize(2);
    }

    @Test
    @DisplayName("旧格式 key 迁移写失败时会话仍可用，不被 CAS 冲突卡死")
    void legacyArrayWithFailedMigrationStillUsable() {
        String legacyJson = "[{\"type\":\"USER\",\"text\":\"legacy-msg\",\"name\":null,"
                + "\"toolExecutionId\":null,\"toolName\":null,\"toolCalls\":null}]";
        when(ops.get(KEY)).thenReturn(legacyJson);
        doThrow(new RedisConnectionFailureException("redis down"))
                .when(ops).set(any(String.class), any(String.class), any(Duration.class));

        var memory = provider.getMemory(1L);
        assertThat(memory.messages()).hasSize(1);

        memory.add(UserMessage.from("new-msg"));

        // 会话不卡死：本地可继续累积；CAS 在 mock 中成功则正常持久化
        assertThat(memory.messages()).hasSize(2);
        verify(template).execute(any(RedisScript.class), eq(List.of(KEY)), any(Object[].class));
    }

    private RedisChatMemoryProvider freshProvider() {
        @SuppressWarnings("unchecked")
        ObjectProvider<StringRedisTemplate> templateProvider = mock(ObjectProvider.class);
        when(templateProvider.getIfAvailable()).thenReturn(template);
        return new RedisChatMemoryProvider(templateProvider, new ObjectMapper());
    }
}
