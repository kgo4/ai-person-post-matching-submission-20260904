package com.example.matching.entity.learning;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 答题记录实体
 *
 * @author system
 */
@Data
@EqualsAndHashCode(callSuper = false)
@TableName("learning_quiz_record")
public class LearningQuizRecord implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 主键，自增 */
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /** 员工ID */
    private Long empId;

    /** 题目ID */
    private Long quizId;

    /** 学习计划ID */
    private Long planId;

    /** 学习步骤ID */
    private Long stepId;

    /** 用户答案 */
    private String userAnswer;

    /** 是否正确：0错误，1正确 */
    private Integer isCorrect;

    /** 答题用时（秒） */
    private Integer answerTime;

    /** 得分 */
    private BigDecimal answerScore;

    /** 尝试次数 */
    private Integer attemptCount;

    /** 首次答题时间 */
    private LocalDateTime firstAttemptTime;

    /** 最后答题时间 */
    private LocalDateTime lastAttemptTime;

    /** 累计正确次数 */
    private Integer correctCount;

    /** 是否已掌握：0未掌握，1已掌握 */
    private Integer isMastered;

    /** 掌握时间 */
    private LocalDateTime masteredTime;

    /** 逻辑删除：0未删除，1已删除 */
    @TableLogic
    private Integer isDeleted;

    /** 创建人ID */
    private Long createdBy;

    /** 创建时间 */
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdTime;

    /** 更新人ID */
    private Long updatedBy;

    /** 更新时间 */
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedTime;

    /** 乐观锁版本号 */
    @Version
    private Integer version;
}
