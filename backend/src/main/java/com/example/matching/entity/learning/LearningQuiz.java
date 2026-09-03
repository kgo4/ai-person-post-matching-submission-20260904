package com.example.matching.entity.learning;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 测验题目实体
 *
 * @author system
 */
@Data
@EqualsAndHashCode(callSuper = false)
@TableName("learning_quiz")
public class LearningQuiz implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 主键，自增 */
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /** 题目编码 */
    private String quizCode;

    /** 题目文本 */
    private String questionText;

    /** 题目类型：SINGLE_CHOICE/MULTI_CHOICE/FILL_BLANK/SHORT_ANSWER */
    private String questionType;

    /** 选项JSON数组 */
    private String optionsJson;

    /** 参考答案 */
    private String referenceAnswer;

    /** 答案解析 */
    private String answerExplanation;

    /** 难度级别：EASY/MEDIUM/HARD */
    private String difficultyLevel;

    /** 所属知识领域ID */
    private Long domainId;

    /** 所属知识点ID */
    private Long nodeId;

    /** 关联能力标签ID */
    private Long tagId;

    /** 预计答题时间（秒） */
    private Integer estimatedTime;

    /** 分值 */
    private BigDecimal score;

    /** 使用次数 */
    private Integer usageCount;

    /** 正确率 */
    private BigDecimal correctRate;

    /** 状态：ACTIVE/INACTIVE */
    private String status;

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
