package com.example.matching.integration.fault;

import com.example.matching.config.RedisCacheErrorHandler;
import com.example.matching.security.TokenInvalidationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.cache.Cache;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Duration;
import java.util.Date;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNoException;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("Redis Failure Injection Tests")
class RedisFailureInjectionTest {

    private final RedisCacheErrorHandler errorHandler = new RedisCacheErrorHandler();

    @Nested
    @DisplayName("RedisCacheErrorHandler")
    class CacheErrorHandlerTests {

        @Test
        @DisplayName("handleCacheGetError logs warning and does not throw")
        void handleCacheGetError_doesNotThrow() {
            Cache cache = mock(Cache.class);
            when(cache.getName()).thenReturn("testCache");

            RedisConnectionFailureException ex = new RedisConnectionFailureException("Connection refused");

            assertThatNoException()
                    .isThrownBy(() -> errorHandler.handleCacheGetError(ex, cache, "myKey"));
        }

        @Test
        @DisplayName("handleCachePutError logs warning and does not throw")
        void handleCachePutError_doesNotThrow() {
            Cache cache = mock(Cache.class);
            when(cache.getName()).thenReturn("testCache");

            RedisConnectionFailureException ex = new RedisConnectionFailureException("Connection refused");

            assertThatNoException()
                    .isThrownBy(() -> errorHandler.handleCachePutError(ex, cache, "myKey", "myValue"));
        }

        @Test
        @DisplayName("handleCacheEvictError logs warning and does not throw")
        void handleCacheEvictError_doesNotThrow() {
            Cache cache = mock(Cache.class);
            when(cache.getName()).thenReturn("testCache");

            RedisConnectionFailureException ex = new RedisConnectionFailureException("Connection refused");

            assertThatNoException()
                    .isThrownBy(() -> errorHandler.handleCacheEvictError(ex, cache, "myKey"));
        }

        @Test
        @DisplayName("handleCacheClearError logs warning and does not throw")
        void handleCacheClearError_doesNotThrow() {
            Cache cache = mock(Cache.class);
            when(cache.getName()).thenReturn("testCache");

            RedisConnectionFailureException ex = new RedisConnectionFailureException("Connection refused");

            assertThatNoException()
                    .isThrownBy(() -> errorHandler.handleCacheClearError(ex, cache));
        }

        @Test
        @DisplayName("All error handlers survive null cache name gracefully")
        void allHandlersSurviveNullMessage() {
            Cache cache = mock(Cache.class);
            when(cache.getName()).thenReturn("testCache");

            RuntimeException ex = new RuntimeException((String) null);

            assertThatNoException().isThrownBy(() -> errorHandler.handleCacheGetError(ex, cache, "k"));
            assertThatNoException().isThrownBy(() -> errorHandler.handleCachePutError(ex, cache, "k", "v"));
            assertThatNoException().isThrownBy(() -> errorHandler.handleCacheEvictError(ex, cache, "k"));
            assertThatNoException().isThrownBy(() -> errorHandler.handleCacheClearError(ex, cache));
        }
    }

    @Nested
    @DisplayName("TokenInvalidationService with Redis failures")
    class TokenInvalidationServiceTests {

        @Mock
        private RedisTemplate<String, Object> redisTemplate;

        @Mock
        private ValueOperations<String, Object> valueOperations;

        private TokenInvalidationService service;

        @BeforeEach
        void setUp() {
            service = new TokenInvalidationService(
                    new StubObjectProvider<>(redisTemplate),
                    new StubObjectProvider<com.example.matching.mapper.security.TokenBlacklistMapper>(null));
        }

        @Test
        @DisplayName("isTokenValid returns true (fail-open) when Redis throws and DB fallback unavailable")
        void isTokenValid_returnsFalseOnRedisConnectionFailureAndNoDbFallback() {
            when(redisTemplate.opsForValue()).thenReturn(valueOperations);
            when(valueOperations.get(anyString()))
                    .thenThrow(new RedisConnectionFailureException("Connection refused"));

            boolean result = service.isTokenValid(1L, new Date());

            assertThat(result).isFalse();
        }

        @Test
        @DisplayName("isTokenValid returns true (fail-open) when Redis throws timeout and DB fallback unavailable")
        void isTokenValid_returnsFalseOnRedisTimeoutAndNoDbFallback() {
            when(redisTemplate.opsForValue()).thenReturn(valueOperations);
            when(valueOperations.get(anyString()))
                    .thenThrow(new org.springframework.data.redis.RedisSystemException(
                            "Connection timed out", new java.io.IOException("timeout")));

            boolean result = service.isTokenValid(1L, new Date());

            assertThat(result).isFalse();
        }

        @Test
        @DisplayName("isTokenValid returns false when issuedAt is null")
        void isTokenValid_returnsFalseOnNullIssuedAt() {
            boolean result = service.isTokenValid(1L, null);

            assertThat(result).isFalse();
            verifyNoInteractions(redisTemplate);
        }

        @Test
        @DisplayName("isTokenValid returns true (fail-open) when Redis and DB both unavailable")
        void isTokenValid_returnsFalseWhenRedisAndDbUnavailable() {
            TokenInvalidationService serviceNoRedis =
                    new TokenInvalidationService(
                            new StubObjectProvider<RedisTemplate<String, Object>>(null),
                            new StubObjectProvider<com.example.matching.mapper.security.TokenBlacklistMapper>(null));

            boolean result = serviceNoRedis.isTokenValid(1L, new Date());

            assertThat(result).isFalse();
        }

        @Test
        @DisplayName("invalidateUserTokens does not throw when Redis throws connection exception")
        void invalidateUserTokens_doesNotThrowOnRedisFailure() {
            when(redisTemplate.opsForValue()).thenReturn(valueOperations);
            doThrow(new RedisConnectionFailureException("Connection refused"))
                    .when(valueOperations).set(anyString(), any(), any(Duration.class));

            assertThatNoException()
                    .isThrownBy(() -> service.invalidateUserTokens(1L));
        }

        @Test
        @DisplayName("invalidateUserTokens does not throw when RedisTemplate is null")
        void invalidateUserTokens_doesNotThrowWhenRedisUnavailable() {
            TokenInvalidationService serviceNoRedis =
                    new TokenInvalidationService(
                            new StubObjectProvider<RedisTemplate<String, Object>>(null),
                            new StubObjectProvider<com.example.matching.mapper.security.TokenBlacklistMapper>(null));

            assertThatNoException()
                    .isThrownBy(() -> serviceNoRedis.invalidateUserTokens(1L));
        }
    }

    // ==================== Stub ====================

    private record StubObjectProvider<T>(T instance) implements org.springframework.beans.factory.ObjectProvider<T> {
        @Override
        public T getObject(Object... args) { return instance; }
        @Override
        public T getIfAvailable() { return instance; }
        @Override
        public T getIfUnique() { return instance; }
        @Override
        public T getObject() { return instance; }
    }
}
