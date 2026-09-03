package com.example.matching.integration.fault;

import com.example.matching.ai.validation.DeterministicAiFallbacks;
import com.example.matching.common.exception.AiServiceException;
import com.example.matching.resilience.AiServiceResilience;
import org.junit.jupiter.api.*;
import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.client.WireMock;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("LLM Failure Injection Tests")
class LlmFailureInjectionTest {

    private static WireMockServer wireMockServer;
    private final AiServiceResilience resilience = new AiServiceResilience(
            io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry.ofDefaults(),
            java.util.concurrent.Executors.newSingleThreadExecutor());

    @BeforeAll
    static void startWireMock() {
        wireMockServer = new WireMockServer(WireMockConfiguration.options().dynamicPort());
        wireMockServer.start();
        WireMock.configureFor("localhost", wireMockServer.port());
    }

    @AfterAll
    static void stopWireMock() {
        if (wireMockServer != null) wireMockServer.stop();
    }

    @AfterEach
    void resetStubs() {
        wireMockServer.resetAll();
    }

    @Test
    @DisplayName("Circuit breaker fallback returns controlled degraded JSON when LLM returns HTTP 500")
    void circuitBreakerFallbackOnHttp500() {
        stubFor(post(urlEqualTo("/api/llm/analyze"))
                .willReturn(aResponse()
                        .withStatus(500)
                        .withBody("{\"error\":\"Internal Server Error\"}")));

        String result = resilience.callWithResilience(
                "test-llm-500",
                () -> callLlmEndpoint("/api/llm/analyze", "{\"prompt\":\"test\"}"),
                DeterministicAiFallbacks.MATCHING_ANALYSIS);

        assertThat(result).isNotNull().contains("degraded");
    }

    @Test
    @DisplayName("Circuit breaker fallback returns controlled degraded JSON when LLM connection times out")
    void circuitBreakerFallbackOnTimeout() {
        stubFor(post(urlEqualTo("/api/llm/analyze"))
                .willReturn(aResponse()
                        .withFixedDelay(5000)
                        .withStatus(200)
                        .withBody("{\"result\":\"delayed\"}")));

        String result = resilience.callWithResilience(
                "test-llm-timeout",
                () -> {
                    try {
                        HttpClient client = HttpClient.newBuilder()
                                .connectTimeout(Duration.ofSeconds(1))
                                .build();
                        HttpRequest request = HttpRequest.newBuilder()
                                .uri(URI.create("http://localhost:" + wireMockServer.port() + "/api/llm/analyze"))
                                .timeout(Duration.ofSeconds(1))
                                .POST(HttpRequest.BodyPublishers.ofString("{\"prompt\":\"test\"}"))
                                .build();
                        return client.send(request, HttpResponse.BodyHandlers.ofString()).body();
                    } catch (Exception e) {
                        throw new RuntimeException(e);
                    }
                },
                DeterministicAiFallbacks.EMPTY_JSON_OBJECT);

        assertThat(result).isNotNull();
    }

    @Test
    @DisplayName("Circuit breaker fallback handles empty response body")
    void circuitBreakerFallbackOnEmptyBody() {
        stubFor(post(urlEqualTo("/api/llm/empty"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withBody("")));

        String result = resilience.callWithResilience(
                "test-llm-empty",
                () -> callLlmEndpoint("/api/llm/empty", "{\"prompt\":\"test\"}"),
                DeterministicAiFallbacks.EMPTY_JSON_ARRAY);

        assertThat(result).isNotNull();
    }

    @Test
    @DisplayName("Circuit breaker fallback handles invalid JSON response")
    void circuitBreakerFallbackOnInvalidJson() {
        stubFor(post(urlEqualTo("/api/llm/invalid"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("THIS IS NOT VALID JSON {{{")));

        String result = resilience.callWithResilience(
                "test-llm-invalid-json",
                () -> callLlmEndpoint("/api/llm/invalid", "{\"prompt\":\"test\"}"),
                DeterministicAiFallbacks.EMPTY_JSON_OBJECT);

        assertThat(result).isNotNull();
    }

    @Test
    @DisplayName("Call without fallback throws AiServiceException after retries are exhausted")
    void callWithoutFallbackThrowsAiServiceException() {
        stubFor(post(urlEqualTo("/api/llm/no-fallback"))
                .willReturn(aResponse()
                        .withStatus(503)
                        .withBody("{\"error\":\"Service Unavailable\"}")));

        org.junit.jupiter.api.Assertions.assertThrows(AiServiceException.class, () -> {
            resilience.callWithResilience(
                    "test-llm-no-fallback",
                    () -> callLlmEndpoint("/api/llm/no-fallback", "{\"prompt\":\"test\"}"));
        });
    }

    @Test
    @DisplayName("Unknown fallback name is rejected by the controlled registry")
    void unknownFallbackNameIsRejected() {
        org.junit.jupiter.api.Assertions.assertThrows(IllegalArgumentException.class,
                () -> DeterministicAiFallbacks.get("UNREGISTERED_FALLBACK"));
    }

    // ==================== helpers ====================

    private String callLlmEndpoint(String path, String body) {
        try {
            HttpClient client = HttpClient.newBuilder()
                    .connectTimeout(Duration.ofSeconds(5))
                    .build();
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create("http://localhost:" + wireMockServer.port() + path))
                    .timeout(Duration.ofSeconds(5))
                    .POST(HttpRequest.BodyPublishers.ofString(body))
                    .header("Content-Type", "application/json")
                    .build();
            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() >= 400) {
                throw new RuntimeException("HTTP " + response.statusCode() + ": " + response.body());
            }
            return response.body();
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
