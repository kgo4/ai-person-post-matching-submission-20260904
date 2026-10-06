package com.example.matching.dto.post.api;

import java.io.Serializable;
import java.util.List;

/**
 * 批量确认落地。
 * <p>
 * 只对「治理判定 PASS 且仍待确认」的候选生效：REVIEW / BLOCK 的候选必须逐张人工看过再落地，
 * 批量按钮不能成为绕过治理判定的后门。被跳过的候选会在结果里带上原因，不做静默忽略。
 *
 * @param candidateIds  待落地候选ID
 * @param reviewComment 审核意见，统一写入本批候选
 */
public record TrendBatchConfirmRequest(
        List<Long> candidateIds,
        String reviewComment) implements Serializable {
}
