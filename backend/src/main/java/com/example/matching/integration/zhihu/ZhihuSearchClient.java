package com.example.matching.integration.zhihu;

import lombok.RequiredArgsConstructor;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.net.http.HttpClient;
import java.time.Clock;
import java.time.Duration;
import java.util.List;
import org.springframework.web.client.RestClientResponseException;

@Component
@RequiredArgsConstructor
public class ZhihuSearchClient {
    private final ZhihuApiProperties properties;
    private final ObjectMapper objectMapper;
    private final Clock clock = Clock.systemUTC();
    private RestClient restClient;

    private synchronized RestClient client() {
        if (restClient == null) {
            HttpClient httpClient = HttpClient.newBuilder()
                    .connectTimeout(Duration.ofMillis(properties.getConnectTimeoutMillis()))
                    .build();
            JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory(httpClient);
            factory.setReadTimeout(Duration.ofMillis(properties.getReadTimeoutMillis()));
            restClient = RestClient.builder().baseUrl(properties.getBaseUrl()).requestFactory(factory).build();
        }
        return restClient;
    }

    public ZhihuSearchResponse search(String query, int count) {
        if (!properties.isUsable()) {
            throw new IllegalStateException("Zhihu API is not configured");
        }
        String normalizedQuery = query.trim();
        if (normalizedQuery.isBlank()) throw new IllegalArgumentException("Zhihu query must not be blank");
        int maxQueryLength = Math.max(1, properties.getMaxQueryLength());
        if (normalizedQuery.length() > maxQueryLength) {
            throw new IllegalArgumentException("Zhihu query exceeds max length " + maxQueryLength);
        }
        int normalizedCount = Math.max(1, Math.min(10, count));
        String response = null;
        int attempts = Math.max(0, Math.min(3, properties.getMaxRetries())) + 1;
        for (int attempt = 1; attempt <= attempts; attempt++) {
            try {
                response = client().get()
                        .uri(uriBuilder -> uriBuilder.path("/api/v1/content/zhihu_search")
                                .queryParam("Query", normalizedQuery)
                                .queryParam("Count", normalizedCount)
                                .build())
                        .header("Authorization", "Bearer " + properties.getAccessSecret())
                        .header("X-Request-Timestamp", String.valueOf(clock.instant().getEpochSecond()))
                        .header("Content-Type", "application/json")
                        .retrieve()
                        .body(String.class);
                break;
            } catch (RestClientResponseException exception) {
                boolean retryable = exception.getStatusCode().is5xxServerError()
                        || exception.getStatusCode().value() == 429;
                if (!retryable || attempt == attempts) throw exception;
                backoff(attempt);
            } catch (RuntimeException exception) {
                if (attempt == attempts) throw exception;
                backoff(attempt);
            }
        }
        if (response == null || response.isBlank()) {
            return new ZhihuSearchResponse(false, null, List.of(), "empty_response");
        }
        try {
            JsonNode root = objectMapper.readTree(response);
            JsonNode data = root.has("Data") ? root.get("Data") : root;
            return objectMapper.treeToValue(data, ZhihuSearchResponse.class);
        } catch (Exception e) {
            throw new IllegalStateException("Invalid Zhihu API response", e);
        }
    }

    private void backoff(int attempt) {
        try {
            Thread.sleep(Math.max(0, properties.getRetryBackoffMillis()) * (long) attempt);
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Zhihu request interrupted", interrupted);
        }
    }
}
