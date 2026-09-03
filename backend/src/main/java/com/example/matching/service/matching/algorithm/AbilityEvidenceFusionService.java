package com.example.matching.service.matching.algorithm;

import com.example.matching.common.enums.AbilitySourceCredibility;
import com.example.matching.common.enums.AbilitySourceType;
import com.example.matching.dto.matching.MatchingAbilitySnapshot;
import com.example.matching.service.system.SourceWeightResolver;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.*;

/**
 * 能力证据融合服务（M-12：只消费匹配专用能力快照，不接触 Entity）。
 */
@Service
public class AbilityEvidenceFusionService {

    private final SourceWeightResolver weightResolver;

    public AbilityEvidenceFusionService(SourceWeightResolver weightResolver) {
        this.weightResolver = weightResolver;
    }

    public Map<Long, BigDecimal> fuseAbilityLevel(List<MatchingAbilitySnapshot> empAbilities) {
        Map<Long, List<MatchingAbilitySnapshot>> grouped = new HashMap<>();
        for (MatchingAbilitySnapshot ability : empAbilities) {
            Long abilityKey = matchingAbilityKey(ability);
            if (abilityKey != null) {
                grouped.computeIfAbsent(abilityKey, k -> new ArrayList<>()).add(ability);
            }
        }

        Map<Long, BigDecimal> result = new HashMap<>();
        for (var entry : grouped.entrySet()) {
            List<MatchingAbilitySnapshot> abilities = entry.getValue();

            BigDecimal weightedSum = BigDecimal.ZERO;
            BigDecimal weightSum = BigDecimal.ZERO;

            for (MatchingAbilitySnapshot a : abilities) {
                double credibility = weightResolver.resolveCredibility(a.sourceType());
                double sourceWeight = a.sourceWeight() != null ? a.sourceWeight().doubleValue() : 1.0;
                double timeFactor = calculateTimeFactor(a.evaluationDate());

                double combinedWeight = credibility * sourceWeight * timeFactor;
                weightedSum = weightedSum.add(BigDecimal.valueOf((a.level() != null ? a.level() : 0) * combinedWeight));
                weightSum = weightSum.add(BigDecimal.valueOf(combinedWeight));
            }

            if (weightSum.compareTo(BigDecimal.ZERO) > 0) {
                result.put(entry.getKey(), weightedSum.divide(weightSum, 2, RoundingMode.HALF_UP));
            } else {
                result.put(entry.getKey(), BigDecimal.ZERO);
            }
        }
        return result;
    }

    public Map<Long, List<EvidenceDetail>> generateEvidenceDetail(List<MatchingAbilitySnapshot> empAbilities) {
        Map<Long, List<EvidenceDetail>> result = new HashMap<>();
        for (MatchingAbilitySnapshot a : empAbilities) {
            Long abilityKey = matchingAbilityKey(a);
            if (abilityKey == null) {
                continue;
            }
            EvidenceDetail detail = new EvidenceDetail();
            detail.setTagId(a.tagId());
            detail.setMasteryLevel(a.level());
            detail.setSource(AbilitySourceType.canonicalize(a.sourceType()));
            detail.setCredibility(weightResolver.resolveCredibility(a.sourceType()));
            detail.setSourceWeight(a.sourceWeight() != null ? a.sourceWeight().doubleValue() : 1.0);
            detail.setTimeFactor(calculateTimeFactor(a.evaluationDate()));
            detail.setEvaluationDate(a.evaluationDate());
            result.computeIfAbsent(abilityKey, k -> new ArrayList<>()).add(detail);
        }
        return result;
    }

    /**
     * Matching identity belongs to a formal employee-ability fact, not to an
     * optional taxonomy tag. Legacy snapshots without an ability ID retain
     * their tag ID as the compatibility key.
     */
    private Long matchingAbilityKey(MatchingAbilitySnapshot ability) {
        return ability.abilityId() != null ? ability.abilityId() : ability.tagId();
    }

    public double calculateTimeFactor(LocalDate evaluationDate) {
        if (evaluationDate == null) return 0.8;
        long months = ChronoUnit.MONTHS.between(evaluationDate, LocalDate.now());
        if (months <= 6) return 1.0;
        if (months <= 12) return 0.9;
        if (months <= 24) return 0.8;
        return 0.7;
    }
}
