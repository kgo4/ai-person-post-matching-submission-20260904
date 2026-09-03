package com.example.matching.security;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.Locale;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

@Slf4j
@Service
public class AuthRateLimitService {

    private static final int MAX_LOGIN_FAILURES = 5;
    private static final int MAX_REGISTRATIONS = 5;
    private static final Duration BLOCK_WINDOW = Duration.ofMinutes(1);
    private static final String REDIS_KEY_PREFIX = "auth:rate-limit:";
    private static final DefaultRedisScript<Long> INCREMENT_WITH_TTL = new DefaultRedisScript<>(
            "local count = redis.call('INCR', KEYS[1]); if count == 1 then redis.call('PEXPIRE', KEYS[1], ARGV[1]); end; return count",
            Long.class);

    private final StringRedisTemplate stringRedisTemplate;
    private final Cache<String, AtomicInteger> loginFailures = Caffeine.newBuilder()
            .expireAfterWrite(BLOCK_WINDOW)
            .maximumSize(100_000)
            .build();
    private final Cache<String, AtomicInteger> registrations = Caffeine.newBuilder()
            .expireAfterWrite(BLOCK_WINDOW)
            .maximumSize(100_000)
            .build();

    public AuthRateLimitService(StringRedisTemplate stringRedisTemplate) {
        this.stringRedisTemplate = stringRedisTemplate;
    }

    public void checkLoginAllowed(String clientIp, String username) {
        String key = loginKey(clientIp, username);
        String redisCount = null;
        try {
            redisCount = stringRedisTemplate.opsForValue().get(REDIS_KEY_PREFIX + key);
        } catch (Exception e) {
            log.debug("Redis rate limit unavailable, falling back to local", e);
        }
        int count = redisCount != null ? Integer.parseInt(redisCount) : 0;
        if (count >= MAX_LOGIN_FAILURES) {
            throw new RateLimitExceededException();
        }
        AtomicInteger attempts = loginFailures.getIfPresent(key);
        if (attempts != null && attempts.get() >= MAX_LOGIN_FAILURES) {
            throw new RateLimitExceededException();
        }
    }

    public void recordLoginFailure(String clientIp, String username) {
        String key = loginKey(clientIp, username);
        try {
            incrementWithTtl(REDIS_KEY_PREFIX + key);
        } catch (Exception e) {
            log.debug("Redis rate limit record failed, using local", e);
        }
        loginFailures.get(key, ignored -> new AtomicInteger()).incrementAndGet();
    }

    public void clearLoginFailures(String clientIp, String username) {
        String key = loginKey(clientIp, username);
        try {
            stringRedisTemplate.delete(REDIS_KEY_PREFIX + key);
        } catch (Exception e) {
            log.debug("Redis rate limit clear failed", e);
        }
        loginFailures.invalidate(key);
    }

    public void checkRegistrationAllowed(String clientIp) {
        try {
            Long count = incrementWithTtl(REDIS_KEY_PREFIX + "reg:" + clientIp);
            if (count != null && count > MAX_REGISTRATIONS) {
                throw new RateLimitExceededException();
            }
        } catch (RateLimitExceededException exception) {
            throw exception;
        } catch (Exception e) {
            log.debug("Redis rate limit unavailable, falling back to local", e);
        }
        AtomicInteger attempts = registrations.get(clientIp, ignored -> new AtomicInteger());
        if (attempts.incrementAndGet() > MAX_REGISTRATIONS) {
            throw new RateLimitExceededException();
        }
    }

    private String loginKey(String clientIp, String username) {
        return clientIp + ':' + username.trim().toLowerCase(Locale.ROOT);
    }

    private Long incrementWithTtl(String key) {
        return stringRedisTemplate.execute(INCREMENT_WITH_TTL, List.of(key),
                String.valueOf(BLOCK_WINDOW.toMillis()));
    }
}
