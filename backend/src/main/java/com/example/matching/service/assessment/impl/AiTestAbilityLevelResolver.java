package com.example.matching.service.assessment.impl;

import com.example.matching.entity.workflow.PersonAbilityClaimGroup;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * AI 测试逐项核验等级确定性归并器。
 * <p>
 * AI 只负责「每题对错 / 得分」，本类用确定性规则把题目得分率归并为每个能力的等级，
 * 避免让模型直接输出多能力结构化等级带来的幻觉与难校验。
 * <p>
 * 规则（与 spec §4.3 一致）：
 * <ul>
 *   <li>单题：封顶 L2（单题证据弱），得分率 &ge; 0.8 → L2，否则 L1。</li>
 *   <li>多题：得分率 &ge; 0.75 → L3；&ge; 0.5 → L2；否则 L1。</li>
 *   <li>归并结果封顶 L3（AI_TEST 单来源 ceiling）。</li>
 * </ul>
 *
 * @author system
 */
@Slf4j
@Component
public class AiTestAbilityLevelResolver {

    /** 单题封顶等级 */
    public static final int SINGLE_QUESTION_CEILING = 2;

    /** AI_TEST 来源封顶等级 */
    public static final int SOURCE_CEILING = 3;

    /** 单题满分判定的得分率阈值 */
    public static final double SINGLE_PASS_RATE = 0.8;

    /** 多题 L3 得分率阈值 */
    public static final double MULTI_L3_RATE = 0.75;

    /** 多题 L2 得分率阈值 */
    public static final double MULTI_L2_RATE = 0.5;

    private final ObjectMapper objectMapper;

