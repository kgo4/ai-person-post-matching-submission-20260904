package com.example.matching.agent.service.impl;

import com.example.matching.application.agent.AgentMemoryPort;
import com.example.matching.entity.matching.MatchingRecord;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 匹配评分阶段的记忆规则应用器。
 * <p>
 * 在匹配评分流程中应用HARD治理规则（只降不升、可拦截），
 * 确保已沉淀的治理经验在评分环节闭环生效。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class MatchScoringMemoryRuleApplier {

    private final AgentMemoryContextService memoryContextService;
    private final AgentMemoryPort agentMemoryPort;
    private final ObjectMapper objectMapper;

    /**
     * Apply HARD memory rules to a matching record BEFORE final evaluation.
     * Only HARD rules are applied. GUIDANCE rules are logged but not enforced.
     * Rules can only REDUCE scores or BLOCK matches - never increase scores.
     */
    public MemoryApplyResult apply(MatchingRecord record, String empText, String postText,
                                   Set<Long> employeeTagIds) {
        BigDecimal scoreBefore = record.getAiMatchScore();

        String combinedText = (empText != null ? empText : "") + " | " + (postText != null ? postText : "");

        AgentMemoryContextService.ContextRules contextRules;
        try {
            contextRules = memoryContextService.resolveRules(combinedText, AgentMemoryContextService.SCOPE_MATCHING);
        } catch (Exception e) {
            log.warn("Failed to resolve memory rules for matching: {}", e.getMessage());
            return MemoryApplyResult.noop();
        }

        List<AgentMemoryPort.MemoryEntry> hardRules = contextRules.hardRules();
        if (hardRules.isEmpty()) {
            return MemoryApplyResult.noop();
        }

        List<Long> appliedRuleIds = new ArrayList<>();
        List<String> appliedActions = new ArrayList<>();
        Map<String, Object> auditSnapshot = new LinkedHashMap<>();

        String agentName = "MATCH_EXECUTION_SCORING";
        String sourceType = "MATCHING";

        for (AgentMemoryPort.MemoryEntry rule : hardRules) {
            String rulePayload = rule.rulePayloadJson();
            if (rulePayload == null || rulePayload.isBlank()) {
                continue;
            }
            try {
                JsonNode payload = objectMapper.readTree(rulePayload);
                String action = payload.path("action").asText(null);
                if (action == null) {
                    continue;
                }
                JsonNode params = payload.path("params");

                switch (action) {
                    case "MATCH_EXCLUDE" -> {
                        record.setAiMatchScore(BigDecimal.ZERO);
                        record.setMatchStatus(4);
                        String reason = params.path("reason").asText("Blocked by governance memory rule");
                        record.setQuantitativeReport("{\"conclusion\":\"" + reason + "\"}");
                        appliedRuleIds.add(rule.id());
                        appliedActions.add("MATCH_EXCLUDE");
                        auditSnapshot.put("excluded", true);
                        auditSnapshot.put("exclusionReason", reason);

                        Map<String, Object> auditCtx = new LinkedHashMap<>();
                        auditCtx.put("scope", AgentMemoryContextService.SCOPE_MATCHING);
                        auditCtx.put("ruleStrength", "HARD");
                        auditCtx.put("action", "MATCH_EXCLUDE");
                        auditCtx.put("reason", reason);
                        agentMemoryPort.markUsedAndLogHit(rule.id(), agentName, sourceType, null,
                                combinedText, objectMapper.writeValueAsString(auditCtx), "APPLIED_BY_CODE");

                        return new MemoryApplyResult(
                                appliedRuleIds, appliedActions, scoreBefore,
                                BigDecimal.ZERO, true, reason, auditSnapshot);
                    }
                    case "MATCH_SCORE_CAP" -> {
                        BigDecimal maxScore = BigDecimal.valueOf(params.path("maxScore").asDouble(100.0));
                        BigDecimal currentScore = record.getAiMatchScore();
                        boolean capped = false;
                        if (currentScore != null && currentScore.compareTo(maxScore) > 0) {
                            record.setAiMatchScore(maxScore);
                            capped = true;
                        }
                        appliedRuleIds.add(rule.id());
                        appliedActions.add("MATCH_SCORE_CAP:" + maxScore);
                        auditSnapshot.put("scoreCap", maxScore);
                        auditSnapshot.put("scoreCapApplied", capped);

                        Map<String, Object> auditCtx = new LinkedHashMap<>();
                        auditCtx.put("scope", AgentMemoryContextService.SCOPE_MATCHING);
                        auditCtx.put("ruleStrength", "HARD");
                        auditCtx.put("action", "MATCH_SCORE_CAP");
                        auditCtx.put("maxScore", maxScore);
                        auditCtx.put("capped", capped);
                        agentMemoryPort.markUsedAndLogHit(rule.id(), agentName, sourceType, null,
                                combinedText, objectMapper.writeValueAsString(auditCtx), "APPLIED_BY_CODE");
                    }
                    case "MATCH_REQUIRE_TAG" -> {
                        Long requiredTagId = params.path("requiredTagId").asLong();
                        if (requiredTagId != null && requiredTagId > 0
                                && (employeeTagIds == null || !employeeTagIds.contains(requiredTagId))) {
                            log.warn("MATCH_REQUIRE_TAG: tagId={} not found, blocking match", requiredTagId);
                            appliedRuleIds.add(rule.id());
                            appliedActions.add("MATCH_REQUIRE_TAG_BLOCKED");
                            auditSnapshot.put("requireTag", Map.of("requiredTagId", requiredTagId, "blocked", true));

                            Map<String, Object> auditCtx = new LinkedHashMap<>();
                            auditCtx.put("scope", AgentMemoryContextService.SCOPE_MATCHING);
                            auditCtx.put("ruleStrength", "HARD");
                            auditCtx.put("action", "MATCH_REQUIRE_TAG");
                            auditCtx.put("requiredTagId", requiredTagId);
                            auditCtx.put("blocked", true);
                            agentMemoryPort.markUsedAndLogHit(rule.id(), agentName, sourceType, null,
                                    combinedText, objectMapper.writeValueAsString(auditCtx), "APPLIED_BY_CODE");

                            record.setAiMatchScore(BigDecimal.ZERO);
                            record.setMatchStatus(4);
                            String reason = "Required ability tag " + requiredTagId + " not found in score breakdown";
                            record.setQuantitativeReport("{\"conclusion\":\"" + reason + "\"}");
                            return new MemoryApplyResult(
                                    appliedRuleIds, appliedActions, scoreBefore,
                                    BigDecimal.ZERO, true, reason, auditSnapshot);
                        }

                        appliedRuleIds.add(rule.id());
                        appliedActions.add("MATCH_REQUIRE_TAG:" + requiredTagId);
                        auditSnapshot.put("requireTag", Map.of("requiredTagId", requiredTagId));

                        Map<String, Object> auditCtx = new LinkedHashMap<>();
                        auditCtx.put("scope", AgentMemoryContextService.SCOPE_MATCHING);
                        auditCtx.put("ruleStrength", "HARD");
                        auditCtx.put("action", "MATCH_REQUIRE_TAG");
                        auditCtx.put("requiredTagId", requiredTagId);
                        agentMemoryPort.markUsedAndLogHit(rule.id(), agentName, sourceType, null,
                                combinedText, objectMapper.writeValueAsString(auditCtx), "APPLIED_BY_CODE");
                    }
                    default -> {
                        log.debug("Unknown memory rule action '{}' for matching, rule id={}", action, rule.id());
                    }
                }
            } catch (Exception e) {
                log.warn("Failed to apply memory rule {} for matching: {}", rule.id(), e.getMessage());
            }
        }

        BigDecimal scoreAfter = record.getAiMatchScore();
        return new MemoryApplyResult(
                appliedRuleIds, appliedActions, scoreBefore, scoreAfter, false, null, auditSnapshot);
    }

    public record MemoryApplyResult(
            List<Long> appliedRuleIds,
            List<String> appliedActions,
            BigDecimal scoreBefore,
            BigDecimal scoreAfter,
            boolean excluded,
            String exclusionReason,
            Map<String, Object> auditSnapshot
    ) {
        public static MemoryApplyResult noop() {
            return new MemoryApplyResult(List.of(), List.of(), null, null, false, null, Map.of());
        }
    }
}
