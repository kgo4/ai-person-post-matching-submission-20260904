package com.example.matching.infrastructure.llm.memory;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.memory.ChatMemory;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 面试结束会话记忆清理测试：验证 Redis 与 Caffeine 两个实现的
 * 缓存键、内存对象和消息内容均被移除，且清理幂等。
 */
class ChatMemoryClearTest {

    @Test
    void stageMemoriesAreIsolatedPerInterviewStage() {
        // M1：同 session 的 plan 与 answer-quality memory 不共享消息
        CaffeineChatMemoryProvider provider = new CaffeineChatMemoryProvider();

        ChatMemory planMemory = provider.getMemory("INTERVIEW_PLAN:42");
        planMemory.add(UserMessage.from("计划阶段消息"));
        ChatMemory qualityMemory = provider.getMemory("INTERVIEW_ANSWER_QUALITY:42");
        qualityMemory.add(UserMessage.from("回答质量阶段消息"));

        assertThat(provider.getMemory("INTERVIEW_PLAN:42").messages())
                .extracting(m -> ((dev.langchain4j.data.message.UserMessage) m).singleText())
                .containsExactly("计划阶段消息");
        assertThat(provider.getMemory("INTERVIEW_ANSWER_QUALITY:42").messages())
                .extracting(m -> ((dev.langchain4j.data.message.UserMessage) m).singleText())
                .containsExactly("回答质量阶段消息");
    }

    @Test
    void sessionEndClearsAllStageMemories() {
        // M1：会话结束后所有 stage memory 被删除
        CaffeineChatMemoryProvider provider = new CaffeineChatMemoryProvider();

        provider.getMemory("INTERVIEW_PLAN:42").add(UserMessage.from("p"));
        provider.getMemory("INTERVIEW_ANSWER_QUALITY:42").add(UserMessage.from("a"));
        provider.getMemory("INTERVIEW_FOLLOW_UP:42").add(UserMessage.from("f"));
        provider.getMemory("INTERVIEW_OBSERVATION:42").add(UserMessage.from("o"));
        provider.getMemory("INTERVIEW_REPORT:42").add(UserMessage.from("r"));
        assertThat(provider.hasMemory("INTERVIEW_PLAN:42")).isTrue();

        provider.clear(42L);

        assertThat(provider.hasMemory("INTERVIEW_PLAN:42")).isFalse();
        assertThat(provider.hasMemory("INTERVIEW_ANSWER_QUALITY:42")).isFalse();
        assertThat(provider.hasMemory("INTERVIEW_FOLLOW_UP:42")).isFalse();
        assertThat(provider.hasMemory("INTERVIEW_OBSERVATION:42")).isFalse();
        assertThat(provider.hasMemory("INTERVIEW_REPORT:42")).isFalse();
        assertThat(provider.hasMemory(42L)).isFalse();
    }

    @Test
    void caffeineProviderClearRemovesMemoryObjectAndMessages() {
        CaffeineChatMemoryProvider provider = new CaffeineChatMemoryProvider();

        ChatMemory memory = provider.getMemory(42L);
        memory.add(UserMessage.from("敏感内容-不应残留"));
        assertThat(provider.hasMemory(42L)).isTrue();

        provider.clear(42L);

        assertThat(provider.hasMemory(42L)).isFalse();
        // 再次获取的是全新空记忆，旧消息内容已不存在
        ChatMemory fresh = provider.getMemory(42L);
        assertThat(fresh.messages()).isEmpty();
    }

    @Test
    void caffeineProviderClearIsIdempotent() {
        CaffeineChatMemoryProvider provider = new CaffeineChatMemoryProvider();
        provider.clear(42L);
        provider.clear(42L);
        assertThat(provider.hasMemory(42L)).isFalse();
    }

    @Test
    void redisProviderClearRemovesLocalObjectsAndRedisKey() {
        ObjectMapper mapper = new ObjectMapper();
        ValueOperations<String, String> valueOps = mock(ValueOperations.class);
        StringRedisTemplate template = mock(StringRedisTemplate.class);
        when(template.opsForValue()).thenReturn(valueOps);
        when(valueOps.get(anyString())).thenReturn(null);

        @SuppressWarnings("unchecked")
        ObjectProvider<StringRedisTemplate> providerOf = mock(ObjectProvider.class);
        when(providerOf.getIfAvailable()).thenReturn(template);

        RedisChatMemoryProvider provider = new RedisChatMemoryProvider(providerOf, mapper);

        ChatMemory memory = provider.getMemory(7L);
        memory.add(UserMessage.from("会话消息-不应残留"));
        assertThat(provider.hasMemory(7L)).isTrue();

        provider.clear(7L);

        verify(template).delete("chat:memory:7");
        assertThat(provider.hasMemory(7L)).isFalse();
        // 再次获取的是全新空记忆
        assertThat(provider.getMemory(7L).messages()).isEmpty();
    }

    @Test
    void redisProviderClearIsIdempotentAndNullSafe() {
        ObjectMapper mapper = new ObjectMapper();
        ValueOperations<String, String> valueOps = mock(ValueOperations.class);
        StringRedisTemplate template = mock(StringRedisTemplate.class);
        when(template.opsForValue()).thenReturn(valueOps);

        @SuppressWarnings("unchecked")
        ObjectProvider<StringRedisTemplate> providerOf = mock(ObjectProvider.class);
        when(providerOf.getIfAvailable()).thenReturn(template);

        RedisChatMemoryProvider provider = new RedisChatMemoryProvider(providerOf, mapper);
        provider.clear(7L);
        provider.clear(null);
        assertThat(provider.hasMemory(7L)).isFalse();
    }
}
