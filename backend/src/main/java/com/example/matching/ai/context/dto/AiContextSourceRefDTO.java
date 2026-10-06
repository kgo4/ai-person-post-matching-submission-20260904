package com.example.matching.ai.context.dto;

import lombok.Data;

import java.math.BigDecimal;

/**
 * AI上下文来源引用DTO
 * <p>
 * 标准引用格式：
 * fact:EMP_ABILITY:{id}
 * fact:POST_ABILITY_MODEL:{id}
 * evidence:CONTEST_EVIDENCE:{id}
 * matching:MATCHING_RECORD:{id}
 * feedback:MATCHING_FEEDBACK:{id}
 * kg:NODE:{nodeKey}
 * rag:CHUNK:{chunkId}
 * learning:RESOURCE:{id}
 * learning:PATH:{id}
 *
 * @author system
 */
@Data
public class AiContextSourceRefDTO {

    /** 标准引用标识，如 fact:EMP_ABILITY:123 */
    private String ref;

    /** 引用类型：fact/evidence/matching/feedback/kg/rag/learning */
    private String refType;

    /** 引用对象ID */
    private String refId;

    /** 标题 */
    private String title;

    /** 原文摘要 */
    private String snippet;

    /** 来源类型：MANUAL/AI_ASSESSMENT/RESUME_PARSE/VIDEO_INTERVIEW/PMS_ANALYSIS 等 */
    private String sourceType;

    /** 模型置信度 0-100 */
    private BigDecimal confidenceScore;

    /** 来源可信度 0-100 */
    private BigDecimal credibilityScore;

    /** 审核状态：PENDING/VERIFIED/REJECTED */
    private String reviewStatus;
}
