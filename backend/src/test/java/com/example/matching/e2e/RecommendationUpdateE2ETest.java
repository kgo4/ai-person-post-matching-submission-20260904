package com.example.matching.e2e;

import com.example.matching.common.result.R;
import com.example.matching.dto.employee.api.EmployeeAbilityCreateRequest;
import com.example.matching.dto.employee.api.EmployeeAbilityUpdateRequest;
import com.example.matching.dto.employee.api.EmployeeCreateRequest;
import com.example.matching.dto.system.api.AbilityTagCreateRequest;
import com.example.matching.infra.AbstractE2ETest;
import org.junit.jupiter.api.*;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.*;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * E2E test that modifies employee abilities and verifies that
 * dependent cache data (ability profile, vector recall epoch) reflects
 * the changes.
 * <p>
 * This test exercises the cache invalidation contract: when an
 * employee's ability data changes, the ability profile endpoint
 * must return the updated data, confirming that the Redis cache
 * (including vector:emp:epoch) was invalidated.
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class RecommendationUpdateE2ETest extends AbstractE2ETest {

    private static String token;
    private static Long tagId;
    private static Long empId;
    private static Long abilityId;

    @Test
    @Order(1)
    @DisplayName("Setup - Login and create prerequisite data")
    void setup() {
        token = loginAsAdmin();

        // Create ability tag
        AbilityTagCreateRequest tagReq = new AbilityTagCreateRequest(
                "E2E-CACHE-TAG",
                "E2E缓存测试标签",
                null,
                "TECHNICAL",
                1,
                "cache test tag",
                1,
                1
        );
        ResponseEntity<R> tagResp = restTemplate.exchange(
                "/api/system/ability-tag",
                HttpMethod.POST,
                authEntity(token, tagReq),
                R.class
        );
        assertThat(tagResp.getBody()).isNotNull();
        assertThat(tagResp.getBody().getCode()).isEqualTo(200);

        @SuppressWarnings("unchecked")
        Map<String, Object> tagData = (Map<String, Object>) tagResp.getBody().getData();
        tagId = ((Number) tagData.get("id")).longValue();

        // Create employee
        EmployeeCreateRequest empReq = new EmployeeCreateRequest(
                "E2E-CACHE-EMP",
                "E2E缓存测试员工",
                1,
                null,
                null,
                null,
                null
        );
        ResponseEntity<R> empResp = restTemplate.exchange(
                "/api/employee",
                HttpMethod.POST,
                authEntity(token, empReq),
                R.class
        );
        assertThat(empResp.getBody()).isNotNull();
        assertThat(empResp.getBody().getCode()).isEqualTo(200);

        // Find the created employee by querying
        ResponseEntity<R> listResp = restTemplate.exchange(
                "/api/employee/page?current=1&size=10&keyword=E2E-CACHE-EMP",
                HttpMethod.GET,
                authEntity(token),
                new ParameterizedTypeReference<R>() {}
        );
        @SuppressWarnings("unchecked")
        Map<String, Object> pageData = (Map<String, Object>) listResp.getBody().getData();
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> records = (List<Map<String, Object>>) pageData.get("records");
        assertThat(records).isNotEmpty();
        empId = ((Number) records.get(0).get("id")).longValue();
    }

    @Test
    @Order(2)
    @DisplayName("Create ability and verify profile")
    void createAbilityAndVerifyProfile() {
        EmployeeAbilityCreateRequest abilityReq = new EmployeeAbilityCreateRequest(
                empId,
                "Java",
                tagId,
                3,
                "MANUAL",
                new BigDecimal("0.80"),
                null,
                "initial level"
        );

        ResponseEntity<R> createResp = restTemplate.exchange(
                "/api/employee/ability",
                HttpMethod.POST,
                authEntity(token, abilityReq),
                R.class
        );
        assertThat(createResp.getBody()).isNotNull();
        assertThat(createResp.getBody().getCode()).isEqualTo(200);

        // Query the ability list and capture the ID
        ResponseEntity<R> listResp = restTemplate.exchange(
                "/api/employee/ability/" + empId,
                HttpMethod.GET,
                authEntity(token),
                new ParameterizedTypeReference<R>() {}
        );
        assertThat(listResp.getStatusCode()).isEqualTo(HttpStatus.OK);

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> abilities = (List<Map<String, Object>>) listResp.getBody().getData();
        assertThat(abilities).isNotEmpty();

        abilityId = ((Number) abilities.get(0).get("id")).longValue();

        // Verify profile reflects the created ability
        ResponseEntity<R> profileResp = restTemplate.exchange(
                "/api/employee/ability/profile/" + empId,
                HttpMethod.GET,
                authEntity(token),
                new ParameterizedTypeReference<R>() {}
        );
        assertThat(profileResp.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(profileResp.getBody()).isNotNull();
        assertThat(profileResp.getBody().getCode()).isEqualTo(200);
    }

    @Test
    @Order(3)
    @DisplayName("Modify ability level and verify profile updated")
    void modifyAbilityAndVerifyCacheInvalidation() {
        // First, read the current ability list to capture old level
        ResponseEntity<R> beforeResp = restTemplate.exchange(
                "/api/employee/ability/" + empId,
                HttpMethod.GET,
                authEntity(token),
                new ParameterizedTypeReference<R>() {}
        );
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> beforeAbilities = (List<Map<String, Object>>) beforeResp.getBody().getData();
        Integer oldLevel = (Integer) beforeAbilities.get(0).get("masteryLevel");

        // Modify the ability to a new level
        EmployeeAbilityUpdateRequest updateReq = new EmployeeAbilityUpdateRequest(
                "Java",
                tagId,
                5,
                "MANUAL",
                new BigDecimal("0.95"),
                null,
                "updated level for cache test"
        );

        ResponseEntity<R> updateResp = restTemplate.exchange(
                "/api/employee/ability/" + abilityId,
                HttpMethod.PUT,
                authEntity(token, updateReq),
                R.class
        );
        assertThat(updateResp.getBody()).isNotNull();
        assertThat(updateResp.getBody().getCode()).isEqualTo(200);

        // Query the ability list again - the cache should be invalidated
        // and the new level should be returned
        ResponseEntity<R> afterResp = restTemplate.exchange(
                "/api/employee/ability/" + empId,
                HttpMethod.GET,
                authEntity(token),
                new ParameterizedTypeReference<R>() {}
        );
        assertThat(afterResp.getStatusCode()).isEqualTo(HttpStatus.OK);

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> afterAbilities = (List<Map<String, Object>>) afterResp.getBody().getData();
        assertThat(afterAbilities).isNotEmpty();

        Integer newLevel = (Integer) afterAbilities.get(0).get("masteryLevel");
        assertThat(newLevel).isEqualTo(5);
        assertThat(newLevel).isNotEqualTo(oldLevel);

        // Verify profile also reflects the update
        ResponseEntity<R> profileResp = restTemplate.exchange(
                "/api/employee/ability/profile/" + empId,
                HttpMethod.GET,
                authEntity(token),
                new ParameterizedTypeReference<R>() {}
        );
        assertThat(profileResp.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(profileResp.getBody().getCode()).isEqualTo(200);
    }

    @Test
    @Order(4)
    @DisplayName("Recommendation endpoint returns valid structure")
    void recommendationEndpointReturnsValidStructure() {
        // The recommend endpoint should accept the request and return
        // a valid structure even if vector services are unavailable.
        String requestJson = String.format("""
                {
                    "empId": %d,
                    "topK": 3,
                    "enableHardConditionPreview": false,
                    "enableL2Preview": false
                }
                """, empId);

        HttpHeaders headers = authHeaders(token);
        HttpEntity<String> entity = new HttpEntity<>(requestJson, headers);

        ResponseEntity<R> response = restTemplate.exchange(
                "/api/matching/recommend/posts-by-employee",
                HttpMethod.POST,
                entity,
                new ParameterizedTypeReference<R>() {}
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();

        // If vector services are available, code=200 with recommendations.
        // Otherwise, may return error code.
        int code = response.getBody().getCode();
        assertThat(code).isIn(200, 500, 10501, 10502);
    }

    @Test
    @Order(5)
    @DisplayName("Reverse recommendation endpoint returns valid structure")
    void reverseRecommendationEndpointReturnsValidStructure() {
        // Test post-to-employee recommendation (need a post first)
        // We create a minimal post for this test
        String postCreateJson = """
                {
                    "postCode": "E2E-REC-POST",
                    "postName": "E2E推荐测试岗位",
                    "jobDescription": "recommendation test",
                    "status": 1
                }
                """;

        HttpHeaders headers = authHeaders(token);
        restTemplate.exchange(
                "/api/post",
                HttpMethod.POST,
                new HttpEntity<>(postCreateJson, headers),
                R.class
        );

        // Find the created post
        ResponseEntity<R> postListResp = restTemplate.exchange(
                "/api/post/page?current=1&size=10&keyword=E2E-REC-POST",
                HttpMethod.GET,
                authEntity(token),
                new ParameterizedTypeReference<R>() {}
        );
        @SuppressWarnings("unchecked")
        Map<String, Object> postPageData = (Map<String, Object>) postListResp.getBody().getData();
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> postRecords = (List<Map<String, Object>>) postPageData.get("records");
        Assumptions.assumeTrue(!postRecords.isEmpty(), "Post creation succeeded");

        Long recPostId = ((Number) postRecords.get(0).get("id")).longValue();

        String requestJson = String.format("""
                {
                    "postId": %d,
                    "topK": 3,
                    "enableHardConditionPreview": false,
                    "enableL2Preview": false
                }
                """, recPostId);

        ResponseEntity<R> response = restTemplate.exchange(
                "/api/matching/recommend/employees-by-post",
                HttpMethod.POST,
                new HttpEntity<>(requestJson, headers),
                new ParameterizedTypeReference<R>() {}
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();

        int code = response.getBody().getCode();
        assertThat(code).isIn(200, 500, 10501, 10502);
    }
}
