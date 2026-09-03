package com.example.matching.application.agent;

import java.util.List;

/**
 * Port for agent memory retrieval and tracking.
 * Abstracts the storage and search of governance experience memories.
 */
public interface AgentMemoryPort {

    /**
     * Search for memories matching the given text within a scope.
     * Uses configured trigger expressions for matching.
     *
     * @param text  the text to match against
     * @param scope the scope to search within
     * @return matching memories
     */
    List<MemoryEntry> searchMemories(String text, String scope);

    /**
     * Search for ACTIVE rules matching text/scope with structured payload.
     * Used by AgentMemoryContextService for the governance closed loop.
     *
     * @param text  normalized text to match
     * @param scope application scope
     * @return matching active rules sorted by priority desc, updatedTime desc
     */
    List<MemoryEntry> searchActiveRules(String text, String scope);

    /**
     * Record that a memory was used (increments use count).
     */
    void markUsed(Long memoryId);

    /**
     * Record a rule hit with outcome and context.
     *
     * @param memoryId     the memory/rule ID
     * @param agentName    agent name (EMPLOYEE_ABILITY_EXTRACTION, etc.)
     * @param sourceType   source type of the input
     * @param sourceRefId  source reference ID
     * @param hitText      text fragment that triggered the rule
     * @param hitContextJson structured JSON context (scope, ruleStrength, retrievalRank, outcome, before/after)
     * @param outcome      one of RETRIEVED_NOT_APPLIED, APPLIED_BY_AGENT, APPLIED_BY_CODE, CONFLICT_SUPERSEDED, REJECTED_BY_VALIDATION
     */
    void logHit(Long memoryId, String agentName, String sourceType, Long sourceRefId,
                String hitText, String hitContextJson, String outcome);

    /**
     * Mark a rule as used and log the hit in the same transaction.
     */
    void markUsedAndLogHit(Long memoryId, String agentName, String sourceType, Long sourceRefId,
                           String hitText, String hitContextJson, String outcome);

    /**
     * Supersede old rule(s) that match the same ruleKey as the new active rule.
     *
     * @param ruleKey    the rule key of the new active rule
     * @param excludeId  exclude this ID from supersedence
     */
    void supersedeByRuleKey(String ruleKey, Long excludeId);

    /**
     * Get all active tag-normalize memories.
     */
    List<MemoryEntry> getTagNormalizeMemories();

    /**
     * Get all active tag-reject memories.
     */
    List<MemoryEntry> getTagRejectMemories();

    /**
     * Immutable memory entry for the application layer.
     */
    record MemoryEntry(
            Long id,
            String memoryType,
            String title,
            String content,
            String triggerExpressionsJson,
            String applicableScope,
            int priority,
            String rulePayloadJson,
            String ruleStrength,
            String ruleKey
    ) {
        public MemoryEntry(Long id, String memoryType, String title, String content,
                           String triggerExpressionsJson, String applicableScope, int priority) {
            this(id, memoryType, title, content, triggerExpressionsJson, applicableScope, priority,
                    null, null, null);
        }
    }
}
