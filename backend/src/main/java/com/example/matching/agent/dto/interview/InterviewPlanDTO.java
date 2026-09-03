package com.example.matching.agent.dto.interview;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class InterviewPlanDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    private List<Question> questions;
    private String strategy;
    private Integer estimatedDuration;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Question implements Serializable {

        private static final long serialVersionUID = 1L;

        private Integer order;
        private String text;
        private String type;
        private String difficulty;
        private List<Long> expectedTagIds;
        private String followUpStrategy;
        /** A verbatim project/work fragment selected from cleanedResumeBackground. */
        private String projectAnchor;
    }
}
