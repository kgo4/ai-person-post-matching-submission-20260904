package com.example.matching.service.matching.algorithm;

import com.example.matching.ai.service.VectorEmbeddingService;
import com.example.matching.common.enums.MatchTypeEnum;
import com.example.matching.dto.matching.MatchDetailDTO;
import com.example.matching.dto.matching.MatchingAbilitySnapshot;
import com.example.matching.dto.matching.MatchingRequirementSnapshot;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
import com.example.matching.service.matching.MatchingTrainingWeightProfileStore;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.*;

@Service
public class SemanticMatchEngine {

    private final VectorEmbeddingService vectorEmbeddingService;
    private final MatchingTrainingWeightProfileStore weightProfileStore;

    /** Hungarian 最大维度阈值（matching.semantic-assignment.hungarian-max-dimension） */
    @Value("${matching.semantic-assignment.hungarian-max-dimension:100}")
    private int hungarianMaxDimension = SemanticAssignmentSolver.DEFAULT_HUNGARIAN_MAX_DIMENSION;

    @Autowired
    public SemanticMatchEngine(VectorEmbeddingService vectorEmbeddingService,
                               ObjectProvider<MatchingTrainingWeightProfileStore> weightProfileProvider) {
        this.vectorEmbeddingService = vectorEmbeddingService;
        this.weightProfileStore = weightProfileProvider == null ? null : weightProfileProvider.getIfAvailable();
    }

    /** Compatibility constructor for focused tests and non-Spring callers. */
    public SemanticMatchEngine(com.example.matching.service.matching.TagCanonicalResolver ignoredTagCanonicalResolver,
                               VectorEmbeddingService vectorEmbeddingService,
                               com.example.matching.port.tag.TagQueryPort ignoredTagQueryPort,
                               org.springframework.data.redis.core.RedisTemplate<String, Object> ignoredRedisTemplate) {
        this(vectorEmbeddingService, null);
    }

    public List<MatchDetailDTO> performSemanticMatching(Map<Long, BigDecimal> fusedLevels,
                                                         List<MatchingAbilitySnapshot> empAbilities,
                                                         List<MatchingRequirementSnapshot> postRequirements) {
        return performSemanticMatching(fusedLevels, empAbilities, postRequirements, true);
    }

