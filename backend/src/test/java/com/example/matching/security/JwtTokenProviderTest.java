package com.example.matching.security;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;

class JwtTokenProviderTest {

    private JwtTokenProvider provider;

    @BeforeEach
    void setUp() {
        provider = new JwtTokenProvider();
        ReflectionTestUtils.setField(provider, "secret", "test-signing-key-that-is-long-enough-for-hmac-sha256");
        ReflectionTestUtils.setField(provider, "expiration", 60_000L);
    }

    @Test
    void generatesAndReadsAValidToken() {
        String token = provider.generateToken(8L, "pengchao");

        assertThat(provider.validateToken(token)).isTrue();
        assertThat(provider.getUserIdFromToken(token)).isEqualTo(8L);
        assertThat(provider.getUsernameFromToken(token)).isEqualTo("pengchao");
        assertThat(provider.getIssuedAtFromToken(token)).isNotNull();
    }

    @Test
    void rejectsMalformedOrExpiredTokens() {
        assertThat(provider.validateToken("not-a-token")).isFalse();

        ReflectionTestUtils.setField(provider, "expiration", -1L);
        assertThat(provider.validateToken(provider.generateToken(8L, "expired"))).isFalse();
    }
}
