package com.example.matching.dto.contest;

import lombok.Data;

import java.math.BigDecimal;

/**
 * 证据创建DTO
 *
 * @author system
 */
@Data
public class EvidenceCreateDTO {

    /** 来源类型 */
    private String sourceType;

    /** 来源记录ID */
    private Long sourceRefId;

    /** 来源标题或文件名 */
    private String sourceTitle;

    /** 原始来源文本 */
    private String sourceText;

    /** 目标类型 */
    private String targetType;

    /** 目标实体ID */
    private Long targetRefId;

    /** 能力名称 */
    private String abilityName;

    /** 标签ID */
    private Long tagId;

    /** 置信度 0-100 */
    private BigDecimal confidenceScore;

    /** 可信度 0-100 */
    private BigDecimal credibilityScore;
}
