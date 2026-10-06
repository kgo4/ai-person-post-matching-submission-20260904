package com.example.matching.agent.dto.interview;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class InterviewReportDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    private List<String> strengths;
    private List<String> weaknesses;
    private List<String> riskSignals;
    private List<String> improvementSuggestions;
    private List<LearningPathSuggestion> learningPathSuggestions;
    private String conclusion;
    private String recommendation;
    private List<String> sourceRefs;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class LearningPathSuggestion implements Serializable {

        private static final long serialVersionUID = 1L;

        private Long tagId;
        private String abilityName;
        private Integer currentLevel;
        private Integer targetLevel;
        private String suggestion;
        private String priority;
    }
}
