package com.example.matching.agent.dto;

import lombok.Data;

/**
 * 岗位能力Agent请求DTO
 *
 * @author system
 */
@Data
public class PostAbilityAgentRequest {
    /** 岗位ID */
    private Long postId;

    /** 是否包含核心能力分析 */
    private Boolean includeCoreAbilities;

    /** 是否包含权重风险分析 */
    private Boolean includeWeightRisks;

    /** 是否包含优化建议 */
    private Boolean includeSuggestions;
}
