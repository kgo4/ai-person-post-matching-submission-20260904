package com.example.matching.entity.learning;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 学习项目任务实体
 *
 * @author system
 */
@Data
@EqualsAndHashCode(callSuper = false)
@TableName("learning_project_task")
public class LearningProjectTask implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /** 所属计划ID */
    private Long planId;

    /** 关联步骤ID */
    private Long stepId;

    /** 关联能力标签ID */
    private Long abilityTagId;

    /** 项目名称 */
    private String projectName;

    /** 项目URL */
    private String projectUrl;

    /** 任务标题 */
    private String taskTitle;

    /** 任务背景 */
    private String taskBackground;

    /** 任务要求 */
    private String taskRequirements;

    /** 验收标准 */
    private String acceptanceCriteria;

    /** 难度等级：EASY/MEDIUM/HARD */
    private String difficultyLevel;

    /** 期望输出 */
    private String expectedOutput;

    /** 状态：PENDING/IN_PROGRESS/SUBMITTED/COMPLETED/REVISION_REQUIRED */
    private String status;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedTime;

    @TableLogic
    private Integer isDeleted;
}
