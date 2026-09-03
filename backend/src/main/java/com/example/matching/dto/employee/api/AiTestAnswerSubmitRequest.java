package com.example.matching.dto.employee.api;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;

import java.util.Map;

/**
 * AI 能力测试答案提交请求。
 * <p>
 * 仅在 HTTP 边界使用；业务方法仍接收 {@code Map<String, Object>} 以保持数据库载荷与既有客户端 JSON 兼容：
 * {@code { "answers": { "questionId": "answer" } }}
 * <p>
 * 多选题答案以数组提交：{@code { "answers": { "questionId": ["optA", "optB"] } }}
 */
public record AiTestAnswerSubmitRequest(
        @NotEmpty(message = "答案不能为空")
        Map<@NotBlank(message = "题目ID不能为空") String, Object> answers
) {
}
