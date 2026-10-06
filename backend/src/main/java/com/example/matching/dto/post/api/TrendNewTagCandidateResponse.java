package com.example.matching.dto.post.api;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 本次解析提出的新能力标签候选。
 * <p>
 * 只读展示 + 两个动作（采用现有标签 / 忽略）。**不在趋势页里把标签转正**：
 * 正式标签必须指定 L1 能力域，属于标签治理流程，硬塞进趋势页会把
 * 「零手工填写」变成一堆必填下拉。
 */
@Data
public class TrendNewTagCandidateResponse {

    private Long id;

    /** AI 从材料里提出的能力名 */
    private String candidateName;

    /** 最相似的既有标签（可能为空，表示相似度过低不值得参考） */
    private Long similarTagId;

    private String similarTagName;

    private BigDecimal similarityScore;

    /** 提出该能力的证据片段 */
    private String evidenceText;

    /** PENDING / APPROVED / REJECTED / MERGED */
    private String status;

    /** 该能力在本次解析中被提出的次数 */
    private Integer occurrenceCount;

    private LocalDateTime createdTime;
}