    /**
     * @param allowRuntimeEmbedding whether unnamed abilities may invoke the external embedding service
     */
    public List<MatchDetailDTO> performSemanticMatching(Map<Long, BigDecimal> fusedLevels,
                                                         List<MatchingAbilitySnapshot> empAbilities,
                                                         List<MatchingRequirementSnapshot> postRequirements,
                                                         boolean allowRuntimeEmbedding) {
        List<MatchDetailDTO> details = new ArrayList<>();
        Map<Long, MatchingAbilitySnapshot> abilitiesByKey = new LinkedHashMap<>();
        for (MatchingAbilitySnapshot ability : empAbilities) {
            Long key = matchingAbilityKey(ability);
            if (key != null) {
                abilitiesByKey.putIfAbsent(key, ability);
            }
        }
        Set<Long> availableAbilityKeys = new LinkedHashSet<>(abilitiesByKey.keySet());
        // Matching is based on formal employee/post ability tables. Taxonomy tags
        // are metadata only and must not be used as an identity or match gate.
        Map<Long, Long> postCanonicalMap = Map.of();
        Map<Long, Long> empCanonicalMap = Map.of();

        for (MatchingRequirementSnapshot req : postRequirements) {
            Long reqCanonicalId = canonicalId(req.tagId(), postCanonicalMap);
            Long key = findExactAbility(req, availableAbilityKeys, abilitiesByKey);
            MatchTypeEnum type = MatchTypeEnum.EXACT;
            if (key == null) {
                details.add(MatchDetailDTO.noMatch(req.tagId(), reqCanonicalId, req.minRequiredLevel(),
                        isRequired(req), isCore(req)));
            } else {
                details.add(buildMatchDetail(req, reqCanonicalId, abilitiesByKey.get(key),
                        canonicalId(abilitiesByKey.get(key).tagId(), empCanonicalMap), type,
                        BigDecimal.ONE, effectiveLevel(fusedLevels, key, abilitiesByKey.get(key))));
                availableAbilityKeys.remove(key);
            }
        }

        List<Integer> unmatched = new ArrayList<>();
        for (int i = 0; i < details.size(); i++) if (details.get(i).getMatchType() == MatchTypeEnum.NONE) unmatched.add(i);
        if (!unmatched.isEmpty() && !availableAbilityKeys.isEmpty()) {
            List<Long> keys = new ArrayList<>(availableAbilityKeys);
            Map<String, List<Float>> runtimeEmbeddings = prepareRuntimeEmbeddings(
                    unmatched, keys, abilitiesByKey, postRequirements, allowRuntimeEmbedding);
            double[][] scores = new double[unmatched.size()][keys.size()];
            for (int row = 0; row < unmatched.size(); row++) {
                MatchingRequirementSnapshot req = postRequirements.get(unmatched.get(row));
                List<Float> reqVector = vectorForRequirement(req, runtimeEmbeddings);
                double threshold = semanticThreshold(req);
                // Lexical overlap is a deterministic, explainable fallback.
                // It must not be held to the same high threshold as embeddings:
                // a post may say “Java backend development” while the formal
                // employee table stores the atomic skill “Java”.
                double lexicalThreshold = Math.min(threshold, 0.68d);
                for (int col = 0; col < keys.size(); col++) {
                    List<Float> empVector = vectorForAbility(abilitiesByKey.get(keys.get(col)), runtimeEmbeddings);
                    if (reqVector != null && empVector != null) {
                        Float similarity = vectorEmbeddingService.cosineSimilarity(reqVector, empVector);
                        if (similarity != null && similarity >= threshold) scores[row][col] = similarity;
                    }
                    if (scores[row][col] == 0d) {
                        double lexical = abilityNameSimilarity(req.abilityName(),
                                abilitiesByKey.get(keys.get(col)).abilityName());
                        if (lexical >= lexicalThreshold) scores[row][col] = lexical;
                    }
                }
            }
            for (SemanticAssignmentSolver.Assignment assignment : assignmentSolver().solve(scores)) {
                if (assignment.rowIndex() < 0 || assignment.colIndex() < 0) continue;
                int reqIndex = unmatched.get(assignment.rowIndex());
                Long key = keys.get(assignment.colIndex());
                MatchingRequirementSnapshot req = postRequirements.get(reqIndex);
                MatchingAbilitySnapshot ability = abilitiesByKey.get(key);
                details.set(reqIndex, buildMatchDetail(req, canonicalId(req.tagId(), postCanonicalMap), ability,
                        canonicalId(ability.tagId(), empCanonicalMap), MatchTypeEnum.SEMANTIC_FALLBACK,
                        BigDecimal.valueOf(assignment.score()).setScale(2, RoundingMode.HALF_UP),
                        effectiveLevel(fusedLevels, key, ability)));
            }
        }
        return details;
    }

    private Long matchingAbilityKey(MatchingAbilitySnapshot ability) {
        return ability.abilityId();
    }

    private BigDecimal effectiveLevel(Map<Long, BigDecimal> fusedLevels, Long key,
                                      MatchingAbilitySnapshot ability) {
        BigDecimal fused = fusedLevels == null ? null : fusedLevels.get(key);
        return fused != null ? fused : BigDecimal.valueOf(ability.level() == null ? 0 : ability.level());
    }

    private Long findExactAbility(MatchingRequirementSnapshot req, Set<Long> keys,
                                  Map<Long, MatchingAbilitySnapshot> abilities) {
        for (Long key : keys) {
            MatchingAbilitySnapshot ability = abilities.get(key);
            if (sameAbilityName(req.abilityName(), ability.abilityName())) return key;
        }
        return null;
    }

    private boolean sameAbilityName(String left, String right) {
        if (left == null || right == null) return false;
        String normalizedLeft = normalizeAbilityName(left);
        String normalizedRight = normalizeAbilityName(right);
        return !normalizedLeft.isEmpty() && normalizedLeft.equals(normalizedRight);
    }

    /**
     * Names from a person profile and a post model can differ only in display
     * formatting. This is an exact comparison after formatting normalization,
     * not a fuzzy semantic fallback.
     */
    private String normalizeAbilityName(String value) {
        return value.trim()
                .replaceAll("[^\\p{L}\\p{N}]", "")
                .toLowerCase(Locale.ROOT);
    }

