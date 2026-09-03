package com.example.matching.service.kg;

import com.example.matching.entity.kg.KgGraphChangeSet;

public interface KnowledgeGraphIncrementalService {

    IncrementalGraphResult apply(KgGraphChangeSet changeSet);

    record IncrementalGraphResult(int affectedNodeCount, int affectedEdgeCount, String graphVersion) {
    }
}
