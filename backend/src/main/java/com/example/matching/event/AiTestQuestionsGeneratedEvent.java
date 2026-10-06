package com.example.matching.event;

/**
 * AI 测试题目异步生成完成事件。
 *
 * @param testId 测试记录 ID
 * @param workflowId 关联的能力评估工作流 ID；非工作流测试为 null
 */
public record AiTestQuestionsGeneratedEvent(Long testId, Long workflowId) {
}
