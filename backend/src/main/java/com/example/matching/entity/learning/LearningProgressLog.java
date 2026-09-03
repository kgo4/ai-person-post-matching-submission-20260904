package com.example.matching.entity.learning;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 学习进度日志实体
 *
 * @author system
 */
@Data
@EqualsAndHashCode(callSuper = false)
@TableName("learning_progress_log")
public class LearningProgressLog implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /** 所属计划ID */
    private Long planId;

    /** 关联步骤ID */
    private Long stepId;

    /** 员工ID */
    private Long empId;

    /** 动作类型：STEP_STARTED/STEP_COMPLETED/PROJECT_SUBMITTED/PROJECT_REJECTED/EVIDENCE_CREATED */
    private String actionType;

    /** 动作描述 */
    private String actionDesc;

    /** 关联证据ID */
    private Long evidenceId;

    /** 知识节点ID（增强学习路径进度用） */
    private Long nodeId;

    /** 进度状态：IN_PROGRESS/COMPLETED/ABANDONED */
    private String progressStatus;

    /** 创建时间 */
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdTime;
}
