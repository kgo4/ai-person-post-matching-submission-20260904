package com.example.matching.agent.dto.interview;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class InterviewAnswerQualityDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    private StarCompleteness starCompleteness;
    private Integer specificityScore;
    private Integer evidenceScore;
    private Integer personalContributionScore;
    private Integer logicConsistencyScore;
    private Boolean needFollowUp;
    private String followUpReason;
    private String targetDimension;
    private String suggestedFollowUpType;
    private List<String> missingEvidence;
    private List<String> logicRisks;
    private String conclusion;
    private List<String> sourceRefs;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class StarCompleteness implements Serializable {

        private static final long serialVersionUID = 1L;

        private Boolean situation;
        private Boolean task;
        private Boolean action;
        private Boolean result;
    }
}
