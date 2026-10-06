package com.example.matching.service.matching;

import com.example.matching.agent.dto.MatchingAnalysisAgentRequest;
import com.example.matching.agent.dto.MatchingAnalysisAgentResult;
import com.example.matching.agent.service.MatchingAnalysisAgentService;
import com.example.matching.ai.service.AiMatchingService;
import com.example.matching.common.constant.AiConstant;
import com.example.matching.common.exception.BusinessException;
import com.example.matching.common.exception.ErrorCodeEnum;
import com.example.matching.dto.matching.MatchingAbilitySnapshot;
import com.example.matching.dto.matching.MatchingEmployeeProfile;
import com.example.matching.dto.matching.MatchingPostProfile;
import com.example.matching.dto.matching.MatchingRequirementSnapshot;
import com.example.matching.entity.matching.MatchingRecord;
import com.example.matching.ai.validation.DeterministicAiFallbacks;
import com.example.matching.resilience.AiServiceResilience;
import com.example.matching.service.matching.MatchingAlgorithmService.HardConditionResult;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

/**
 * AI 匹配分析服务 — L3 层 AI 评分和报告生成。
 */
@Slf4j
@Service
public class MatchingAiAnalysisService {

    private final AiMatchingService aiMatchingService;
    private final MatchingAnalysisAgentService matchingAnalysisAgentService;
    private final AiServiceResilience aiServiceResilience;
    private final ObjectMapper objectMapper;
    private final Executor aiTaskExecutor;
    private final MatchingAiScoringStateMachine aiScoringStateMachine;
    private final MatchingEvidenceScoreCalculator evidenceScoreCalculator;
    private final MeterRegistry meterRegistry;

    public MatchingAiAnalysisService(AiMatchingService aiMatchingService,
                                     MatchingAnalysisAgentService matchingAnalysisAgentService,
                                     AiServiceResilience aiServiceResilience,
                                     ObjectMapper objectMapper,
                                     @Qualifier("aiTaskExecutor") Executor aiTaskExecutor,
                                     MatchingAiScoringStateMachine aiScoringStateMachine,
                                     MatchingEvidenceScoreCalculator evidenceScoreCalculator,
                                     MeterRegistry meterRegistry) {
        this.aiMatchingService = aiMatchingService;
        this.matchingAnalysisAgentService = matchingAnalysisAgentService;
        this.aiServiceResilience = aiServiceResilience;
        this.objectMapper = objectMapper;
        this.aiTaskExecutor = aiTaskExecutor;
        this.aiScoringStateMachine = aiScoringStateMachine;
        this.evidenceScoreCalculator = evidenceScoreCalculator;
        this.meterRegistry = meterRegistry;
    }

    public String generateAiReport(Long recordId) {
        return aiMatchingService.generateAnalysisReport(recordId);
    }

    /**
     * 对 L2 候选结果执行 AI 深度评分（L3）。
     * <p>
     * 同步契约：本方法通过 {@code CompletableFuture.allOf(...).join()} 等待所有已声明候选
     * 的评分完成（含成功与失败路径）后才返回。它不是"异步提交"——调用方可以依赖
     * 返回后记录的 aiScoringStatus 均已处于终态（COMPLETED/FAILED/SKIPPED）。
     */
    public void runAiScoring(List<MatchingRecord> candidates,
                             Map<Long, List<MatchingAbilitySnapshot>> abilitiesMap,
                             Map<Long, BigDecimal> vectorScoreMap,
                             BigDecimal modelQualityScore,
                             BigDecimal feedbackCalibration,
                             Map<Long, MatchingEmployeeProfile> empMap,
                             MatchingPostProfile post,
                             List<MatchingRequirementSnapshot> requirements,
                             Map<Long, String> tagNameMap,
                             int aiTopN, int aiThreshold,
                             MatchingAlgorithmService matchingAlgorithmService,
                             MatchingScoreService matchingScoreService,
                             MatchingTrainingWeightProfileStore weightProfileStore) {
        runAiScoring(candidates, abilitiesMap, vectorScoreMap, modelQualityScore, feedbackCalibration,
                empMap, post, requirements, tagNameMap, aiTopN, aiThreshold, false,
                matchingAlgorithmService, matchingScoreService, weightProfileStore);
    }

