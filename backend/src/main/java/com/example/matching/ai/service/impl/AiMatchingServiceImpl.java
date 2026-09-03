package com.example.matching.ai.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.example.matching.agent.dto.MatchingAnalysisAgentRequest;
import com.example.matching.agent.dto.MatchingAnalysisAgentResult;
import com.example.matching.agent.service.MatchingAnalysisAgentService;
import com.example.matching.ai.service.AiMatchingService;
import com.example.matching.ai.service.LangChain4jChatService;
import com.example.matching.ai.service.PromptTemplateService;
import com.example.matching.dto.matching.MatchingAbilitySnapshot;
import com.example.matching.dto.matching.MatchingReportDTO;
import com.example.matching.dto.matching.MatchingRequirementSnapshot;
import com.example.matching.entity.matching.MatchingRecord;
import com.example.matching.mapper.matching.MatchingRecordMapper;
import com.example.matching.resilience.AiServiceResilience;
import com.example.matching.service.matching.MatchingAlgorithmService;
import com.example.matching.service.matching.MatchingDataQueryService;
import com.example.matching.service.matching.MatchingFeedbackDatasetService;
import com.example.matching.service.matching.MatchingReportService;
import com.example.matching.service.post.PostAbilityModelService;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.*;
import java.util.stream.Collectors;

/**
 * AI匹配服务实现
 * <p>
 * 使用 LangChain4j + FreeMarker 调用大模型生成人岗匹配分析报告。
 * 当 AI 服务不可用时自动降级为纯量化算法报告。
 */
@Slf4j
@Service
public class AiMatchingServiceImpl implements AiMatchingService {

    private final LangChain4jChatService langChain4jChatService;
    private final PromptTemplateService promptTemplateService;
    private final AiServiceResilience aiServiceResilience;
    private final MatchingRecordMapper matchingRecordMapper;
    private final MatchingDataQueryService dataQuery;
    private final MatchingFeedbackDatasetService feedbackDatasetService;
    private final MatchingAlgorithmService matchingAlgorithmService;
    private final PostAbilityModelService postAbilityModelService;
    private final MatchingReportService matchingReportService;
    private final ObjectMapper objectMapper;
    private final MatchingAnalysisAgentService matchingAnalysisAgentService;

    public AiMatchingServiceImpl(LangChain4jChatService langChain4jChatService,
                                 PromptTemplateService promptTemplateService,
                                 AiServiceResilience aiServiceResilience,
                                 MatchingRecordMapper matchingRecordMapper,
                                 MatchingDataQueryService dataQuery,
                                 MatchingFeedbackDatasetService feedbackDatasetService,
                                 MatchingAlgorithmService matchingAlgorithmService,
                                 PostAbilityModelService postAbilityModelService,
                                 MatchingReportService matchingReportService,
                                 ObjectMapper objectMapper,
                                 MatchingAnalysisAgentService matchingAnalysisAgentService) {
        this.langChain4jChatService = langChain4jChatService;
        this.promptTemplateService = promptTemplateService;
        this.aiServiceResilience = aiServiceResilience;
        this.matchingRecordMapper = matchingRecordMapper;
        this.dataQuery = dataQuery;
        this.feedbackDatasetService = feedbackDatasetService;
        this.matchingAlgorithmService = matchingAlgorithmService;
        this.postAbilityModelService = postAbilityModelService;
        this.matchingReportService = matchingReportService;
        this.objectMapper = objectMapper;
        this.matchingAnalysisAgentService = matchingAnalysisAgentService;
    }

    /**
     * 单员工能力加载（M-12：返回匹配专用能力快照）
     */
    private List<MatchingAbilitySnapshot> loadAbilitiesForEmp(Long empId) {
        Map<Long, List<MatchingAbilitySnapshot>> map = dataQuery.batchLoadAbilitySnapshots(List.of(empId));
        return map.getOrDefault(empId, List.of());
    }

    @Override
    public List<Map<String, Object>> executeMatching(Long postId, List<Long> empIds, String strategy) {
        log.warn("executeMatching via AI is deprecated; use MatchingRecordService instead");
        return Collections.emptyList();
    }

