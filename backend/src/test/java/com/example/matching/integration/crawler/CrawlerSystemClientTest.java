package com.example.matching.integration.crawler;

import com.example.matching.config.CrawlerSystemProperties;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * {@link CrawlerSystemClient} 的契约测试：跑一个真实本地 HTTP 桩，验证请求路径、
 * 鉴权头透传，以及 {@code {code,message,data}} 外壳的拆解。
 */
class CrawlerSystemClientTest {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private HttpServer server;
    private String baseUrl;
    private final AtomicReference<String> lastPath = new AtomicReference<>();
    private final AtomicReference<String> lastToken = new AtomicReference<>();
    private final AtomicReference<String> lastBody = new AtomicReference<>();

    @BeforeEach
    void startServer() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/api/crawler/task/", exchange -> {
            lastPath.set(exchange.getRequestURI().getPath());
            lastToken.set(exchange.getRequestHeaders().getFirst("X-Crawler-Api-Token"));
            respond(exchange, 200, "{\"code\":200,\"message\":\"操作成功\",\"data\":{\"taskId\":\"t-1\",\"status\":\"RUNNING\"}}");
        });
        server.createContext("/api/crawler/trigger", exchange -> {
            lastPath.set(exchange.getRequestURI().getPath());
            lastToken.set(exchange.getRequestHeaders().getFirst("X-Crawler-Api-Token"));
            lastBody.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            respond(exchange, 200, "{\"code\":200,\"message\":\"爬取任务已启动\",\"data\":{\"taskId\":\"t-9\"}}");
        });
        server.start();
        baseUrl = "http://127.0.0.1:" + server.getAddress().getPort();
    }

    @AfterEach
    void stopServer() {
        server.stop(0);
    }

    private static void respond(com.sun.net.httpserver.HttpExchange exchange, int status, String body)
            throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().add("Content-Type", "application/json");
        exchange.sendResponseHeaders(status, bytes.length);
        try (OutputStream out = exchange.getResponseBody()) {
            out.write(bytes);
        }
    }

    private CrawlerSystemClient client(String token) {
        CrawlerSystemProperties properties = new CrawlerSystemProperties();
        properties.setBaseUrl(baseUrl);
        properties.setApiToken(token);
        properties.setEnabled(true);
        return new CrawlerSystemClient(properties, objectMapper);
    }

    @Test
    void unwrapsResultEnvelopeAndPassesToken() throws Exception {
        JsonNode data = client("secret-token").getTaskStatus("t-1");

        assertThat(lastPath.get()).isEqualTo("/api/crawler/task/t-1");
        assertThat(lastToken.get()).isEqualTo("secret-token");
        assertThat(data.get("taskId").asText()).isEqualTo("t-1");
        assertThat(data.get("status").asText()).isEqualTo("RUNNING");
    }

    @Test
    void triggerSendsBodyAndReturnsData() throws Exception {
        JsonNode data = client("secret-token").triggerCrawl(Map.of("sources", java.util.List.of("boss")));

        assertThat(lastPath.get()).isEqualTo("/api/crawler/trigger");
        assertThat(lastBody.get()).contains("\"boss\"");
        assertThat(data.get("taskId").asText()).isEqualTo("t-9");
    }

    @Test
    void blankBaseUrlIsReportedAsUnavailable() {
        CrawlerSystemProperties properties = new CrawlerSystemProperties();
        properties.setBaseUrl("");
        CrawlerSystemClient unavailable = new CrawlerSystemClient(properties, objectMapper);

        assertThat(unavailable.isAvailable()).isFalse();
        assertThatThrownBy(() -> unavailable.getTaskStatus("t-1"))
                .isInstanceOf(CrawlerSystemException.class)
                .hasMessageContaining("未接入");
    }

    @Test
    void disabledProxyIsReportedAsUnavailable() {
        CrawlerSystemProperties properties = new CrawlerSystemProperties();
        properties.setBaseUrl(baseUrl);
        properties.setEnabled(false);
        CrawlerSystemClient disabled = new CrawlerSystemClient(properties, objectMapper);

        assertThat(disabled.isAvailable()).isFalse();
    }

    @Test
    void defaultPropertiesAreDisabledSoUndeployedCrawlerDoesNotMislead() {
        // 回归：默认配置（未设任何环境变量）必须是"未启用"，否则会表现为
        // "代理组件存在但一调就失败"的误导状态，也可能阻塞部署。
        CrawlerSystemProperties properties = new CrawlerSystemProperties();

        assertThat(properties.isEnabled()).isFalse();
        assertThat(properties.getBaseUrl()).isEmpty();
        assertThat(properties.isAvailable()).isFalse();
    }

    @Test
    void enabledWithoutBaseUrlIsUnavailable() {
        // 只开了开关却没给地址：属于配置不完整，仍必须判为不可用
        CrawlerSystemProperties properties = new CrawlerSystemProperties();
        properties.setEnabled(true);
        properties.setBaseUrl("");

        assertThat(properties.isAvailable()).isFalse();
    }

    @Test
    void enabledWithBaseUrlIsAvailable() {
        CrawlerSystemProperties properties = new CrawlerSystemProperties();
        properties.setEnabled(true);
        properties.setBaseUrl(baseUrl);

        assertThat(properties.isAvailable()).isTrue();
    }

    @Test
    void unreachableHostRaisesCrawlerSystemException() {
        CrawlerSystemProperties properties = new CrawlerSystemProperties();
        // 保留 1 端口几乎必然连接失败
        properties.setBaseUrl("http://127.0.0.1:1");
        properties.setConnectTimeoutMillis(500);
        CrawlerSystemClient broken = new CrawlerSystemClient(properties, objectMapper);

        assertThatThrownBy(() -> broken.getTaskStatus("t-1"))
                .isInstanceOf(CrawlerSystemException.class);
    }
}
