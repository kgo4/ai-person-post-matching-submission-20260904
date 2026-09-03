package com.example.matching.dto.learning;

import lombok.Data;

import javax.validation.constraints.NotNull;

/**
 * 评估题目生成请求
 *
 * @author system
 */
@Data
public class LearningAssessmentGenerateRequest {

    /** 学习路径计划ID */
    @NotNull(message = "planId cannot be null")
    private Long planId;

    /** 是否包含项目评审题 */
    private Boolean includeProjectReview;
}
