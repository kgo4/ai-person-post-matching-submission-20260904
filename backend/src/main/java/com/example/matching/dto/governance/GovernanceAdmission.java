package com.example.matching.dto.governance;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Data
public class GovernanceAdmission {
    private Long id;
    private String admissionCode;
    private String scenario;
    private String claimType;
    private String claimText;
    private String sourceType;
    private Long sourceRefId;
    private String evidenceText;
    private String sourceRefsJson;
    private String ragChunkIdsJson;
    private Long matchedTagId;
    private Long similarTagId;

    private BigDecimal legacySupportScore;
    private BigDecimal harnessSupportScore;
    private BigDecimal finalSupportScore;
    private String legacyDecision;
    private String harnessDecision;
    private String finalDecision;
    private String decisionRule;
    private String harnessCheckCode;
    private String traceId;

    private String riskLevel;
    private boolean selfEvidence;
    private String reasonJson;
    private String acceptedSourceRefsJson;
    private String invalidSourceRefsJson;
    private String missingEvidenceJson;

    private String businessTargetType;
    private Long businessTargetId;
    private String applyStatus;

    private Long contextSnapshotId;
    private String contextHash;
    private String claimPayloadJson;

    /** RETRYABLE 已重试次数 */
    private Integer retryCount;

    /** 下次重试时间（RETRYABLE 状态使用） */
    private LocalDateTime nextRetryTime;

    private String reviewStatus;
    private String reviewComment;
    private LocalDateTime reviewedTime;
    private LocalDateTime createdTime;

    public boolean isAdmitted() {
        return GovernanceGrant.PASS.name().equals(finalDecision);
    }

    public boolean isRetryable() {
        return GovernanceGrant.RETRY.name().equals(finalDecision);
    }

    public boolean isBlocked() {
        return GovernanceGrant.BLOCK.name().equals(finalDecision);
    }

    public boolean isPendingReview() {
        return GovernanceGrant.REVIEW.name().equals(finalDecision);
    }
}
