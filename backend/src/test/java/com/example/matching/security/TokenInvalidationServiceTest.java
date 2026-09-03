package com.example.matching.security;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.example.matching.entity.security.TokenBlacklist;
import com.example.matching.mapper.security.TokenBlacklistMapper;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.util.Date;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class TokenInvalidationServiceTest {

    @Test
    void rejects_tokens_when_redis_and_db_are_unavailable() {
        ObjectProvider<RedisTemplate<String, Object>> unavailableRedis = mock(ObjectProvider.class);
        when(unavailableRedis.getIfAvailable()).thenReturn(null);
        @SuppressWarnings("unchecked")
        ObjectProvider<com.example.matching.mapper.security.TokenBlacklistMapper> unavailableMapper =
                mock(ObjectProvider.class);
        when(unavailableMapper.getIfAvailable()).thenReturn(null);
        TokenInvalidationService service = new TokenInvalidationService(unavailableRedis, unavailableMapper);

        assertThat(service.isTokenValid(1L, new Date())).isFalse();
    }

    @Test
    void acceptsTokenWithoutWarningWhenBlacklistLookupFindsNoRevocation() {
        RedisTemplate<String, Object> redisTemplate = mock(RedisTemplate.class);
        @SuppressWarnings("unchecked")
        ValueOperations<String, Object> valueOperations = mock(ValueOperations.class);
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get("auth:token-invalid-before:1")).thenReturn(null);

        TokenBlacklistMapper mapper = mock(TokenBlacklistMapper.class);
        when(mapper.selectOne(org.mockito.ArgumentMatchers.any())).thenReturn(null);

        @SuppressWarnings("unchecked")
        ObjectProvider<RedisTemplate<String, Object>> redisProvider = mock(ObjectProvider.class);
        when(redisProvider.getIfAvailable()).thenReturn(redisTemplate);
        @SuppressWarnings("unchecked")
        ObjectProvider<TokenBlacklistMapper> mapperProvider = mock(ObjectProvider.class);
        when(mapperProvider.getIfAvailable()).thenReturn(mapper);

        Logger logger = (Logger) LoggerFactory.getLogger(TokenInvalidationService.class);
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        logger.addAppender(appender);
        try {
            TokenInvalidationService service = new TokenInvalidationService(redisProvider, mapperProvider);

            assertThat(service.isTokenValid(1L, new Date())).isTrue();
            assertThat(appender.list)
                    .noneMatch(event -> event.getFormattedMessage().contains("blacklist DB is unavailable"));
        } finally {
            logger.detachAppender(appender);
            appender.stop();
        }
    }
}
