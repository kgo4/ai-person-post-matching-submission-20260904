package com.example.matching.ai.context.dto;

import lombok.Data;

import java.math.BigDecimal;

/**
 * AI上下文证据DTO
 *
 * @author system
 */
@Data
public class AiContextEvidenceDTO {

    /** 证据ID */
    private Long evidenceId;

    /** 证据编码 */
    private String evidenceCode;

    /** 来源类型 */
    private String sourceType;

    /** 来源标题 */
    private String sourceTitle;

    /** 原文摘要 */
    private String sourceSnippet;

    /** 关联能力名称 */
    private String abilityName;

    /** 关联能力标签ID */
    private Long tagId;

    /** 置信度 */
    private BigDecimal confidenceScore;

    /** 可信度 */
    private BigDecimal credibilityScore;

    /** 审核状态 */
    private String evidenceStatus;

    /** 标准引用标识 */
    private String sourceRef;
}
