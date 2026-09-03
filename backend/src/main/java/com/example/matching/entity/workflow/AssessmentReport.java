package com.example.matching.entity.workflow;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 评估流程综合报告实体
 * <p>
 * 一次评估（workflow）一份报告；面试结束后生成主体，聚合审核/等级确认完成后回填。
 */
@Data
@TableName("assessment_report")
public class AssessmentReport implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    private Long workflowId;

    private Long empId;

    private Long postId;

    private Long sessionId;

    /** 报告状态：READY/FAILED */
    private String status;

    private Integer overallScore;

    private Integer postMatchScore;

    private String resumeSummaryJson;

    private String testSummaryJson;

    private String interviewSummaryJson;

    private String aggregateSummaryJson;

    private String levelSummaryJson;

    private String conclusion;

    private String recommendation;

    private LocalDateTime generatedAt;

    private LocalDateTime completedAt;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedTime;

    @Version
    private Integer version;
}
