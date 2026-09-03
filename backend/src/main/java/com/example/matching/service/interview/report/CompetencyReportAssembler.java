package com.example.matching.service.interview.report;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.example.matching.agent.dto.interview.InterviewReportDTO;
import com.example.matching.agent.service.impl.AgentOutputValidator;
import com.example.matching.common.exception.BusinessException;
import com.example.matching.entity.employee.EmpVideoInterviewSession;
import com.example.matching.entity.employee.EmpVideoInterviewEvidence;
import com.example.matching.entity.interview.InterviewAbilityObservation;
import com.example.matching.entity.post.PostAbilityModel;
import com.example.matching.mapper.employee.EmpVideoInterviewSessionMapper;
import com.example.matching.mapper.employee.EmpVideoInterviewEvidenceMapper;
import com.example.matching.mapper.interview.InterviewAbilityObservationMapper;
import com.example.matching.mapper.post.PostAbilityModelMapper;
import com.example.matching.dto.interview.AbilityRadarItem;
import com.example.matching.dto.interview.CompetencyReport;
import com.example.matching.dto.interview.LearningPathSuggestion;
import com.example.matching.agent.lc4j.InterviewReportAiService;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.*;

@Slf4j
@Service
@RequiredArgsConstructor
public class CompetencyReportAssembler {

    private final EmpVideoInterviewSessionMapper sessionMapper;
    private final InterviewAbilityObservationMapper observationMapper;
    private final EmpVideoInterviewEvidenceMapper evidenceMapper;
    private final PostAbilityModelMapper postAbilityModelMapper;
    private final ObjectMapper objectMapper;
    private final ObjectProvider<InterviewReportAiService> interviewReportAiServiceProvider;
    private final AgentOutputValidator agentOutputValidator;
    private final com.example.matching.infrastructure.llm.LlmResponseParser llmResponseParser;

    private static final String HARNESS_PASS = "PASS";
    private static final String HARNESS_BLOCK = "BLOCK";
    private static final String HARNESS_REVIEW = "REVIEW";

    public CompetencyReport generateCompetencyReport(Long sessionId) {
        log.info("生成胜任力评估报告，sessionId={}", sessionId);

        EmpVideoInterviewSession session = sessionMapper.selectById(sessionId);
        if (session == null) {
            throw new BusinessException(404, "面试会话不存在");
        }

        List<InterviewAbilityObservation> observations = observationMapper.selectList(
                Wrappers.<InterviewAbilityObservation>lambdaQuery()
                        .eq(InterviewAbilityObservation::getSessionId, sessionId)
        );

        Map<Long, PostAbilityModel> abilityModelMap = new HashMap<>();
        if (session.getPostId() != null) {
            List<PostAbilityModel> models = postAbilityModelMapper.selectList(
                    Wrappers.<PostAbilityModel>lambdaQuery()
                            .eq(PostAbilityModel::getPostId, session.getPostId())
            );
            for (PostAbilityModel model : models) {
                abilityModelMap.put(model.getTagId(), model);
            }
        }

        int answerEvidenceScore = calculateOverallScore(observations);
        int overallScore = calculateOverallScoreWithVisualEvidence(sessionId, answerEvidenceScore);

        int postMatchScore = calculatePostMatchScore(observations, abilityModelMap);

        List<AbilityRadarItem> radarItems = buildRadarItems(observations, abilityModelMap);

        if (observations.isEmpty()) {
            return buildRuleBasedReport(session, observations, abilityModelMap, radarItems,
                    overallScore, postMatchScore);
        }

        InterviewReportAiService reportAiService = interviewReportAiServiceProvider.getIfAvailable();
        if (reportAiService != null) {
            try {
                String context = buildInterviewReportContext(session, observations, radarItems);
                InterviewReportDTO reportDto = com.example.matching.agent.config.AgentToolProvider
                        .withScope(() -> reportAiService.generateReport(sessionId, context));
                agentOutputValidator.validateOrThrow(reportDto, "INTERVIEW_REPORT");
                CompetencyReport aiReport = mergeAiReportFromDto(session, observations, radarItems,
                        reportDto, overallScore, postMatchScore);
                if (aiReport != null) {
                    log.info("LangChain4j 报告文本生成完成，sessionId={}", sessionId);
                    return aiReport;
                }
            } catch (Exception e) {
                log.warn("LangChain4j 报告生成失败，回退到规则生成: {}", e.getMessage());
            }
        }

        return buildRuleBasedReport(session, observations, abilityModelMap, radarItems,
                overallScore, postMatchScore);
    }

