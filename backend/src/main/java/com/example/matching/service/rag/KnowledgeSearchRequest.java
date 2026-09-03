package com.example.matching.service.rag;

import java.util.List;

public record KnowledgeSearchRequest(
        String query,
        String scenario,
        int topK,
        List<String> sourceTypes
) {
}
