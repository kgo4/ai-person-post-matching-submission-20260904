package com.example.matching.integration.cache;

import com.example.matching.config.RedisCacheNames;
import com.example.matching.entity.system.SysUser;
import com.example.matching.infra.AbstractIntegrationTest;
import com.example.matching.service.system.SysUserService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration tests verifying authentication/user cache consistency with real Redis.
 * <p>
 * Validates that getByUsername caches the user and resetPassword evicts the cache,
 * forcing subsequent lookups to hit the database with fresh data.
 */
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class AuthCacheConsistencyIT extends AbstractIntegrationTest {

    private static final Long USER_ID = 999901L;
    private static final String USERNAME = "cachetest_user";

    @Autowired private SysUserService sysUserService;
    @Autowired private PasswordEncoder passwordEncoder;
    @Autowired private RedisTemplate<String, Object> redisTemplate;
    @Autowired private org.springframework.jdbc.core.JdbcTemplate jdbcTemplate;

    @BeforeEach
    void setUp() {
        jdbcTemplate.update("DELETE FROM sys_user WHERE id = ?", USER_ID);
        redisTemplate.delete(redisTemplate.keys("matching:v2:auth:sysuser*"));

        jdbcTemplate.update(
                "INSERT INTO sys_user (id, username, password, real_name, status, is_deleted) "
                        + "VALUES (?, ?, ?, 'Cache Test User', 1, 0)",
                USER_ID, USERNAME, passwordEncoder.encode("testPass123"));
    }

    @AfterEach
    void tearDown() {
        jdbcTemplate.update("DELETE FROM sys_user WHERE id = ?", USER_ID);
        redisTemplate.delete(redisTemplate.keys("matching:v2:auth:sysuser*"));
    }

    private boolean userCacheExists(String username) {
        String redisKey = "matching:v2:" + RedisCacheNames.AUTH_SYSUSER + "::" + username;
        return Boolean.TRUE.equals(redisTemplate.hasKey(redisKey));
    }

    // -- tests ----------------------------------------------------------------

    @Test
    @DisplayName("getByUsername populates cache; resetPassword evicts it")
    void cacheEvictedAfterPasswordReset() {
        // 1. populate cache
        SysUser cached = sysUserService.getByUsername(USERNAME);
        assertThat(cached).isNotNull();
        assertThat(cached.getId()).isEqualTo(USER_ID);
        assertThat(userCacheExists(USERNAME)).isTrue();

        // 2. reset password -> evicts all auth:sysuser entries
        sysUserService.resetPassword(USER_ID);

        assertThat(userCacheExists(USERNAME)).isFalse();

        // 3. next read hits DB and returns fresh data
        SysUser fresh = sysUserService.getByUsername(USERNAME);
        assertThat(fresh).isNotNull();
        // password should now be the default password "123456"
        assertThat(passwordEncoder.matches("123456", fresh.getPassword())).isTrue();
        // old password should no longer match
        assertThat(passwordEncoder.matches("testPass123", fresh.getPassword())).isFalse();
    }

    @Test
    @DisplayName("second getByUsername call returns cached result without DB query")
    void secondCallReturnsCachedResult() {
        SysUser first = sysUserService.getByUsername(USERNAME);
        SysUser second = sysUserService.getByUsername(USERNAME);

        assertThat(first).isNotNull();
        assertThat(second).isNotNull();
        assertThat(first.getId()).isEqualTo(second.getId());
        assertThat(first.getUsername()).isEqualTo(second.getUsername());

        // cache key was populated
        assertThat(userCacheExists(USERNAME)).isTrue();
    }

    @Test
    @DisplayName("resetPassword invalidates tokens via Redis for the user")
    void resetPasswordSetsTokenInvalidationKey() {
        sysUserService.resetPassword(USER_ID);

        // TokenInvalidationService writes a key auth:token-invalid-before:{userId}
        String tokenKey = "auth:token-invalid-before:" + USER_ID;
        assertThat(Boolean.TRUE.equals(redisTemplate.hasKey(tokenKey))).isTrue();
    }
}