    public void runAiScoring(List<MatchingRecord> candidates,
                             Map<Long, List<MatchingAbilitySnapshot>> abilitiesMap,
                             Map<Long, BigDecimal> vectorScoreMap,
                             BigDecimal modelQualityScore,
                             BigDecimal feedbackCalibration,
                             Map<Long, MatchingEmployeeProfile> empMap,
                             MatchingPostProfile post,
                             List<MatchingRequirementSnapshot> requirements,
                             Map<Long, String> tagNameMap,
                             int aiTopN, int aiThreshold, boolean forceAiMatching,
                             MatchingAlgorithmService matchingAlgorithmService,
                             MatchingScoreService matchingScoreService,
                             MatchingTrainingWeightProfileStore weightProfileStore) {
        List<MatchingRecord> aiCandidates = candidates.stream()
                .filter(item -> item.getForcedByList() == null)
                // force 模式：记忆规则排除的记录（screeningLevel=null，唯一来源）也进入 AI 分析
                //（其 matchStatus=4 的排除语义不变，AI 结果仅作展示）；
                // 硬条件实际失败（screeningLevel=1）仍排除，不绕过 L1。
                .filter(item -> forceAiMatching
                        ? (item.getScreeningLevel() == null || item.getScreeningLevel() >= 2)
                        : (item.getScreeningLevel() != null && item.getScreeningLevel() >= 2))
                .filter(item -> forceAiMatching || meetsAutomaticAiThreshold(item, aiThreshold))
                .sorted(Comparator.comparing(MatchingRecord::getL2Score,
                        Comparator.nullsLast(Comparator.reverseOrder())))
                .limit(aiTopN).toList();

        log.info("L3 AI scoring candidates selected: total={}, selected={}, topN={}, l2PriorityThreshold={}, forced={}",
                candidates.size(), aiCandidates.size(), aiTopN, aiThreshold, forceAiMatching);

        Set<Long> aiCandidateIds = aiCandidates.stream()
                .map(MatchingRecord::getId)
                .filter(Objects::nonNull)
                .collect(java.util.stream.Collectors.toSet());
        candidates.stream()
                .filter(record -> !aiCandidateIds.contains(record.getId()))
                .filter(record -> AiConstant.AI_SCORING_PENDING.equals(record.getAiScoringStatus()))
                .forEach(record -> aiScoringStateMachine.skipIfPending(record.getId()));

        Set<Long> claimedIds = new HashSet<>();
        List<CompletableFuture<Void>> futures = new ArrayList<>();
        for (MatchingRecord record : aiCandidates) {
            if (aiScoringStateMachine.claimForProcessing(record.getId())) {
                claimedIds.add(record.getId());
                recordOutcome("accepted");
                futures.add(CompletableFuture.runAsync(
                        () -> scoreCandidate(record, abilitiesMap, vectorScoreMap, modelQualityScore,
                                feedbackCalibration, empMap, post, requirements, tagNameMap,
                                matchingAlgorithmService, matchingScoreService, weightProfileStore),
                        aiTaskExecutor));
            }
        }

        if (futures.isEmpty()) {
            recordOutcome("skipped");
            return;
        }
        Timer.Sample sample = Timer.start(meterRegistry);
        try {
            CompletableFuture.allOf(futures.toArray(CompletableFuture[]::new)).join();
        } finally {
            sample.stop(Timer.builder("matching.ai.score.duration")
                    .register(meterRegistry));
        }

        aiCandidates.stream()
                .filter(record -> !claimedIds.contains(record.getId()))
                .filter(record -> AiConstant.AI_SCORING_PENDING.equals(record.getAiScoringStatus()))
                .forEach(record -> aiScoringStateMachine.skipIfPending(record.getId()));
    }

    private boolean meetsAutomaticAiThreshold(MatchingRecord record, int aiThreshold) {
        return record.getScreeningLevel() != null
                && record.getScreeningLevel() >= 2
                && record.getL2Score() != null
                && record.getL2Score().doubleValue() >= aiThreshold;
    }

    private void recordOutcome(String outcome) {
        Counter.builder("matching.ai.candidates")
                .tag("outcome", outcome)
                .register(meterRegistry)
                .increment();
    }

