package com.example.matching.ai.context.dto;

import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

/**
 * AI上下文能力DTO
 *
 * @author system
 */
@Data
public class AiContextAbilityDTO {

    /** 能力标签ID */
    private Long abilityTagId;

    /** 能力名称 */
    private String abilityName;

    /** 当前等级（员工能力） */
    private Integer currentLevel;

    /** 要求等级（岗位要求） */
    private Integer requiredLevel;

    /** 权重 */
    private BigDecimal weight;

    /** 是否必填 */
    private Boolean required;

    /** 是否核心 */
    private Boolean core;

    /** 来源：EMP_ABILITY/POST_ABILITY_MODEL */
    private String source;

    /** 可信度 */
    private BigDecimal credibility;

    /** 证据数量 */
    private Integer evidenceCount;

    /** 来源引用列表 */
    private List<String> sourceRefs;
}
