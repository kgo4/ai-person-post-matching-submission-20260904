package com.example.matching.service.rag.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.example.matching.config.VolcengineKnowledgeBaseProperties;
import com.example.matching.entity.rag.RagKnowledgeDocument;
import com.example.matching.integration.volcengine.kb.VolcengineKnowledgeBaseClient;
import com.example.matching.mapper.rag.RagKnowledgeDocumentMapper;
import com.example.matching.service.rag.CloudKnowledgeSyncService;
import com.example.matching.service.rag.KnowledgeChunker;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class CloudKnowledgeSyncServiceImpl implements CloudKnowledgeSyncService {

    private static final String POINT_ADD_PATH = "/api/knowledge/point/add";
    private static final int DEFAULT_LIMIT = 100;

    private final RagKnowledgeDocumentMapper documentMapper;
    private final KnowledgeChunker knowledgeChunker;
    private final VolcengineKnowledgeBaseProperties properties;
    private final VolcengineKnowledgeBaseClient client;

    @Override
    public Map<String, Object> syncSystemKnowledge(String sourceType, int limit, boolean dryRun) {
        int effectiveLimit = limit > 0 ? limit : DEFAULT_LIMIT;
        List<RagKnowledgeDocument> documents = documentMapper.selectList(
                Wrappers.<RagKnowledgeDocument>lambdaQuery()
                        .eq(RagKnowledgeDocument::getDocStatus, "ACTIVE")
                        .eq(RagKnowledgeDocument::getIsDeleted, 0)
                        .eq(sourceType != null && !sourceType.isBlank(), RagKnowledgeDocument::getSourceType, sourceType)
                        .last("LIMIT " + effectiveLimit)
        );

        int created = 0;
        int skipped = 0;
        int failed = 0;
        List<Map<String, Object>> samples = new ArrayList<>();
        for (RagKnowledgeDocument doc : documents) {
            Map<String, Object> payload = buildPointPayload(doc);
            if (samples.size() < 3) {
                samples.add(payload);
            }
            if (dryRun) {
                skipped++;
                continue;
            }
            if (!properties.isUsable()) {
                skipped++;
                continue;
            }
            try {
                client.postJson(POINT_ADD_PATH, payload);
                created++;
            } catch (Exception e) {
                failed++;
                log.warn("Sync knowledge document to Volcengine failed. docId={}, error={}", doc.getId(), e.getMessage());
            }
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("enabled", properties.isUsable());
        result.put("dryRun", dryRun);
        result.put("sourceType", sourceType);
        result.put("scanned", documents.size());
        result.put("created", created);
        result.put("skipped", skipped);
        result.put("failed", failed);
        result.put("samplePayloads", samples);
        return result;
    }

    private Map<String, Object> buildPointPayload(RagKnowledgeDocument doc) {
        Map<String, Object> payload = new LinkedHashMap<>();
        if (properties.getResourceId() != null && !properties.getResourceId().isBlank()) {
            payload.put("resource_id", properties.getResourceId());
        } else {
            payload.put("name", properties.getCollectionName());
            payload.put("project", properties.getProject());
        }
        String docId = "system_" + doc.getSourceType() + "_" + doc.getSourceRefId();
        payload.put("doc_id", docId);
        payload.put("doc_name", doc.getTitle());
        payload.put("source", "system");

        List<String> chunkTexts = knowledgeChunker.chunk(doc.getContent());
        List<Map<String, Object>> points = new ArrayList<>();
        for (int i = 0; i < chunkTexts.size(); i++) {
            Map<String, Object> point = new LinkedHashMap<>();
            point.put("point_id", docId + "_" + i);
            point.put("content", chunkTexts.get(i));
            point.put("metadata", Map.of(
                    "source_type", doc.getSourceType(),
                    "source_ref_id", String.valueOf(doc.getSourceRefId()),
                    "local_doc_id", String.valueOf(doc.getId()),
                    "title", doc.getTitle()
            ));
            points.add(point);
        }
        payload.put("points", points);
        return payload;
    }
}
