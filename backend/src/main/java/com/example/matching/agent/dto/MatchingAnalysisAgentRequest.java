package com.example.matching.agent.dto;

import lombok.Data;

/**
 * 匹配分析Agent请求DTO
 *
 * @author system
 */
@Data
public class MatchingAnalysisAgentRequest {
    /** 匹配记录ID */
    private Long matchingRecordId;

    /** 是否包含证据详情 */
    private Boolean includeEvidenceDetails;

    /** 是否包含学习建议 */
    private Boolean includeLearningSuggestions;
}
