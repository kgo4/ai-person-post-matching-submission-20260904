package com.example.matching.config;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class WebSocketOriginPatternsTest {

    @Test
    void parsesAllConfiguredOriginsForWebSocketHandshake() {
        assertThat(WebSocketOriginPatterns.fromCommaSeparated(
                "http://129.211.182.128, https://hunyuanfajian.xyz, https://www.hunyuanfajian.xyz"))
                .containsExactly(
                        "http://129.211.182.128",
                        "https://hunyuanfajian.xyz",
                        "https://www.hunyuanfajian.xyz");
    }
}
