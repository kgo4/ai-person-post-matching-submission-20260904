package com.example.matching.agent.service.impl;

import com.example.matching.application.agent.AgentMemoryPort;
import com.example.matching.application.agent.AgentMemoryPort.MemoryEntry;
import com.example.matching.agent.dto.person.PersonAbilityClaim;
import com.example.matching.agent.dto.person.PersonAbilityExtractionResult;
import com.example.matching.agent.dto.post.PostAbilityClaim;
import com.example.matching.agent.dto.post.PostAbilityExtractionResult;
import com.example.matching.agent.service.impl.AgentMemoryContextService.ContextRules;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Agent 记忆规则强制执行器
 * <p>
 * 对Agent输出JSON执行硬规则（TAG_NORMALIZE, TAG_REJECT, LEVEL_RULE, SOURCE_POLICY）。
 * 不允许LLM覆盖硬规则。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AgentMemoryRuleEnforcer {

    private final AgentMemoryPort agentMemoryPort;
    private final ObjectMapper objectMapper;
    private final AgentMemoryContextService memoryContextService;

    private static final BigDecimal MAX_CONFIDENCE = new BigDecimal("100");

    /**
     * 对人员能力提取结果执行硬规则。
     */
    public EnforcementResult enforce(PersonAbilityExtractionResult result,
                                     ContextRules contextRules,
                                     String sourceType,
                                     Long sourceRefId,
                                     String rawText) {
        List<PersonAbilityClaim> claims = result.getClaims();
        if (claims == null || claims.isEmpty()) {
            return new EnforcementResult(List.of(), false, false);
        }

        List<MemoryEntry> hardRules = filterHard(contextRules.hardRules(),
                "TAG_NORMALIZE", "TAG_REJECT", "LEVEL_RULE");

        boolean anyApplied = false;
        String agentName = "EMPLOYEE_ABILITY_EXTRACTION";

        List<ClaimAdapter> adapters = new ArrayList<>();
        for (PersonAbilityClaim c : claims) {
            adapters.add(new PersonClaimAdapter(c));
        }

        for (MemoryEntry rule : hardRules) {
            switch (rule.memoryType()) {
                case "TAG_NORMALIZE" -> {
                    int applied = applyTagNormalizeShared(adapters, rule, sourceType, sourceRefId, agentName, rawText);
                    if (applied > 0) anyApplied = true;
                    else recordRetrievedNotApplied(rule, agentName, sourceType, sourceRefId, rawText);
                }
                case "TAG_REJECT" -> {
                    int applied = applyTagRejectShared(adapters, claims, rule, sourceType, sourceRefId, agentName, rawText, true);
                    if (applied > 0) anyApplied = true;
                    else recordRetrievedNotApplied(rule, agentName, sourceType, sourceRefId, rawText);
                }
                case "LEVEL_RULE" -> {
                    int applied = applyLevelRuleShared(adapters, rule, sourceType, sourceRefId, agentName, rawText);
                    if (applied > 0) anyApplied = true;
                    else recordRetrievedNotApplied(rule, agentName, sourceType, sourceRefId, rawText);
                }
            }
        }

        return new EnforcementResult(claims, anyApplied, result.getClaims().stream()
                .anyMatch(c -> c.getAbilityTagId() != null && c.getAbilityTagId() < 0));
    }

    /**
     * 对岗位能力提取结果执行硬规则。
     */
    public EnforcementResult enforcePost(PostAbilityExtractionResult result,
                                         ContextRules contextRules,
                                         String sourceType,
                                         Long sourceRefId,
                                         String rawText) {
        List<PostAbilityClaim> claims = result.getClaims();
        if (claims == null || claims.isEmpty()) {
            return new EnforcementResult(List.of(), false, false);
        }

        List<MemoryEntry> hardRules = filterHard(contextRules.hardRules(),
                "TAG_NORMALIZE", "TAG_REJECT", "LEVEL_RULE");

        boolean anyApplied = false;
        String agentName = "POST_ABILITY_EXTRACTION";

        List<ClaimAdapter> adapters = new ArrayList<>();
        for (PostAbilityClaim c : claims) {
            adapters.add(new PostClaimAdapter(c));
        }

        for (MemoryEntry rule : hardRules) {
            switch (rule.memoryType()) {
                case "TAG_NORMALIZE" -> {
                    int applied = applyTagNormalizeShared(adapters, rule, sourceType, sourceRefId, agentName, rawText);
                    if (applied > 0) anyApplied = true;
                    else recordRetrievedNotApplied(rule, agentName, sourceType, sourceRefId, rawText);
                }
                case "TAG_REJECT" -> {
                    int applied = applyTagRejectShared(adapters, claims, rule, sourceType, sourceRefId, agentName, rawText, false);
                    if (applied > 0) anyApplied = true;
                    else recordRetrievedNotApplied(rule, agentName, sourceType, sourceRefId, rawText);
                }
                case "LEVEL_RULE" -> {
                    int applied = applyLevelRuleShared(adapters, rule, sourceType, sourceRefId, agentName, rawText);
                    if (applied > 0) anyApplied = true;
                    else recordRetrievedNotApplied(rule, agentName, sourceType, sourceRefId, rawText);
                }
            }
        }

        return new EnforcementResult(claims, anyApplied, false);
    }

    // ──────────────── 共享规则执行方法 ────────────────

    private int applyTagNormalizeShared(List<ClaimAdapter> adapters, MemoryEntry rule,
                                        String sourceType, Long sourceRefId, String agentName, String rawText) {
        int count = 0;
        RuleAction action = parseAction(rule);
        List<String> sourceTerms = memoryContextService.extractSourceTerms(rule);

        for (ClaimAdapter adapter : adapters) {
            String abilityName = adapter.normalizedName() != null
                    ? adapter.normalizedName() : adapter.abilityName();
            if (abilityName == null) continue;

            boolean matched = sourceTerms.stream().anyMatch(t -> abilityName.equalsIgnoreCase(t.trim()));
            if (!matched) {
                matched = abilityName.equalsIgnoreCase(rule.title());
            }
            if (!matched) continue;

            if (action.targetTagId() != null) {
                String before = adapter.normalizedName() + ":" + adapter.tagId();
                adapter.setTagId(action.targetTagId());
                if (action.targetName() != null) {
                    adapter.setNormalizedName(action.targetName());
                }
                count++;

                Map<String, Object> ctx = buildHitContext("APPLIED_BY_CODE", 1, before,
                        adapter.normalizedName() + ":" + adapter.tagId());
                logHit(rule, agentName, sourceType, sourceRefId, abilityName, ctx);
            }
        }
        return count;
    }

    private int applyTagRejectShared(List<ClaimAdapter> adapters, List<?> claims, MemoryEntry rule,
                                     String sourceType, Long sourceRefId, String agentName, String rawText,
                                     boolean useTitleFallback) {
        int count = 0;
        List<String> sourceTerms = memoryContextService.extractSourceTerms(rule);

        Iterator<ClaimAdapter> adapterIter = adapters.iterator();
        Iterator<?> claimIter = claims.iterator();
        while (adapterIter.hasNext() && claimIter.hasNext()) {
            ClaimAdapter adapter = adapterIter.next();
            claimIter.next();
            String abilityName = adapter.normalizedName() != null
                    ? adapter.normalizedName() : adapter.abilityName();
            if (abilityName == null) continue;

            boolean matched = sourceTerms.stream().anyMatch(t -> abilityName.toLowerCase().contains(t.toLowerCase()));
            if (!matched && useTitleFallback) {
                matched = abilityName.equalsIgnoreCase(rule.title().replace("拒绝标签: ", ""));
            }
            if (!matched) continue;

            Map<String, Object> ctx = buildHitContext("REJECTED_BY_VALIDATION", 1,
                    abilityName, "REMOVED");
            logHit(rule, agentName, sourceType, sourceRefId, abilityName, ctx);

            adapterIter.remove();
            claimIter.remove();
            count++;
        }
        return count;
    }

    private int applyLevelRuleShared(List<ClaimAdapter> adapters, MemoryEntry rule,
                                     String sourceType, Long sourceRefId, String agentName, String rawText) {
        int count = 0;
        RuleAction action = parseAction(rule);
        List<String> sourceTerms = memoryContextService.extractSourceTerms(rule);

        for (ClaimAdapter adapter : adapters) {
            String abilityName = adapter.normalizedName() != null
                    ? adapter.normalizedName() : adapter.abilityName();
            if (abilityName == null) continue;

            boolean matched = sourceTerms.stream().anyMatch(t -> abilityName.toLowerCase().contains(t.toLowerCase()));
            if (!matched) continue;

            boolean modified = false;
            int before = adapter.level();

            if (action.maxLevel() != null && adapter.level() > action.maxLevel()) {
                adapter.setLevel(action.maxLevel());
                modified = true;
            }
            if (action.minLevel() != null && adapter.level() < action.minLevel()) {
                adapter.setLevel(action.minLevel());
                modified = true;
            }

            if (modified) {
                count++;
                Map<String, Object> ctx = buildHitContext("APPLIED_BY_CODE", 1,
                        before, adapter.level());
                logHit(rule, agentName, sourceType, sourceRefId, abilityName, ctx);
            }
        }
        return count;
    }

    // ──────────────── 声明适配器 ────────────────

    private interface ClaimAdapter {
        String abilityName();
        String normalizedName();
        Long tagId();
        void setTagId(Long tagId);
        int level();
        void setLevel(int level);
        void setNormalizedName(String name);
        String auditDescription();
    }

    private static class PersonClaimAdapter implements ClaimAdapter {
        final PersonAbilityClaim claim;

        PersonClaimAdapter(PersonAbilityClaim c) {
            this.claim = c;
        }

        public String abilityName() { return claim.getAbilityName(); }
        public String normalizedName() { return claim.getNormalizedAbilityName(); }
        public Long tagId() { return claim.getAbilityTagId(); }
        public void setTagId(Long tagId) { claim.setAbilityTagId(tagId); }
        public int level() { return claim.getMasteryLevel() != null ? claim.getMasteryLevel() : 0; }
        public void setLevel(int level) { claim.setMasteryLevel(level); }
        public void setNormalizedName(String name) { claim.setNormalizedAbilityName(name); }
        public String auditDescription() { return "person:" + claim.getEmpId(); }
    }

    private static class PostClaimAdapter implements ClaimAdapter {
        final PostAbilityClaim claim;

        PostClaimAdapter(PostAbilityClaim c) {
            this.claim = c;
        }

        public String abilityName() { return claim.getAbilityName(); }
        public String normalizedName() { return claim.getNormalizedAbilityName(); }
        public Long tagId() { return claim.getAbilityTagId(); }
        public void setTagId(Long tagId) { claim.setAbilityTagId(tagId); }
        public int level() { return claim.getRequiredLevel() != null ? claim.getRequiredLevel() : 0; }
        public void setLevel(int level) { claim.setRequiredLevel(level); }
        public void setNormalizedName(String name) { claim.setNormalizedAbilityName(name); }
        public String auditDescription() { return "post:" + claim.getPostId(); }
    }

    // ──────────────── 证据治理 ────────────────

    /**
     * 对证据治理结果执行SOURCE_POLICY规则。
     * 缺证据结果由PASS改为REVIEW。
     */
    public String enforceSourcePolicy(String sourceType, String decision,
                                       MemoryEntry rule, String agentName,
                                       String sourceRefDesc, Long sourceRefId, String rawText) {
        if (!"SOURCE_POLICY".equals(rule.memoryType())) return decision;
        if (!"HARD".equals(rule.ruleStrength())) return decision;

        RuleAction action = parseAction(rule);
        if (action.sourceTypes() != null && action.sourceTypes().length > 0) {
            boolean matches = false;
            for (String st : action.sourceTypes()) {
                if (st.equalsIgnoreCase(sourceType)) {
                    matches = true;
                    break;
                }
            }
            if (!matches) return decision;
        }

        if ("PASS".equals(decision) && "REQUIRE_SOURCE_REF".equals(action.kind())) {
            Map<String, Object> ctx = buildHitContext("APPLIED_BY_CODE", 1,
                    decision, "REVIEW");
            logHit(rule, agentName, sourceType, sourceRefId,
                    sourceRefDesc + " - sourcePolicy enforced", ctx);
            return "REVIEW";
        }

        return decision;
    }

    /**
     * 对证据治理应用LEVEL_RULE：来源类型匹配时限制等级。
     */
    public Integer enforceLevelCap(String sourceType, Integer suggestedLevel,
                                    MemoryEntry rule, String agentName,
                                    String hitText, Long sourceRefId) {
        if (!"LEVEL_RULE".equals(rule.memoryType())) return suggestedLevel;
        if (!"HARD".equals(rule.ruleStrength())) return suggestedLevel;

        RuleAction action = parseAction(rule);
        if (action.maxLevel() != null && suggestedLevel != null
                && suggestedLevel > action.maxLevel()) {
            Map<String, Object> ctx = buildHitContext("APPLIED_BY_CODE", 1,
                    suggestedLevel, action.maxLevel());
            logHit(rule, agentName, sourceType, sourceRefId, hitText, ctx);
            return action.maxLevel();
        }

        return suggestedLevel;
    }

    public void enforceTagReject(String sourceType, MemoryEntry rule, String agentName,
                                 String hitText, Long sourceRefId, String beforeDecision) {
        if (!"TAG_REJECT".equals(rule.memoryType()) || !"HARD".equals(rule.ruleStrength())) return;
        logHit(rule, agentName, sourceType, sourceRefId, hitText,
                buildHitContext("REJECTED_BY_VALIDATION", 1, beforeDecision, "REVIEW"));
    }

    public void recordRetrievedNotApplied(MemoryEntry rule, String agentName, String sourceType,
                                          Long sourceRefId, String hitText) {
        logHit(rule, agentName, sourceType, sourceRefId, hitText,
                buildHitContext("RETRIEVED_NOT_APPLIED", 1, null, null));
    }

    /**
     * Guidance rules are not enforced by code. Attribute them to the agent only when its raw JSON
     * contains the rule's target tag, otherwise retain an explicit retrieved-but-not-applied audit row.
     */
    public void auditGuidanceResponse(ContextRules contextRules, String rawResponse, String agentName,
                                      String sourceType, Long sourceRefId, String hitText) {
        if (contextRules == null || contextRules.guidanceRules() == null) return;
        String response = rawResponse == null ? "" : rawResponse.toLowerCase();
        for (MemoryEntry rule : contextRules.guidanceRules()) {
            RuleAction action = parseAction(rule);
            String targetName = action.targetName();
            boolean applied = targetName != null && !targetName.isBlank()
                    && response.contains(targetName.toLowerCase());
            if (applied) {
                logHit(rule, agentName, sourceType, sourceRefId, hitText,
                        buildHitContext("APPLIED_BY_AGENT", 1, null, targetName));
            } else {
                recordRetrievedNotApplied(rule, agentName, sourceType, sourceRefId, hitText);
            }
        }
    }

    // ──────────────── 工具方法 ────────────────

    private List<MemoryEntry> filterHard(List<MemoryEntry> rules, String... types) {
        if (rules == null) return List.of();
        return rules.stream()
                .filter(r -> "HARD".equals(r.ruleStrength()))
                .filter(r -> {
                    for (String t : types) {
                        if (t.equals(r.memoryType())) return true;
                    }
                    return false;
                })
                .toList();
    }

    private RuleAction parseAction(MemoryEntry entry) {
        if (entry.rulePayloadJson() == null || entry.rulePayloadJson().isBlank()) {
            return RuleAction.EMPTY;
        }
        try {
            JsonNode root = objectMapper.readTree(entry.rulePayloadJson());
            JsonNode action = root.path("action");
            String kind = action.path("kind").asText(null);
            Long targetTagId = action.path("targetTagId").isNull() ? null : action.path("targetTagId").asLong();
            String targetName = action.path("targetName").asText(null);
            Integer maxLevel = action.path("maxLevel").isNull() ? null : action.path("maxLevel").asInt();
            Integer minLevel = action.path("minLevel").isNull() ? null : action.path("minLevel").asInt();
            List<String> sourceTypes = null;
            JsonNode sourceTypesNode = action.path("sourceTypes");
            if (!sourceTypesNode.isArray()) {
                sourceTypesNode = root.path("condition").path("sourceTypes");
            }
            if (sourceTypesNode.isArray()) {
                sourceTypes = new ArrayList<>();
                for (JsonNode st : sourceTypesNode) {
                    if (st.isTextual()) sourceTypes.add(st.asText());
                }
            }
            return new RuleAction(kind, targetTagId, targetName, maxLevel, minLevel,
                    sourceTypes != null ? sourceTypes.toArray(new String[0]) : null);
        } catch (Exception e) {
            log.debug("Failed to parse rulePayloadJson action: {}", entry.id());
            return RuleAction.EMPTY;
        }
    }

    private Map<String, Object> buildHitContext(String outcome, int rank, Object before, Object after) {
        Map<String, Object> ctx = new LinkedHashMap<>();
        ctx.put("scope", "GOVERNANCE_CLOSED_LOOP");
        ctx.put("ruleStrength", "HARD");
        ctx.put("retrievalRank", rank);
        ctx.put("outcome", outcome);
        ctx.put("before", before);
        ctx.put("after", after);
        return ctx;
    }

    private void logHit(MemoryEntry rule, String agentName, String sourceType,
                        Long sourceRefId, String hitText, Map<String, Object> ctx) {
        try {
            String ctxJson = objectMapper.writeValueAsString(ctx);
            agentMemoryPort.markUsedAndLogHit(
                    rule.id(), agentName, sourceType, sourceRefId,
                    hitText, ctxJson, (String) ctx.get("outcome"));
        } catch (Exception e) {
            log.warn("Failed to log agent memory hit: memoryId={}, error={}", rule.id(), e.getMessage());
        }
    }

    public record RuleAction(
            String kind,
            Long targetTagId,
            String targetName,
            Integer maxLevel,
            Integer minLevel,
            String[] sourceTypes
    ) {
        static final RuleAction EMPTY = new RuleAction(null, null, null, null, null, null);
    }

    public record EnforcementResult(
            List<?> claims,
            boolean rulesApplied,
            boolean hasReviewNeeded
    ) {
        @SuppressWarnings("unchecked")
        public <T> List<T> claimsAs() {
            return (List<T>) claims;
        }
    }
}
