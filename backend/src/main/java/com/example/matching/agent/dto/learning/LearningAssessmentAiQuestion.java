package com.example.matching.agent.dto.learning;

import lombok.Data;

/**
 * 学习测评题目 AI 生成结果
 */
@Data
public class LearningAssessmentAiQuestion {

    /** 面试/测试题目文本 */
    private String questionText;

    /** 参考答案要点（可验证的实践说明，用于后续评分参考） */
    private String referenceAnswer;

    /** 难度等级：EASY / MEDIUM / HARD */
    private String difficultyLevel;

    /** 评分要点（评审该答案时应重点考察的内容） */
    private String scoringPoints;
}
