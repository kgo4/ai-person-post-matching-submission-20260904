package com.example.matching.event;

/**
 * AI 测试题目最终生成失败事件。
 *
 * @param testId 测试记录 ID
 * @param workflowId 关联的能力评估工作流 ID；非工作流测试为 null
 * @param errorMessage 失败原因
 */
public record AiTestQuestionsGenerationFailedEvent(Long testId, Long workflowId, String errorMessage) {
}
