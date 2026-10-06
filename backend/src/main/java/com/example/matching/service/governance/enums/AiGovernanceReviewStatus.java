package com.example.matching.service.governance.enums;

/**
 * AI 治理审核状态枚举
 *
 * @author system
 */
public enum AiGovernanceReviewStatus {

    /** 待处理 */
    PENDING("待处理"),
    /** 已采纳 */
    ACCEPTED("已采纳"),
    /** 已驳回 */
    REJECTED("已驳回"),
    /** 已处理 */
    RESOLVED("已处理"),
    /** 自动通过 */
    AUTO_PASSED("自动通过");

    private final String label;

    AiGovernanceReviewStatus(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }
}