    /** Deterministic fallback when tag vectors are missing or unavailable. */
    private double abilityNameSimilarity(String left, String right) {
        if (left == null || right == null) return 0d;
        String a = normalizeAbilityName(left);
        String b = normalizeAbilityName(right);
        if (a.isEmpty() || b.isEmpty()) return 0d;
        if (a.equals(b)) return 1d;
        if (a.length() >= 2 && (a.contains(b) || b.contains(a))) return 0.86d;

        Set<String> leftTokens = nameTokens(a);
        Set<String> rightTokens = nameTokens(b);
        if (leftTokens.isEmpty() || rightTokens.isEmpty()) return 0d;
        Set<String> intersection = new HashSet<>(leftTokens);
        intersection.retainAll(rightTokens);
        if (intersection.isEmpty()) return 0d;
        Set<String> union = new HashSet<>(leftTokens);
        union.addAll(rightTokens);
        double jaccard = (double) intersection.size() / union.size();
        double containment = (double) intersection.size()
                / Math.min(leftTokens.size(), rightTokens.size());
        return Math.min(0.95d, Math.max(jaccard, containment * 0.86d));
    }

    private Set<String> nameTokens(String normalized) {
        Set<String> tokens = new HashSet<>();
        java.util.regex.Matcher matcher = java.util.regex.Pattern
                .compile("[a-z]+\\d*|\\d+|[\\p{IsHan}]")
                .matcher(normalized);
        while (matcher.find()) {
            String token = matcher.group();
            if (token.length() > 1 || token.matches("[\\p{IsHan}]")) tokens.add(token);
        }
        return tokens;
    }

    private boolean isRequired(MatchingRequirementSnapshot req) { return req.isRequired() != null && req.isRequired() == 1; }
    private boolean isCore(MatchingRequirementSnapshot req) { return req.isCore() != null && req.isCore() == 1; }
    private MatchingTrainingWeightProfileStore.WeightProfile profile() {
        return weightProfileStore != null ? weightProfileStore.currentProfile()
                : MatchingTrainingWeightProfileStore.WeightProfile.defaultProfile();
    }
    private double semanticThreshold(MatchingRequirementSnapshot req) {
        var profile = profile();
        if (isCore(req)) return profile.getCoreSemanticThreshold();
        if (isRequired(req)) return profile.getRequiredSemanticThreshold();
        return profile.getOptionalSemanticThreshold();
    }
    private Long canonicalId(Long tagId, Map<Long, Long> canonicalIds) {
        return tagId == null ? null : canonicalIds.getOrDefault(tagId, tagId);
    }

    private Map<String, List<Float>> prepareRuntimeEmbeddings(
            List<Integer> unmatched,
            List<Long> abilityKeys,
            Map<Long, MatchingAbilitySnapshot> abilitiesByKey,
            List<MatchingRequirementSnapshot> postRequirements,
            boolean allowRuntimeEmbedding) {
        if (!allowRuntimeEmbedding) {
            return Map.of();
        }

        LinkedHashSet<String> names = new LinkedHashSet<>();
        for (Integer index : unmatched) {
            String name = postRequirements.get(index).abilityName();
            if (name != null && !name.isBlank()) {
                names.add(name.trim());
            }
        }
        for (Long key : abilityKeys) {
            String name = abilitiesByKey.get(key).abilityName();
            if (name != null && !name.isBlank()) {
                names.add(name.trim());
            }
        }
        if (names.isEmpty()) {
            return Map.of();
        }

        List<String> orderedNames = new ArrayList<>(names);
        List<List<Float>> vectors = vectorEmbeddingService.embedBatch(orderedNames);
        Map<String, List<Float>> result = new HashMap<>();
        boolean completeBatchResponse = vectors != null && vectors.size() == orderedNames.size();
        boolean hasUsableBatchVector = false;
        if (completeBatchResponse) {
            for (int i = 0; i < orderedNames.size(); i++) {
                List<Float> vector = vectors.get(i);
                if (vector != null && !vector.isEmpty()) {
                    result.put(orderedNames.get(i), vector);
                    hasUsableBatchVector = true;
                }
            }
        }

        // 批量调用已明确返回“每项为空”时，视为本轮 embedding 不可用。
        // 不再对每个名称逐项等待同一个已失效的远端模型，直接交给下面的词面匹配，
        // 避免单人单岗匹配被 30 秒超时按能力数量放大。批量部分缺失仍保留逐项补偿。
        if (completeBatchResponse && !hasUsableBatchVector) {
            return Map.of();
        }

        // 保留原有降级语义：批量接口部分缺失或未提供结果时，为缺失名称逐项补偿。
        for (String name : orderedNames) {
            if (!result.containsKey(name)) {
                List<Float> vector = embedAbilityName(name);
                if (vector != null && !vector.isEmpty()) {
                    result.put(name, vector);
                }
            }
        }
        return result;
    }

