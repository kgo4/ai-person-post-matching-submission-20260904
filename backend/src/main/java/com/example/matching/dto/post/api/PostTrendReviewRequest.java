package com.example.matching.dto.post.api;

import java.io.Serializable;

/**
 * 候选审核（驳回 / 仅标记通过但不落地）。
 * <p>
 * 正常路径是直接调 {@code /candidates/{id}/confirm} 落地，本接口用于「先挂着」或「不要这个候选」。
 *
 * @param confirmStatus APPROVED 或 REJECTED
 * @param reviewComment 审核意见；驳回时建议给出理由，便于后续回溯为什么没建这个岗位
 */
public record PostTrendReviewRequest(String confirmStatus, String reviewComment) implements Serializable {
}
