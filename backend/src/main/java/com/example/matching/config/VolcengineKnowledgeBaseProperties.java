package com.example.matching.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Volcengine online knowledge base configuration.
 */
@Data
@Component
@ConfigurationProperties(prefix = "volcengine.knowledge-base")
public class VolcengineKnowledgeBaseProperties {

    private boolean enabled = false;
    private String endpoint = "https://api-knowledgebase.mlp.cn-beijing.volces.com";
    private String region = "cn-beijing";
    private String service = "air";
    private String accessKey = "";
    private String secretKey = "";
    private String collectionName = "";
    private String project = "default";
    private String resourceId = "";
    private String pipelineName = "";
    private String providerMode = "hybrid";
    private String syncMode = "point";
    private int connectTimeoutMillis = 10_000;
    private int readTimeoutMillis = 60_000;
    private float denseWeight = 0.5f;
    private boolean rerank = true;
    private String rerankModel = "base-multilingual-rerank";

    public boolean hasCredentials() {
        return notBlank(accessKey) && notBlank(secretKey);
    }

    public boolean hasCollectionTarget() {
        return notBlank(resourceId) || notBlank(collectionName);
    }

    public boolean isUsable() {
        return enabled && hasCredentials() && hasCollectionTarget();
    }

    private boolean notBlank(String value) {
        return value != null && !value.isBlank();
    }
}
