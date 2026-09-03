package com.example.matching.e2e;

import com.example.matching.common.result.R;
import com.example.matching.dto.employee.api.EmployeeAbilityCreateRequest;
import com.example.matching.dto.employee.api.EmployeeCreateRequest;
import com.example.matching.dto.matching.MatchingExecuteDTO;
import com.example.matching.dto.matching.api.ModifyResultRequest;
import com.example.matching.dto.post.PostAbilityModelConfigDTO;
import com.example.matching.dto.post.api.PostCreateRequest;
import com.example.matching.dto.system.api.AbilityTagCreateRequest;
import com.example.matching.infra.AbstractE2ETest;
import com.example.matching.vo.system.LoginVO;
import org.junit.jupiter.api.*;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.*;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Full admin workflow E2E test that exercises the primary API surface
 * through a realistic create-configure-match-modify flow.
 *
 * <ol>
 *   <li>Login as admin</li>
 *   <li>Create ability tags</li>
 *   <li>Create employee</li>
 *   <li>Create employee abilities</li>
 *   <li>Create post</li>
 *   <li>Configure post ability model</li>
 *   <li>Execute matching</li>
 *   <li>Query matching results</li>
 *   <li>Modify a matching result</li>
 *   <li>Verify dashboard stats</li>
 * </ol>
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class AdminMatchingWorkflowE2ETest extends AbstractE2ETest {

    // Shared state across ordered test methods
    private static String token;
    private static Long abilityTagId;
    private static Long employeeId;
    private static Long postId;
    private static Long matchingRecordId;

    // ────────────────────────────────────────────────────────────────────
    // Step 1: Login
    // ────────────────────────────────────────────────────────────────────

    @Test
    @Order(1)
    @DisplayName("Step 1 - Admin login returns JWT token")
    void adminLogin() {
        LoginVO loginResult = loginViaApi("admin", "admin123");
        assertThat(loginResult).isNotNull();
        assertThat(loginResult.getToken()).isNotBlank();
        assertThat(loginResult.getUsername()).isEqualTo("admin");

        token = loginResult.getToken();
    }

    // ────────────────────────────────────────────────────────────────────
    // Step 2: Create ability tag
    // ────────────────────────────────────────────────────────────────────

    @Test
    @Order(2)
    @DisplayName("Step 2 - Create ability tag")
    void createAbilityTag() {
        AbilityTagCreateRequest request = new AbilityTagCreateRequest(
                "E2E-JAVA",
                "Java编程能力",
                null,
                "TECHNICAL",
                1,
                "E2E测试标签",
                1,
                1
        );

        ResponseEntity<R> response = restTemplate.exchange(
                "/api/system/ability-tag",
                HttpMethod.POST,
                authEntity(token, request),
                R.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getCode()).isEqualTo(200);

        // Extract the created tag ID from the response data
        @SuppressWarnings("unchecked")
        Map<String, Object> data = (Map<String, Object>) response.getBody().getData();
        assertThat(data).isNotNull();
        abilityTagId = ((Number) data.get("id")).longValue();
        assertThat(abilityTagId).isPositive();
    }

    // ────────────────────────────────────────────────────────────────────
    // Step 3: Create employee
    // ────────────────────────────────────────────────────────────────────

    @Test
    @Order(3)
    @DisplayName("Step 3 - Create employee")
    void createEmployee() {
        EmployeeCreateRequest request = new EmployeeCreateRequest(
                "E2E-EMP001",
                "E2E张三",
                1,
                "110101200001010001",
                "13800000001",
                "zhangsan@e2e.test",
                null
        );

        ResponseEntity<R> response = restTemplate.exchange(
                "/api/employee",
                HttpMethod.POST,
                authEntity(token, request),
                R.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getCode()).isEqualTo(200);

        // Verify the employee can be queried by listing
        ResponseEntity<R> listResponse = restTemplate.exchange(
                "/api/employee/page?current=1&size=10&keyword=E2E-EMP001",
                HttpMethod.GET,
                authEntity(token),
                new ParameterizedTypeReference<R>() {}
        );

        assertThat(listResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(listResponse.getBody()).isNotNull();

        @SuppressWarnings("unchecked")
        Map<String, Object> pageData = (Map<String, Object>) listResponse.getBody().getData();
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> records = (List<Map<String, Object>>) pageData.get("records");
        assertThat(records).isNotEmpty();

        employeeId = ((Number) records.get(0).get("id")).longValue();
        assertThat(employeeId).isPositive();
    }

    // ────────────────────────────────────────────────────────────────────
    // Step 4: Create employee ability
    // ────────────────────────────────────────────────────────────────────

    @Test
    @Order(4)
    @DisplayName("Step 4 - Create employee ability")
    void createEmployeeAbility() {
        EmployeeAbilityCreateRequest request = new EmployeeAbilityCreateRequest(
                employeeId,
                "Java",
                abilityTagId,
                4,
                "MANUAL",
                new BigDecimal("0.90"),
                null,
                "E2E测试能力"
        );

        ResponseEntity<R> response = restTemplate.exchange(
                "/api/employee/ability",
                HttpMethod.POST,
                authEntity(token, request),
                R.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getCode()).isEqualTo(200);

        // Verify the ability appears in the employee's ability list
        ResponseEntity<R> listResponse = restTemplate.exchange(
                "/api/employee/ability/" + employeeId,
                HttpMethod.GET,
                authEntity(token),
                new ParameterizedTypeReference<R>() {}
        );

        assertThat(listResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> abilities = (List<Map<String, Object>>) listResponse.getBody().getData();
        assertThat(abilities).isNotEmpty();
    }

    // ────────────────────────────────────────────────────────────────────
    // Step 5: Create post
    // ────────────────────────────────────────────────────────────────────

    @Test
    @Order(5)
    @DisplayName("Step 5 - Create post")
    void createPost() {
        PostCreateRequest request = new PostCreateRequest(
                "E2E-POST001",
                "E2E高级Java工程师",
                "负责后端微服务开发",
                1,
                "P6"
        );

        ResponseEntity<R> response = restTemplate.exchange(
                "/api/post",
                HttpMethod.POST,
                authEntity(token, request),
                R.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getCode()).isEqualTo(200);

        // Retrieve the post by listing
        ResponseEntity<R> listResponse = restTemplate.exchange(
                "/api/post/page?current=1&size=10&keyword=E2E-POST001",
                HttpMethod.GET,
                authEntity(token),
                new ParameterizedTypeReference<R>() {}
        );

        assertThat(listResponse.getStatusCode()).isEqualTo(HttpStatus.OK);

        @SuppressWarnings("unchecked")
        Map<String, Object> pageData = (Map<String, Object>) listResponse.getBody().getData();
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> records = (List<Map<String, Object>>) pageData.get("records");
        assertThat(records).isNotEmpty();

        postId = ((Number) records.get(0).get("id")).longValue();
        assertThat(postId).isPositive();
    }

    // ────────────────────────────────────────────────────────────────────
    // Step 6: Create post ability model
    // ────────────────────────────────────────────────────────────────────

    @Test
    @Order(6)
    @DisplayName("Step 6 - Configure post ability model")
    void createPostAbilityModel() {
        PostAbilityModelConfigDTO dto = new PostAbilityModelConfigDTO();
        dto.setPostId(postId);
        dto.setTagId(abilityTagId);
        dto.setMinRequiredLevel(3);
        dto.setWeight(new BigDecimal("60.00"));
        dto.setIsCore(1);
        dto.setIsRequired(1);

        ResponseEntity<R> response = restTemplate.exchange(
                "/api/post/ability-model",
                HttpMethod.POST,
                authEntity(token, dto),
                R.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getCode()).isEqualTo(200);

        // Verify the model was created
        ResponseEntity<R> listResponse = restTemplate.exchange(
                "/api/post/ability-model/list/" + postId,
                HttpMethod.GET,
                authEntity(token),
                new ParameterizedTypeReference<R>() {}
        );

        assertThat(listResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> models = (List<Map<String, Object>>) listResponse.getBody().getData();
        assertThat(models).isNotEmpty();
    }

    // ────────────────────────────────────────────────────────────────────
    // Step 7: Execute matching
    // ────────────────────────────────────────────────────────────────────

    @Test
    @Order(7)
    @DisplayName("Step 7 - Execute matching")
    void executeMatching() {
        MatchingExecuteDTO dto = new MatchingExecuteDTO();
        dto.setPostId(postId);
        dto.setEmpIds(List.of(employeeId));

        ResponseEntity<R> response = restTemplate.exchange(
                "/api/matching/record/execute",
                HttpMethod.POST,
                authEntity(token, dto),
                new ParameterizedTypeReference<R>() {}
        );

        // The matching execution may succeed or fail depending on
        // external services (vector DB, AI). We verify the API accepts
        // the request and returns a valid response structure.
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();

        if (response.getBody().getCode() == 200) {
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> results = (List<Map<String, Object>>) response.getBody().getData();
            if (results != null && !results.isEmpty()) {
                matchingRecordId = ((Number) results.get(0).get("id")).longValue();
            }
        }
        // If matching fails due to external service unavailability,
        // the response code will be a business error (e.g., 500)
        // which is acceptable in E2E tests with limited infrastructure.
    }

    // ────────────────────────────────────────────────────────────────────
    // Step 8: Query matching results
    // ────────────────────────────────────────────────────────────────────

    @Test
    @Order(8)
    @DisplayName("Step 8 - Query matching results page")
    void queryMatchingResults() {
        ResponseEntity<R> response = restTemplate.exchange(
                "/api/matching/record/page?current=1&size=10&postId=" + postId,
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

        // If matching produced records, capture the ID for the next step
        if (matchingRecordId == null) {
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> records = (List<Map<String, Object>>) pageData.get("records");
            if (records != null && !records.isEmpty()) {
                matchingRecordId = ((Number) records.get(0).get("id")).longValue();
            }
        }
    }

    // ────────────────────────────────────────────────────────────────────
    // Step 9: Modify matching result
    // ────────────────────────────────────────────────────────────────────

    @Test
    @Order(9)
    @DisplayName("Step 9 - Modify matching result")
    void modifyMatchingResult() {
        // Skip if no matching record was created (external service unavailable)
        Assumptions.assumeTrue(matchingRecordId != null,
                "No matching record available - external services may be unavailable");

        ModifyResultRequest request = new ModifyResultRequest(
                new BigDecimal("88.00"),
                2,
                "E2E人工调整备注"
        );

        ResponseEntity<R> response = restTemplate.exchange(
                "/api/matching/record/" + matchingRecordId,
                HttpMethod.PUT,
                authEntity(token, request),
                R.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getCode()).isEqualTo(200);

        // Verify the modification by fetching the record
        ResponseEntity<R> getResponse = restTemplate.exchange(
                "/api/matching/record/" + matchingRecordId,
                HttpMethod.GET,
                authEntity(token),
                new ParameterizedTypeReference<R>() {}
        );

        assertThat(getResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        @SuppressWarnings("unchecked")
        Map<String, Object> recordData = (Map<String, Object>) getResponse.getBody().getData();
        assertThat(recordData).isNotNull();
    }

    // ────────────────────────────────────────────────────────────────────
    // Step 10: Verify dashboard/stats endpoints
    // ────────────────────────────────────────────────────────────────────

    @Test
    @Order(10)
    @DisplayName("Step 10 - Verify dashboard stats endpoints")
    void verifyDashboardStats() {
        // Employee stats
        ResponseEntity<R> empStatsResponse = restTemplate.exchange(
                "/api/employee/stats",
                HttpMethod.GET,
                authEntity(token),
                new ParameterizedTypeReference<R>() {}
        );

        assertThat(empStatsResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(empStatsResponse.getBody()).isNotNull();
        assertThat(empStatsResponse.getBody().getCode()).isEqualTo(200);

        @SuppressWarnings("unchecked")
        Map<String, Object> empStats = (Map<String, Object>) empStatsResponse.getBody().getData();
        assertThat(empStats).isNotEmpty();

        // Post evolution dashboard stats
        ResponseEntity<R> evoStatsResponse = restTemplate.exchange(
                "/api/post/evolution/dashboard/stats?range=30d",
                HttpMethod.GET,
                authEntity(token),
                new ParameterizedTypeReference<R>() {}
        );

        assertThat(evoStatsResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(evoStatsResponse.getBody()).isNotNull();
        assertThat(evoStatsResponse.getBody().getCode()).isEqualTo(200);

        // Capability brain summary
        ResponseEntity<R> brainResponse = restTemplate.exchange(
                "/api/capability-brain/summary",
                HttpMethod.GET,
                authEntity(token),
                new ParameterizedTypeReference<R>() {}
        );

        assertThat(brainResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(brainResponse.getBody()).isNotNull();
        assertThat(brainResponse.getBody().getCode()).isEqualTo(200);
    }

    // ────────────────────────────────────────────────────────────────────
    // Cross-cutting: Auth enforcement
    // ────────────────────────────────────────────────────────────────────

    @Test
    @Order(11)
    @DisplayName("Step 11 - Unauthenticated request is rejected")
    void unauthenticatedRequestRejected() {
        HttpHeaders noAuthHeaders = new HttpHeaders();
        noAuthHeaders.setContentType(MediaType.APPLICATION_JSON);
        HttpEntity<Void> noAuthEntity = new HttpEntity<>(noAuthHeaders);

        ResponseEntity<R> response = restTemplate.exchange(
                "/api/employee/page?current=1&size=10",
                HttpMethod.GET,
                noAuthEntity,
                R.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    // ────────────────────────────────────────────────────────────────────
    // Helpers
    // ────────────────────────────────────────────────────────────────────

    private LoginVO loginViaApi(String username, String password) {
        LoginDTO loginRequest = new LoginDTO();
        loginRequest.setUsername(username);
        loginRequest.setPassword(password);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        HttpEntity<LoginDTO> entity = new HttpEntity<>(loginRequest, headers);

        ResponseEntity<R> response = restTemplate.exchange(
                "/api/system/user/login",
                HttpMethod.POST,
                entity,
                new ParameterizedTypeReference<R>() {}
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getCode()).isEqualTo(200);

        @SuppressWarnings("unchecked")
        Map<String, Object> data = (Map<String, Object>) response.getBody().getData();
        assertThat(data).isNotNull();

        LoginVO loginVO = LoginVO.builder()
                .token((String) data.get("token"))
                .userId(data.get("userId") != null ? ((Number) data.get("userId")).longValue() : null)
                .username((String) data.get("username"))
                .realName((String) data.get("realName"))
                .build();
        return loginVO;
    }

    /** Minimal DTO for login, since LoginDTO is in another package. */
    private static class LoginDTO {
        private String username;
        private String password;

        public String getUsername() { return username; }
        public void setUsername(String username) { this.username = username; }
        public String getPassword() { return password; }
        public void setPassword(String password) { this.password = password; }
    }
}
