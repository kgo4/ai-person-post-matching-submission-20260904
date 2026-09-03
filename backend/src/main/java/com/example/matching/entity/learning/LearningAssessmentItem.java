package com.example.matching.entity.learning;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 学习评估题目实体
 *
 * @author system
 */
@Data
@EqualsAndHashCode(callSuper = false)
@TableName("learning_assessment_item")
public class LearningAssessmentItem implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /** 所属计划ID */
    private Long planId;

    /** 关联步骤ID */
    private Long stepId;

    /** 关联能力标签ID */
    private Long abilityTagId;

    /** 题目类型：INTERVIEW/PROJECT_REVIEW/QUIZ */
    private String questionType;

    /** 题目文本 */
    private String questionText;

    /** 参考答案 */
    private String referenceAnswer;

    /** 难度等级：EASY/MEDIUM/HARD */
    private String difficultyLevel;

    /** 来源：AI_GENERATED/SYSTEM_TEMPLATE/MANUAL */
    private String source;

    /** 用户提交的作答内容 */
    private String answerText;

    /** 自动评分，0-100 */
    private Integer score;

    /** 作答状态：PENDING/PASSED/NOT_PASSED */
    private String assessmentStatus;

    /** 评分反馈 */
    private String scoringFeedback;

    private LocalDateTime answeredTime;

    private LocalDateTime scoredTime;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedTime;

    @TableLogic
    private Integer isDeleted;
}