    @Override
    public String generateAnalysisReport(Long matchingRecordId) {
        try {
            MatchingRecord record = matchingRecordMapper.selectById(matchingRecordId);
            if (record == null) {
                return buildErrorReport("匹配记录不存在");
            }

            MatchingAnalysisAgentResult result = runGroundedAnalysisAgent(matchingRecordId);
            return objectMapper.writeValueAsString(buildAgentReportJson(result, record));

        } catch (Exception e) {
            log.error("AI报告生成失败，降级为量化报告。matchingRecordId={}", matchingRecordId, e);
            return buildFallbackReport(matchingRecordId);
        }
    }

    @Override
    public Map<String, Object> generateStructuredScore(Long matchingRecordId) {
        Map<String, Object> result = new HashMap<>();
        try {
            MatchingRecord record = matchingRecordMapper.selectById(matchingRecordId);
            if (record == null) {
                result.put("aiScore", BigDecimal.ZERO);
                result.put("report", buildErrorReport("匹配记录不存在"));
                result.put("conclusion", "错误");
                return result;
            }

            MatchingAnalysisAgentResult agentResult = runGroundedAnalysisAgent(matchingRecordId);

            String reportJson = objectMapper.writeValueAsString(buildAgentReportJson(agentResult, record));
            BigDecimal aiScore = agentResult.getSuggestedLlmScore() != null
                    ? agentResult.getSuggestedLlmScore().setScale(2, RoundingMode.HALF_UP)
                    : (record.getL2Score() != null ? record.getL2Score() : BigDecimal.ZERO);
            String conclusion = agentResult.getConclusion() != null ? agentResult.getConclusion() : "待观察";

            result.put("aiScore", aiScore);
            result.put("report", reportJson);
            result.put("conclusion", conclusion);
            result.put("dimensionScores", agentResult.getDimensionScores() != null
                    ? agentResult.getDimensionScores() : List.of());
            result.put("fallbackUsed", agentResult.getFallbackUsed() != null
                    ? agentResult.getFallbackUsed() : false);

            List<MatchingAbilitySnapshot> abilities = loadAbilitiesForEmp(record.getEmpId());
            List<MatchingRequirementSnapshot> requirements = dataQuery.findPostRequirements(record.getPostId());
            List<MatchingReportDTO.AbilityDetail> abilityDetails = buildAbilityDetails(abilities, requirements);
            result.put("gapAbilities", matchingReportService.extractGapAbilities(abilityDetails));
            result.put("improvementPlan", null);
            result.put("dimensionScoresComputed", true);
            return result;

        } catch (Exception e) {
            log.error("AI结构化评分失败。matchingRecordId={}", matchingRecordId, e);
            result.put("aiScore", quantitativeScore(matchingRecordId));
            result.put("report", buildFallbackReport(matchingRecordId));
            result.put("conclusion", "AI服务不可用");
            result.put("improvementPlan", null);
            result.put("dimensionScoresComputed", false);
            return result;
        }
    }

    /**
     * 统一委托 MatchingAnalysisAgent 链路：构建 context package、岗位范围内 RAG、graph context，
     * 并通过 GroundedAgentOutputValidator 校验后才采用模型输出。RAG 不可用时明确标记 fallbackUsed。
     */
    private MatchingAnalysisAgentResult runGroundedAnalysisAgent(Long matchingRecordId) {
        MatchingAnalysisAgentRequest request = new MatchingAnalysisAgentRequest();
        request.setMatchingRecordId(matchingRecordId);
        MatchingAnalysisAgentResult result = matchingAnalysisAgentService.analyze(request);
        if (result == null) {
            throw new IllegalStateException("Matching analysis agent returned no result");
        }
        return result;
    }

    private Map<String, Object> buildAgentReportJson(MatchingAnalysisAgentResult result, MatchingRecord record) {
        Map<String, Object> json = new LinkedHashMap<>();
        json.put("overallScore", result.getSuggestedLlmScore());
        // 前端契约字段（AiReportData）：aiScore 为 overallScore 别名，confidence 取自 AgentRunResult
        json.put("aiScore", result.getSuggestedLlmScore());
        json.put("confidence", result.getOverallConfidence());
        json.put("conclusion", result.getConclusion());
        json.put("strengths", result.getStrengths());
        json.put("gaps", result.getGaps());
        json.put("suggestions", result.getSuggestions());
        json.put("scoreReasons", result.getScoreReasons());
        json.put("evidenceAnalysis", result.getEvidenceAnalysis());
        json.put("riskSignals", result.getRiskSignals());
        json.put("humanAttentionPoints", result.getHumanAttentionPoints());
        json.put("dimensionScores", result.getDimensionScores());
        json.put("fallbackUsed", result.getFallbackUsed() != null ? result.getFallbackUsed() : false);
        json.put("sourceRefs", result.getSourceRefs());
        if (record != null && record.getModelQualityCoefficient() != null) {
            json.put("modelQualityNote", String.format("模型质量系数 %.1f%%", record.getModelQualityCoefficient().doubleValue()));
        }
        return json;
    }

