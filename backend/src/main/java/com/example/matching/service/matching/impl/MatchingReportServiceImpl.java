package com.example.matching.service.matching.impl;

import com.example.matching.dto.learning.LearningPathItemDTO;
import com.example.matching.dto.matching.GapAbilityDTO;
import com.example.matching.dto.matching.ImprovementPhaseDTO;
import com.example.matching.dto.matching.MatchingReportDTO;
import com.example.matching.service.matching.MatchingReportService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Service
public class MatchingReportServiceImpl implements MatchingReportService {

    @Override
    public List<GapAbilityDTO> extractGapAbilities(List<MatchingReportDTO.AbilityDetail> abilityDetails) {
        if (abilityDetails == null || abilityDetails.isEmpty()) {
            return List.of();
        }
        List<GapAbilityDTO> gaps = new ArrayList<>();
        for (MatchingReportDTO.AbilityDetail detail : abilityDetails) {
            boolean isGap = false;
            if (detail.getActualLevel() != null && detail.getRequiredLevel() != null) {
                BigDecimal required = BigDecimal.valueOf(detail.getRequiredLevel());
                if (detail.getActualLevel().compareTo(required) < 0) {
                    isGap = true;
                }
            }
            if (detail.isWeakEvidence()) {
                isGap = true;
            }
            if (isGap) {
                GapAbilityDTO gap = new GapAbilityDTO();
                gap.setName(detail.getTagName());
                gap.setRequiredLevel(detail.getRequiredLevel());
                gap.setActualLevel(detail.getActualLevel());
                gap.setWeakEvidence(detail.isWeakEvidence());
                gaps.add(gap);
            }
        }
        return gaps;
    }

    @Override
    public String buildGapKnowledgeQuery(List<GapAbilityDTO> gaps) {
        if (gaps == null || gaps.isEmpty()) {
            return "";
        }
        return gaps.stream()
                .map(GapAbilityDTO::getName)
                .filter(name -> name != null && !name.isEmpty())
                .collect(Collectors.joining(" "));
    }

    @Override
    public List<ImprovementPhaseDTO> buildImprovementPlan(List<GapAbilityDTO> gaps, List<LearningPathItemDTO> learningPath) {
        if (gaps == null || gaps.isEmpty()) {
            return List.of();
        }

        List<GapWithMagnitude> gapWithMags = gaps.stream()
                .map(g -> {
                    int magnitude = 0;
                    if (g.getRequiredLevel() != null && g.getActualLevel() != null) {
                        magnitude = Math.max(0, g.getRequiredLevel() - g.getActualLevel().intValue());
                    }
                    return new GapWithMagnitude(g, magnitude);
                })
                .collect(Collectors.toList());

        List<ImprovementPhaseDTO> phases = new ArrayList<>();

        List<GapAbilityDTO> phase1Gaps = gapWithMags.stream()
                .filter(g -> g.magnitude <= 1)
                .map(g -> g.gap)
                .collect(Collectors.toList());
        if (!phase1Gaps.isEmpty()) {
            ImprovementPhaseDTO phase = new ImprovementPhaseDTO();
            phase.setPhase(1);
            phase.setTitle("快速提升");
            phase.setTimeframe("1-2周");
            phase.setDescription("针对微小差距的能力进行快速补强");
            phase.setTargetAbilities(phase1Gaps.stream().map(GapAbilityDTO::getName).collect(Collectors.toList()));
            phase.setResources(matchResources(phase1Gaps, learningPath));
            phases.add(phase);
        }

        List<GapAbilityDTO> phase2Gaps = gapWithMags.stream()
                .filter(g -> g.magnitude >= 2 && g.magnitude <= 3)
                .map(g -> g.gap)
                .collect(Collectors.toList());
        if (!phase2Gaps.isEmpty()) {
            ImprovementPhaseDTO phase = new ImprovementPhaseDTO();
            phase.setPhase(2);
            phase.setTitle("中期提升");
            phase.setTimeframe("1-3个月");
            phase.setDescription("针对中等差距的能力进行系统性提升");
            phase.setTargetAbilities(phase2Gaps.stream().map(GapAbilityDTO::getName).collect(Collectors.toList()));
            phase.setResources(matchResources(phase2Gaps, learningPath));
            phases.add(phase);
        }

        List<GapAbilityDTO> phase3Gaps = gapWithMags.stream()
                .filter(g -> g.magnitude >= 4)
                .map(g -> g.gap)
                .collect(Collectors.toList());
        if (!phase3Gaps.isEmpty()) {
            ImprovementPhaseDTO phase = new ImprovementPhaseDTO();
            phase.setPhase(3);
            phase.setTitle("长期提升");
            phase.setTimeframe("3-6个月");
            phase.setDescription("针对较大差距的能力进行长期规划和培养");
            phase.setTargetAbilities(phase3Gaps.stream().map(GapAbilityDTO::getName).collect(Collectors.toList()));
            phase.setResources(matchResources(phase3Gaps, learningPath));
            phases.add(phase);
        }

        return phases;
    }

    @Override
    public List<LearningPathItemDTO> matchResources(List<GapAbilityDTO> gaps, List<LearningPathItemDTO> learningPath) {
        if (gaps == null || gaps.isEmpty() || learningPath == null || learningPath.isEmpty()) {
            return List.of();
        }
        return learningPath.stream()
                .filter(item -> item.getAbilityName() != null
                        && gaps.stream().anyMatch(gap -> item.getAbilityName().equals(gap.getName())))
                .collect(Collectors.toList());
    }

    private static class GapWithMagnitude {
        final GapAbilityDTO gap;
        final int magnitude;

        GapWithMagnitude(GapAbilityDTO gap, int magnitude) {
            this.gap = gap;
            this.magnitude = magnitude;
        }
    }
}
