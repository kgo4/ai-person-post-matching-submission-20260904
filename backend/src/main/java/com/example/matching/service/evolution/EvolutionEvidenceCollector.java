package com.example.matching.service.evolution;

import com.example.matching.entity.evolution.MarketJdData;
import com.example.matching.entity.evolution.PostEvolutionEvidence;
import com.example.matching.entity.matching.MatchingFeedbackDataset;
import com.example.matching.entity.matching.MatchingRecord;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;

/**
 * 演化任务证据收集器
 * <p>
 * 从 PostEvolutionServiceImpl 抽取，负责从不同数据源创建证据记录。
 * 纯数据变换，无外部依赖。
 */
@Component
public class EvolutionEvidenceCollector {

    public PostEvolutionEvidence createJdEvidence(Long taskId, String jdText) {
        PostEvolutionEvidence evidence = new PostEvolutionEvidence();
        evidence.setTaskId(taskId);
        evidence.setSourceType("MANUAL_JD");
        evidence.setSourceTitle("手动输入的JD文本");
        evidence.setEvidenceText(jdText.length() > 1000 ? jdText.substring(0, 1000) + "..." : jdText);
        evidence.setCollectedTime(LocalDateTime.now());
        evidence.setSimilarityScore(BigDecimal.ONE);
        evidence.setSourceWeight(BigDecimal.valueOf(0.8));
        evidence.setTrustScore(BigDecimal.valueOf(0.7));
        evidence.setSourceRef("source:POST_EVOLUTION_TASK:" + taskId);
        return evidence;
    }

    public PostEvolutionEvidence createMarketJdEvidence(Long taskId, MarketJdData marketJd) {
        PostEvolutionEvidence evidence = new PostEvolutionEvidence();
        evidence.setTaskId(taskId);
        evidence.setSourceType("MARKET_JD");
        evidence.setSourceId(marketJd.getId());
        evidence.setSourceTitle(marketJd.getPostName() + " - " + marketJd.getCompanyName());
        evidence.setEvidenceText(marketJd.getJobDescription() != null
                ? (marketJd.getJobDescription().length() > 500
                ? marketJd.getJobDescription().substring(0, 500) + "..."
                : marketJd.getJobDescription())
                : null);
        evidence.setPublishedTime(marketJd.getPublishedTime());
        evidence.setCollectedTime(LocalDateTime.now());
        evidence.setSourceWeight(BigDecimal.valueOf(0.6));
        evidence.setSimilarityScore(marketJd.getQualityScore());
        evidence.setTrustScore(calculateMarketJdTrustScore(marketJd));
        if (marketJd.getId() != null) {
            evidence.setSourceRef("source:MARKET_JD:" + marketJd.getId());
        }
        return evidence;
    }

    public PostEvolutionEvidence createFeedbackEvidence(Long taskId, MatchingFeedbackDataset feedback) {
        PostEvolutionEvidence evidence = new PostEvolutionEvidence();
        evidence.setTaskId(taskId);
        evidence.setSourceType("MATCHING_FEEDBACK");
        evidence.setSourceId(feedback.getId());
        evidence.setSourceTitle("匹配反馈 - 员工#" + feedback.getEmpId());
        evidence.setEvidenceText("AI分数: " + feedback.getAiMatchScore()
                + ", 人工分数: " + feedback.getFinalMatchScore()
                + ", 反馈原因: " + feedback.getFeedbackReasons());
        evidence.setCollectedTime(LocalDateTime.now());
        evidence.setSimilarityScore(BigDecimal.ONE);
        evidence.setSourceWeight(BigDecimal.valueOf(0.9));
        evidence.setTrustScore(BigDecimal.valueOf(0.85));
        if (feedback.getId() != null) {
            evidence.setSourceRef("source:MATCHING_FEEDBACK:" + feedback.getId());
        }
        return evidence;
    }

    public PostEvolutionEvidence createMatchingGapEvidence(Long taskId, MatchingRecord record) {
        PostEvolutionEvidence evidence = new PostEvolutionEvidence();
        evidence.setTaskId(taskId);
        evidence.setSourceType("MATCHING_GAP");
        evidence.setSourceId(record.getId());
        evidence.setSourceTitle("匹配缺口 - 员工#" + record.getEmpId() + " -> 岗位#" + record.getPostId());
        evidence.setEvidenceText("匹配分: " + record.getAiMatchScore()
                + ", 状态: " + record.getMatchStatus());
        evidence.setCollectedTime(LocalDateTime.now());
        evidence.setSimilarityScore(BigDecimal.ONE);
        evidence.setSourceWeight(BigDecimal.valueOf(0.7));
        evidence.setTrustScore(BigDecimal.valueOf(0.75));
        if (record.getId() != null) {
            evidence.setSourceRef("source:MATCHING_RECORD:" + record.getId());
        }
        return evidence;
    }

    BigDecimal calculateMarketJdTrustScore(MarketJdData marketJd) {
        double score = 0.5;
        if (marketJd.getQualityScore() != null) {
            score = marketJd.getQualityScore().doubleValue() / 100.0;
        }
        if (marketJd.getSourcePlatform() != null) {
            switch (marketJd.getSourcePlatform()) {
                case "BOSS", "ZHILIN", "LIEPIN" -> score += 0.1;
            }
        }
        return BigDecimal.valueOf(Math.min(1.0, score)).setScale(2, RoundingMode.HALF_UP);
    }
}
