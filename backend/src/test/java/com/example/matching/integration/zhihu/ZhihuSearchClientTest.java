package com.example.matching.integration.zhihu;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ZhihuSearchClientTest {
    private HttpServer server;

    @AfterEach
    void stopServer() {
        if (server != null) server.stop(0);
    }

    @Test
    void parsesDataEnvelopeAndSendsAuthHeaders() throws Exception {
        AtomicInteger calls = new AtomicInteger();
        server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext("/api/v1/content/zhihu_search", exchange -> {
            calls.incrementAndGet();
            assertThat(exchange.getRequestHeaders().getFirst("Authorization")).isEqualTo("Bearer secret");
            assertThat(exchange.getRequestHeaders().getFirst("X-Request-Timestamp")).isNotBlank();
            write(exchange, 200, "{\"Data\":{\"HasMore\":false,\"SearchHashId\":\"h1\",\"Items\":[{\"Title\":\"AI\",\"ContentType\":\"ARTICLE\",\"ContentID\":\"1\",\"ContentText\":\"summary\",\"Url\":\"https://www.zhihu.com/question/1\"}]}}");
        });
        server.start();

        ZhihuApiProperties properties = properties("http://localhost:" + server.getAddress().getPort());
        ZhihuSearchResponse response = new ZhihuSearchClient(properties, new ObjectMapper()).search("AI", 8);

        assertThat(calls).hasValue(1);
        assertThat(response.items()).hasSize(1);
        assertThat(response.items().get(0).contentId()).isEqualTo("1");
    }

    @Test
    void retriesServerErrorsWithinBound() throws Exception {
        AtomicInteger calls = new AtomicInteger();
        server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext("/api/v1/content/zhihu_search", exchange -> {
            if (calls.incrementAndGet() == 1) write(exchange, 503, "down");
            else write(exchange, 200, "{\"Items\":[]}");
        });
        server.start();
        ZhihuApiProperties properties = properties("http://localhost:" + server.getAddress().getPort());
        properties.setMaxRetries(1);
        properties.setRetryBackoffMillis(0);

        ZhihuSearchResponse response = new ZhihuSearchClient(properties, new ObjectMapper()).search("AI", 8);

        assertThat(calls).hasValue(2);
        assertThat(response.items()).isEmpty();
    }

    @Test
    void rejectsOverlongQueryBeforeCallingUpstream() {
        ZhihuApiProperties properties = properties("http://localhost:1");
        properties.setMaxQueryLength(3);
        ZhihuSearchClient client = new ZhihuSearchClient(properties, new ObjectMapper());

        assertThatThrownBy(() -> client.search("long", 8))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("max length");
    }

    private ZhihuApiProperties properties(String baseUrl) {
        ZhihuApiProperties properties = new ZhihuApiProperties();
        properties.setEnabled(true);
        properties.setAccessSecret("secret");
        properties.setBaseUrl(baseUrl);
        properties.setConnectTimeoutMillis(1000);
        properties.setReadTimeoutMillis(1000);
        return properties;
    }

    private static void write(HttpExchange exchange, int status, String body) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.sendResponseHeaders(status, bytes.length);
        try (var output = exchange.getResponseBody()) {
            output.write(bytes);
        }
    }
}
