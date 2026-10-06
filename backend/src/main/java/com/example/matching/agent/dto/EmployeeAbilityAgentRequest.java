package com.example.matching.agent.dto;

import lombok.Data;

/**
 * 员工能力Agent请求DTO
 *
 * @author system
 */
@Data
public class EmployeeAbilityAgentRequest {
    /** 员工ID */
    private Long empId;

    /** 是否包含能力来源 */
    private Boolean includeSourceDetails;

    /** 是否包含缺失证据分析 */
    private Boolean includeMissingEvidence;

    /** 是否包含补充建议 */
    private Boolean includeSuggestions;
}
