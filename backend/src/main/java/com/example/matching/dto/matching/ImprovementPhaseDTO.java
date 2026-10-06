package com.example.matching.dto.matching;

import com.example.matching.dto.learning.LearningPathItemDTO;
import lombok.Data;

import java.util.List;

@Data
public class ImprovementPhaseDTO {

    private Integer phase;

    private String title;

    private String timeframe;

    private String description;

    private List<String> targetAbilities;

    private List<LearningPathItemDTO> resources;
}