    private List<Float> vectorForRequirement(MatchingRequirementSnapshot req,
                                             Map<String, List<Float>> runtimeEmbeddings) {
        return vectorForName(req.abilityName(), runtimeEmbeddings);
    }

    private List<Float> vectorForAbility(MatchingAbilitySnapshot ability,
                                         Map<String, List<Float>> runtimeEmbeddings) {
        return vectorForName(ability.abilityName(), runtimeEmbeddings);
    }

    private List<Float> vectorForName(String name, Map<String, List<Float>> runtimeEmbeddings) {
        if (name == null || name.isBlank() || runtimeEmbeddings == null) {
            return null;
        }
        return runtimeEmbeddings.get(name.trim());
    }

    private List<Float> embedAbilityName(String abilityName) {
        return abilityName == null || abilityName.isBlank() ? null : vectorEmbeddingService.embed(abilityName.trim());
    }

    private SemanticAssignmentSolver assignmentSolver() {
        return new SemanticAssignmentSolver(hungarianMaxDimension);
    }

    MatchDetailDTO buildMatchDetail(MatchingRequirementSnapshot req, Long reqCanonicalId,
                                     MatchingAbilitySnapshot matchedAbility, Long empCanonicalId,
                                     MatchTypeEnum matchType, BigDecimal coefficient,
                                     BigDecimal empRawLevel) {
        MatchDetailDTO detail = new MatchDetailDTO();
        detail.setRequiredTagId(req.tagId());
        detail.setRequiredCanonicalTagId(reqCanonicalId);
        detail.setMatchedEmpTagId(matchedAbility.tagId());
        detail.setMatchedEmpAbilityId(matchedAbility.abilityId());
        detail.setMatchedEmpAbilityName(matchedAbility.abilityName());
        detail.setMatchedEmpCanonicalTagId(empCanonicalId);
        detail.setMatchType(matchType);
        detail.setMatchCoefficient(coefficient);
        detail.setEmployeeRawLevel(empRawLevel != null ? empRawLevel : BigDecimal.ZERO);
        detail.setRequiredLevel(req.minRequiredLevel());
        detail.setRequired(req.isRequired() != null && req.isRequired() == 1);
        detail.setCore(req.isCore() != null && req.isCore() == 1);

        // The formal employee level is authoritative. Similarity is a name
        // confidence signal and must not turn a valid level-4 ability into
        // level 3.44 merely because the post uses a more specific label.
        BigDecimal effectiveLevel = empRawLevel != null ? empRawLevel : BigDecimal.ZERO;
        detail.setEffectiveLevel(effectiveLevel);

        detail.setSimilarityScore(coefficient);

        boolean passed = checkSingleAbilityPassed(detail);
        detail.setPassed(passed);
        detail.setScoreContribution(BigDecimal.ZERO);

        return detail;
    }

    boolean checkSingleAbilityPassed(MatchDetailDTO detail) {
        if (detail.getMatchType() == MatchTypeEnum.NONE) {
            return !detail.isRequired();
        }

        BigDecimal empRawLevel = detail.getEmployeeRawLevel();
        BigDecimal requiredLevel = BigDecimal.valueOf(detail.getRequiredLevel());

        int allowedGap = profile().getAllowedLevelGap();
        BigDecimal effectiveRequiredLevel = requiredLevel.subtract(BigDecimal.valueOf(allowedGap)).max(BigDecimal.ONE);
        if (empRawLevel.compareTo(effectiveRequiredLevel) < 0) {
            return false;
        }

            if (detail.isRequired()) {
                if (detail.getMatchType() == MatchTypeEnum.CONFIRMED_SIMILAR ||
                detail.getMatchType() == MatchTypeEnum.SEMANTIC_FALLBACK) {
                return detail.getSimilarityScore().compareTo(BigDecimal.valueOf(semanticThresholdFromDetail(detail))) >= 0;
                }
            }

        return true;
    }

