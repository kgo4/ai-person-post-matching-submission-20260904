package com.example.matching.service.matching.impl;

import com.example.matching.service.matching.MatchingScoreCalculator;
import com.example.matching.service.matching.MatchingScoreService;
import com.example.matching.service.matching.MatchingTrainingWeightProfileStore;
import com.example.matching.service.matching.MatchScoreInput;
import com.example.matching.service.matching.MatchScoreResult;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;

/**
 * 统一匹配评分服务实现 —— 唯一评分入口（M-09）。
 * <p>
 * 执行匹配、推荐预览、训练回放必须通过 {@link #score(MatchScoreInput)} 获取权威分数。
 * MatchingScoreCalculator is an internal implementation detail.
 */
@Slf4j
@Service
public class MatchingScoreServiceImpl implements MatchingScoreService {

    private final MatchingTrainingWeightProfileStore weightProfileStore;

    public MatchingScoreServiceImpl(MatchingTrainingWeightProfileStore weightProfileStore) {
        this.weightProfileStore = weightProfileStore;
    }

    @Override
    public MatchScoreResult score(MatchScoreInput input) {
        BigDecimal abilityScore = input.abilityScore();
        BigDecimal semanticScore = input.semanticScore();
        BigDecimal evidenceScore = input.evidenceScore();
        BigDecimal aiScore = input.aiScore();
        MatchingTrainingWeightProfileStore.WeightProfile profile = input.weightProfile();

        MatchingScoreCalculator.ScoreBreakdown breakdown =
                MatchingScoreCalculator.composeFormalScore(
                        abilityScore, semanticScore, evidenceScore, aiScore, profile);

        boolean hasAi = aiScore != null;
        BigDecimal abilityWeight = requireValidProfileWeight(profile.getAbilityWeight());
        BigDecimal semanticWeight = requireValidProfileWeight(profile.getSemanticWeight());
        BigDecimal evidenceWeight = requireValidProfileWeight(profile.getEvidenceWeight());
        BigDecimal llmWeight = requireValidProfileWeight(profile.getAiWeight());

        validateWeightSum(abilityWeight, semanticWeight, evidenceWeight, llmWeight);

        return MatchScoreResult.from(breakdown,
                abilityWeight, semanticWeight, evidenceWeight, llmWeight, hasAi);
    }

    /**
     * Resolve a weight value to a BigDecimal, throwing on truly unset values.
     * <p>
     * Unset means: null, NaN, negative, or infinite.
     * 0 is a valid explicit configuration (e.g., disabling a dimension).
     *
     * @param w raw weight value
     * @return resolved weight
     * @throws IllegalStateException if the weight is unset or invalid
     */
    private static BigDecimal requireValidProfileWeight(Double w) {
        if (w == null || Double.isNaN(w) || Double.isInfinite(w) || w < 0) {
            throw new IllegalStateException("Invalid matching weight profile value: " + w
                    + ". Ensure the active-weight-profile.json is properly configured.");
        }
        return BigDecimal.valueOf(w);
    }

    /**
     * Validate that the total weight sum is within a reasonable range [0, 1.5].
     * Logs a warning if outside the expected [0.5, 1.2] range.
     */
    private static void validateWeightSum(BigDecimal abilityWeight, BigDecimal semanticWeight,
                                          BigDecimal evidenceWeight, BigDecimal llmWeight) {
        BigDecimal sum = abilityWeight.add(semanticWeight).add(evidenceWeight).add(llmWeight);
        if (sum.compareTo(new BigDecimal("0.999999")) < 0 || sum.compareTo(new BigDecimal("1.000001")) > 0) {
            log.warn("Unified matching weights must sum to 1.0: {} (ability={}, semantic={}, evidence={}, ai={})",
                    sum, abilityWeight, semanticWeight, evidenceWeight, llmWeight);
        }
    }
}
