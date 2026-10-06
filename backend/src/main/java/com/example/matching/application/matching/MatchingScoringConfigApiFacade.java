package com.example.matching.application.matching;

import com.example.matching.dto.matching.ScoringWeightUpdateRequest;
import com.example.matching.dto.matching.ScoringWeightVO;
import com.example.matching.service.matching.MatchingTrainingWeightProfileStore;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import com.example.matching.common.util.WeightScale;

/** 单层匹配评分权重配置门面。 */
@Service
@RequiredArgsConstructor
public class MatchingScoringConfigApiFacade {

    private static final double TOTAL = 1.0d;
    private static final double EPSILON = 0.000001d;
    private static final double MAX_AI_WEIGHT = 0.20d;

    private final MatchingTrainingWeightProfileStore weightProfileStore;

    public ScoringWeightVO getConfig() {
        MatchingTrainingWeightProfileStore.WeightProfile profile = weightProfileStore.currentProfile();
        return new ScoringWeightVO(profile.getVersion(), WeightScale.toPercentage(profile.getAbilityWeight()), WeightScale.toPercentage(profile.getSemanticWeight()),
                WeightScale.toPercentage(profile.getEvidenceWeight()), WeightScale.toPercentage(profile.getAiWeight()), profile.isWhitelistBypassHardRules(),
                profile.getL2MatchingMode(), profile.getRequiredSemanticThreshold(), profile.getCoreSemanticThreshold(),
                profile.getOptionalSemanticThreshold(), profile.getSimilarTagMinimumConfidence(), profile.getAllowedLevelGap(),
                profile.getCoreCoverageThreshold(), profile.getRequiredCoverageThreshold(), profile.getL2PassThreshold(),
                profile.getAiTriggerThreshold());
    }

    public void saveConfig(ScoringWeightUpdateRequest request) {
        if (request == null) return;
        MatchingTrainingWeightProfileStore.WeightProfile profile = weightProfileStore.currentProfile();
        double ability = normalizeWeight(request.abilityWeight(), profile.getAbilityWeight());
        double semantic = normalizeWeight(request.semanticWeight(), profile.getSemanticWeight());
        double evidence = normalizeWeight(request.evidenceWeight(), profile.getEvidenceWeight());
        double ai = normalizeWeight(request.aiWeight(), profile.getAiWeight());
        validate(ability, semantic, evidence, ai);

        profile.setAbilityWeight(ability);
        profile.setSemanticWeight(semantic);
        profile.setEvidenceWeight(evidence);
        profile.setAiWeight(ai);
        if (request.whitelistBypassHardRules() != null) profile.setWhitelistBypassHardRules(request.whitelistBypassHardRules());
        if (request.l2MatchingMode() != null) profile.applyL2ModeDefaults(request.l2MatchingMode());
        applyThresholdOverrides(profile, request);
        validateL2(profile);
        profile.setVersion(nextVersion(profile.getVersion()));
        weightProfileStore.saveActiveProfile(profile);
    }

    private void validateL2(MatchingTrainingWeightProfileStore.WeightProfile profile) {
        if (!between(profile.getRequiredSemanticThreshold()) || !between(profile.getCoreSemanticThreshold())
                || !between(profile.getOptionalSemanticThreshold()) || !between(profile.getSimilarTagMinimumConfidence())
                || !between(profile.getCoreCoverageThreshold()) || !between(profile.getRequiredCoverageThreshold())
                || profile.getAllowedLevelGap() < 0 || profile.getAllowedLevelGap() > 3
                || profile.getL2PassThreshold() < 0 || profile.getL2PassThreshold() > 100
                || profile.getAiTriggerThreshold() < 0 || profile.getAiTriggerThreshold() > 100) {
            throw new IllegalArgumentException("L2 阈值超出允许范围");
        }
        if (profile.getCoreCoverageThreshold() < profile.getRequiredCoverageThreshold() - 1e-9) {
            throw new IllegalArgumentException("核心能力覆盖率不能低于必填能力覆盖率");
        }
    }

    private boolean between(double value) {
        return Double.isFinite(value) && value >= 0d && value <= 1d;
    }

    private void applyThresholdOverrides(MatchingTrainingWeightProfileStore.WeightProfile profile,
                                         ScoringWeightUpdateRequest request) {
        if (request.requiredSemanticThreshold() != null) profile.setRequiredSemanticThreshold(request.requiredSemanticThreshold());
        if (request.coreSemanticThreshold() != null) profile.setCoreSemanticThreshold(request.coreSemanticThreshold());
        if (request.optionalSemanticThreshold() != null) profile.setOptionalSemanticThreshold(request.optionalSemanticThreshold());
        if (request.similarTagMinimumConfidence() != null) profile.setSimilarTagMinimumConfidence(request.similarTagMinimumConfidence());
        if (request.allowedLevelGap() != null) profile.setAllowedLevelGap(request.allowedLevelGap());
        if (request.coreCoverageThreshold() != null) profile.setCoreCoverageThreshold(request.coreCoverageThreshold());
        if (request.requiredCoverageThreshold() != null) profile.setRequiredCoverageThreshold(request.requiredCoverageThreshold());
        if (request.l2PassThreshold() != null) profile.setL2PassThreshold(request.l2PassThreshold());
        if (request.aiTriggerThreshold() != null) profile.setAiTriggerThreshold(request.aiTriggerThreshold());
    }

    private void validate(double ability, double semantic, double evidence, double ai) {
        if (!valid(ability) || !valid(semantic) || !valid(evidence) || !valid(ai)) {
            throw new IllegalArgumentException("匹配权重必须在 0% 到 100% 之间");
        }
        if (ai > MAX_AI_WEIGHT + EPSILON) throw new IllegalArgumentException("AI 权重不能超过 20%");
        double total = ability + semantic + evidence + ai;
        if (Math.abs(total - TOTAL) > EPSILON) {
            throw new IllegalArgumentException("能力、语义、证据和 AI 权重之和必须等于 100%，当前: " + WeightScale.toPercentage(total) + "%");
        }
    }

    private boolean valid(double value) {
        return !Double.isNaN(value) && !Double.isInfinite(value) && value >= 0d && value <= 1d;
    }

    private double normalizeWeight(Double value, double fallback) {
        if (value == null) return fallback;
        if (value < 0d || value > 100d) throw new IllegalArgumentException("匹配权重必须在 0 到 100 之间");
        return value / 100d;
    }

    private String nextVersion(String currentVersion) {
        return currentVersion == null || currentVersion.isBlank() ? "MATCH_SCORE_V2" : currentVersion + "-" + System.currentTimeMillis();
    }
}
