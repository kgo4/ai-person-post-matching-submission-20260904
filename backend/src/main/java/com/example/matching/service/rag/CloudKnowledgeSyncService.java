package com.example.matching.service.rag;

import java.util.Map;

public interface CloudKnowledgeSyncService {

    Map<String, Object> syncSystemKnowledge(String sourceType, int limit, boolean dryRun);
}
