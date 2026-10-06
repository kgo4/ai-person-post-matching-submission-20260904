package com.example.matching.ai.context.dto;

import lombok.Data;

import java.util.List;

/**
 * AI上下文能力差距DTO
 *
 * @author system
 */
@Data
public class AiContextGapDTO {

    /** 能力标签ID */
    private Long abilityTagId;

    /** 能力名称 */
    private String abilityName;

    /** 当前等级 */
    private Integer currentLevel;

    /** 要求等级 */
    private Integer requiredLevel;

    /** 差距数值 */
    private Integer gap;

    /** 差距类型：LEVEL_GAP/MISSING/EVIDENCE_WEAK */
    private String gapType;

    /** 优先级：HIGH/MEDIUM/LOW */
    private String priority;

    /** 是否核心能力 */
    private Boolean core;

    /** 来源引用列表 */
    private List<String> sourceRefs;
}