    private CompetencyReport buildRuleBasedReport(EmpVideoInterviewSession session,
                                                   List<InterviewAbilityObservation> observations,
                                                   Map<Long, PostAbilityModel> abilityModelMap,
                                                   List<AbilityRadarItem> radarItems,
                                                   int overallScore,
                                                   int postMatchScore) {
        List<String> strengths = buildStrengths(observations, abilityModelMap);
        List<String> weaknesses = buildWeaknesses(observations, abilityModelMap);
        List<String> riskSignals = buildOverallRiskSignals(observations);
        List<String> improvementSuggestions = buildImprovementSuggestions(observations, abilityModelMap);
        List<LearningPathSuggestion> learningPathSuggestions = buildLearningPathSuggestions(observations, abilityModelMap);
        String conclusion = generateConclusion(observations, overallScore, postMatchScore);
        String recommendation = generateRecommendation(observations, riskSignals, strengths, weaknesses);

        return new CompetencyReport(
                session.getId(),
                session.getEmpId(),
                session.getPostId(),
                overallScore,
                postMatchScore,
                radarItems,
                observations,
                strengths,
                weaknesses,
                riskSignals,
                improvementSuggestions,
                learningPathSuggestions,
                conclusion,
                recommendation,
                observations.isEmpty(),
                observations.isEmpty() ? "规则兜底无法派生任何能力观察：会话无有效题目关联（tagId+回答）" : null
        );
    }

    private String buildInterviewReportContext(EmpVideoInterviewSession session,
                                                List<InterviewAbilityObservation> observations,
                                                List<AbilityRadarItem> radarItems) {
        Map<String, Object> context = new LinkedHashMap<>();
        context.put("sessionId", session.getId());
        context.put("empId", session.getEmpId());
        context.put("postId", session.getPostId());
        context.put("observations", observations);
        context.put("radarItems", radarItems);
        context.put("rules", List.of(
                "Use only verified observations and post requirements.",
                "Do not change scores unless explicitly provided.",
                "Every recommendation must be supported by observation evidence."
        ));
        try {
            return objectMapper.writeValueAsString(context);
        } catch (Exception e) {
            return String.valueOf(context);
        }
    }

