package com.example.matching.infrastructure.llm.memory;

/**
 * Port for session-scoped chat memory in LangChain4j interview services.
 * <p>
 * Every interview method must have {@code @MemoryId Long sessionId}.
 * This provider returns a bounded, isolated memory window per session.
 * <p>
 * Sessions are keyed by interview session ID (not employee ID).
 * Missing or completed sessions receive no prior memory.
 * Memory contents must never be written to application logs.
 */
public interface ChatMemoryProvider {

    /**
     * Get or create a chat memory for the given session.
     * Returns a memory window that is isolated from all other sessions.
     *
     * @param sessionId the interview session ID
     * @return a ChatMemory-like object (dev.langchain4j.memory.ChatMemory)
     */
    dev.langchain4j.memory.ChatMemory getMemory(Long sessionId);

    /**
     * Get or create a chat memory for the given stage-scoped key
     * （如 {@code INTERVIEW_PLAN:123}）。同一 session 的不同阶段
     * （计划/回答质量/追问/观察/报告）互不共享消息。
     *
     * @param memoryKey stage 前缀 + 会话ID 组成的键
     * @return a ChatMemory-like object (dev.langchain4j.memory.ChatMemory)
     */
    dev.langchain4j.memory.ChatMemory getMemory(String memoryKey);

    /**
     * Clear the memory for a completed session（含全部 stage memory）。
     * Called when an interview session ends to prevent memory leaks.
     *
     * @param sessionId the interview session ID
     */
    void clear(Long sessionId);

    /**
     * Check if a session has existing memory.
     *
     * @param sessionId the interview session ID
     * @return true if memory exists for this session
     */
    boolean hasMemory(Long sessionId);

    /**
     * Check if a stage-scoped memory key has existing memory.
     *
     * @param memoryKey stage 前缀 + 会话ID 组成的键
     * @return true if memory exists for this key
     */
    boolean hasMemory(String memoryKey);
}
