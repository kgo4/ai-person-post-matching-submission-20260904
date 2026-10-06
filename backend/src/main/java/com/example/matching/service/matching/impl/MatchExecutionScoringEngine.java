package com.example.matching.service.matching.impl;

import com.example.matching.agent.service.impl.MatchScoringMemoryRuleApplier;
import com.example.matching.dto.matching.MatchingAbilitySnapshot;
import com.example.matching.dto.matching.MatchingEmployeeProfile;
import com.example.matching.dto.matching.MatchingPostProfile;
import com.example.matching.dto.matching.MatchingRequirementSnapshot;
import com.example.matching.dto.matching.MatchOverride;
import com.example.matching.entity.matching.MatchingBlackWhiteList;
import com.example.matching.entity.matching.MatchingRecord;
import com.example.matching.service.matching.MatchEvaluator;
import com.example.matching.service.matching.MatchScoreResult;
import com.example.matching.service.matching.MatchingTrainingWeightProfileStore;
import com.example.matching.service.matching.MatchingAlgorithmService;
import com.example.matching.service.matching.MatchingAlgorithmService.HardConditionResult;
import com.example.matching.dto.matching.MatchingExecuteDTO.HardCondition;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 匹配执行评分引擎：评分、快照持久化、报告生成。
 * <p>
 * 从 MatchingExecuteServiceImpl（627 行）中拆分的评分组件。
 */
@Slf4j
@Component
public class MatchExecutionScoringEngine {

    private final MatchingAlgorithmService matchingAlgorithmService;
    private final MatchEvaluator matchEvaluator;
    private final MatchScoringMemoryRuleApplier memoryRuleApplier;
    private final ObjectMapper objectMapper;

    @Autowired
    public MatchExecutionScoringEngine(MatchingAlgorithmService matchingAlgorithmService,
                                       MatchEvaluator matchEvaluator,
                                       MatchScoringMemoryRuleApplier memoryRuleApplier,
                                       ObjectMapper objectMapper) {
        this.matchingAlgorithmService = matchingAlgorithmService;
        this.matchEvaluator = matchEvaluator;
        this.memoryRuleApplier = memoryRuleApplier;
        this.objectMapper = objectMapper;
    }

    /** @deprecated RAG 不再参与排名；保留此构造器只兼容旧测试装配。 */
    @Deprecated
    public MatchExecutionScoringEngine(MatchingAlgorithmService matchingAlgorithmService,
                                       com.example.matching.service.matching.RagScoreService ignoredRagScoreService,
                                       MatchEvaluator matchEvaluator,
                                       MatchScoringMemoryRuleApplier memoryRuleApplier,
                                       ObjectMapper objectMapper) {
        this(matchingAlgorithmService, matchEvaluator, memoryRuleApplier, objectMapper);
    }
    private void scoreAndBuildReport(MatchingRecord record, ScoreContext context) {
        Long empId = record.getEmpId();
        Long postId = record.getPostId();
        BigDecimal milvusVectorScore = context.vectorScoreMap().get(empId);

        boolean milvusAvailable = context.vectorScoreMap() != null && !context.vectorScoreMap().isEmpty();
        // RAG 仅用于受控上下文与解释，不参与人员岗位正式排名。
        BigDecimal ragScore = null;
        record.setRagScore(null);

        MatchEvaluator.EvaluationContext evalCtx = new MatchEvaluator.EvaluationContext(
                empId, postId, record.getBatchNo(),
                context.abilities(), context.requirements(), context.blackWhiteList(),
                milvusVectorScore);

        MatchEvaluator.EvaluatedMatch evaluated = matchEvaluator.evaluate(evalCtx);
        MatchingRecord l2Record = evaluated.l2Record();
        BigDecimal finalScore = evaluated.scoreResult().finalScore();

        record.setL2Score(l2Record.getL2Score());
        record.setVectorScore(l2Record.getVectorScore() != null ? l2Record.getVectorScore() : BigDecimal.ZERO);
        record.setPostModelScore(l2Record.getPostModelScore() != null ? l2Record.getPostModelScore() : BigDecimal.ZERO);
        record.setScreeningLevel(2);

        record.setAiMatchScore(finalScore);
        record.setProfileSemanticScore(milvusAvailable ? milvusVectorScore : null);
        record.setEvidenceCredibilityScore(evaluated.evidenceScore());
        record.setEvidenceScore(evaluated.evidenceScore());
        record.setRankScore(finalScore);
        record.setQualityAdjustment(evaluated.scoreResult().qualityAdjustment());
        record.setFeedbackAdjustment(evaluated.scoreResult().feedbackAdjustment());
        record.setCalibrationAdjustment(evaluated.scoreResult().calibrationAdjustment());
        record.setMatchStatus(matchEvaluator.determineStatus(finalScore));
        persistScoreSnapshots(record, evaluated.semanticScore(), evaluated.evidenceScore(),
                evaluated.scoreResult(), evaluated.weightProfile());

        if (context.logMilvusUnavailable() && !milvusAvailable) {
            log.info("semantic weight excluded due to unavailable vector score: empId={}", empId);
        }

        String report;
        if (l2Record.getMatchDetails() != null && !l2Record.getMatchDetails().isEmpty()) {
            report = matchingAlgorithmService.generateReport(
                    record, context.employee().realName(), context.post().postName(),
                    context.abilities(), context.requirements(), context.tagNameMap(), l2Record.getMatchDetails());
        } else {
            // 黑白名单/L1 等无需能力明细的兼容路径继续使用原报告入口。
            report = matchingAlgorithmService.generateReport(
                    record, context.employee().realName(), context.post().postName(),
                    context.abilities(), context.requirements(), context.tagNameMap());
        }
        record.setQuantitativeReport(report);
    }