    private CompetencyReport mergeAiReportText(EmpVideoInterviewSession session,
                                                List<InterviewAbilityObservation> observations,
                                                List<AbilityRadarItem> radarItems,
                                                String aiResponse,
                                                int overallScore,
                                                int postMatchScore) {
        try {
            if (aiResponse == null || aiResponse.isBlank()) {
                aiResponse = "{}";
            }
            String json = llmResponseParser.extractJson(aiResponse);
            Map<String, Object> map = objectMapper.readValue(json, new TypeReference<>() {});

            List<String> strengths = parseStringList(map.get("strengths"));
            List<String> weaknesses = parseStringList(map.get("weaknesses"));
            List<String> riskSignals = parseStringList(map.get("riskSignals"));
            List<String> improvementSuggestions = parseStringList(map.get("improvementSuggestions"));
            String conclusion = (String) map.get("conclusion");
            String recommendation = (String) map.get("recommendation");

            List<LearningPathSuggestion> learningPathSuggestions = new ArrayList<>();
            List<Map<String, Object>> lpsList = (List<Map<String, Object>>) map.get("learningPathSuggestions");
            if (lpsList != null) {
                for (Map<String, Object> lps : lpsList) {
                    learningPathSuggestions.add(new LearningPathSuggestion(
                            lps.get("tagId") != null ? ((Number) lps.get("tagId")).longValue() : null,
                            (String) lps.get("abilityName"),
                            lps.get("currentLevel") != null ? ((Number) lps.get("currentLevel")).intValue() : null,
                            lps.get("targetLevel") != null ? ((Number) lps.get("targetLevel")).intValue() : null,
                            (String) lps.get("suggestion"),
                            (String) lps.get("priority")
                    ));
                }
            }

            return new CompetencyReport(
                    session.getId(),
                    session.getEmpId(),
                    session.getPostId(),
                    overallScore,
                    postMatchScore,
                    radarItems,
                    observations,
                    strengths,
                    weaknesses,
                    riskSignals,
                    improvementSuggestions,
                    learningPathSuggestions,
                    conclusion != null ? conclusion : generateConclusion(observations, overallScore, postMatchScore),
                    recommendation != null ? recommendation : generateRecommendation(observations, riskSignals, strengths, weaknesses),
                    observations.isEmpty(),
                    observations.isEmpty() ? "规则兜底无法派生任何能力观察：会话无有效题目关联（tagId+回答）" : null
            );
        } catch (Exception e) {
            log.warn("解析 LangChain4j 报告失败: {}", e.getMessage());
            return null;
        }
    }

    private int calculateOverallScore(List<InterviewAbilityObservation> observations) {
        if (observations.isEmpty()) {
            return 0;
        }

        BigDecimal totalScore = BigDecimal.ZERO;
        int count = 0;

        for (InterviewAbilityObservation observation : observations) {
            // This report is generated before aggregate Harness runs. A missing
            // decision means "pending review", not a zero-score answer. Only an
            // explicit BLOCK excludes an observation from its evidence score.
            if (!HARNESS_BLOCK.equals(observation.getHarnessDecision())
                    && observation.getObservedLevel() != null) {
                int score = observation.getObservedLevel() * 20;
                totalScore = totalScore.add(BigDecimal.valueOf(score));
                count++;
            }
        }

        if (count == 0) {
            return 0;
        }

        return totalScore.divide(BigDecimal.valueOf(count), 0, RoundingMode.HALF_UP).intValue();
    }

    /**
     * Visual analysis is presentation-only. It never affects an ability observation, radar item,
     * Harness evidence, or formal employee ability. Missing/invalid visual analysis is neutral.
     */
    private int calculateOverallScoreWithVisualEvidence(Long sessionId, int answerEvidenceScore) {
        List<EmpVideoInterviewEvidence> evidences = evidenceMapper.selectList(
                Wrappers.<EmpVideoInterviewEvidence>lambdaQuery()
                        .eq(EmpVideoInterviewEvidence::getSessionId, sessionId)
                        .eq(EmpVideoInterviewEvidence::getEvidenceType, "VISUAL"));
        Map<Long, BigDecimal> visualScoreByQuestion = new LinkedHashMap<>();
        for (EmpVideoInterviewEvidence evidence : evidences) {
            if (evidence.getQuestionId() == null || evidence.getRawScore() == null
                    || evidence.getConfidenceScore() == null
                    || evidence.getConfidenceScore().compareTo(BigDecimal.valueOf(0.8)) < 0) {
                continue;
            }
            BigDecimal score = evidence.getRawScore().max(BigDecimal.ZERO).min(BigDecimal.valueOf(100));
            visualScoreByQuestion.merge(evidence.getQuestionId(), score,
                    (left, right) -> left.add(right).divide(BigDecimal.valueOf(2), 4, RoundingMode.HALF_UP));
        }
        if (visualScoreByQuestion.isEmpty()) {
            return answerEvidenceScore;
        }
        BigDecimal visualScore = visualScoreByQuestion.values().stream()
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .divide(BigDecimal.valueOf(visualScoreByQuestion.size()), 4, RoundingMode.HALF_UP);
        return BigDecimal.valueOf(answerEvidenceScore).multiply(BigDecimal.valueOf(0.90))
                .add(visualScore.multiply(BigDecimal.valueOf(0.10)))
                .setScale(0, RoundingMode.HALF_UP)
                .intValue();
    }

