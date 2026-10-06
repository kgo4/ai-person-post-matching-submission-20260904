package com.example.matching.dto.matching;

import lombok.Data;

import java.util.List;

@Data
public class DimensionScoreDTO {

    private String dimension;

    private String label;

    private Integer score;

    private Integer maxScore;

    private List<String> details;
}
