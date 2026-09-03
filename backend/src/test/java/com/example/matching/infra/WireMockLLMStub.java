package com.example.matching.infra;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.client.WireMock;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;

import static com.github.tomakehurst.wiremock.client.WireMock.*;

/**
 * WireMock stub for external LLM services.
 * <p>
 * Provides pre-configured responses for common AI service scenarios:
 * pure JSON, Markdown-wrapped JSON, invalid JSON, timeout, empty response.
 */
public class WireMockLLMStub implements AutoCloseable {

    private final WireMockServer server;

    public WireMockLLMStub() {
        this.server = new WireMockServer(WireMockConfiguration.options().dynamicPort());
        server.start();
        WireMock.configureFor("localhost", server.port());
    }

    public int getPort() {
        return server.port();
    }

    public String getBaseUrl() {
        return server.baseUrl();
    }

    /**
     * Stub a pure JSON LLM response.
     */
    public void stubPureJsonResponse(String responseBody) {
        server.stubFor(post(urlEqualTo("/v1/chat/completions"))
                .willReturn(aResponse()
                        .withHeader("Content-Type", "application/json")
                        .withBody(buildOpenAIResponse(responseBody))));
    }

    /**
     * Stub a Markdown-wrapped JSON response (LLM returns ```json ... ```).
     */
    public void stubMarkdownJsonResponse(String jsonBody) {
        String wrapped = "```json\n" + jsonBody + "\n```";
        server.stubFor(post(urlEqualTo("/v1/chat/completions"))
                .willReturn(aResponse()
                        .withHeader("Content-Type", "application/json")
                        .withBody(buildOpenAIResponse(wrapped))));
    }

    /**
     * Stub an invalid / non-JSON response.
     */
    public void stubInvalidJsonResponse() {
        server.stubFor(post(urlEqualTo("/v1/chat/completions"))
                .willReturn(aResponse()
                        .withHeader("Content-Type", "application/json")
                        .withBody(buildOpenAIResponse("This is not JSON at all!"))));
    }

    /**
     * Stub a timeout (response delayed beyond client timeout).
     */
    public void stubTimeout(int delayMs) {
        server.stubFor(post(urlEqualTo("/v1/chat/completions"))
                .willReturn(aResponse()
                        .withHeader("Content-Type", "application/json")
                        .withFixedDelay(delayMs)
                        .withBody(buildOpenAIResponse("{\"timeout\":true}"))));
    }

    /**
     * Stub an empty response body.
     */
    public void stubEmptyResponse() {
        server.stubFor(post(urlEqualTo("/v1/chat/completions"))
                .willReturn(aResponse()
                        .withHeader("Content-Type", "application/json")
                        .withBody(buildOpenAIResponse(""))));
    }

    /**
     * Stub an HTTP error response.
     */
    public void stubHttpError(int statusCode) {
        server.stubFor(post(urlEqualTo("/v1/chat/completions"))
                .willReturn(aResponse()
                        .withStatus(statusCode)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"error\":\"internal server error\"}")));
    }

    /**
     * Get number of requests made to the LLM endpoint.
     */
    public int getRequestCount() {
        return server.countRequestsMatching(
                postRequestedFor(urlEqualTo("/v1/chat/completions")).build()).getCount();
    }

    public void resetMappings() {
        server.resetMappings();
    }

    @Override
    public void close() {
        server.stop();
    }

    private String buildOpenAIResponse(String content) {
        return """
                {
                    "id": "chatcmpl-test",
                    "object": "chat.completion",
                    "choices": [
                        {
                            "index": 0,
                            "message": {
                                "role": "assistant",
                                "content": "%s"
                            },
                            "finish_reason": "stop"
                        }
                    ],
                    "usage": {"prompt_tokens": 10, "completion_tokens": 20, "total_tokens": 30}
                }
                """.formatted(content.replace("\"", "\\\"").replace("\n", "\\n"));
    }
}