    private int calculatePostMatchScore(List<InterviewAbilityObservation> observations, Map<Long, PostAbilityModel> abilityModelMap) {
        if (abilityModelMap.isEmpty() || observations.isEmpty()) {
            return 0;
        }

        BigDecimal totalWeight = BigDecimal.ZERO;
        BigDecimal weightedScore = BigDecimal.ZERO;

        for (InterviewAbilityObservation obs : observations) {
            if (!HARNESS_PASS.equals(obs.getHarnessDecision()) || obs.getTagId() == null) {
                continue;
            }
            PostAbilityModel model = abilityModelMap.get(obs.getTagId());
            if (model == null) continue;

            BigDecimal weight = model.getWeight() != null ? model.getWeight() : BigDecimal.ONE;
            int requiredLevel = model.getMinRequiredLevel() != null && model.getMinRequiredLevel() > 0
                    ? model.getMinRequiredLevel() : 3;
            int observedLevel = obs.getObservedLevel() != null ? obs.getObservedLevel() : 0;

            BigDecimal matchRatio = BigDecimal.valueOf(Math.min((double) observedLevel / requiredLevel, 1.0));
            weightedScore = weightedScore.add(matchRatio.multiply(weight).multiply(BigDecimal.valueOf(100)));
            totalWeight = totalWeight.add(weight);
        }

        if (totalWeight.compareTo(BigDecimal.ZERO) == 0) {
            return 0;
        }

        return weightedScore.divide(totalWeight, 0, RoundingMode.HALF_UP).intValue();
    }

    private List<AbilityRadarItem> buildRadarItems(List<InterviewAbilityObservation> observations, Map<Long, PostAbilityModel> abilityModelMap) {
        List<AbilityRadarItem> items = new ArrayList<>();

        for (InterviewAbilityObservation obs : observations) {
            if (obs.getTagId() == null) continue;

            PostAbilityModel model = abilityModelMap.get(obs.getTagId());
            Integer requiredLevel = model != null ? model.getMinRequiredLevel() : null;

            items.add(new AbilityRadarItem(
                    obs.getTagId(),
                    obs.getAbilityName(),
                    obs.getObservedLevel(),
                    requiredLevel,
                    obs.getConfidenceScore(),
                    obs.getHarnessDecision(),
                    obs.getObservedLevel() != null ? obs.getObservedLevel() * 20 : 0
            ));
        }

        return items;
    }

    private List<String> buildStrengths(List<InterviewAbilityObservation> observations, Map<Long, PostAbilityModel> abilityModelMap) {
        List<String> strengths = new ArrayList<>();

        for (InterviewAbilityObservation obs : observations) {
            if (!HARNESS_PASS.equals(obs.getHarnessDecision()) || obs.getTagId() == null) continue;

            PostAbilityModel model = abilityModelMap.get(obs.getTagId());
            int requiredLevel = model != null && model.getMinRequiredLevel() != null ? model.getMinRequiredLevel() : 3;
            int observedLevel = obs.getObservedLevel() != null ? obs.getObservedLevel() : 0;

            if (observedLevel >= requiredLevel) {
                strengths.add(String.format("%s：观察等级 %d 级，达到岗位要求 %d 级", obs.getAbilityName(), observedLevel, requiredLevel));
            }
        }

        return strengths;
    }