    public AiTestAbilityLevelResolver(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    /** 单个能力组的归并结果 */
    public record ResolvedAbility(
            String abilityName, int level, String evidenceText, List<Integer> questionIndexes, Long tagId) {}

    /** 内部：题目解析结果（index 为题目数组下标，对应评估结果的 questionIndex） */
    private record ParsedQuestion(int index, Long tagId, double fullScore) {}

    /** 内部：每题评估结果 */
    private record QuestionResult(int questionIndex, double score, Map<Long, Double> scoreByTag) {
        double scoreFor(Long tagId) {
            return scoreByTag.getOrDefault(tagId, score);
        }
    }

    /**
     * 按简历能力组归并等级。
     *
     * @param questionsJson      题目 JSON（数组，每题含 tagId/score）
     * @param aiEvaluationJson   评估结果 JSON（questionResults 每题 questionIndex/score）
     * @param groups             工作流的简历能力聚合组（canonicalTagId -> 能力）
     * @param overallMasteryLevel 整体掌握等级（预留，未参与归并）
     * @return 每个被覆盖能力组一条归并结果（未映射到任何能力组的题目被忽略）
     */
    public List<ResolvedAbility> resolve(String questionsJson, String aiEvaluationJson,
                                         List<PersonAbilityClaimGroup> groups, Integer overallMasteryLevel) {
        List<ParsedQuestion> questions = parseQuestions(questionsJson);
        if (questions.isEmpty() || groups == null || groups.isEmpty()) {
            return List.of();
        }
        Map<Integer, QuestionResult> resultByIndex = parseResults(aiEvaluationJson);

        Map<Long, PersonAbilityClaimGroup> groupByTag = new HashMap<>();
        for (PersonAbilityClaimGroup g : groups) {
            Long scopeTagId = g.getCanonicalTagId() != null ? g.getCanonicalTagId() : g.getId();
            if (scopeTagId != null) {
                groupByTag.put(scopeTagId, g);
            }
        }
        Map<Long, List<ParsedQuestion>> byGroup = new LinkedHashMap<>();
        for (ParsedQuestion q : questions) {
            PersonAbilityClaimGroup g = groupByTag.get(q.tagId());
            if (g == null) {
                continue; // 未映射到能力组的题目：忽略，不产出伪造的能力证据
            }
            byGroup.computeIfAbsent(g.getId(), k -> new ArrayList<>()).add(q);
        }
        List<ResolvedAbility> resolved = new ArrayList<>();
        for (Map.Entry<Long, List<ParsedQuestion>> e : byGroup.entrySet()) {
            PersonAbilityClaimGroup g = findGroup(groups, e.getKey());
            if (g == null) {
                continue;
            }
            List<ParsedQuestion> qs = e.getValue();
            double totalFull = 0;
            double totalActual = 0;
            List<Integer> indexes = new ArrayList<>();
            for (ParsedQuestion q : qs) {
                QuestionResult r = resultByIndex.get(q.index());
                totalFull += q.fullScore();
                if (r != null) {
                    totalActual += clamp(r.scoreFor(q.tagId()), 0, q.fullScore());
                }
                indexes.add(q.index());
            }
            double rate = totalFull > 0 ? totalActual / totalFull : 0;
            int level = levelFor(qs.size(), rate);
            String name = g.getNormalizedAbilityName() != null ? g.getNormalizedAbilityName() : "能力#" + g.getId();
            String evidence = "AI测试核验[" + name + "]：覆盖题目Q" + indexes
                    + "，得分率=" + String.format("%.0f%%", rate * 100) + "，归并等级=L" + level;
            resolved.add(new ResolvedAbility(name, level, evidence, indexes,
                    g.getCanonicalTagId() != null ? g.getCanonicalTagId() : g.getId()));
        }
        return resolved;
    }

    /**
     * 确定性等级映射（纯函数，便于单测）。
     *
     * @param questionCount 该能力覆盖的题目数
     * @param scoreRate     得分率（0-1）
     */
    public static int levelFor(int questionCount, double scoreRate) {
        if (questionCount <= 1) {
            return scoreRate >= SINGLE_PASS_RATE ? SINGLE_QUESTION_CEILING : 1;
        }
        int level;
        if (scoreRate >= MULTI_L3_RATE) {
            level = 3;
        } else if (scoreRate >= MULTI_L2_RATE) {
            level = 2;
        } else {
            level = 1;
        }
        return Math.min(level, SOURCE_CEILING);
    }

    private List<ParsedQuestion> parseQuestions(String questionsJson) {
        if (questionsJson == null || questionsJson.isBlank()) {
            return List.of();
        }
        try {
            List<Map<String, Object>> list = objectMapper.readValue(
                    questionsJson, new TypeReference<List<Map<String, Object>>>() {});
            List<ParsedQuestion> result = new ArrayList<>();
            for (int i = 0; i < list.size(); i++) {
                Map<String, Object> q = list.get(i);
                double full = q.get("score") instanceof Number n ? n.doubleValue() : 0;
                for (Long tagId : parseTagIds(q)) {
                    result.add(new ParsedQuestion(i, tagId, full));
                }
            }
            return result;
        } catch (Exception e) {
            log.warn("解析测试题目JSON失败: {}", e.getMessage());
            return List.of();
        }
    }

    private Map<Integer, QuestionResult> parseResults(String aiEvaluationJson) {
        if (aiEvaluationJson == null || aiEvaluationJson.isBlank()) {
            return Map.of();
        }
        try {
            Map<String, Object> root = objectMapper.readValue(
                    aiEvaluationJson, new TypeReference<Map<String, Object>>() {});
            Object qr = root.get("questionResults");
            if (!(qr instanceof List<?> list)) {
                return Map.of();
            }
            Map<Integer, QuestionResult> result = new HashMap<>();
            for (Object item : list) {
                if (!(item instanceof Map<?, ?> m)) {
                    continue;
                }
                int index = m.get("questionIndex") instanceof Number n ? n.intValue() : -1;
                double score = m.get("score") instanceof Number n ? n.doubleValue() : 0;
                Map<Long, Double> scoreByTag = new HashMap<>();
                Object tagEvaluations = m.get("tagEvaluations");
                if (tagEvaluations instanceof List<?> evaluations) {
                    for (Object evaluation : evaluations) {
                        if (evaluation instanceof Map<?, ?> evaluationMap
                                && evaluationMap.get("tagId") instanceof Number tag
                                && evaluationMap.get("score") instanceof Number tagScore) {
                            scoreByTag.put(tag.longValue(), tagScore.doubleValue());
                        }
                    }
                }
                result.put(index, new QuestionResult(index, score, scoreByTag));
            }
            return result;
        } catch (Exception e) {
            log.warn("解析测试评估结果JSON失败: {}", e.getMessage());
            return Map.of();
        }
    }

    private PersonAbilityClaimGroup findGroup(List<PersonAbilityClaimGroup> groups, Long groupId) {
        for (PersonAbilityClaimGroup g : groups) {
            if (groupId.equals(g.getId())) {
                return g;
            }
        }
        return null;
    }

    private List<Long> parseTagIds(Map<String, Object> question) {
        Object multiple = question.get("abilityTagIds");
        if (multiple instanceof List<?> list) {
            return list.stream().filter(Number.class::isInstance).map(Number.class::cast)
                    .map(Number::longValue).distinct().toList();
        }
        Object single = question.get("assessmentAbilityId") != null ? question.get("assessmentAbilityId")
                : question.get("abilityTagId") != null ? question.get("abilityTagId") : question.get("tagId");
        return single instanceof Number number ? List.of(number.longValue()) : List.of();
    }

    private static double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(value, max));
    }
}
