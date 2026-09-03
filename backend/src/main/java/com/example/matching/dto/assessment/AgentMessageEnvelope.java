package com.example.matching.dto.assessment;

import java.time.Instant;

/** Strongly typed async contract. Queue messages carry this envelope and a payload reference only. */
public record AgentMessageEnvelope(
        String messageId, String traceId, Long workflowId, Long stageRunId,
        String fromAgent, String toAgent, String contractVersion, Long payloadRef,
        String scopeHash, String taxonomyVersion, Integer attempt, Instant expiresAt,
        Integer quota) {
}
