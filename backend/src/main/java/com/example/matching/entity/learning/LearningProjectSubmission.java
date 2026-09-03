package com.example.matching.entity.learning;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 学习项目提交实体
 *
 * @author system
 */
@Data
@EqualsAndHashCode(callSuper = false)
@TableName("learning_project_submission")
public class LearningProjectSubmission implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /** 关联任务ID */
    private Long taskId;

    /** 所属计划ID */
    private Long planId;

    /** 关联步骤ID */
    private Long stepId;

    /** 提交员工ID */
    private Long empId;

    /** 仓库URL */
    private String repoUrl;

    /** 演示URL */
    private String demoUrl;

    /** 报告URL */
    private String reportUrl;

    /** 提交文本说明 */
    private String submissionText;

    /** AI审核结果 */
    private String aiReviewResult;

    /** 审核状态：PENDING/APPROVED/REJECTED */
    private String reviewStatus;

    /** 审核意见 */
    private String reviewComment;

    /** 关联证据ID */
    private Long evidenceId;

    /** 审核人ID */
    private Long reviewedBy;

    /** 审核时间 */
    private LocalDateTime reviewedTime;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedTime;

    @TableLogic
    private Integer isDeleted;
}
