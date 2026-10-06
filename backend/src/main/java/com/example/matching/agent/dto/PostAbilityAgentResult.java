package com.example.matching.agent.dto;

import lombok.Data;
import lombok.EqualsAndHashCode;

import java.util.List;

/**
 * 岗位能力Agent结果DTO
 *
 * @author system
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class PostAbilityAgentResult extends AgentRunResult {
    /** 岗位模型总结 */
    private String modelSummary;

    /** 核心能力列表 */
    private List<AbilityItem> coreAbilities;

    /** 权重风险列表 */
    private List<WeightRiskItem> weightRisks;

    /** 缺失能力列表 */
    private List<String> missingAbilities;

    /** 优化建议列表 */
    private List<String> suggestions;

    /**
     * 能力项
     */
    @Data
    public static class AbilityItem {
        private Long abilityTagId;
        private String abilityName;
        private Integer requiredLevel;
        private Integer weight;
        private Boolean core;
        private String description;
    }

    /**
     * 权重风险项
     */
    @Data
    public static class WeightRiskItem {
        private Long abilityTagId;
        private String abilityName;
        private Integer currentWeight;
        private String riskType;
        private String riskDescription;
    }
}
