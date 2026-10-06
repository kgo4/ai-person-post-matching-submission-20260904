package com.example.matching.ai.context.dto;

import lombok.Data;

import java.util.List;

/**
 * AI上下文风险信号DTO
 *
 * @author system
 */
@Data
public class AiContextRiskSignalDTO {

    /** 风险类型：WEAK_EVIDENCE/CORE_ABILITY_GAP/SELF_EVIDENCE/POST_MODEL_LOW_QUALITY/FEEDBACK_BIAS */
    private String riskType;

    /** 风险等级：HIGH/MEDIUM/LOW */
    private String riskLevel;

    /** 风险描述 */
    private String message;

    /** 关联来源引用 */
    private List<String> sourceRefs;
}
