package com.example.matching.infra;

import com.example.matching.security.JwtTokenProvider;
import com.example.matching.security.UserDetailsServiceImpl;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.core.userdetails.UserDetails;

/**
 * Base class for end-to-end API tests.
 * <p>
 * Extends {@link AbstractIntegrationTest} to reuse container lifecycle
 * and adds a running Spring Boot application context with random port.
 * Provides helpers for JWT authentication and REST calls.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
public abstract class AbstractE2ETest extends AbstractIntegrationTest {

    @Autowired
    protected TestRestTemplate restTemplate;

    @Autowired
    protected JwtTokenProvider jwtTokenProvider;

    @Autowired
    protected UserDetailsServiceImpl userDetailsService;

    /**
     * Authenticate as admin and return JWT token.
     */
    protected String loginAsAdmin() {
        UserDetails admin = userDetailsService.loadUserByUsername("admin");
        // Extract userId from the user details (admin has ID 1 as created by DataInitializer)
        return jwtTokenProvider.generateToken(1L, admin.getUsername());
    }

    /**
     * Build Authorization header with Bearer token.
     */
    protected HttpHeaders authHeaders(String token) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(token);
        return headers;
    }

    /**
     * Build HttpEntity with auth headers and body.
     */
    protected <T> HttpEntity<T> authEntity(String token, T body) {
        return new HttpEntity<>(body, authHeaders(token));
    }

    /**
     * Build HttpEntity with auth headers only (no body).
     */
    protected HttpEntity<Void> authEntity(String token) {
        return new HttpEntity<>(authHeaders(token));
    }
}