    private void persistScoreSnapshots(MatchingRecord record,
                                       BigDecimal semanticScore,
                                       BigDecimal evidenceScore,
                                       MatchScoreResult scoreResult,
                                       MatchingTrainingWeightProfileStore.WeightProfile profile) {
        record.setWeightProfileVersion(profile.getVersion());

        Map<String, Object> weightSnapshot = new LinkedHashMap<>();
        weightSnapshot.put("profileVersion", profile.getVersion());
        weightSnapshot.put("abilityWeight", profile.getAbilityWeight());
        weightSnapshot.put("semanticWeight", profile.getSemanticWeight());
        weightSnapshot.put("evidenceWeight", profile.getEvidenceWeight());
        weightSnapshot.put("aiWeight", profile.getAiWeight());
        weightSnapshot.put("resolvedAbilityWeight", scoreResult.abilityWeight());
        weightSnapshot.put("resolvedSemanticWeight", scoreResult.semanticWeight());
        weightSnapshot.put("resolvedEvidenceWeight", scoreResult.evidenceWeight());
        weightSnapshot.put("resolvedLlmWeight", scoreResult.llmWeight());

        Map<String, Object> scoreBreakdown = new LinkedHashMap<>();
        scoreBreakdown.put("ability", scoreDimension(record.getPostModelScore(), scoreResult.abilityWeight()));
        scoreBreakdown.put("semantic", scoreDimension(semanticScore, scoreResult.semanticWeight()));
        scoreBreakdown.put("evidence", scoreDimension(evidenceScore, scoreResult.evidenceWeight()));
        scoreBreakdown.put("ai", scoreDimension(null, scoreResult.llmWeight()));
        scoreBreakdown.put("rankScore", scoreResult.rankScore());
        scoreBreakdown.put("qualityAdjustment", scoreResult.qualityAdjustment());
        scoreBreakdown.put("feedbackAdjustment", scoreResult.feedbackAdjustment());
        scoreBreakdown.put("calibrationAdjustment", scoreResult.calibrationAdjustment());
        scoreBreakdown.put("dimensionFinalScore", scoreResult.finalScore());
        scoreBreakdown.put("finalScore", scoreResult.finalScore());
        scoreBreakdown.put("rankScore", scoreResult.rankScore());
        scoreBreakdown.put("algorithmVersion", scoreResult.algorithmVersion());

        try {
            record.setWeightSnapshotJson(objectMapper.writeValueAsString(weightSnapshot));
            record.setScoreBreakdownJson(objectMapper.writeValueAsString(scoreBreakdown));
        } catch (Exception e) {
            log.error("Failed to serialize score snapshots: empId={}, postId={}",
                    record.getEmpId(), record.getPostId(), e);
            record.setWeightSnapshotJson("{\"error\":\"serialize_failed\"}");
            record.setScoreBreakdownJson("{\"error\":\"serialize_failed\"}");
        }
    }

    private Map<String, Object> scoreDimension(BigDecimal rawScore, BigDecimal weight) {
        Map<String, Object> dimension = new LinkedHashMap<>();
        dimension.put("rawScore", rawScore);
        dimension.put("weight", weight);
        dimension.put("weightedScore", rawScore != null && weight != null ? rawScore.multiply(weight) : null);
        return dimension;
    }