    /** Executes L3 scoring for a record already atomically claimed by the retry worker. */
    public void scoreClaimedCandidate(MatchingRecord record,
                                      Map<Long, List<MatchingAbilitySnapshot>> abilitiesMap,
                                      Map<Long, BigDecimal> vectorScoreMap,
                                      BigDecimal modelQualityScore,
                                      BigDecimal feedbackCalibration,
                                      Map<Long, MatchingEmployeeProfile> empMap,
                                      MatchingPostProfile post,
                                      List<MatchingRequirementSnapshot> requirements,
                                      Map<Long, String> tagNameMap,
                                      MatchingAlgorithmService matchingAlgorithmService,
                                      MatchingScoreService matchingScoreService,
                                      MatchingTrainingWeightProfileStore weightProfileStore) {
        scoreCandidate(record, abilitiesMap, vectorScoreMap, modelQualityScore, feedbackCalibration, empMap,
                post, requirements, tagNameMap, matchingAlgorithmService, matchingScoreService, weightProfileStore);
    }

    private void scoreCandidate(MatchingRecord record,
                                Map<Long, List<MatchingAbilitySnapshot>> abilitiesMap,
                                Map<Long, BigDecimal> vectorScoreMap,
                                BigDecimal modelQualityScore,
                                BigDecimal feedbackCalibration,
                                Map<Long, MatchingEmployeeProfile> empMap,
                                MatchingPostProfile post,
                                List<MatchingRequirementSnapshot> requirements,
                                Map<Long, String> tagNameMap,
                                MatchingAlgorithmService matchingAlgorithmService,
                                MatchingScoreService matchingScoreService,
                                MatchingTrainingWeightProfileStore weightProfileStore) {
        try {
            Map<String, Object> aiResult = generateAiScore(record.getId());
            BigDecimal llmScoreVal = toBigDecimal(aiResult.get("aiScore"));
            String aiReport = extractReportFromAiResult(aiResult);

            BigDecimal evidenceScore = evidenceScoreCalculator.computeEvidenceScoreFromSnapshots(
                    abilitiesMap.getOrDefault(record.getEmpId(), List.of()));
            BigDecimal semanticScore = vectorScoreMap != null
                    && vectorScoreMap.containsKey(record.getEmpId())
                    ? record.getVectorScore() : null;

            MatchingTrainingWeightProfileStore.WeightProfile profile = weightProfileStore.currentProfile();
            MatchScoreResult scoreResult = matchingScoreService.score(
                    MatchScoreInput.withAi(record.getPostModelScore(), semanticScore, evidenceScore,
                            llmScoreVal, profile));

            BigDecimal finalScore = scoreResult.finalScore();
            int matchStatus = matchingAlgorithmService.determineMatchStatus(finalScore);

            MatchingEmployeeProfile employee = empMap.get(record.getEmpId());
            List<MatchingAbilitySnapshot> abilities = abilitiesMap.getOrDefault(record.getEmpId(), List.of());
            String quantitativeReport = null;
            if (employee != null) {
                if (record.getMatchDetails() != null && !record.getMatchDetails().isEmpty()) {
                    quantitativeReport = matchingAlgorithmService.generateReport(
                            record, employee.realName(), post.postName(), abilities, requirements, tagNameMap,
                            record.getMatchDetails());
                } else {
                    quantitativeReport = matchingAlgorithmService.generateReport(
                            record, employee.realName(), post.postName(), abilities, requirements, tagNameMap);
                }
            }

            MatchingAiScoringResult result = new MatchingAiScoringResult(
                    llmScoreVal, finalScore, evidenceScore,
                    scoreResult.rankScore(), scoreResult.qualityAdjustment(),
                    scoreResult.feedbackAdjustment(), scoreResult.calibrationAdjustment(),
                    aiReport, quantitativeReport, matchStatus,
                    composeScoreBreakdown(record.getScoreBreakdownJson(), scoreResult, llmScoreVal));

            if (aiScoringStateMachine.completeIfProcessing(record.getId(), result)) {
                applyCompletedResult(record, result);
            } else {
                log.debug("AI scoring completed by another worker: recordId={}", record.getId());
            }
        } catch (Exception e) {
            log.warn("L3 AI scoring failed, keep L2 result. recordId={}: {}", record.getId(), e.getMessage());
            int attemptCount = record.getAiScoringAttemptCount() != null ? record.getAiScoringAttemptCount() : 0;
            aiScoringStateMachine.failIfProcessing(record.getId(), truncate(e.getMessage(), 500), attemptCount);
        }
    }

