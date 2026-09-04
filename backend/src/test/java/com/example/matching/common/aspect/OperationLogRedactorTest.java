package com.example.matching.common.aspect;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class OperationLogRedactorTest {

    private final OperationLogRedactor redactor = new OperationLogRedactor(new ObjectMapper());

    @Test
    void recognizesOnlyConfiguredSensitiveEndpoints() {
        assertThat(redactor.isSensitiveEndpoint("/api/system/user/login?redirect=/home")).isTrue();
        assertThat(redactor.isSensitiveEndpoint("/api/system/user/change-password")).isTrue();
        assertThat(redactor.isSensitiveEndpoint("/api/post/list")).isFalse();
        assertThat(redactor.isSensitiveEndpoint(null)).isFalse();
    }

    @Test
    void redactsSensitiveFieldsAtEveryNestedLevel() {
        Map<String, Object> payload = Map.of(
                "username", "pengchao",
                "password", "p@ss",
                "nested", Map.of("secret", "key", "items", List.of(Map.of("token", "jwt"))));
        String body = redactor.redactRequestBody(new Object[]{payload});

        assertThat(body).contains("[REDACTED]");
        assertThat(body).doesNotContain("p@ss").doesNotContain("\"key\"").doesNotContain("\"jwt\"");
        assertThat(body).contains("pengchao");
    }

    @Test
    void redactsResponsesAndReportsSerializationFailureSafely() {
        String response = redactor.redactResponseBody(Map.of("token", "token-value", "name", "visible"));

        assertThat(response).contains("[REDACTED]").contains("visible").doesNotContain("token-value");
        assertThat(redactor.redactResponseBody(new Object() {
            public Object getLoop() { return this; }
        })).isEqualTo("[serialization error]");
    }
}
