package com.example.matching.infrastructure.llm.memory;

import dev.langchain4j.memory.ChatMemory;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * LangChain4j adapter that bridges the infrastructure ChatMemoryProvider
 * to LangChain4j's {@code dev.langchain4j.memory.ChatMemoryProvider} interface.
 * <p>
 * Used in {@code AiServices.builder().chatMemoryProvider(...)} for session-isolated
 * interview memory.
 * <p>
 * M1：每个 adapter 实例绑定一个 stage 前缀（INTERVIEW_PLAN / INTERVIEW_ANSWER_QUALITY /
 * INTERVIEW_FOLLOW_UP / INTERVIEW_OBSERVATION / INTERVIEW_REPORT），同一 session 的
 * 不同面试阶段使用独立 memory 窗口，互不共享消息。
 */
@Slf4j
@Component
public class LangChain4jChatMemoryProvider implements dev.langchain4j.memory.chat.ChatMemoryProvider {

    /** 默认（无 stage 前缀）适配器：保持旧行为 */
    public static final String DEFAULT_STAGE = "";

    private final ChatMemoryProvider memoryProvider;
    private final String stagePrefix;

    @Autowired
    public LangChain4jChatMemoryProvider(ChatMemoryProvider memoryProvider) {
        this(memoryProvider, DEFAULT_STAGE);
    }

    public LangChain4jChatMemoryProvider(ChatMemoryProvider memoryProvider, String stagePrefix) {
        this.memoryProvider = memoryProvider;
        this.stagePrefix = stagePrefix == null ? DEFAULT_STAGE : stagePrefix.trim();
    }

    @Override
    public ChatMemory get(Object memoryId) {
        Long sessionId = extractSessionId(memoryId);
        if (sessionId == null) {
            throw new IllegalArgumentException(
                    "ChatMemory requires a non-null Long sessionId as @MemoryId, got: " + memoryId);
        }
        if (stagePrefix.isEmpty()) {
            return memoryProvider.getMemory(sessionId);
        }
        return memoryProvider.getMemory(stagePrefix + ":" + sessionId);
    }

    private Long extractSessionId(Object memoryId) {
        if (memoryId == null) {
            return null;
        }
        if (memoryId instanceof Long) {
            return (Long) memoryId;
        }
        if (memoryId instanceof Number) {
            return ((Number) memoryId).longValue();
        }
        try {
            return Long.parseLong(memoryId.toString());
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
