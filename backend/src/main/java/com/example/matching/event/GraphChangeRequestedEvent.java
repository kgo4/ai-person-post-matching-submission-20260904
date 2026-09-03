package com.example.matching.event;

import java.util.Map;

/**
 * A business-domain request to refresh a graph projection.
 * The publisher does not depend on the KG application service.
 */
public record GraphChangeRequestedEvent(
        String sourceType,
        String entityType,
        Long entityId,
        String operationType,
        Map<String, Object> payload,
        Long createdBy
) {}
