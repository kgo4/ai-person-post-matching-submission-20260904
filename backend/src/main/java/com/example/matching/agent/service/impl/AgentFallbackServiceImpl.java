package com.example.matching.agent.service.impl;

import com.example.matching.agent.dto.*;
import com.example.matching.agent.service.AgentFallbackService;
import com.example.matching.application.agent.AgentScoreBreakdown;
import com.example.matching.application.agent.EmployeeAbilitySnapshot;
import com.example.matching.application.agent.PostRequirementSnapshot;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.*;

/**
 * Agent降级服务实现
 *
 * @author system
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AgentFallbackServiceImpl implements AgentFallbackService {

    private final AgentRunConfidencePolicy confidencePolicy;

    @Value("${agent.fallback.learning-project-url:}")
    private String learningProjectUrl;

    @Override
    public EvidenceGovernanceAgentResult fallbackEvidenceGovernance(EvidenceGovernanceAgentRequest request) {
        EvidenceGovernanceAgentResult result = new EvidenceGovernanceAgentResult();
        result.setFallbackUsed(true);
        result.setRawModelOutput(null);

        if (request == null) {
            result.setDecision("BLOCK");
            result.setRiskLevel("HIGH");
            result.setSupportScore(BigDecimal.ZERO);
            result.setSelfEvidence(false);
            result.setReasons(List.of("Missing governance request"));
            result.setMissingEvidence(List.of("Missing evidence claim"));
            result.setOverallConfidence(confidencePolicy.calculate(result.getSourceRefs(), true));
            return result;
        }

        if (request.getClaimText() == null || request.getClaimText().trim().isEmpty()) {
            result.setDecision("BLOCK");
            result.setRiskLevel("HIGH");
            result.setSupportScore(BigDecimal.ZERO);
            result.setSelfEvidence(false);
            result.setReasons(List.of("声明文本为空"));
            result.setMissingEvidence(List.of("缺少证据文本"));
            result.setOverallConfidence(confidencePolicy.calculate(result.getSourceRefs(), true));
            return result;
        }

        if (request.getSourceRefs() == null || request.getSourceRefs().isEmpty()) {
            result.setDecision("REVIEW");
            result.setRiskLevel("MEDIUM");
            result.setSupportScore(new BigDecimal("30"));
            result.setSelfEvidence(false);
            result.setReasons(List.of("缺少来源引用"));
            result.setMissingEvidence(List.of("需要提供可追溯的来源引用"));
            result.setOverallConfidence(confidencePolicy.calculate(result.getSourceRefs(), true));
            return result;
        }

        boolean onlyAiSources = request.getSourceRefs().stream()
                .allMatch(ref -> ref.startsWith("ai:") || ref.startsWith("generated:"));
        if (onlyAiSources) {
            result.setDecision("BLOCK");
            result.setRiskLevel("HIGH");
            result.setSupportScore(new BigDecimal("20"));
            result.setSelfEvidence(true);
            result.setReasons(List.of("仅包含AI生成的来源引用，无法作为有效证据"));
            result.setMissingEvidence(List.of("需要人工审核或第三方来源的证据"));
            result.setOverallConfidence(confidencePolicy.calculate(result.getSourceRefs(), true));
            return result;
        }

        boolean hasFactOrEvidence = request.getSourceRefs().stream()
                .anyMatch(ref -> ref.startsWith("fact:") || ref.startsWith("evidence:"));
        if (hasFactOrEvidence && request.getEvidenceText() != null && !request.getEvidenceText().trim().isEmpty()) {
            result.setDecision("PASS");
            result.setRiskLevel("LOW");
            result.setSupportScore(new BigDecimal("75"));
            result.setSelfEvidence(false);
            result.setReasons(List.of("包含可追溯的事实或证据来源"));
        } else {
            result.setDecision("REVIEW");
            result.setRiskLevel("MEDIUM");
            result.setSupportScore(new BigDecimal("50"));
            result.setSelfEvidence(false);
            result.setReasons(List.of("来源引用存在但证据文本不完整"));
        }

        result.setOverallConfidence(confidencePolicy.calculate(result.getSourceRefs(), true));
        return result;
    }

    @Override
    public LearningPathAgentResult fallbackLearningPath(AgentContextPackage context) {
        LearningPathAgentResult result = new LearningPathAgentResult();
        result.setFallbackUsed(true);
        result.setRawModelOutput(null);

        List<LearningPathAgentResult.LearningStepSuggestion> steps = new ArrayList<>();
        List<LearningPathAgentResult.ProjectTaskSuggestion> projectTasks = new ArrayList<>();
        List<LearningPathAgentResult.AssessmentSuggestion> assessments = new ArrayList<>();

        List<PostRequirementSnapshot> requirements = context.getPostRequirements();
        List<EmployeeAbilitySnapshot> abilities = context.getEmployeeAbilities();

        if (requirements != null && abilities != null) {
            Map<Long, Integer> empAbilityMap = new HashMap<>();
            for (EmployeeAbilitySnapshot ability : abilities) {
                if (ability.abilityTagId() != null) {
                    empAbilityMap.put(ability.abilityTagId(),
                            ability.currentLevel() != null ? ability.currentLevel() : 0);
                }
            }

            for (PostRequirementSnapshot req : requirements) {
                Long tagId = req.abilityTagId();
                int requiredLevel = req.requiredLevel() != null ? req.requiredLevel() : 3;
                String abilityName = req.abilityName() != null ? req.abilityName() : "未知能力";
                boolean isCore = req.core();

                int currentLevel = empAbilityMap.getOrDefault(tagId, 0);

                if (currentLevel < requiredLevel) {
                    LearningPathAgentResult.LearningStepSuggestion step = new LearningPathAgentResult.LearningStepSuggestion();
                    step.setAbilityTagId(tagId);
                    step.setAbilityName(abilityName);
                    step.setCurrentLevel(currentLevel);
                    step.setTargetLevel(requiredLevel);
                    step.setPriority(isCore ? "HIGH" : (requiredLevel - currentLevel >= 2 ? "HIGH" : "MEDIUM"));
                    step.setTitle("提升「" + abilityName + "」从 L" + currentLevel + " 到 L" + requiredLevel);
                    step.setDescription("通过学习和实践，系统提升" + abilityName + "能力至目标等级");
                    step.setEstimatedHours(Math.max(8, (requiredLevel - currentLevel) * 16));
                    steps.add(step);

                    LearningPathAgentResult.ProjectTaskSuggestion task = new LearningPathAgentResult.ProjectTaskSuggestion();
                    task.setAbilityTagId(tagId);
                    task.setAbilityName(abilityName);
                    task.setTitle("基于「" + abilityName + "」的岗位能力改造实践");
                    task.setProjectName("Open-source learning project");
                    if (learningProjectUrl != null && !learningProjectUrl.isBlank()) {
                        task.setProjectUrl(learningProjectUrl);
                    }
                    task.setRequirements("1. 明确" + abilityName + "在目标岗位中的使用场景\n2. 在当前系统中补充一个可运行或可说明的改造点\n3. 输出实现说明、关键截图或仓库链接");
                    task.setAcceptanceCriteria("1. 成果能解释其支持的能力标签\n2. 至少包含一条可追溯材料链接或说明");
                    task.setExpectedOutput("Git repository URL, implementation note, screenshots or report");
                    task.setDifficulty(isCore ? "HARD" : "MEDIUM");
                    projectTasks.add(task);

                    LearningPathAgentResult.AssessmentSuggestion assessment = new LearningPathAgentResult.AssessmentSuggestion();
                    assessment.setAbilityTagId(tagId);
                    assessment.setAbilityName(abilityName);
                    assessment.setQuestionType("INTERVIEW");
                    assessment.setQuestionText("请结合目标岗位，说明你如何在项目中应用「" + abilityName + "」，并给出一个可验证的成果证据。");
                    assessment.setReferenceAnswer("回答应包含业务场景、技术方案、关键实现、验证方式和证据链接。");
                    assessment.setDifficulty(isCore ? "HARD" : "MEDIUM");
                    assessments.add(assessment);
                }
            }
        }

        result.setSummary("基于匹配差距生成的学习路径建议，共" + steps.size() + "个步骤");
        result.setSteps(steps);
        result.setProjectTasks(projectTasks);
        result.setAssessments(assessments);
        result.setSourceRefs(context.getSourceRefs());
        result.setOverallConfidence(confidencePolicy.calculate(context.getSourceRefs(), true));

        return result;
    }

    @Override
    public MatchingAnalysisAgentResult fallbackMatchingAnalysis(AgentContextPackage context) {
        MatchingAnalysisAgentResult result = new MatchingAnalysisAgentResult();
        result.setFallbackUsed(true);
        result.setRawModelOutput(null);

        List<String> strengths = new ArrayList<>();
        List<String> gaps = new ArrayList<>();
        List<String> riskSignals = new ArrayList<>();
        List<String> humanAttentionPoints = new ArrayList<>();
        List<String> suggestions = new ArrayList<>();
        List<Map<String, Object>> scoreReasons = new ArrayList<>();
        List<Map<String, Object>> evidenceAnalysis = new ArrayList<>();

        List<PostRequirementSnapshot> requirements = safeList(context.getPostRequirements());
        List<EmployeeAbilitySnapshot> abilities = safeList(context.getEmployeeAbilities());
        List<String> factRefs = sourceRefs(context);

        if (!requirements.isEmpty()) {
            Map<Long, Integer> empAbilityMap = new HashMap<>();
            Map<String, EmployeeAbilitySnapshot> empAbilityByName = new HashMap<>();
            for (EmployeeAbilitySnapshot ability : abilities) {
                if (ability.abilityTagId() != null) {
                    empAbilityMap.put(ability.abilityTagId(),
                            ability.currentLevel() != null ? ability.currentLevel() : 0);
                }
                if (ability.abilityName() != null && !ability.abilityName().isBlank()) {
                    empAbilityByName.put(normalizeAbilityName(ability.abilityName()), ability);
                }
            }

            for (PostRequirementSnapshot req : requirements) {
                Long tagId = req.abilityTagId();
                int requiredLevel = req.requiredLevel() != null ? req.requiredLevel() : 3;
                String abilityName = req.abilityName() != null ? req.abilityName() : "未知能力";
                boolean isCore = req.core();

                EmployeeAbilitySnapshot matchedAbility = tagId != null ? null : empAbilityByName.get(normalizeAbilityName(abilityName));
                int currentLevel = tagId != null
                        ? empAbilityMap.getOrDefault(tagId, 0)
                        : matchedAbility != null && matchedAbility.currentLevel() != null ? matchedAbility.currentLevel() : 0;

                if (currentLevel >= requiredLevel) {
                    strengths.add(abilityName + "达到要求等级 L" + requiredLevel);
                } else {
                    gaps.add(abilityName + "差距 " + (requiredLevel - currentLevel) + " 级 (L" + currentLevel + " -> L" + requiredLevel + ")");
                    suggestions.add("优先围绕「" + abilityName + "」补齐 L" + currentLevel + " 至 L" + requiredLevel
                            + " 的实践证据，并在完成后重新核验。");
                    if (isCore) {
                        riskSignals.add("核心能力「" + abilityName + "」存在较大差距");
                        humanAttentionPoints.add("需要重点关注「" + abilityName + "」的提升");
                    }
                }

                Map<String, Object> evidence = new LinkedHashMap<>();
                evidence.put("ability", abilityName);
                evidence.put("confidence", evidenceConfidence(matchedAbility, tagId, abilities));
                evidence.put("fusedLevel", currentLevel > 0 ? "L" + currentLevel : "未核验");
                evidence.put("sources", factRefs);
                evidence.put("conflict", currentLevel >= requiredLevel ? null
                        : "岗位要求 L" + requiredLevel + "，当前可用能力等级为 L" + currentLevel);
                evidenceAnalysis.add(evidence);
            }
        }

        for (AgentScoreBreakdown breakdown : safeList(context.getScoreBreakdown())) {
            Map<String, Object> reason = new LinkedHashMap<>();
            reason.put("factor", breakdown.dimension());
            reason.put("direction", breakdown.score() != null && breakdown.score().compareTo(new BigDecimal("60")) >= 0 ? "+" : "-");
            reason.put("impact", breakdown.weight());
            reason.put("reason", breakdown.description() != null && !breakdown.description().isBlank()
                    ? breakdown.description()
                    : "该维度由服务端匹配模型根据已加载的人员能力与岗位要求计算。");
            reason.put("factRefs", factRefs);
            scoreReasons.add(reason);
        }

        BigDecimal matchScore = context.getMatchScore();
        String conclusion;
        if (matchScore != null) {
            if (matchScore.compareTo(new BigDecimal("80")) >= 0) {
                conclusion = "匹配度较高，员工能力与岗位要求基本符合";
            } else if (matchScore.compareTo(new BigDecimal("60")) >= 0) {
                conclusion = "匹配度中等，部分能力需要提升";
            } else {
                conclusion = "匹配度较低，存在较多能力差距需要弥补";
            }
        } else {
            conclusion = "无法计算匹配分数";
        }

        result.setSuggestedLlmScore(matchScore);
        result.setConclusion(conclusion);
        result.setStrengths(strengths);
        result.setGaps(gaps);
        result.setRiskSignals(riskSignals);
        result.setHumanAttentionPoints(humanAttentionPoints);
        result.setSuggestions(suggestions.stream().limit(5).toList());
        result.setScoreReasons(scoreReasons);
        result.setEvidenceAnalysis(evidenceAnalysis);
        result.setDimensionScores(safeList(context.getScoreBreakdown()).stream().map(this::toDimensionScore).toList());
        result.setSourceRefs(context.getSourceRefs());
        result.setOverallConfidence(confidencePolicy.calculate(context.getSourceRefs(), true));

        return result;
    }

    private Map<String, Object> toDimensionScore(AgentScoreBreakdown breakdown) {
        Map<String, Object> score = new LinkedHashMap<>();
        score.put("dimension", breakdown.dimension());
        score.put("score", breakdown.score());
        score.put("weight", breakdown.weight());
        return score;
    }

    private List<String> sourceRefs(AgentContextPackage context) {
        return safeList(context.getSourceRefs()).stream()
                .map(ref -> ref.getRef() != null && !ref.getRef().isBlank() ? ref.getRef() : ref.getRefId())
                .filter(Objects::nonNull)
                .filter(ref -> !ref.isBlank())
                .limit(8)
                .toList();
    }

    private String evidenceConfidence(EmployeeAbilitySnapshot matchedAbility, Long tagId,
                                      List<EmployeeAbilitySnapshot> abilities) {
        EmployeeAbilitySnapshot ability = matchedAbility;
        if (ability == null && tagId != null) {
            ability = abilities.stream().filter(item -> tagId.equals(item.abilityTagId())).findFirst().orElse(null);
        }
        if (ability == null || ability.evidenceCount() <= 0) {
            return "低";
        }
        if (ability.credibility() != null && ability.credibility().compareTo(new BigDecimal("75")) >= 0) {
            return "高";
        }
        return "中";
    }

    private String normalizeAbilityName(String abilityName) {
        return abilityName.trim().replaceAll("\\s+", "").toLowerCase(Locale.ROOT);
    }

    private <T> List<T> safeList(List<T> values) {
        return values == null ? List.of() : values;
    }
}