    public MatchingRecord buildScoredRecord(MatchContext context) {
        MatchingEmployeeProfile employee = context.employee();
        Long empId = employee.empId();
        Long postId = context.post().postId();
        MatchingRecord record = buildBaseRecord(context.batchNo(), empId, postId, context.postModelVersion(),
                context.modelQualityScore(), context.feedbackCalibration());

        MatchingBlackWhiteList bwHit = findBwListHit(empId, postId, context.blackWhiteList());
        MatchOverride override = toOverride(bwHit);
        boolean whitelistBypassesHardRules = override.isWhitelist()
                && context.weightProfile() != null
                && context.weightProfile().isWhitelistBypassHardRules();
        if (override.isBlacklist() || whitelistBypassesHardRules) {
            if (whitelistBypassesHardRules) {
                record.setHardConditionResult("{\"bypassed\":true,\"reason\":\"whitelist\"}");
            }
            applyOverride(record, override, context);
            return record;
        }

        if (context.hardConditions() != null && !context.hardConditions().isEmpty()) {
            HardConditionResult hcResult = matchingAlgorithmService.checkHardConditions(
                    employee, context.hardConditions(), context.resumeBasicInfo());
            try {
                record.setHardConditionResult(objectMapper.writeValueAsString(hcResult));
            } catch (Exception e) {
                record.setHardConditionResult("{\"error\":\"serialize_failed\"}");
            }

            if (!hcResult.isPassed()) {
                record.setPostModelScore(BigDecimal.ZERO);
                record.setVectorScore(context.vectorScoreMap().getOrDefault(empId, BigDecimal.ZERO));
                record.setL2Score(BigDecimal.ZERO);
                record.setAiMatchScore(BigDecimal.ZERO);
                record.setMatchStatus(4);
                record.setScreeningLevel(1);
                return record;
            }
        }

        if (override.enforced()) {
            applyOverride(record, override, context);
            return record;
        }

        // Apply Agent Memory rules (only HARD rules, only reduce/block)
        MatchScoringMemoryRuleApplier.MemoryApplyResult memResult = null;
        if (memoryRuleApplier != null) {
            String empText = buildEmpText(context.employee(), context.abilities());
            String postText = buildPostText(context.post(), context.requirements());
            Set<Long> employeeTagIds = context.abilities() == null ? Set.of() : context.abilities().stream()
                    .map(MatchingAbilitySnapshot::tagId)
                    .filter(java.util.Objects::nonNull)
                    .collect(Collectors.toSet());
            memResult = memoryRuleApplier.apply(record, empText, postText, employeeTagIds);
            if (memResult.excluded()) {
                return record;
            }
        }

        scoreAndBuildReport(record, new ScoreContext(
                employee, context.post(), context.abilities(), context.requirements(), context.blackWhiteList(),
                context.vectorScoreMap(), context.modelQualityScore(),
                context.feedbackCalibration(), context.tagNameMap(), context.logMilvusUnavailable(),
                context.weightProfile()));

        // After scoring: apply any pending MATCH_SCORE_CAP and append audit info
        if (memResult != null && !memResult.appliedActions().isEmpty()) {
            for (String action : memResult.appliedActions()) {
                if (action.startsWith("MATCH_SCORE_CAP:")) {
                    try {
                        BigDecimal maxScore = new BigDecimal(action.substring("MATCH_SCORE_CAP:".length()));
                        if (record.getAiMatchScore() != null && record.getAiMatchScore().compareTo(maxScore) > 0) {
                            record.setAiMatchScore(maxScore);
                            record.setMatchStatus(matchEvaluator.determineStatus(maxScore));
                            if (record.getRankScore() != null && record.getRankScore().compareTo(maxScore) > 0) {
                                record.setRankScore(maxScore);
                            }
                        }
                    } catch (Exception e) {
                        log.warn("Failed to apply score cap from action '{}'", action, e);
                    }
                }
            }
            try {
                Map<String, Object> sbMap = objectMapper.readValue(record.getScoreBreakdownJson(), Map.class);
                Map<String, Object> memInfo = new LinkedHashMap<>();
                memInfo.put("appliedRuleIds", memResult.appliedRuleIds());
                memInfo.put("appliedActions", memResult.appliedActions());
                memInfo.put("scoreBefore", memResult.scoreBefore());
                memInfo.put("scoreAfter", memResult.scoreAfter());
                memInfo.put("excluded", memResult.excluded());
                sbMap.put("memoryRules", memInfo);
                sbMap.put("rankScore", record.getRankScore());
                sbMap.put("finalScore", record.getAiMatchScore());
                record.setScoreBreakdownJson(objectMapper.writeValueAsString(sbMap));
            } catch (Exception e) {
                log.warn("Failed to append memory rule info to score breakdown", e);
            }
        }

        return record;
    }

