package com.example.matching.entity.learning;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 掌握度日志实体
 *
 * @author system
 */
@Data
@EqualsAndHashCode(callSuper = false)
@TableName("learning_mastery_log")
public class LearningMasteryLog implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 主键，自增 */
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /** 员工ID */
    private Long empId;

    /** 知识领域ID */
    private Long domainId;

    /** 知识点ID */
    private Long nodeId;

    /** 能力标签ID */
    private Long tagId;

    /** 掌握度评分：0-100 */
    private BigDecimal masteryScore;

    /** 答题总数 */
    private Integer quizCount;

    /** 正确答题数 */
    private Integer correctCount;

    /** 已掌握题目数 */
    private Integer masteredCount;

    /** 计算时间 */
    private LocalDateTime calculationTime;

    /** 计算来源：AUTO/MANUAL/QUIZ */
    private String calculationSource;

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
