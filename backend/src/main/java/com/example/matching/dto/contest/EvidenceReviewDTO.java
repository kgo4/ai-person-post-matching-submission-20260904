package com.example.matching.dto.contest;

import lombok.Data;

/**
 * 证据审核DTO
 *
 * @author system
 */
@Data
public class EvidenceReviewDTO {

    /** 审核状态：VERIFIED/REJECTED */
    private String evidenceStatus;

    /** 审核意见 */
    private String reviewComment;
}
