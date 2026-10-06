package com.example.matching.agent.dto;

import lombok.Data;

/**
 * 学习路径Agent请求DTO
 *
 * @author system
 */
@Data
public class LearningPathAgentRequest {
    /** 匹配记录ID */
    private Long matchingRecordId;

    /** 是否包含项目任务 */
    private Boolean includeProjectTasks;

    /** 是否包含评估题目 */
    private Boolean includeAssessments;

    /** 目标分数 */
    private Integer targetScore;
}
