package com.example.matching.dto.learning;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 学习路径计划视图对象
 *
 * @author system
 */
@Data
public class LearningPathPlanVO {

    private Long id;
    private Long empId;
    private String empName;
    private Long postId;
    private String postName;
    private Long matchingRecordId;
    private String planTitle;
    private String planStatus;
    private BigDecimal currentScore;
    private BigDecimal targetScore;
    private String aiSummary;
    private Integer generatedByAi;
    private LocalDateTime createdTime;
    private LocalDateTime updatedTime;

    /** 学习步骤列表 */
    private List<LearningPathStepVO> steps;

    /** 总步骤数 */
    private Integer totalStepCount;

    /** 已完成步骤数 */
    private Integer completedStepCount;

    /** 项目任务数 */
    private Integer projectTaskCount;

    /** 待审核提交数 */
    private Integer pendingSubmissionCount;
}
