package com.example.matching.service.rag.impl;

import com.example.matching.config.VolcengineKnowledgeBaseProperties;
import com.example.matching.integration.volcengine.kb.VolcengineRequestSigner;
import com.example.matching.integration.volcengine.kb.VolcengineKnowledgeRestClient;
import com.example.matching.service.rag.KnowledgeSearchHit;
import com.example.matching.service.rag.KnowledgeSearchProvider;
import com.example.matching.service.rag.KnowledgeSearchRequest;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class VolcengineKnowledgeSearchProvider implements KnowledgeSearchProvider {

    private static final String SEARCH_PATH = "/api/knowledge/collection/search_knowledge";

    private final VolcengineKnowledgeBaseProperties properties;
    private final ObjectMapper objectMapper;
    private final VolcengineKnowledgeRestClient restClient;

    @Override
    public List<KnowledgeSearchHit> search(KnowledgeSearchRequest request) {
        if (!properties.isUsable() || request == null || request.query() == null || request.query().isBlank()) {
            return List.of();
        }
        try {
            Map<String, Object> payload = buildSearchPayload(request);
            String body = objectMapper.writeValueAsString(payload);
            VolcengineRequestSigner signer = new VolcengineRequestSigner(
                    properties.getAccessKey(),
                    properties.getSecretKey(),
                    properties.getRegion(),
                    properties.getService(),
                    Clock.systemUTC()
            );
            Map<String, String> signedHeaders = signer.sign("POST", SEARCH_PATH, body);
            String response = restClient.get().post()
                    .uri(SEARCH_PATH)
                    .headers(headers -> signedHeaders.forEach(headers::add))
                    .body(body)
                    .retrieve()
                    .body(String.class);
            return parseSearchHits(response);
        } catch (Exception e) {
            log.warn("火山引擎知识库搜索失败，可继续降级。scenario={}, error={}",
                    request.scenario(), e.getMessage());
            return List.of();
        }
    }

    private Map<String, Object> buildSearchPayload(KnowledgeSearchRequest request) {
        Map<String, Object> payload = new LinkedHashMap<>();
        if (notBlank(properties.getResourceId())) {
            payload.put("resource_id", properties.getResourceId());
        } else {
            payload.put("name", properties.getCollectionName());
            payload.put("project", properties.getProject());
        }
        payload.put("query", request.query());
        payload.put("limit", request.topK());
        if (notBlank(properties.getPipelineName())) {
            payload.put("pipeline_name", properties.getPipelineName());
        }

        Map<String, Object> queryParam = new LinkedHashMap<>();
        queryParam.put("dense_weight", properties.getDenseWeight());
        Map<String, Object> postProcessing = new LinkedHashMap<>();
        postProcessing.put("rerank_switch", properties.isRerank());
        postProcessing.put("rerank_model", properties.getRerankModel());
        postProcessing.put("rerank_only_chunk", false);
        queryParam.put("post_processing", postProcessing);
        if (request.sourceTypes() != null && !request.sourceTypes().isEmpty()) {
            queryParam.put("doc_filter", Map.of(
                    "op", "must",
                    "field", "source_type",
                    "conds", request.sourceTypes()
            ));
        }
        payload.put("query_param", queryParam);
        return payload;
    }

    private List<KnowledgeSearchHit> parseSearchHits(String response) throws Exception {
        if (response == null || response.isBlank()) {
            return List.of();
        }
        Map<String, Object> root = objectMapper.readValue(response, new TypeReference<>() {
        });
        Object data = root.get("data");
        if (!(data instanceof Map<?, ?> dataMap)) {
            return List.of();
        }
        Object rawResults = dataMap.get("result");
        if (!(rawResults instanceof List<?> results)) {
            rawResults = dataMap.get("results");
        }
        if (!(rawResults instanceof List<?> results)) {
            return List.of();
        }
        List<KnowledgeSearchHit> hits = new ArrayList<>();
        int totalResults = results.size();
        for (int i = 0; i < results.size(); i++) {
            Object item = results.get(i);
            if (!(item instanceof Map<?, ?> itemMap)) {
                continue;
            }
            String content = firstString(itemMap, "content", "chunk_content", "text");
            if (!notBlank(content)) {
                continue;
            }
            Map<String, Object> metadata = new LinkedHashMap<>();
            metadata.put("backend", "volcengine");
            metadata.put("raw", itemMap);
            // 若响应携带真实业务来源类型则写入 originSourceType，供服务端精细过滤；
            // 云端无法提供时保持缺失，不伪造来源
            String originSourceType = firstString(itemMap, "source_type", "sourceType", "origin_source_type", "originSourceType");
            if (notBlank(originSourceType)) {
                metadata.put("originSourceType", originSourceType);
            }
            // M25：云知识命中回链业务实体——若响应（itemMap/doc_info）携带服务端维护的
            // 来源引用 ID（source_ref_id/sourceRefId）则写入 metadata；云端无法提供时
            // 显式写入 null，不伪造来源引用
            String chunkId = valueOrDefault(firstString(itemMap, "id", "point_id"), "volcengine:" + hits.size());
            String title = "Volcengine Knowledge";
            String documentId = "volcengine-doc";
            Object docInfo = itemMap.get("doc_info");
            if (docInfo instanceof Map<?, ?> docMap) {
                title = valueOrDefault(firstString(docMap, "doc_name"), title);
                documentId = valueOrDefault(firstString(docMap, "doc_id"), documentId);
                metadata.put("docInfo", docMap);
                String sourceRefId = firstString(itemMap, "source_ref_id", "sourceRefId", "source_ref");
                if (sourceRefId == null) {
                    sourceRefId = firstString(docMap, "source_ref_id", "sourceRefId", "source_ref");
                }
                metadata.put("sourceRefId", notBlank(sourceRefId) ? sourceRefId : null);
            } else {
                String sourceRefId = firstString(itemMap, "source_ref_id", "sourceRefId", "source_ref");
                metadata.put("sourceRefId", notBlank(sourceRefId) ? sourceRefId : null);
            }
            float rawScore = firstNumber(itemMap, "rerank_score", "score");
            double rankBasedNormalized = totalResults > 0 ? (1.0 - (double) i / totalResults) : 0.0;
            hits.add(new KnowledgeSearchHit(chunkId, documentId, "VOLCENGINE_KB", title, content, rawScore, metadata,
                    rankBasedNormalized, "RERANK", "RANK_BASED", (double) rawScore));
        }
        return hits;
    }

    private String firstString(Map<?, ?> map, String... keys) {
        for (String key : keys) {
            Object value = map.get(key);
            if (value != null) {
                return String.valueOf(value);
            }
        }
        return null;
    }

    private float firstNumber(Map<?, ?> map, String... keys) {
        for (String key : keys) {
            Object value = map.get(key);
            if (value instanceof Number number) {
                return number.floatValue();
            }
            if (value != null) {
                try {
                    return Float.parseFloat(String.valueOf(value));
                } catch (NumberFormatException ignored) {
                    // 尝试下一个字段
                }
            }
        }
        return 0f;
    }

    private String valueOrDefault(String value, String defaultValue) {
        return notBlank(value) ? value : defaultValue;
    }

    private boolean notBlank(String value) {
        return value != null && !value.isBlank();
    }
}