    private BigDecimal extractAiScore(String json, MatchingRecord record) {
        try {
            @SuppressWarnings("unchecked")
            Map<String, Object> map = objectMapper.readValue(json, Map.class);
            Object score = map.get("aiScore");
            if (score instanceof Number n) {
                return new BigDecimal(n.toString()).setScale(2, RoundingMode.HALF_UP);
            }
        } catch (Exception exception) {
            log.debug("解析AI评分失败，使用量化评分兜底", exception);
        }
        return record.getL2Score() != null ? record.getL2Score() : BigDecimal.ZERO;
    }

    private String extractConclusion(String json) {
        try {
            @SuppressWarnings("unchecked")
            Map<String, Object> map = objectMapper.readValue(json, Map.class);
            Object c = map.get("conclusion");
            return c != null ? c.toString() : "待观察";
        } catch (Exception e) {
            log.debug("解析AI结论失败，使用默认结论", e);
            return "待观察";
        }
    }

    private String buildFallbackScoreJson(MatchingRecord record) {
        BigDecimal score = record.getL2Score() != null ? record.getL2Score() : BigDecimal.ZERO;
        return String.format("""
                {"aiScore": %s, "confidence": 0.0, "conclusion": "AI服务熔断", "dimensionScores": [], "strengths": [], "gaps": [], "suggestions": [], "scoreReasons": [], "riskSignals": ["AI服务不可用，当前结果已回退到量化评分"], "evidenceAnalysis": [], "modelQualityNote": "AI服务不可用", "weakEvidenceFlags": [], "coreGaps": [], "humanAttentionPoints": [], "historicalReferenceUsed": []}
                """, score);
    }

