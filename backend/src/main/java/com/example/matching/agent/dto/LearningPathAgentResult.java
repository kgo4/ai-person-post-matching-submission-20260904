package com.example.matching.agent.dto;

import lombok.Data;
import lombok.EqualsAndHashCode;

import java.util.List;

/**
 * 学习路径Agent结果DTO
 *
 * @author system
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class LearningPathAgentResult extends AgentRunResult {
    /** 学习摘要 */
    private String summary;

    /** 学习步骤建议 */
    private List<LearningStepSuggestion> steps;

    /** 项目任务建议 */
    private List<ProjectTaskSuggestion> projectTasks;

    /** 评估题目建议 */
    private List<AssessmentSuggestion> assessments;

    /**
     * 学习步骤建议
     */
    @Data
    public static class LearningStepSuggestion {
        private Long abilityTagId;
        private String abilityName;
        private Integer currentLevel;
        private Integer targetLevel;
        private String priority;
        private String title;
        private String description;
        private Integer estimatedHours;
        /** AI 建议引用的资源ID；必须经过后端校验，不能编造资源 */
        private Long resourceId;
        private List<String> sourceRefs;
    }

    /**
     * 项目任务建议
     */
    @Data
    public static class ProjectTaskSuggestion {
        private Long abilityTagId;
        private String abilityName;
        private String title;
        private String projectName;
        private String projectUrl;
        private String requirements;
        private String acceptanceCriteria;
        private String expectedOutput;
        private String difficulty;
    }

    /**
     * 评估题目建议
     */
    @Data
    public static class AssessmentSuggestion {
        private Long abilityTagId;
        private String abilityName;
        private String questionType;
        private String questionText;
        private String referenceAnswer;
        private String difficulty;
    }
}
