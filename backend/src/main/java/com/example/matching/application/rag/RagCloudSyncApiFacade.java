package com.example.matching.application.rag;

import com.example.matching.config.VolcengineKnowledgeBaseProperties;
import com.example.matching.service.rag.CloudKnowledgeSyncService;
import com.example.matching.service.rag.RagRetrievalService;
import com.example.matching.service.rag.RagScenarioEnum;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.*;

@Service
@RequiredArgsConstructor
public class RagCloudSyncApiFacade {

    private final CloudKnowledgeSyncService cloudKnowledgeSyncService;
    private final VolcengineKnowledgeBaseProperties kbProperties;
    private final RagRetrievalService ragRetrievalService;

    public Map<String, Object> getStatus() {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("enabled", kbProperties.isEnabled());
        result.put("usable", kbProperties.isUsable());
        result.put("providerMode", kbProperties.getProviderMode());
        result.put("resourceId", mask(kbProperties.getResourceId()));
        result.put("collectionName", kbProperties.getCollectionName());
        result.put("endpoint", kbProperties.getEndpoint());
        result.put("hasCredentials", kbProperties.hasCredentials());
        result.put("hasCollectionTarget", kbProperties.hasCollectionTarget());

        List<Map<String, Object>> scenarios = new ArrayList<>();
        for (RagScenarioEnum s : RagScenarioEnum.values()) {
            Map<String, Object> sm = new LinkedHashMap<>();
            sm.put("key", s.name());
            sm.put("name", s.getName());
            sm.put("allowCloud", s.isAllowCloud());
            scenarios.add(sm);
        }
        result.put("scenarios", scenarios);

        return result;
    }

    public Map<String, Object> sync(String sourceType, int limit, boolean dryRun) {
        return cloudKnowledgeSyncService.syncSystemKnowledge(sourceType, limit, dryRun);
    }

    public Map<String, Object> updateConfig(Map<String, Object> request) {
        if (request.containsKey("enabled")) kbProperties.setEnabled(Boolean.TRUE.equals(request.get("enabled")));
        setIfPresent(request, "endpoint", kbProperties::setEndpoint);
        setIfPresent(request, "region", kbProperties::setRegion);
        setIfPresent(request, "accessKey", kbProperties::setAccessKey);
        setIfPresent(request, "secretKey", kbProperties::setSecretKey);
        setIfPresent(request, "resourceId", kbProperties::setResourceId);
        setIfPresent(request, "collectionName", kbProperties::setCollectionName);
        setIfPresent(request, "providerMode", kbProperties::setProviderMode);
        return getStatus();
    }

    private static void setIfPresent(Map<String, Object> request, String key, java.util.function.Consumer<String> setter) {
        Object value = request.get(key);
        if (value instanceof String text && !text.isBlank()) setter.accept(text.trim());
    }

    public Map<String, Object> search(String queryText, String scenario) {
        RagScenarioEnum scenarioEnum;
        try {
            scenarioEnum = RagScenarioEnum.valueOf(scenario);
        } catch (IllegalArgumentException e) {
            scenarioEnum = RagScenarioEnum.JD_ABILITY_EXTRACT;
        }

        var retrievalResult = ragRetrievalService.retrieve(queryText, scenarioEnum);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("queryText", queryText);
        result.put("scenario", scenarioEnum.getName());
        result.put("providerMode", retrievalResult.getProviderMode());
        result.put("fallbackUsed", retrievalResult.isFallbackUsed());
        result.put("allowCloud", scenarioEnum.isAllowCloud());
        result.put("hits", retrievalResult.getHits());
        result.put("hitCount", retrievalResult.getHitCount());
        result.put("latencyMs", retrievalResult.getLatencyMs());
        return result;
    }

    private static String mask(String value) {
        if (value == null || value.isBlank()) return "";
        if (value.length() <= 8) return "****";
        return value.substring(0, 4) + "****" + value.substring(value.length() - 4);
    }
}
