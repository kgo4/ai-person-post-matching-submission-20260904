package com.example.matching.dto.post;

import java.io.Serializable;
import java.util.List;

/**
 * 批量落地请求。
 * <p>
 * 只对 `harness_decision=PASS` 且 `confirm_status=PENDING` 的候选生效；
 * 其余候选会被跳过并在响应里逐条说明原因，不做静默忽略。
 */
public record PostTrendBatchConfirmRequest(
        List<Long> candidateIds
) implements Serializable {
}
