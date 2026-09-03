package com.example.matching.dto.harness;

import lombok.Data;

@Data
public class AiHarnessReviewUpdateDTO {

    /**
     * 审核状态：ACCEPTED / REJECTED / RESOLVED
     */
    private String reviewStatus;

    /**
     * 审核备注
     * - ACCEPTED: 采纳理由（可选）
     * - REJECTED: 拒绝原因（必填）
     * - RESOLVED: 处理说明（必填）
     */
    private String reviewComment;

    /**
     * 拒绝原因分类（仅 REJECTED 时使用）
     * EVIDENCE_INSUFFICIENT - 证据不足
     * INCONSISTENT_WITH_SOURCE - 与原文不符
     * SELF_EVIDENCE - 自证据
     * TAG_INACCURATE - 能力标签不准确
     * DUPLICATE - 重复
     * OTHER - 其他
     */
    private String rejectReasonCategory;

    /**
     * 采纳后是否应用到业务数据（仅 ACCEPTED 时使用）
     */
    private Boolean applyToBusiness;
}
