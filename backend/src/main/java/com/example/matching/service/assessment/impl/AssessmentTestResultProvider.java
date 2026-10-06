package com.example.matching.service.assessment.impl;

import com.example.matching.entity.ability.PersonAbilityClaim;
import com.example.matching.entity.employee.EmpAiTest;
import com.example.matching.service.assessment.AbilityEvidenceCollectionService;
import com.example.matching.service.employee.AiTestService;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 评估测试结果摘要提供者。
 * <p>
 * 产出裁剪后的「每能力测试等级 + 简历声称等级 + 整体薄弱题」，供 AI 面试的计划生成
 * 与观察分析两处注入，实现"面试结合测试结果交叉核验"而不全量塞入测试 JSON。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AssessmentTestResultProvider {

    private static final int MAX_WEAK_QUESTIONS = 3;
    private static final int WEAK_QUESTION_MAX_LEN = 80;

    private final AbilityEvidenceCollectionService evidenceCollectionService;
    private final AiTestService aiTestService;
    private final ObjectMapper objectMapper;

    public record TestResultSummary(
            List<AbilityResult> abilities, Integer overallTestLevel, List<String> weakQuestions) {
    }

    public record AbilityResult(String abilityName, Integer testLevel, Integer resumeClaimedLevel) {
    }

    public TestResultSummary buildSummary(Long workflowId) {
        if (workflowId == null) {
            return empty();
        }
        try {
            // 1. 测试证据（归并后的每能力等级）
            List<PersonAbilityClaim> allClaims = evidenceCollectionService.listClaimsByWorkflow(workflowId);
            List<PersonAbilityClaim> testClaims = allClaims.stream()
                    .filter(c -> "AI_TEST".equals(c.getSourceType()) && "ACTIVE".equals(c.getStatus()))
                    .toList();
            // 2. 简历证据（用于 resumeClaimedLevel 对比），按 claimGroupId 取最高等级
            List<PersonAbilityClaim> resumeClaims = allClaims.stream()
                    .filter(c -> "RESUME_PARSE".equals(c.getSourceType()) && "ACTIVE".equals(c.getStatus()))
                    .toList();
            Map<Long, Integer> resumeLevelByGroup = new HashMap<>();
            for (PersonAbilityClaim c : resumeClaims) {
                if (c.getClaimGroupId() != null && c.getClaimedLevel() != null) {
                    resumeLevelByGroup.merge(c.getClaimGroupId(), c.getClaimedLevel(), Math::max);
                }
            }

            // 3. 测试记录（整体等级 + 薄弱题）
            EmpAiTest test = aiTestService.getLatestByWorkflowId(workflowId);
            Integer overall = test != null ? test.getMasteryLevel() : null;
            List<String> weakQuestions = test != null ? parseWeakQuestions(test) : List.of();

            List<AbilityResult> abilities = new ArrayList<>();
            for (PersonAbilityClaim c : testClaims) {
                String name = c.getNormalizedAbilityName() != null
                        ? c.getNormalizedAbilityName() : c.getAbilityName();
                Integer resumeLevel = c.getClaimGroupId() != null
                        ? resumeLevelByGroup.get(c.getClaimGroupId()) : null;
                abilities.add(new AbilityResult(name, c.getClaimedLevel(), resumeLevel));
            }
            if (abilities.isEmpty() && overall == null && weakQuestions.isEmpty()) {
                return empty();
            }
            return new TestResultSummary(abilities, overall, weakQuestions);
        } catch (Exception e) {
            log.warn("构建测试结果摘要失败，返回空摘要: workflowId={}, error={}", workflowId, e.getMessage());
            return empty();
        }
    }

    private TestResultSummary empty() {
        return new TestResultSummary(List.of(), null, List.of());
    }

    private List<String> parseWeakQuestions(EmpAiTest test) {
        try {
            List<Map<String, Object>> questions = parseQuestionList(test.getQuestions());
            Map<Integer, Boolean> correctByIndex = parseCorrectByIndex(test.getAiEvaluation());
            List<String> weak = new ArrayList<>();
            for (Map.Entry<Integer, Boolean> e : correctByIndex.entrySet()) {
                if (Boolean.TRUE.equals(e.getValue())) {
                    continue; // 只保留答错题
                }
                int idx = e.getKey();
                if (idx >= 0 && idx < questions.size()) {
                    String text = String.valueOf(questions.get(idx).getOrDefault("question", ""));
                    if (!text.isBlank() && !"null".equals(text)) {
                        weak.add(text.length() > WEAK_QUESTION_MAX_LEN
                                ? text.substring(0, WEAK_QUESTION_MAX_LEN) + "..." : text);
                    }
                }
                if (weak.size() >= MAX_WEAK_QUESTIONS) {
                    break;
                }
            }
            return weak;
        } catch (Exception e) {
            log.warn("解析测试薄弱题失败: testId={}, error={}", test.getId(), e.getMessage());
            return List.of();
        }
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> parseQuestionList(String json) {
        if (json == null || json.isBlank()) {
            return List.of();
        }
        try {
            String trimmed = json.trim();
            List<?> list;
            if (trimmed.startsWith("{")) {
                Map<String, Object> wrapper = objectMapper.readValue(
                        trimmed, new TypeReference<Map<String, Object>>() {});
                Object q = wrapper.get("questions");
                list = q instanceof List<?> l ? l : List.of();
            } else {
                list = objectMapper.readValue(trimmed, new TypeReference<List<?>>() {});
            }
            List<Map<String, Object>> result = new ArrayList<>();
            for (Object item : list) {
                if (item instanceof Map<?, ?> map) {
                    result.add((Map<String, Object>) map);
                }
            }
            return result;
        } catch (Exception e) {
            return List.of();
        }
    }

    private Map<Integer, Boolean> parseCorrectByIndex(String evaluationJson) {
        Map<Integer, Boolean> result = new LinkedHashMap<>();
        if (evaluationJson == null || evaluationJson.isBlank()) {
            return result;
        }
        try {
            Map<String, Object> evaluation = objectMapper.readValue(
                    evaluationJson, new TypeReference<Map<String, Object>>() {});
            Object resultsObj = evaluation.get("questionResults");
            if (resultsObj == null) {
                resultsObj = evaluation.get("details");
            }
            if (!(resultsObj instanceof List<?> list)) {
                return result;
            }
            int fallbackIndex = 0;
            for (Object item : list) {
                if (!(item instanceof Map<?, ?> map)) {
                    continue;
                }
                Integer index = map.get("questionIndex") instanceof Number n ? n.intValue() : fallbackIndex;
                Boolean isCorrect = map.get("isCorrect") instanceof Boolean b ? b : null;
                if (isCorrect != null) {
                    result.put(index, isCorrect);
                }
                fallbackIndex++;
            }
            return result;
        } catch (Exception e) {
            return result;
        }
    }
}
