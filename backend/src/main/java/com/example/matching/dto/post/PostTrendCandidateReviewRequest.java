package com.example.matching.dto.post;

import java.io.Serializable;

/**
 * 候选审核（不落地，仅标记人工结论）。
 *
 * @param confirmStatus APPROVED 通过 / REJECTED 驳回 / PENDING 撤回结论
 * @param reviewComment 审核意见
 */
public record PostTrendCandidateReviewRequest(
        String confirmStatus,
        String reviewComment
) implements Serializable {
}
