package com.example.matching.service.evolution.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.example.matching.entity.evolution.PostEvolutionChangeItem;
import com.example.matching.entity.evolution.PostEvolutionEvidence;
import com.example.matching.entity.matching.MatchingFeedbackDataset;
import com.example.matching.mapper.matching.MatchingFeedbackDatasetMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 岗位演化评分器：证据/相关度/新鲜度/反馈/多样性五维加权评分。
 * <p>
 * 从 PostEvolutionServiceImpl（1100+ 行）中拆分的纯计算组件。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PostEvolutionScoringService {

    private final MatchingFeedbackDatasetMapper feedbackDatasetMapper;

    public EvolutionScore calculateEvolutionScore(PostEvolutionChangeItem item,
                                                  List<PostEvolutionEvidence> allEvidence,
                                                  Long postId) {
        List<PostEvolutionEvidence> relatedEvidence = findRelatedEvidence(item, allEvidence);
        BigDecimal evidenceScore = calculateEvidenceScore(relatedEvidence);
        BigDecimal relevanceScore = calculateRelevanceScore(item, postId);
        BigDecimal freshnessScore = calculateFreshnessScore(relatedEvidence);
        BigDecimal feedbackScore = calculateFeedbackScore(item, postId);
        BigDecimal diversityScore = calculateDiversityScore(relatedEvidence);

        // EvolutionScore = 0.30 * EvidenceScore + 0.25 * RelevanceScore + 0.20 * FreshnessScore
        //                + 0.15 * FeedbackScore + 0.10 * DiversityScore
        BigDecimal finalScore = evidenceScore.multiply(BigDecimal.valueOf(0.30))
                .add(relevanceScore.multiply(BigDecimal.valueOf(0.25)))
                .add(freshnessScore.multiply(BigDecimal.valueOf(0.20)))
                .add(feedbackScore.multiply(BigDecimal.valueOf(0.15)))
                .add(diversityScore.multiply(BigDecimal.valueOf(0.10)))
                .setScale(2, RoundingMode.HALF_UP);

        EvolutionScore score = new EvolutionScore();
        score.setEvidenceScore(evidenceScore);
        score.setRelevanceScore(relevanceScore);
        score.setFreshnessScore(freshnessScore);
        score.setFeedbackScore(feedbackScore);
        score.setDiversityScore(diversityScore);
        score.setFinalScore(finalScore);
        return score;
    }

    private BigDecimal calculateEvidenceScore(List<PostEvolutionEvidence> evidenceList) {
        if (evidenceList.isEmpty()) {
            return BigDecimal.ZERO;
        }

        double baseScore = Math.min(100, evidenceList.size() * 20);

        double avgTrust = evidenceList.stream()
                .mapToDouble(e -> e.getTrustScore() != null ? e.getTrustScore().doubleValue() : 0.5)
                .average()
                .orElse(0.5);

        return BigDecimal.valueOf(baseScore * avgTrust).setScale(2, RoundingMode.HALF_UP);
    }

    private BigDecimal calculateRelevanceScore(PostEvolutionChangeItem item, Long postId) {
        if (item.getTagId() != null) {
            return BigDecimal.valueOf(80);
        }
        return BigDecimal.valueOf(50);
    }

    private BigDecimal calculateFreshnessScore(List<PostEvolutionEvidence> evidenceList) {
        if (evidenceList.isEmpty()) {
            return BigDecimal.ZERO;
        }

        LocalDateTime now = LocalDateTime.now();
        double totalScore = 0;
        int count = 0;

        for (PostEvolutionEvidence evidence : evidenceList) {
            if (evidence.getCollectedTime() != null) {
                long daysBetween = java.time.Duration.between(evidence.getCollectedTime(), now).toDays();
                if (daysBetween <= 30) {
                    totalScore += 100;
                } else if (daysBetween <= 90) {
                    totalScore += 70;
                } else if (daysBetween <= 180) {
                    totalScore += 40;
                } else {
                    totalScore += 20;
                }
                count++;
            }
        }

        return count > 0 ? BigDecimal.valueOf(totalScore / count).setScale(2, RoundingMode.HALF_UP) : BigDecimal.ZERO;
    }

    private BigDecimal calculateFeedbackScore(PostEvolutionChangeItem item, Long postId) {
        Long feedbackCount = feedbackDatasetMapper.selectCount(
                new LambdaQueryWrapper<MatchingFeedbackDataset>()
                        .eq(MatchingFeedbackDataset::getPostId, postId)
                        .eq(MatchingFeedbackDataset::getAdoptionStatus, 3)); // 未采纳

        if (feedbackCount > 10) {
            return BigDecimal.valueOf(90);
        } else if (feedbackCount > 5) {
            return BigDecimal.valueOf(70);
        } else if (feedbackCount > 0) {
            return BigDecimal.valueOf(50);
        }
        return BigDecimal.valueOf(30);
    }

    private BigDecimal calculateDiversityScore(List<PostEvolutionEvidence> evidenceList) {
        if (evidenceList.isEmpty()) {
            return BigDecimal.ZERO;
        }

        Set<String> sourceTypes = evidenceList.stream()
                .map(PostEvolutionEvidence::getSourceType)
                .collect(Collectors.toSet());

        return BigDecimal.valueOf(Math.min(100, sourceTypes.size() * 25));
    }

    public List<PostEvolutionEvidence> findRelatedEvidence(PostEvolutionChangeItem item,
                                                            List<PostEvolutionEvidence> allEvidence) {
        return allEvidence.stream()
                .filter(e -> {
                    if (e.getEvidenceText() != null && item.getAbilityName() != null) {
                        return e.getEvidenceText().contains(item.getAbilityName());
                    }
                    return false;
                })
                .collect(Collectors.toList());
    }

    public String toHarnessChangeType(PostEvolutionChangeItem item) {
        if (item.getChangeType() == null) {
            return "UPDATE_ABILITY";
        }
        return switch (item.getChangeType()) {
            case "ADDED" -> "ADD_ABILITY";
            case "REMOVED" -> "REMOVE_ABILITY";
            case "UPDATED_LEVEL" -> {
                if (item.getOldLevel() != null && item.getNewLevel() != null) {
                    yield item.getNewLevel() > item.getOldLevel()
                            ? "UPGRADE_LEVEL" : "DOWNGRADE_LEVEL";
                }
                yield "UPDATE_LEVEL";
            }
            case "UPDATED_WEIGHT" -> "UPDATE_WEIGHT";
            case "UPDATED_CORE" -> "UPDATE_CORE";
            default -> item.getChangeType();
        };
    }

    /**
     * 演化评分结果
     */
    public static class EvolutionScore {
        private BigDecimal evidenceScore;
        private BigDecimal relevanceScore;
        private BigDecimal freshnessScore;
        private BigDecimal feedbackScore;
        private BigDecimal diversityScore;
        private BigDecimal finalScore;

        public BigDecimal getEvidenceScore() { return evidenceScore; }
        public void setEvidenceScore(BigDecimal evidenceScore) { this.evidenceScore = evidenceScore; }
        public BigDecimal getRelevanceScore() { return relevanceScore; }
        public void setRelevanceScore(BigDecimal relevanceScore) { this.relevanceScore = relevanceScore; }
        public BigDecimal getFreshnessScore() { return freshnessScore; }
        public void setFreshnessScore(BigDecimal freshnessScore) { this.freshnessScore = freshnessScore; }
        public BigDecimal getFeedbackScore() { return feedbackScore; }
        public void setFeedbackScore(BigDecimal feedbackScore) { this.feedbackScore = feedbackScore; }
        public BigDecimal getDiversityScore() { return diversityScore; }
        public void setDiversityScore(BigDecimal diversityScore) { this.diversityScore = diversityScore; }
        public BigDecimal getFinalScore() { return finalScore; }
        public void setFinalScore(BigDecimal finalScore) { this.finalScore = finalScore; }
    }
}
