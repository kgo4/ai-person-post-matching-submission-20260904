package com.example.matching.dto.learning;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 学习项目任务视图对象
 *
 * @author system
 */
@Data
public class LearningProjectTaskVO {

    private Long id;
    private Long planId;
    private Long stepId;
    private Long abilityTagId;
    private String projectName;
    private String projectUrl;
    private String taskTitle;
    private String taskBackground;
    private String taskRequirements;
    private String acceptanceCriteria;
    private String difficultyLevel;
    private String expectedOutput;
    private String status;
    private LocalDateTime createdTime;

    /** 最新提交ID */
    private Long latestSubmissionId;

    /** 最新提交状态 */
    private String latestSubmissionStatus;
}
