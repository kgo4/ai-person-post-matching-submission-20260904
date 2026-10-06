package com.example.matching.integration.cache;

import com.example.matching.config.RedisCacheErrorHandler;
import com.example.matching.security.TokenInvalidationService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.cache.annotation.CachingConfigurer;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.interceptor.CacheErrorHandler;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.stereotype.Service;

import java.util.Date;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/**
 * Redis 不可用时的降级验证。
 * <p>
 * 验证 {@link RedisCacheErrorHandler} 和 {@link TokenInvalidationService}
 * 在 Redis 连接失败时不抛出异常，优雅降级。
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("Redis 不可用降级测试")
class RedisUnavailableTest {

    @Mock
    private Cache cache;

    @Mock
    @SuppressWarnings("unchecked")
    private RedisTemplate<String, Object> redisTemplate;

    @Mock
    @SuppressWarnings("unchecked")
    private ValueOperations<String, Object> valueOperations;

    @Mock
    private ObjectProvider<RedisTemplate<String, Object>> redisTemplateProvider;

    @InjectMocks
    private TokenInvalidationService tokenInvalidationService;

    private final RedisCacheErrorHandler errorHandler = new RedisCacheErrorHandler();

    // ==================== RedisCacheErrorHandler ====================

    @Test
    @DisplayName("缓存读取失败时降级返回null，不抛异常")
    void handleCacheGetError_doesNotThrow() {
        when(cache.getName()).thenReturn("test:cache");
        RuntimeException error = new RedisConnectionFailureException("Connection refused");

        assertThatCode(() -> errorHandler.handleCacheGetError(error, cache, "testKey"))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("缓存写入失败时静默跳过，不抛异常")
    void handleCachePutError_doesNotThrow() {
        when(cache.getName()).thenReturn("test:cache");
        RuntimeException error = new RedisConnectionFailureException("Connection refused");

        assertThatCode(() -> errorHandler.handleCachePutError(error, cache, "testKey", "testValue"))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("缓存清除失败时静默跳过，不抛异常")
    void handleCacheEvictError_doesNotThrow() {
        when(cache.getName()).thenReturn("test:cache");
        RuntimeException error = new RedisConnectionFailureException("Connection refused");

        assertThatCode(() -> errorHandler.handleCacheEvictError(error, cache, "testKey"))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("缓存清空失败时静默跳过，不抛异常")
    void handleCacheClearError_doesNotThrow() {
        when(cache.getName()).thenReturn("test:cache");
        RuntimeException error = new RedisConnectionFailureException("Connection refused");

        assertThatCode(() -> errorHandler.handleCacheClearError(error, cache))
                .doesNotThrowAnyException();
    }

    // ==================== TokenInvalidationService ====================

    @Test
    @DisplayName("Redis不可用时token验证返回false（拒绝无法验证的token）")
    void isTokenValid_returnsFalseWhenRedisUnavailable() {
        when(redisTemplateProvider.getIfAvailable()).thenReturn(null);

        boolean result = tokenInvalidationService.isTokenValid(1L, new Date());

        assertThat(result).isFalse();
    }

    @Test
    @DisplayName("Redis不可用时token失效操作不抛异常")
    void invalidateUserTokens_doesNotThrowWhenRedisUnavailable() {
        when(redisTemplateProvider.getIfAvailable()).thenReturn(null);

        assertThatCode(() -> tokenInvalidationService.invalidateUserTokens(1L))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("Redis操作抛异常时token验证返回false")
    void isTokenValid_returnsFalseWhenRedisThrowsException() {
        when(redisTemplateProvider.getIfAvailable()).thenReturn(redisTemplate);
        when(redisTemplate.opsForValue()).thenThrow(new RedisConnectionFailureException("Connection lost"));

        boolean result = tokenInvalidationService.isTokenValid(1L, new Date());

        assertThat(result).isFalse();
    }

    @Test
    @DisplayName("issuedAt为null时token验证返回false")
    void isTokenValid_returnsFalseWhenIssuedAtIsNull() {
        boolean result = tokenInvalidationService.isTokenValid(1L, null);

        assertThat(result).isFalse();
    }

    @Test
    @DisplayName("Redis正常时token验证通过")
    void isTokenValid_returnsTrueWhenTokenIsValid() {
        when(redisTemplateProvider.getIfAvailable()).thenReturn(redisTemplate);
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get(anyString())).thenReturn(null); // no invalidation record

        Date issuedAt = new Date();
        boolean result = tokenInvalidationService.isTokenValid(1L, issuedAt);

        assertThat(result).isTrue();
    }

    @Test
    @DisplayName("token在失效时间之前签发时验证失败")
    void isTokenValid_returnsFalseWhenTokenIssuedBeforeInvalidation() {
        when(redisTemplateProvider.getIfAvailable()).thenReturn(redisTemplate);
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);

        long invalidBefore = System.currentTimeMillis();
        when(valueOperations.get(anyString())).thenReturn(invalidBefore);

        Date issuedAt = new Date(invalidBefore - 10000); // issued 10s before invalidation
        boolean result = tokenInvalidationService.isTokenValid(1L, issuedAt);

        assertThat(result).isFalse();
    }

    @Test
    @DisplayName("token在失效时间之后签发时验证通过")
    void isTokenValid_returnsTrueWhenTokenIssuedAfterInvalidation() {
        when(redisTemplateProvider.getIfAvailable()).thenReturn(redisTemplate);
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);

        long invalidBefore = System.currentTimeMillis() - 60000; // 1 minute ago
        when(valueOperations.get(anyString())).thenReturn(invalidBefore);

        Date issuedAt = new Date(); // issued now (after invalidation)
        boolean result = tokenInvalidationService.isTokenValid(1L, issuedAt);

        assertThat(result).isTrue();
    }

    // ==================== @Cacheable graceful degradation ====================

    /**
     * Minimal service with a {@code @Cacheable} method, used to verify that
     * the caching interceptor delegates to {@link RedisCacheErrorHandler} and
     * allows the method to execute normally when the cache backend throws.
     */
    @Service
    static class CacheableTestService {
        @Cacheable(value = "test:failing-cache", key = "#id")
        public String getSomething(Long id) {
            return "result-" + id;
        }
    }

    /**
     * A {@link CacheManager} whose caches throw {@link RedisConnectionFailureException}
     * on every {@code get} operation, simulating a completely unavailable Redis.
     */
    static class FailingCacheManager implements CacheManager {

        @Override
        public java.util.Collection<String> getCacheNames() {
            return java.util.Collections.emptyList();
        }

        @Override
        public Cache getCache(String name) {
            return new Cache() {
                @Override
                public String getName() {
                    return name;
                }

                @Override
                public Object getNativeCache() {
                    return this;
                }

                @Override
                public ValueWrapper get(Object key) {
                    throw new RedisConnectionFailureException("Simulated Redis down");
                }

                @Override
                public <T> T get(Object key, Class<T> type) {
                    throw new RedisConnectionFailureException("Simulated Redis down");
                }

                @Override
                public <T> T get(Object key, java.util.concurrent.Callable<T> valueLoader) {
                    throw new RedisConnectionFailureException("Simulated Redis down");
                }

                @Override
                public void put(Object key, Object value) {
                    // silently skip — Redis is "down"
                }

                @Override
                public void evict(Object key) {
                    // silently skip
                }

                @Override
                public void clear() {
                    // silently skip
                }
            };
        }
    }

    @Configuration
    @EnableCaching
    @Import({CacheableTestService.class})
    static class FailingCacheTestConfig implements CachingConfigurer {
        @Bean
        @Override
        public CacheManager cacheManager() {
            return new FailingCacheManager();
        }

        @Bean
        @Override
        public CacheErrorHandler errorHandler() {
            return new RedisCacheErrorHandler();
        }
    }

    @Test
    @DisplayName("@Cacheable method still returns DB result when cache backend is completely down")
    void cacheableMethod_returnsDbResultWhenCacheFails() {
        try (var ctx = new AnnotationConfigApplicationContext(FailingCacheTestConfig.class)) {
            CacheableTestService service = ctx.getBean(CacheableTestService.class);
            RedisCacheErrorHandler handler = ctx.getBean(RedisCacheErrorHandler.class);

            // verify the error handler is wired into the caching infrastructure
            assertThat(handler).isNotNull();

            // The cache get will throw RedisConnectionFailureException;
            // RedisCacheErrorHandler.handleCacheGetError suppresses it,
            // allowing the method body to execute and return the DB result.
            String result = service.getSomething(42L);
            assertThat(result).isEqualTo("result-42");

            // Second call also works — cache still fails, method re-executes
            String result2 = service.getSomething(99L);
            assertThat(result2).isEqualTo("result-99");
        }
    }
}
