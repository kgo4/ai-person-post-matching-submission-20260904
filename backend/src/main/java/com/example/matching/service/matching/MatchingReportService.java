package com.example.matching.service.matching;

import com.example.matching.dto.learning.LearningPathItemDTO;
import com.example.matching.dto.matching.GapAbilityDTO;
import com.example.matching.dto.matching.ImprovementPhaseDTO;
import com.example.matching.dto.matching.MatchingReportDTO;

import java.util.List;

public interface MatchingReportService {

    List<GapAbilityDTO> extractGapAbilities(List<MatchingReportDTO.AbilityDetail> abilityDetails);

    String buildGapKnowledgeQuery(List<GapAbilityDTO> gaps);

    List<ImprovementPhaseDTO> buildImprovementPlan(List<GapAbilityDTO> gaps, List<LearningPathItemDTO> learningPath);

    List<LearningPathItemDTO> matchResources(List<GapAbilityDTO> gaps, List<LearningPathItemDTO> learningPath);
}
