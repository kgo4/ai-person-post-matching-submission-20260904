package com.example.matching.dto.learning;

import lombok.Data;

/**
 * 项目提交审核请求
 *
 * @author system
 */
@Data
public class LearningProjectReviewDTO {

    /** 审核状态：APPROVED/REJECTED */
    private String reviewStatus;

    /** 审核意见 */
    private String reviewComment;

    /** 审核后能力等级（可选） */
    private Integer abilityLevelAfter;

    /** 审核评分 (0-100) */
    private Integer score;
}
