package com.example.matching.config;

import com.example.matching.security.JwtTokenProvider;
import com.example.matching.security.UserDetailsServiceImpl;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.ResponseEntity;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = SecurityConfigTest.TestController.class)
@Import({SecurityConfig.class, SecurityConfigTest.TestBeans.class, SecurityConfigTest.TestController.class})
class SecurityConfigTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private SecurityProperties securityProperties;

    @Test
    void websocketInterviewPathDoesNotRequireJwt() throws Exception {
        mockMvc.perform(get("/ws/interview/{sessionId}", 9L))
                .andExpect(status().isOk());
    }

    @Test
    void protectedApiStillRequiresJwt() throws Exception {
        mockMvc.perform(get("/api/protected"))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "USER")
    void ordinaryUserCannotMutateAgentMemory() throws Exception {
        mockMvc.perform(put("/api/governance/agent-memory/{id}", 9L))
                .andExpect(status().isForbidden());
    }

    @Test
    void shouldAllowStandardHeaders() {
        String[] allowed = securityProperties.getAllowedHeaders().split(",");
        assertThat(allowed).contains("Authorization", "Content-Type", "X-Requested-With", "X-Trace-Id", "Idempotency-Key");
    }

    @Test
    @WithMockUser(roles = "USER")
    void regularUserCannotReadEmployeePage() throws Exception {
        mockMvc.perform(get("/api/employee/page"))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "USER")
    void regularUserCannotWriteAbilityGovernance() throws Exception {
        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post(
                "/api/ability/governance/change-level")
                .contentType("application/json")
                .content("{}"))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void adminCanReadEmployeePage() throws Exception {
        mockMvc.perform(get("/api/employee/page"))
                .andExpect(status().isOk());
    }

    @Test
    void shouldRejectUnknownHeader() {
        String[] allowed = securityProperties.getAllowedHeaders().split(",");
        assertThat(allowed).doesNotContain("X-Admin-Bypass");
    }

    @RestController
    static class TestController {

        @GetMapping("/ws/interview/{sessionId}")
        ResponseEntity<String> websocketPath(@PathVariable Long sessionId) {
            return ResponseEntity.ok("ws-" + sessionId);
        }

        @GetMapping("/api/protected")
        ResponseEntity<String> protectedApi() {
            return ResponseEntity.ok("protected");
        }

        @PutMapping("/api/governance/agent-memory/{id}")
        ResponseEntity<String> updateAgentMemory(@PathVariable Long id) {
            return ResponseEntity.ok("updated-" + id);
        }

        @GetMapping("/api/employee/page")
        ResponseEntity<String> employeePage() {
            return ResponseEntity.ok("employee-page");
        }

        @PostMapping("/api/ability/governance/change-level")
        ResponseEntity<String> changeLevel(@RequestBody String body) {
            return ResponseEntity.ok("changed");
        }
    }

    @TestConfiguration
    static class TestBeans {

        @Bean
        JwtAuthenticationFilter jwtAuthenticationFilter() {
            return new JwtAuthenticationFilter(mock(JwtTokenProvider.class), mock(UserDetailsServiceImpl.class),
                    mock(com.example.matching.security.TokenInvalidationService.class));
        }

        @Bean
        SecurityProperties securityProperties() {
            SecurityProperties props = new SecurityProperties();
            props.setAllowedOrigins("http://localhost:3000");
            props.setAllowedHeaders("Authorization,Content-Type,X-Requested-With,X-Trace-Id,Idempotency-Key");
            return props;
        }
    }

    @SpringBootApplication
    static class TestApplication {
    }
}