    private double semanticThresholdFromDetail(MatchDetailDTO detail) {
        var profile = profile();
        if (detail.isCore()) return profile.getCoreSemanticThreshold();
        return detail.isRequired() ? profile.getRequiredSemanticThreshold() : profile.getOptionalSemanticThreshold();
    }

    public boolean checkRequiredAbilitiesWithDetails(List<MatchDetailDTO> matchDetails) {
        for (MatchDetailDTO detail : matchDetails) {
            if (detail.isRequired() && !detail.isPassed()) {
                return false;
            }
        }
        return true;
    }

    public BigDecimal calculateAbilityCompatibilityScore(List<MatchDetailDTO> matchDetails,
                                                          List<MatchingRequirementSnapshot> postRequirements) {
        if (postRequirements.isEmpty()) {
            return new BigDecimal("100.00");
        }

        boolean allWeightsZero = postRequirements.stream()
                .allMatch(req -> req.weight() == null || req.weight().compareTo(BigDecimal.ZERO) <= 0);

        BigDecimal totalWeightedScore = BigDecimal.ZERO;
        BigDecimal totalWeight = BigDecimal.ZERO;

        for (int i = 0; i < postRequirements.size(); i++) {
            MatchingRequirementSnapshot req = postRequirements.get(i);
            MatchDetailDTO detail = matchDetails.get(i);

            BigDecimal weight;
            if (allWeightsZero) {
                weight = BigDecimal.ONE;
            } else {
                weight = req.weight() != null ? req.weight() : BigDecimal.ZERO;
            }
            totalWeight = totalWeight.add(weight);

            BigDecimal score;
            if (detail.getMatchType() == MatchTypeEnum.NONE) {
                score = BigDecimal.ZERO;
            } else {
                BigDecimal effectiveLevel = detail.getEffectiveLevel();
                Integer configuredRequiredLevel = req.minRequiredLevel();
                int requiredLevelValue = configuredRequiredLevel == null || configuredRequiredLevel <= 0
                        ? 1 : configuredRequiredLevel;
                BigDecimal requiredLevel = BigDecimal.valueOf(requiredLevelValue);

                // Apply only a small confidence adjustment for fuzzy matches;
                // exact and high-confidence lexical matches retain the formal
                // ability level instead of being penalized twice.
                BigDecimal confidenceFactor = BigDecimal.valueOf(
                        0.85d + (detail.getSimilarityScore() == null ? 0d
                                : detail.getSimilarityScore().doubleValue() * 0.15d));
                if (effectiveLevel.compareTo(requiredLevel) >= 0) {
                    if (req.isCore() != null && req.isCore() == 1) {
                        score = weight.multiply(confidenceFactor).min(weight);
                    } else {
                        BigDecimal baseScore = weight;
                        BigDecimal extra = effectiveLevel.subtract(requiredLevel)
                                .divide(requiredLevel, 2, RoundingMode.HALF_UP)
                                .multiply(weight)
                                .multiply(new BigDecimal("0.5"));
                        score = baseScore.add(extra).multiply(confidenceFactor).min(weight);
                    }
                    } else {
                        score = effectiveLevel.divide(requiredLevel, 2, RoundingMode.HALF_UP)
                                .multiply(weight).multiply(confidenceFactor).min(weight);
                    }
            }

            detail.setScoreContribution(score);
            totalWeightedScore = totalWeightedScore.add(score);
        }

        if (totalWeight.compareTo(BigDecimal.ZERO) > 0) {
            return totalWeightedScore
                    .divide(totalWeight, 4, RoundingMode.HALF_UP)
                    .multiply(new BigDecimal("100.00"))
                    .setScale(2, RoundingMode.HALF_UP);
        }
        return BigDecimal.ZERO;
    }
}
