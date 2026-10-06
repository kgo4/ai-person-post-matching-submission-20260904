package com.example.matching.agent.dto.learning;

import lombok.Data;

/**
 * 学习测评答案 AI 评分结果
 */
@Data
public class LearningAssessmentAiScore {

    /** 评分 0-100 */
    private Integer score;

    /** 是否通过（默认 score>=60） */
    private Boolean passed;

    /** 评审反馈（改进建议） */
    private String feedback;
}