    /**
     * 构建 FreeMarker 数据模型（含证据融合详情，M-12：只消费匹配专用 DTO）
     */
    private Map<String, Object> buildDataModel(com.example.matching.dto.matching.MatchingEmployeeProfile emp,
                                                com.example.matching.dto.matching.MatchingPostProfile post,
                                                List<MatchingAbilitySnapshot> abilities,
                                                List<MatchingRequirementSnapshot> requirements,
                                                MatchingRecord record) {
        Map<String, Object> model = new HashMap<>();

        // 岗位信息
        model.put("postName", post.postName());
        model.put("jobDescription", post.jobDescription() != null ? post.jobDescription() : "暂无描述");
        model.put("postLevel", post.postLevel() != null ? post.postLevel() : "暂无");

        // 岗位能力要求
        List<Map<String, Object>> reqList = requirements.stream().map(req -> {
            Map<String, Object> m = new HashMap<>();
            m.put("tagName", req.abilityName() != null ? req.abilityName() : "未知#" + req.tagId());
            m.put("factRef", "fact:POST_ABILITY_MODEL:" + post.postId() + ":" + req.tagId());
            m.put("minRequiredLevel", req.minRequiredLevel());
            m.put("weight", req.weight());
            m.put("isRequired", req.isRequired());
            m.put("isCore", req.isCore());
            return m;
        }).collect(Collectors.toList());
        model.put("postRequirements", reqList);

        // 候选人信息
        model.put("empName", emp.realName());
        model.put("empLevel", emp.level() != null ? emp.level() : "暂无");

        // 候选人能力（含证据融合详情）
        Map<Long, BigDecimal> fusedLevels = matchingAlgorithmService.fuseAbilityLevel(abilities);
        Map<Long, List<MatchingAlgorithmService.EvidenceDetail>> evidenceMap =
                matchingAlgorithmService.generateEvidenceDetail(abilities);

        List<Map<String, Object>> abilityList = abilities.stream()
                .collect(Collectors.groupingBy(MatchingAbilitySnapshot::tagId))
                .entrySet().stream()
                .map(entry -> {
                    Map<String, Object> m = new HashMap<>();
                    Long tagId = entry.getKey();
                    String tagName = entry.getValue().stream()
                            .map(MatchingAbilitySnapshot::abilityName)
                            .filter(Objects::nonNull)
                            .findFirst().orElse("未知#" + tagId);
                    m.put("tagName", tagName);
                    m.put("masteryLevel", entry.getValue().stream()
                            .mapToInt(a -> a.level() != null ? a.level() : 0).max().orElse(0));
                    m.put("fusedLevel", fusedLevels.getOrDefault(tagId, BigDecimal.ZERO));
                    m.put("evaluationSource", entry.getValue().stream()
                            .map(MatchingAbilitySnapshot::sourceType)
                            .filter(Objects::nonNull)
                            .distinct().collect(Collectors.joining(", ")));

                    // 证据详情
                    List<MatchingAlgorithmService.EvidenceDetail> evidences = evidenceMap.getOrDefault(tagId, List.of());
                    List<Map<String, Object>> evidenceDetails = evidences.stream().map(ev -> {
                        Map<String, Object> ed = new HashMap<>();
                        ed.put("source", ev.getSource() != null ? ev.getSource() : "未知");
                        ed.put("level", ev.getMasteryLevel());
                        ed.put("credibility", String.format("%.2f", ev.getCredibility()));
                        ed.put("factRef", "fact:EMP_ABILITY:" + tagId + ":" + (ev.getEvaluationDate() != null ? ev.getEvaluationDate() : "unknown"));
                        return ed;
                    }).collect(Collectors.toList());
                    m.put("evidenceDetails", evidenceDetails);
                    m.put("factRef", "fact:EMP_ABILITY:" + tagId);

                    // 弱证据标记
                    boolean weakEvidence = evidences.stream()
                            .allMatch(e -> "RESUME_PARSE".equals(e.getSource()));
                    m.put("weakEvidence", weakEvidence);

                    return m;
                }).collect(Collectors.toList());
        model.put("empAbilities", abilityList);

        // 量化匹配结果
        model.put("aiMatchScore", record.getAiMatchScore());
        model.put("matchStatus", record.getMatchStatus());
        model.put("l2Score", record.getL2Score());
        model.put("evidenceScore", record.getEvidenceScore());
        model.put("vectorScore", record.getVectorScore());
        model.put("postModelScore", record.getPostModelScore());
        model.put("feedbackCalibration", record.getFeedbackCalibration());
        model.put("matchStatusText", getMatchStatusText(record.getMatchStatus()));

        // 岗位模型质量评分
        try {
            BigDecimal qualityScore = postAbilityModelService.calculateQualityScore(post.postId());
            model.put("modelQualityScore", qualityScore);
        } catch (Exception e) {
            model.put("modelQualityScore", "未知");
        }

        return model;
    }

    private List<MatchingReportDTO.AbilityDetail> buildAbilityDetails(
            List<MatchingAbilitySnapshot> abilities, List<MatchingRequirementSnapshot> requirements) {
        Map<Long, BigDecimal> fusedLevels = matchingAlgorithmService.fuseAbilityLevel(abilities);
        Map<Long, List<MatchingAlgorithmService.EvidenceDetail>> evidenceMap =
                matchingAlgorithmService.generateEvidenceDetail(abilities);

        List<MatchingReportDTO.AbilityDetail> details = new ArrayList<>();
        for (MatchingRequirementSnapshot req : requirements) {
            MatchingReportDTO.AbilityDetail detail = new MatchingReportDTO.AbilityDetail();
            detail.setTagId(req.tagId());
            detail.setTagName(req.abilityName() != null ? req.abilityName() : "未知#" + req.tagId());
            detail.setRequiredLevel(req.minRequiredLevel());
            detail.setActualLevel(fusedLevels.getOrDefault(req.tagId(), BigDecimal.ZERO));
            detail.setIsCore(req.isCore());
            detail.setIsRequired(req.isRequired());

            List<MatchingAlgorithmService.EvidenceDetail> evidences =
                    evidenceMap.getOrDefault(req.tagId(), List.of());
            boolean weakEvidence = !evidences.isEmpty()
                    && evidences.stream().allMatch(e -> "RESUME_PARSE".equals(e.getSource()));
            detail.setWeakEvidence(weakEvidence);

            details.add(detail);
        }
        return details;
    }