    private List<String> buildWeaknesses(List<InterviewAbilityObservation> observations, Map<Long, PostAbilityModel> abilityModelMap) {
        List<String> weaknesses = new ArrayList<>();

        for (InterviewAbilityObservation obs : observations) {
            if (obs.getTagId() == null) continue;

            if (!HARNESS_PASS.equals(obs.getHarnessDecision())) {
                weaknesses.add(String.format("%s：Harness 决策为 %s，未能通过验证", obs.getAbilityName(), obs.getHarnessDecision()));
                continue;
            }

            PostAbilityModel model = abilityModelMap.get(obs.getTagId());
            int requiredLevel = model != null && model.getMinRequiredLevel() != null ? model.getMinRequiredLevel() : 3;
            int observedLevel = obs.getObservedLevel() != null ? obs.getObservedLevel() : 0;

            if (observedLevel < requiredLevel) {
                weaknesses.add(String.format("%s：观察等级 %d 级，低于岗位要求 %d 级", obs.getAbilityName(), observedLevel, requiredLevel));
            }
        }

        return weaknesses;
    }

    private List<String> buildOverallRiskSignals(List<InterviewAbilityObservation> observations) {
        List<String> risks = new ArrayList<>();

        long blockCount = observations.stream().filter(o -> HARNESS_BLOCK.equals(o.getHarnessDecision())).count();
        long reviewCount = observations.stream().filter(o -> HARNESS_REVIEW.equals(o.getHarnessDecision())).count();

        if (blockCount > 0) {
            risks.add("存在 " + blockCount + " 项能力被Harness拦截");
        }

        if (reviewCount > 0) {
            risks.add("存在 " + reviewCount + " 项能力需要人工审核");
        }

        long lowConfidenceCount = observations.stream()
                .filter(o -> o.getConfidenceScore() != null && o.getConfidenceScore().compareTo(BigDecimal.valueOf(50)) < 0)
                .count();
        if (lowConfidenceCount > 0) {
            risks.add("存在 " + lowConfidenceCount + " 项低置信度能力观察");
        }

        return risks;
    }

    private List<String> buildImprovementSuggestions(List<InterviewAbilityObservation> observations, Map<Long, PostAbilityModel> abilityModelMap) {
        List<String> suggestions = new ArrayList<>();

        for (InterviewAbilityObservation obs : observations) {
            if (!HARNESS_PASS.equals(obs.getHarnessDecision()) || obs.getTagId() == null) continue;

            PostAbilityModel model = abilityModelMap.get(obs.getTagId());
            if (model == null) continue;

            int requiredLevel = model.getMinRequiredLevel() != null ? model.getMinRequiredLevel() : 3;
            int observedLevel = obs.getObservedLevel() != null ? obs.getObservedLevel() : 0;

            if (observedLevel < requiredLevel) {
                suggestions.add(String.format("建议将「%s」从 %d 级提升到 %d 级，以满足岗位要求",
                        obs.getAbilityName(), observedLevel, requiredLevel));
            }
        }

        return suggestions;
    }

    private List<LearningPathSuggestion> buildLearningPathSuggestions(List<InterviewAbilityObservation> observations, Map<Long, PostAbilityModel> abilityModelMap) {
        List<LearningPathSuggestion> suggestions = new ArrayList<>();

        for (InterviewAbilityObservation obs : observations) {
            if (!HARNESS_PASS.equals(obs.getHarnessDecision()) || obs.getTagId() == null) continue;

            PostAbilityModel model = abilityModelMap.get(obs.getTagId());
            if (model == null) continue;

            int requiredLevel = model.getMinRequiredLevel() != null ? model.getMinRequiredLevel() : 3;
            int observedLevel = obs.getObservedLevel() != null ? obs.getObservedLevel() : 0;
            int gap = requiredLevel - observedLevel;

            if (gap > 0) {
                String priority = gap >= 2 ? "HIGH" : "MEDIUM";
                String suggestion = String.format("通过项目实践和系统学习提升「%s」能力，当前 %d 级，目标 %d 级",
                        obs.getAbilityName(), observedLevel, requiredLevel);

                suggestions.add(new LearningPathSuggestion(
                        obs.getTagId(),
                        obs.getAbilityName(),
                        observedLevel,
                        requiredLevel,
                        suggestion,
                        priority
                ));
            }
        }

        return suggestions;
    }

