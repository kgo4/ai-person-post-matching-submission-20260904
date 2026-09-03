package com.example.matching.security;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.example.matching.entity.security.TokenBlacklist;
import com.example.matching.mapper.security.TokenBlacklistMapper;
import com.example.matching.schedule.ScheduledTaskRunner;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Date;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Service
@RequiredArgsConstructor
public class TokenInvalidationService {

    private static final String KEY_PREFIX = "auth:token-invalid-before:";
    private static final Duration TTL = Duration.ofDays(2);

    private final ObjectProvider<RedisTemplate<String, Object>> redisTemplateProvider;
    private final ObjectProvider<TokenBlacklistMapper> blacklistMapperProvider;
    private final ConcurrentHashMap<Long, Long> localInvalidBefore = new ConcurrentHashMap<>();

    @org.springframework.beans.factory.annotation.Autowired(required = false)
    private ScheduledTaskRunner taskRunner;

    public void invalidateUserTokens(Long userId) {
        long now = System.currentTimeMillis();
        localInvalidBefore.put(userId, now);
        boolean redisOk = false;
        boolean dbOk = false;

        RedisTemplate<String, Object> redisTemplate = redisTemplateProvider.getIfAvailable();
        if (redisTemplate != null) {
            try {
                redisTemplate.opsForValue().set(KEY_PREFIX + userId, now, TTL);
                redisOk = true;
            } catch (Exception e) {
                log.error("Redis 不可用，令牌撤销延迟到 DB 兜底: userId={}", userId, e);
            }
        } else {
            log.error("Redis 未就绪，令牌撤销使用 DB 兜底: userId={}", userId);
        }

        if (!redisOk) {
            try {
                TokenBlacklistMapper mapper = blacklistMapperProvider.getIfAvailable();
                if (mapper != null) {
                    TokenBlacklist record = new TokenBlacklist();
                    record.setUserId(userId);
                    record.setInvalidatedAt(LocalDateTime.now());
                    mapper.insert(record);
                    dbOk = true;
                }
            } catch (Exception e) {
                log.error("DB 令牌撤销兜底也失败: userId={}", userId, e);
            }
        }

        if (!redisOk && !dbOk) {
            log.error("令牌撤销完全失败，依赖 JWT 自然过期: userId={}", userId);
        }
    }

    public boolean isTokenValid(Long userId, Date issuedAt) {
        if (issuedAt == null) {
            return false;
        }
        Long locallyInvalidBefore = localInvalidBefore.get(userId);
        if (locallyInvalidBefore != null && System.currentTimeMillis() - locallyInvalidBefore >= TTL.toMillis()) {
            localInvalidBefore.remove(userId, locallyInvalidBefore);
            locallyInvalidBefore = null;
        }
        if (locallyInvalidBefore != null && issuedAt.getTime() < locallyInvalidBefore) {
            return false;
        }

        RedisTemplate<String, Object> redisTemplate = redisTemplateProvider.getIfAvailable();
        if (redisTemplate != null) {
            try {
                Object invalidBefore = redisTemplate.opsForValue().get(KEY_PREFIX + userId);
                if (invalidBefore instanceof Number timestamp) {
                    localInvalidBefore.put(userId, timestamp.longValue());
                    return issuedAt.getTime() >= timestamp.longValue();
                }
                // Redis 可用但 key 缺失：失效可能发生在 Redis 故障窗口（仅写入 DB），
                // 必须查 DB 兜底。不缓存未命中结果——另一实例可能在故障窗口写入
                // DB 黑名单，任何长度的负缓存都会留下跨实例放行窗口。
                return checkDbFallback(userId, issuedAt, false);
            } catch (Exception e) {
                log.error("Redis 令牌校验失败，回退到 DB 兜底: userId={}", userId, e);
                return checkDbFallback(userId, issuedAt, true);
            }
        }

        return checkDbFallback(userId, issuedAt, true);
    }

    private boolean checkDbFallback(Long userId, Date issuedAt, boolean failClosed) {
        try {
            TokenBlacklistMapper mapper = blacklistMapperProvider.getIfAvailable();
            if (mapper != null) {
                TokenBlacklist latest = mapper.selectOne(
                        new LambdaQueryWrapper<TokenBlacklist>()
                                .eq(TokenBlacklist::getUserId, userId)
                                .orderByDesc(TokenBlacklist::getInvalidatedAt)
                                .last("LIMIT 1"));
                if (latest != null && latest.getInvalidatedAt() != null) {
                    long invalidatedAtMillis = latest.getInvalidatedAt()
                            .atZone(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli();
                    localInvalidBefore.put(userId, invalidatedAtMillis);
                    return issuedAt.getTime() >= invalidatedAtMillis;
                }
                return true;
            }
        } catch (Exception e) {
            log.error("DB 令牌校验兜底失败: userId={}", userId, e);
        }
        if (failClosed) {
            log.warn("Redis and token blacklist DB are unavailable; rejecting JWT (fail-closed): userId={}", userId);
        } else {
            log.warn("Token blacklist DB is unavailable after a Redis cache miss; accepting JWT: userId={}", userId);
        }
        return !failClosed;
    }

    @Scheduled(cron = "0 0 3 * * ?")
    public void cleanupExpiredBlacklist() {
        if (taskRunner != null) {
            taskRunner.run("token_blacklist_cleanup", this::cleanupExpiredBlacklistInternal);
            return;
        }
        try {
            cleanupExpiredBlacklistInternal();
        } catch (Exception e) {
            log.error("清理令牌黑名单失败", e);
        }
    }

    private void cleanupExpiredBlacklistInternal() {
        TokenBlacklistMapper mapper = blacklistMapperProvider.getIfAvailable();
        if (mapper != null) {
            LocalDateTime cutoff = LocalDateTime.now().minusDays(2);
            mapper.delete(new LambdaQueryWrapper<TokenBlacklist>()
                    .lt(TokenBlacklist::getCreatedTime, cutoff));
            log.debug("清理过期令牌黑名单完成: cutoff={}", cutoff);
        }
    }
}
