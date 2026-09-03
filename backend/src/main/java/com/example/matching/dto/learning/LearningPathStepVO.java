package com.example.matching.dto.learning;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 学习路径步骤视图对象
 *
 * @author system
 */
@Data
public class LearningPathStepVO {

    private Long id;
    private Long planId;
    private Long abilityTagId;
    private String abilityName;
    private Integer currentLevel;
    private Integer targetLevel;
    private String gapType;
    private String priority;
    private String stepTitle;
    private String stepDescription;
    private Integer estimatedHours;
    private String status;
    private String evidenceStatus;
    private Integer sortOrder;
    private LocalDateTime createdTime;

    /** 推荐学习资源ID */
    private Long resourceId;

    /** 推荐资源标题 */
    private String resourceTitle;

    /** 推荐资源链接 */
    private String resourceUrl;

    /** 推荐资源类型 */
    private String resourceType;

    /** 该能力匹配到的启用资源总数（0 表示暂无匹配资源） */
    private Integer resourceCount;

    /** 关联的项目任务列表 */
    private List<LearningProjectTaskVO> projectTasks;
}
