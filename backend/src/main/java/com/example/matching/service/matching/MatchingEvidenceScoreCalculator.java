package com.example.matching.service.matching;

import com.example.matching.dto.matching.MatchingAbilitySnapshot;
import com.example.matching.entity.employee.EmpAbility;
import com.example.matching.service.system.SourceWeightConfigService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.List;

@Component
@RequiredArgsConstructor
public class MatchingEvidenceScoreCalculator {

    public static final BigDecimal STRONG_THRESHOLD = new BigDecimal("100");
    public static final BigDecimal MEDIUM_THRESHOLD = new BigDecimal("70");
    public static final BigDecimal WEAK_THRESHOLD = new BigDecimal("40");

    private final SourceWeightConfigService weightConfigService;
    private final MatchingAlgorithmService matchingAlgorithmService;

    public BigDecimal computeEvidenceScore(List<EmpAbility> abilities) {
        if (abilities == null || abilities.isEmpty()) {
            return BigDecimal.ZERO;
        }
        double totalCredibility = 0;
        for (EmpAbility ability : abilities) {
            double credibility = weightConfigService.getWeight(ability.getEvaluationSource()).doubleValue();
            double timeFactor = matchingAlgorithmService.calculateTimeFactor(ability.getEvaluationDate());
            totalCredibility += credibility * timeFactor;
        }
        double avg = totalCredibility / abilities.size();
        return BigDecimal.valueOf(Math.min(100, avg * 100)).setScale(2, RoundingMode.HALF_UP);
    }

    /**
     * M-12：基于匹配专用能力快照计算证据可信度分数（评分层不接触 Entity）
     */
    public BigDecimal computeEvidenceScoreFromSnapshots(List<MatchingAbilitySnapshot> abilities) {
        if (abilities == null || abilities.isEmpty()) {
            return BigDecimal.ZERO;
        }
        double totalCredibility = 0;
        for (MatchingAbilitySnapshot ability : abilities) {
            double credibility = weightConfigService.getWeight(ability.sourceType()).doubleValue();
            double timeFactor = matchingAlgorithmService.calculateTimeFactor(ability.evaluationDate());
            totalCredibility += credibility * timeFactor;
        }
        double avg = totalCredibility / abilities.size();
        return BigDecimal.valueOf(Math.min(100, avg * 100)).setScale(2, RoundingMode.HALF_UP);
    }
}
