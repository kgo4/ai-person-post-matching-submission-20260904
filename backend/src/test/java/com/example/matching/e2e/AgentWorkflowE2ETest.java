package com.example.matching.e2e;

import com.example.matching.common.result.R;
import com.example.matching.infra.AbstractE2ETest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.*;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * E2E test exercising the Agent workflow endpoints.
 * <p>
 * Tests the post evolution agent run endpoint and verifies
 * that the agent API surface accepts requests and returns
 * properly structured responses.
 * <p>
 * Note: Actual AI/LLM agent execution requires external services
 * (Volcengine ARK, etc.) which are disabled in integration tests.
 * These tests verify API contract, auth, and response format.
 */
class AgentWorkflowE2ETest extends AbstractE2ETest {

    private String token;

    @BeforeEach
    void setUp() {
        token = loginAsAdmin();
    }

    @Test
    @DisplayName("Agent run endpoint accepts request and returns response")
    void agentRunEndpointAcceptsRequest() {
        // Construct an agent run request (minimum viable)
        String requestJson = """
                {
                    "postId": null,
                    "postName": "E2E测试岗位",
                    "industry": "互联网",
                    "triggerType": "MANUAL_RUN"
                }
                """;

        HttpHeaders headers = authHeaders(token);
        HttpEntity<String> entity = new HttpEntity<>(requestJson, headers);

        ResponseEntity<R> response = restTemplate.exchange(
                "/api/post/evolution/agent/run",
                HttpMethod.POST,
                entity,
                new ParameterizedTypeReference<R>() {}
        );

        // The agent endpoint should accept the request.
        // Response code depends on whether the agent service is available.
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();

        // If agent service is available, response code is 200 with data.
        // If unavailable, it may return a business error code.
        assertThat(response.getBody().getCode()).isIn(200, 500, 10501);
    }

    @Test
    @DisplayName("Agent memory governance page endpoint is accessible")
    void agentMemoryGovernancePageAccessible() {
        ResponseEntity<R> response = restTemplate.exchange(
                "/api/governance/agent-memory/page?pageNum=1&pageSize=10",
                HttpMethod.GET,
                authEntity(token),
                new ParameterizedTypeReference<R>() {}
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getCode()).isEqualTo(200);

        @SuppressWarnings("unchecked")
        Map<String, Object> pageData = (Map<String, Object>) response.getBody().getData();
        assertThat(pageData).containsKey("records");
        assertThat(pageData).containsKey("total");
    }

    @Test
    @DisplayName("Agent governance events page endpoint is accessible")
    void agentGovernanceEventsAccessible() {
        ResponseEntity<R> response = restTemplate.exchange(
                "/api/governance/agent-memory/events/page?pageNum=1&pageSize=10",
                HttpMethod.GET,
                authEntity(token),
                new ParameterizedTypeReference<R>() {}
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getCode()).isEqualTo(200);
    }

    @Test
    @DisplayName("Person ability governance memories endpoint is accessible")
    void abilityGovernanceMemoriesAccessible() {
        ResponseEntity<R> response = restTemplate.exchange(
                "/api/ability/governance/memories?scope=ALL",
                HttpMethod.GET,
                authEntity(token),
                new ParameterizedTypeReference<R>() {}
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getCode()).isEqualTo(200);
    }

    @Test
    @DisplayName("Agent run rejects unauthenticated request")
    void agentRunRejectsUnauthenticated() {
        String requestJson = """
                {
                    "triggerType": "MANUAL_RUN"
                }
                """;

        HttpHeaders noAuthHeaders = new HttpHeaders();
        noAuthHeaders.setContentType(MediaType.APPLICATION_JSON);
        HttpEntity<String> noAuthEntity = new HttpEntity<>(requestJson, noAuthHeaders);

        ResponseEntity<R> response = restTemplate.exchange(
                "/api/post/evolution/agent/run",
                HttpMethod.POST,
                noAuthEntity,
                R.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    @DisplayName("Evolution task page endpoint returns paginated structure")
    void evolutionTaskPageAccessible() {
        ResponseEntity<R> response = restTemplate.exchange(
                "/api/post/evolution/tasks/page?current=1&size=10",
                HttpMethod.GET,
                authEntity(token),
                new ParameterizedTypeReference<R>() {}
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getCode()).isEqualTo(200);

        @SuppressWarnings("unchecked")
        Map<String, Object> pageData = (Map<String, Object>) response.getBody().getData();
        assertThat(pageData).containsKey("records");
        assertThat(pageData).containsKey("total");
    }
}
