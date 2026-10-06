package com.example.matching.agent.dto;

import lombok.Data;

import java.math.BigDecimal;

/**
 * Agent来源引用DTO
 *
 * @author system
 */
@Data
public class AgentSourceRef {
    /** 完整标准引用标识，如 fact:EMP_ABILITY:123 */
    private String ref;

    /** 引用类型：fact, evidence, source, kg, rag, matching */
    private String refType;

    /** 引用ID，如 EMP_ABILITY:12 */
    private String refId;

    /** 标题 */
    private String title;

    /** 原文摘要 */
    private String snippet;

    /** 来源类型：MANUAL/AI_ASSESSMENT/RESUME_PARSE/VIDEO_INTERVIEW 等 */
    private String sourceType;

    /** 置信度 */
    private BigDecimal confidenceScore;

    /** 可信度 */
    private BigDecimal credibilityScore;

    /** 审核状态：PENDING/VERIFIED/REJECTED */
    private String reviewStatus;
}
