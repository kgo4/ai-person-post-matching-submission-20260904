package com.example.matching.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.Arrays;
import java.util.List;

/**
 * Spring Security 配置。
 * <p>
 * 配置公开访问的端点和需要认证的端点。
 */
@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class SecurityConfig {

    private final JwtAuthenticationFilter jwtAuthenticationFilter;
    private final SecurityProperties securityProperties;

    public SecurityConfig(JwtAuthenticationFilter jwtAuthenticationFilter, SecurityProperties securityProperties) {
        this.jwtAuthenticationFilter = jwtAuthenticationFilter;
        this.securityProperties = securityProperties;
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration config = new CorsConfiguration();
        List<String> origins = Arrays.stream(securityProperties.getAllowedOrigins().split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .toList();
        config.setAllowedOrigins(origins);
        config.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "OPTIONS"));
        String[] allowedHeaders = (securityProperties.getAllowedHeaders() != null
                && !securityProperties.getAllowedHeaders().isBlank())
                ? securityProperties.getAllowedHeaders().split(",")
                : new String[]{"Authorization", "Content-Type", "X-Requested-With", "X-Trace-Id", "Idempotency-Key"};
        config.setAllowedHeaders(Arrays.asList(allowedHeaders));
        config.setAllowCredentials(true);
        config.setMaxAge(3600L);
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);
        return source;
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
                .cors(cors -> cors.configurationSource(corsConfigurationSource()))
                .csrf(AbstractHttpConfigurer::disable)
                .headers(headers -> headers
                        .contentTypeOptions(contentTypeOptions -> {})
                        .frameOptions(frameOptions -> frameOptions.deny())
                        .httpStrictTransportSecurity(hsts -> hsts
                                .includeSubDomains(true)
                                .maxAgeInSeconds(31_536_000))
                        .contentSecurityPolicy(csp -> csp.policyDirectives(
                                "default-src 'self'; frame-ancestors 'none'; base-uri 'self'; object-src 'none'")))
                .sessionManagement(session -> session
                        .sessionCreationPolicy(SessionCreationPolicy.STATELESS)
                )
                .authorizeHttpRequests(auth -> auth
                        // 公开访问的端点
                        .requestMatchers(
                                "/api/system/user/login",
                                "/api/system/user/register",
                                // Container and platform liveness/readiness probe.
                                "/actuator/health",
                                // WebSocket 握手端点（由 InterviewWebSocketAuthInterceptor 单独鉴权）
                                "/ws/**",
                                // Swagger/Knife4j API文档
                                // 静态资源
                                "/static/**",
                                "/public/**",
                                "/favicon.ico"
                        ).permitAll()
                        // 其他所有请求需要认证
                        .requestMatchers("/api/system/role/**").hasRole("ADMIN")
                        .requestMatchers("/actuator/**").hasRole("ADMIN")
                        .requestMatchers("/api/system/dlq/**").hasRole("ADMIN")
                        .requestMatchers("/api/matching/outbox/**").hasRole("ADMIN")
                        .requestMatchers("/api/post/evolution/**").hasRole("ADMIN")
                        .requestMatchers(HttpMethod.POST, "/api/governance/agent-memory/**").hasRole("ADMIN")
                        .requestMatchers(HttpMethod.PUT, "/api/governance/agent-memory/**").hasRole("ADMIN")
                        .requestMatchers(HttpMethod.DELETE, "/api/governance/agent-memory/**").hasRole("ADMIN")
                        .requestMatchers(HttpMethod.GET, "/api/system/user/current").authenticated()
                        .requestMatchers(HttpMethod.PUT, "/api/system/user/change-password").authenticated()
                        .requestMatchers(HttpMethod.GET, "/api/system/user/page").hasRole("ADMIN")
                        .requestMatchers(HttpMethod.POST, "/api/system/user").hasRole("ADMIN")
                        .requestMatchers(HttpMethod.GET, "/api/system/user/*").hasRole("ADMIN")
                        .requestMatchers(HttpMethod.PUT, "/api/system/user/*").hasRole("ADMIN")
                        .requestMatchers(HttpMethod.DELETE, "/api/system/user/*").hasRole("ADMIN")
                        // 管理员写操作：匹配执行、训练、权重、治理、图谱
                        // 实际执行端点位于 /api/matching/record/**（旧规则 /api/matching/execute 与真实路径不匹配，已修正）
                        .requestMatchers(HttpMethod.POST, "/api/matching/record/execute").hasRole("ADMIN")
                        .requestMatchers(HttpMethod.POST, "/api/matching/record/execute-async").hasRole("ADMIN")
                        .requestMatchers(HttpMethod.POST, "/api/matching/record/{id}/retry-ai-scoring").hasRole("ADMIN")
                        .requestMatchers(HttpMethod.PUT, "/api/matching/record/**").hasRole("ADMIN")
                        .requestMatchers(HttpMethod.DELETE, "/api/matching/record/**").hasRole("ADMIN")
                        .requestMatchers("/api/system/tag-governance/**").hasRole("ADMIN")
                        .requestMatchers("/api/system/ai-model-config/**").hasRole("ADMIN")
                        .requestMatchers("/api/matching/scoring-config/**").hasRole("ADMIN")
                        .requestMatchers("/api/matching/calibration/export").hasRole("ADMIN")
                        .requestMatchers("/api/admin/**").hasRole("ADMIN")
                        .requestMatchers(HttpMethod.POST, "/api/system/source-weight/**").hasRole("ADMIN")
                        .requestMatchers(HttpMethod.PUT, "/api/system/source-weight/**").hasRole("ADMIN")
                        .requestMatchers(HttpMethod.POST, "/api/employee/ability/governance/**").hasRole("ADMIN")
                        .requestMatchers(HttpMethod.PUT, "/api/employee/ability/governance/**").hasRole("ADMIN")
                        .requestMatchers(HttpMethod.POST, "/api/kg/graph/**").hasRole("ADMIN")
                        // Web 管理端：防止普通注册用户横向越权
                        .requestMatchers("/api/employee/**").hasRole("ADMIN")
                        .requestMatchers("/api/post/**").hasRole("ADMIN")
                        .requestMatchers("/api/contest/**").hasRole("ADMIN")
                        .requestMatchers("/api/rag/**").hasRole("ADMIN")
                        .requestMatchers("/api/kg/**").hasRole("ADMIN")
                        .requestMatchers("/api/ability/**").hasRole("ADMIN")
                        .requestMatchers("/api/learning/**").hasRole("ADMIN")
                        .requestMatchers("/api/vector/**").hasRole("ADMIN")
                        .requestMatchers("/api/ai-interview/**").hasRole("ADMIN")
                        .requestMatchers("/api/capability-closure/**").hasRole("ADMIN")
                        .requestMatchers("/api/capability-brain/**").hasRole("ADMIN")
                        .requestMatchers("/api/ai-governance/**").hasRole("ADMIN")
                        .requestMatchers("/api/test/**").hasRole("ADMIN")
                        // 本地上传文件（简历、资源封面等）静态访问无需认证
                        .requestMatchers("/uploads/**").permitAll()
                        .anyRequest().authenticated()
                )
                .formLogin(AbstractHttpConfigurer::disable)
                .httpBasic(AbstractHttpConfigurer::disable)
                .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);
        return http.build();
    }

}
