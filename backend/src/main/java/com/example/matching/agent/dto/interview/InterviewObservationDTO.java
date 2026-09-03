package com.example.matching.agent.dto.interview;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class InterviewObservationDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    private List<Observation> observations;
    private List<String> sourceRefs;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Observation implements Serializable {

        private static final long serialVersionUID = 1L;

        private Long tagId;
        private String abilityName;
        private Integer observedLevel;
        private Integer confidenceScore;
        private String evidenceText;
        private List<String> sourceRefs;
    }
}
