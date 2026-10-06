package com.example.matching.event;

/**
 * AI 测试评估完成事件
 * <p>
 * 测试评分完成后发布；工作流测试（workflowId 非空）由能力评估流程
 * 保存为测试证据 Claim 并推进工作流，不直接正式入库。
 *
 * @author system
 */
public record AiTestEvaluatedEvent(Long testId, Long empId, Long workflowId) {
}
