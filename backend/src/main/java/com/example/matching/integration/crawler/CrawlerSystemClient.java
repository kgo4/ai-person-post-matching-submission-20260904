package com.example.matching.integration.crawler;

import com.example.matching.config.CrawlerSystemProperties;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import java.net.http.HttpClient;
import java.time.Duration;
import java.util.Map;

/**
 * 独立爬虫系统的 HTTP 客户端。
 * <p>
 * 主系统只做「触发 / 查询状态 / 重推」的代理转发，不复制爬虫业务逻辑；
 * 爬虫抓到 JD 后仍由其自身回调 {@code /api/internal/market-jd/crawler-import} 推送。
 */
@Component
@RequiredArgsConstructor
public class CrawlerSystemClient {

    private final CrawlerSystemProperties properties;
    private final ObjectMapper objectMapper;
    private volatile RestClient restClient;

    /** 爬虫系统是否已配置且启用。判定口径以 {@link CrawlerSystemProperties#isAvailable()} 为唯一来源。 */
    public boolean isAvailable() {
        return properties.isAvailable();
    }

    /** 触发爬取任务，返回爬虫原始响应（含 taskId/status）。 */
    public JsonNode triggerCrawl(Map<String, Object> payload) {
        return post("/api/crawler/trigger", payload);
    }

    /** 查询任务状态。 */
    public JsonNode getTaskStatus(String taskId) {
        return get("/api/crawler/task/" + taskId);
    }

    /** 重新推送指定任务的待推送数据。 */
    public JsonNode retryPush(String taskId) {
        return post("/api/crawler/task/" + taskId + "/retry-push", null);
    }

    private JsonNode post(String path, Object body) {
        ensureAvailable();
        try {
            String response = body == null
                    ? client().post().uri(path).retrieve().body(String.class)
                    : client().post().uri(path).body(body).retrieve().body(String.class);
            return unwrap(response);
        } catch (RestClientResponseException exception) {
            throw new CrawlerSystemException(
                    "爬虫系统调用失败: " + path + ", status=" + exception.getStatusCode().value(), exception);
        } catch (RuntimeException exception) {
            throw new CrawlerSystemException("爬虫系统不可达: " + path + ", " + exception.getMessage(), exception);
        }
    }

    private JsonNode get(String path) {
        ensureAvailable();
        try {
            return unwrap(client().get().uri(path).retrieve().body(String.class));
        } catch (RestClientResponseException exception) {
            throw new CrawlerSystemException(
                    "爬虫系统调用失败: " + path + ", status=" + exception.getStatusCode().value(), exception);
        } catch (RuntimeException exception) {
            throw new CrawlerSystemException("爬虫系统不可达: " + path + ", " + exception.getMessage(), exception);
        }
    }

    /** 拆掉爬虫的 {@code {code,message,data}} 外壳，返回 data 节点。 */
    private JsonNode unwrap(String raw) {
        if (raw == null || raw.isBlank()) {
            return objectMapper.createObjectNode();
        }
        try {
            JsonNode root = objectMapper.readTree(raw);
            if (root.has("data") && !root.get("data").isNull()) {
                return root.get("data");
            }
            return root;
        } catch (Exception exception) {
            throw new CrawlerSystemException("爬虫系统响应解析失败", exception);
        }
    }

    private void ensureAvailable() {
        if (!isAvailable()) {
            throw new CrawlerSystemException("爬虫系统未接入：请配置 crawler-system.base-url", null);
        }
    }

    private RestClient client() {
        RestClient current = restClient;
        if (current == null) {
            synchronized (this) {
                current = restClient;
                if (current == null) {
                    HttpClient httpClient = HttpClient.newBuilder()
                            .connectTimeout(Duration.ofMillis(properties.getConnectTimeoutMillis()))
                            .build();
                    JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory(httpClient);
                    factory.setReadTimeout(Duration.ofMillis(properties.getReadTimeoutMillis()));
                    RestClient.Builder builder = RestClient.builder()
                            .baseUrl(properties.getBaseUrl())
                            .requestFactory(factory);
                    if (properties.getApiToken() != null && !properties.getApiToken().isBlank()) {
                        builder.defaultHeader("X-Crawler-Api-Token", properties.getApiToken());
                    }
                    current = builder.build();
                    restClient = current;
                }
            }
        }
        return current;
    }
}