    public record ScoreContext(
            MatchingEmployeeProfile employee,
            MatchingPostProfile post,
            List<MatchingAbilitySnapshot> abilities,
            List<MatchingRequirementSnapshot> requirements,
            List<MatchingBlackWhiteList> blackWhiteList,
            Map<Long, BigDecimal> vectorScoreMap,
            BigDecimal modelQualityScore,
            BigDecimal feedbackCalibration,
            Map<Long, String> tagNameMap,
            boolean logMilvusUnavailable,
            MatchingTrainingWeightProfileStore.WeightProfile weightProfile
    ) {
    }

    public record MatchContext(
            String batchNo,
            MatchingEmployeeProfile employee,
            MatchingPostProfile post,
            String postModelVersion,
            BigDecimal modelQualityScore,
            BigDecimal feedbackCalibration,
            List<HardCondition> hardConditions,
            Map<String, Object> resumeBasicInfo,
            List<MatchingAbilitySnapshot> abilities,
            List<MatchingRequirementSnapshot> requirements,
            List<MatchingBlackWhiteList> blackWhiteList,
            Map<Long, BigDecimal> vectorScoreMap,
            Map<Long, String> tagNameMap,
            boolean logMilvusUnavailable,
            MatchingTrainingWeightProfileStore.WeightProfile weightProfile
    ) {
    }

    private MatchingRecord buildBaseRecord(String batchNo, Long empId, Long postId,
                                            String postModelVersion, BigDecimal modelQualityScore,
                                            BigDecimal feedbackCalibration) {
        MatchingRecord record = new MatchingRecord();
        record.setBatchNo(batchNo);
        record.setEmpId(empId);
        record.setPostId(postId);
        record.setPostModelVersion(postModelVersion);
        record.setApprovalStatus(0);
        record.setModelQualityCoefficient(modelQualityScore);
        record.setFeedbackCalibration(feedbackCalibration);
        return record;
    }

    private MatchingBlackWhiteList findBwListHit(Long empId, Long postId, List<MatchingBlackWhiteList> bwList) {
        if (bwList == null) return null;
        MatchingBlackWhiteList whitelist = null;
        for (MatchingBlackWhiteList bw : bwList) {
            boolean empMatch = bw.getEmpId() != null && bw.getEmpId().equals(empId);
            boolean postMatch = bw.getPostId() != null && bw.getPostId().equals(postId);
            if (!empMatch || !postMatch || bw.getListType() == null) {
                continue;
            }
            if (bw.getListType() == 2) {
                return bw;
            }
            if (bw.getListType() == 1) {
                whitelist = bw;
            }
        }
        return whitelist;
    }

    private void applyOverride(MatchingRecord record,
                               MatchOverride override,
                               MatchContext context) {
        Long empId = record.getEmpId();
        record.setForcedByList(override.listType());
        record.setL2Score(override.forcedScore());
        record.setPostModelScore(override.forcedScore());
        record.setVectorScore(context.vectorScoreMap().getOrDefault(empId, BigDecimal.ZERO));
        record.setEvidenceCredibilityScore(BigDecimal.ZERO);
        record.setEvidenceScore(BigDecimal.ZERO);
        record.setRagScore(null);
        record.setAiMatchScore(override.forcedScore());
        record.setRankScore(override.forcedScore());
        record.setQualityAdjustment(BigDecimal.ZERO);
        record.setFeedbackAdjustment(BigDecimal.ZERO);
        record.setCalibrationAdjustment(BigDecimal.ZERO);
        record.setMatchStatus(override.forcedMatchStatus());
        record.setScreeningLevel(2);
        record.setQuantitativeReport("{\"conclusion\":\"" + override.forceReason() + "\"}");
    }

    private static MatchOverride toOverride(MatchingBlackWhiteList bwHit) {
        if (bwHit == null) {
            return MatchOverride.NONE;
        }
        if (bwHit.getListType() == 2) {
            return MatchOverride.blacklist();
        }
        return MatchOverride.whitelist();
    }

    private String buildEmpText(MatchingEmployeeProfile employee, List<MatchingAbilitySnapshot> abilities) {
        StringBuilder sb = new StringBuilder();
        sb.append("Employee: ").append(employee.realName());
        if (abilities != null) {
            for (MatchingAbilitySnapshot ab : abilities) {
                sb.append("; TagId=").append(ab.tagId()).append(" Level=").append(ab.level());
            }
        }
        return sb.toString();
    }

    private String buildPostText(MatchingPostProfile post, List<MatchingRequirementSnapshot> requirements) {
        StringBuilder sb = new StringBuilder();
        sb.append("Post: ").append(post.postName());
        if (requirements != null) {
            for (MatchingRequirementSnapshot req : requirements) {
                sb.append("; TagId=").append(req.tagId()).append(" MinLevel=").append(req.minRequiredLevel());
            }
        }
        return sb.toString();
    }
}
