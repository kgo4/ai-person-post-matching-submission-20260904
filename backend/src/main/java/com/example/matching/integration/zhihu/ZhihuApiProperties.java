package com.example.matching.integration.zhihu;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "zhihu.api")
public class ZhihuApiProperties {
    private boolean enabled = false;
    private String baseUrl = "https://developer.zhihu.com";
    private String accessSecret = "";
    private int connectTimeoutMillis = 5000;
    private int readTimeoutMillis = 15000;
    private int maxQueryLength = 200;
    private int maxRetries = 2;
    private int retryBackoffMillis = 150;
    private int cacheTtlSeconds = 30;

    public boolean isUsable() {
        return enabled && accessSecret != null && !accessSecret.isBlank();
    }
}