    private String generateConclusion(List<InterviewAbilityObservation> observations, int overallScore, int postMatchScore) {
        if (observations.isEmpty()) {
            return "本次面试未提取到有效的能力观察";
        }

        long passCount = observations.stream().filter(o -> HARNESS_PASS.equals(o.getHarnessDecision())).count();

        if (overallScore >= 80) {
            return String.format("候选人整体表现优秀（综合%d分），%d项能力通过验证", overallScore, passCount);
        } else if (overallScore >= 60) {
            return String.format("候选人整体表现良好（综合%d分），%d项能力通过验证", overallScore, passCount);
        } else {
            return String.format("候选人整体表现一般（综合%d分），%d项能力通过验证", overallScore, passCount);
        }
    }

    private String generateRecommendation(List<InterviewAbilityObservation> observations, List<String> riskSignals, List<String> strengths, List<String> weaknesses) {
        StringBuilder sb = new StringBuilder();

        if (!strengths.isEmpty()) {
            sb.append("优势能力：\n");
            for (String s : strengths) {
                sb.append("- ").append(s).append("\n");
            }
        }

        if (!weaknesses.isEmpty()) {
            sb.append("待提升能力：\n");
            for (String w : weaknesses) {
                sb.append("- ").append(w).append("\n");
            }
        }

        if (riskSignals.isEmpty() && weaknesses.isEmpty()) {
            sb.append("建议进入下一轮面试或录用流程");
        } else {
            if (!riskSignals.isEmpty()) {
                sb.append("风险提示：\n");
                for (String risk : riskSignals) {
                    sb.append("- ").append(risk).append("\n");
                }
            }
            sb.append("建议人工复核后再做决定");
        }

        return sb.toString();
    }

    private List<String> parseStringList(Object obj) {
        if (obj instanceof List<?> list) {
            return list.stream().map(Object::toString).toList();
        }
        return List.of();
    }

    private CompetencyReport mergeAiReportFromDto(EmpVideoInterviewSession session,
                                                    List<InterviewAbilityObservation> observations,
                                                    List<AbilityRadarItem> radarItems,
                                                    InterviewReportDTO dto,
                                                    int overallScore,
                                                    int postMatchScore) {
        try {
            List<LearningPathSuggestion> learningPathSuggestions = new ArrayList<>();
            if (dto.getLearningPathSuggestions() != null) {
                for (InterviewReportDTO.LearningPathSuggestion lps : dto.getLearningPathSuggestions()) {
                    learningPathSuggestions.add(new LearningPathSuggestion(
                            lps.getTagId(),
                            lps.getAbilityName(),
                            lps.getCurrentLevel(),
                            lps.getTargetLevel(),
                            lps.getSuggestion(),
                            lps.getPriority()
                    ));
                }
            }

            return new CompetencyReport(
                    session.getId(),
                    session.getEmpId(),
                    session.getPostId(),
                    overallScore,
                    postMatchScore,
                    radarItems,
                    observations,
                    dto.getStrengths() != null ? dto.getStrengths() : buildStrengths(observations, new HashMap<>()),
                    dto.getWeaknesses() != null ? dto.getWeaknesses() : buildWeaknesses(observations, new HashMap<>()),
                    dto.getRiskSignals() != null ? dto.getRiskSignals() : List.of(),
                    dto.getImprovementSuggestions() != null ? dto.getImprovementSuggestions() : buildImprovementSuggestions(observations, new HashMap<>()),
                    learningPathSuggestions,
                    dto.getConclusion() != null ? dto.getConclusion() : generateConclusion(observations, overallScore, postMatchScore),
                    dto.getRecommendation() != null ? dto.getRecommendation() : generateRecommendation(observations, List.of(), List.of(), List.of()),
                    observations.isEmpty(),
                    observations.isEmpty() ? "规则兜底无法派生任何能力观察：会话无有效题目关联（tagId+回答）" : null
            );
        } catch (Exception e) {
            log.warn("处理 LangChain4j 报告DTO失败: {}", e.getMessage());
            return null;
        }
    }
}