    private Map<String, Object> buildHistoricalReference(MatchingRecord record) {
        Map<String, Object> reference = new LinkedHashMap<>();
        List<MatchingRecord> samePostRecords = matchingRecordMapper.selectList(
                Wrappers.<MatchingRecord>lambdaQuery()
                        .eq(MatchingRecord::getPostId, record.getPostId())
                        .ne(record.getId() != null, MatchingRecord::getId, record.getId())
                        .isNotNull(MatchingRecord::getAiMatchScore)
                        .orderByDesc(MatchingRecord::getCreatedTime)
                        .last("LIMIT 10"));
        List<MatchingRecord> sameEmployeeRecords = matchingRecordMapper.selectList(
                Wrappers.<MatchingRecord>lambdaQuery()
                        .eq(MatchingRecord::getEmpId, record.getEmpId())
                        .ne(record.getId() != null, MatchingRecord::getId, record.getId())
                        .isNotNull(MatchingRecord::getAiMatchScore)
                        .orderByDesc(MatchingRecord::getCreatedTime)
                        .last("LIMIT 8"));

        reference.put("samePost", summarizeHistory("samePost", samePostRecords));
        reference.put("sameEmployee", summarizeHistory("sameEmployee", sameEmployeeRecords));
        reference.put("feedbackSummary", feedbackDatasetService.getFeedbackSummary(20));
        return reference;
    }

    private Map<String, Object> summarizeHistory(String scope, List<MatchingRecord> records) {
        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("scope", scope);
        summary.put("count", records.size());
        List<BigDecimal> scores = records.stream()
                .map(item -> item.getFinalMatchScore() != null ? item.getFinalMatchScore() : item.getAiMatchScore())
                .filter(Objects::nonNull)
                .toList();
        if (scores.isEmpty()) {
            summary.put("averageScore", null);
            summary.put("topCases", List.of());
            return summary;
        }
        BigDecimal total = scores.stream().reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal average = total.divide(BigDecimal.valueOf(scores.size()), 2, RoundingMode.HALF_UP);
        summary.put("averageScore", average);
        List<Map<String, Object>> topCases = records.stream().limit(3).map(item -> {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("recordId", item.getId());
            row.put("score", item.getFinalMatchScore() != null ? item.getFinalMatchScore() : item.getAiMatchScore());
            row.put("matchStatus", getMatchStatusText(item.getMatchStatus()));
            row.put("factRef", "fact:MATCHING_RECORD:" + item.getId());
            return row;
        }).collect(Collectors.toList());
        summary.put("topCases", topCases);
        return summary;
    }

    private String getMatchStatusText(Integer status) {
        if (status == null) return "未知";
        return switch (status) {
            case 1 -> "强适配";
            case 2 -> "适配";
            case 3 -> "待观察";
            case 4 -> "不适配";
            default -> "待审核";
        };
    }

    private String buildFallbackJson(MatchingRecord record) {
        return String.format("""
                {"overallScore": %s, "conclusion": "AI服务熔断", "note": "AI暂不可用，当前为量化评分"}
                """, record.getAiMatchScore());
    }

    private String buildFallbackReport(Long matchingRecordId) {
        MatchingRecord record = matchingRecordMapper.selectById(matchingRecordId);
        if (record == null) {
            return buildErrorReport("匹配记录不存在");
        }
        com.example.matching.dto.matching.MatchingEmployeeProfile emp =
                dataQuery.findEmployeeForMatching(record.getEmpId());
        return String.format("""
                {
                  "overallScore": %s,
                  "conclusion": "%s",
                  "note": "AI服务暂不可用，当前为量化算法评分"
                }""",
                record.getAiMatchScore(),
                emp != null ? emp.realName() : "未知"
        );
    }

    private String buildErrorReport(String message) {
        try {
            return objectMapper.writeValueAsString(Map.of("error", message));
        } catch (Exception exception) {
            log.error("Unable to serialize error report", exception);
            return "{\"error\":\"report unavailable\"}";
        }
    }

    private BigDecimal quantitativeScore(Long matchingRecordId) {
        MatchingRecord record = matchingRecordMapper.selectById(matchingRecordId);
        return record != null && record.getL2Score() != null ? record.getL2Score() : BigDecimal.ZERO;
    }
}
