package com.example.matching.service.rag;

import java.util.Collections;
import java.util.List;
import java.util.Set;

public record KnowledgeSearchRequest(
        String query,
        String scenario,
        int topK,
        List<String> sourceTypes,
        Set<RagKnowledgeLayer> allowedLayers
) {

    /** 兼容已有 Provider 调用方；未指定层时由上层按旧契约处理。 */
    public KnowledgeSearchRequest(String query, String scenario, int topK, List<String> sourceTypes) {
        this(query, scenario, topK, sourceTypes, Collections.emptySet());
    }

    public KnowledgeSearchRequest {
        sourceTypes = sourceTypes == null ? List.of() : List.copyOf(sourceTypes);
        allowedLayers = allowedLayers == null ? Set.of() : Set.copyOf(allowedLayers);
    }
}