    Map<String, Object> generateAiScore(Long recordId) {
        String resultJson = aiServiceResilience.callWithResilience(
                "matching-analysis-agent",
                () -> {
                    try {
                        MatchingAnalysisAgentRequest agentReq = new MatchingAnalysisAgentRequest();
                        agentReq.setMatchingRecordId(recordId);
                        MatchingAnalysisAgentResult agentResult = com.example.matching.agent.config.AgentToolProvider
                                .withScope(() -> matchingAnalysisAgentService.analyze(agentReq));
                        Map<String, Object> mapped = new LinkedHashMap<>();
                        mapped.put("aiScore", agentResult.getSuggestedLlmScore());
                        mapped.put("aiReport", agentResult.getRawModelOutput() != null
                                ? agentResult.getRawModelOutput() : objectMapper.writeValueAsString(agentResult));
                        mapped.put("conclusion", agentResult.getConclusion());
                        return objectMapper.writeValueAsString(mapped);
                    } catch (com.fasterxml.jackson.core.JsonProcessingException e) {
                        throw com.example.matching.common.exception.AiServiceException.retryable(
                                "matching-analysis-agent", "generateAiScore", "AI评分结果序列化失败", e);
                    }
                },
                DeterministicAiFallbacks.MATCHING_ANALYSIS
        );

        try {
            Map<String, Object> parsed = objectMapper.readValue(resultJson,
                    new com.fasterxml.jackson.core.type.TypeReference<Map<String, Object>>() {});
            if (Boolean.TRUE.equals(parsed.get("degraded"))) {
                throw com.example.matching.common.exception.AiServiceException.retryable(
                        "matching-analysis-agent", "generateAiScore",
                        "AI service returned deterministic degraded result", null);
            }
            return parsed;
        } catch (com.example.matching.common.exception.AiServiceException e) {
            throw e;
        } catch (Exception e) {
            log.warn("解析AI评分结果失败: recordId={}", recordId, e);
            throw com.example.matching.common.exception.AiServiceException.retryable(
                    "matching-analysis-agent", "parseAiScore",
                    "AI result JSON invalid", e);
        }
    }

    private void applyCompletedResult(MatchingRecord record, MatchingAiScoringResult result) {
        record.setLlmScore(result.llmScore());
        record.setAiMatchScore(result.finalScore());
        record.setEvidenceCredibilityScore(result.evidenceScore());
        record.setEvidenceScore(result.evidenceScore());
        record.setRankScore(result.rankScore());
        record.setQualityAdjustment(result.qualityAdjustment());
        record.setFeedbackAdjustment(result.feedbackAdjustment());
        record.setCalibrationAdjustment(result.calibrationAdjustment());
        if (result.scoreBreakdownJson() != null) {
            record.setScoreBreakdownJson(result.scoreBreakdownJson());
        }
        record.setAiAnalysisReport(result.aiAnalysisReport());
        record.setQuantitativeReport(result.quantitativeReport());
        record.setMatchStatus(result.matchStatus());
        record.setAiScoringStatus(AiConstant.AI_SCORING_COMPLETED);
        record.setScreeningLevel(3);
    }

    private String extractReportFromAiResult(Map<String, Object> aiResult) {
        if (aiResult == null) return null;
        Object report = aiResult.get("aiReport");
        return report != null ? report.toString() : null;
    }

    private String composeScoreBreakdown(String existingJson,
                                         MatchScoreResult scoreResult,
                                         BigDecimal aiScore) {
        try {
            Map<String, Object> breakdown = existingJson == null || existingJson.isBlank()
                    ? new LinkedHashMap<>()
                    : objectMapper.readValue(existingJson, Map.class);
            Map<String, Object> aiBreakdown = new LinkedHashMap<>();
            aiBreakdown.put("rawScore", aiScore);
            aiBreakdown.put("weight", scoreResult.llmWeight());
            aiBreakdown.put("fallbackApplied", !scoreResult.hasLlm());
            breakdown.put("ai", aiBreakdown);
            breakdown.put("scoreCompositionVersion", "MATCH_SCORE_V2");
            breakdown.put("dimensionFinalScore", scoreResult.finalScore());
            breakdown.put("finalScore", scoreResult.finalScore());
            breakdown.put("rankScore", scoreResult.rankScore());
            return objectMapper.writeValueAsString(breakdown);
        } catch (Exception e) {
            log.warn("Failed to compose unified score breakdown", e);
            return existingJson;
        }
    }

    private BigDecimal toBigDecimal(Object val) {
        if (val == null) return null;
        if (val instanceof BigDecimal bd) return bd;
        try {
            return new BigDecimal(val.toString());
        } catch (Exception e) {
            return null;
        }
    }

    private static String truncate(String value, int maxLength) {
        if (value == null) {
            return null;
        }
        return value.length() <= maxLength ? value : value.substring(0, maxLength);
    }
}
