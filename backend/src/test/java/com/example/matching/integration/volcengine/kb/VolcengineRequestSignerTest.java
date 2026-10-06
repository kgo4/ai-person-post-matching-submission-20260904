package com.example.matching.integration.volcengine.kb;

import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class VolcengineRequestSignerTest {

    @Test
    void signBuildsDeterministicVolcengineAuthorizationHeader() {
        Clock clock = Clock.fixed(Instant.parse("2026-06-02T08:30:00Z"), ZoneOffset.UTC);
        VolcengineRequestSigner signer = new VolcengineRequestSigner(
                "test-ak",
                "test-sk",
                "cn-beijing",
                "air",
                clock
        );

        String body = "{\"query\":\"java engineer\"}";
        Map<String, String> headers = signer.sign(
                "POST",
                "/api/knowledge/collection/search_knowledge",
                body
        );

        assertThat(headers).containsEntry("X-Date", "20260602T083000Z");
        assertThat(headers).containsEntry("Content-Type", "application/json");
        assertThat(headers.get("Authorization"))
                .startsWith("HMAC-SHA256 Credential=test-ak/20260602/cn-beijing/air/request, SignedHeaders=content-type;x-date, Signature=");
        assertThat(headers.get("Authorization")).doesNotContain("test-sk");
    }
}
