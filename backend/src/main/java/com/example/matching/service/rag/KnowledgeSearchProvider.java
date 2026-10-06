package com.example.matching.service.rag;

import java.util.List;

@FunctionalInterface
public interface KnowledgeSearchProvider {

    List<KnowledgeSearchHit> search(KnowledgeSearchRequest request);
}
