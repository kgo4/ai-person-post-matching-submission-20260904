package com.example.matching.integration.volcengine.kb;

import com.example.matching.config.VolcengineKnowledgeBaseProperties;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.util.Map;

@Component
@RequiredArgsConstructor
public class VolcengineKnowledgeBaseClient {

    private final VolcengineKnowledgeBaseProperties properties;
    private final ObjectMapper objectMapper;
    private final VolcengineKnowledgeRestClient restClient;

    public Map<String, Object> postJson(String path, Map<String, Object> payload) {
        if (!properties.isUsable()) {
            throw new IllegalStateException("Volcengine knowledge base is not configured");
        }
        try {
            String body = objectMapper.writeValueAsString(payload);
            VolcengineRequestSigner signer = new VolcengineRequestSigner(
                    properties.getAccessKey(),
                    properties.getSecretKey(),
                    properties.getRegion(),
                    properties.getService(),
                    Clock.systemUTC()
            );
            Map<String, String> signedHeaders = signer.sign("POST", path, body);
            String response = restClient.get().post()
                    .uri(path)
                    .headers(headers -> signedHeaders.forEach(headers::add))
                    .body(body)
                    .retrieve()
                    .body(String.class);
            if (response == null || response.isBlank()) {
                return Map.of();
            }
            return objectMapper.readValue(response, new TypeReference<>() {
            });
        } catch (Exception e) {
            throw new IllegalStateException("Volcengine knowledge base request failed: " + e.getMessage(), e);
        }
    }

}
