package com.example.matching.service.ability.impl;

import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicInteger;

@Slf4j
@Component
public class MemorySearchCacheEpoch {

    private static final String EPOCH_KEY_PREFIX = "matching:memory:search:epoch:";
    private static final int DEGRADED_CLEAR_THRESHOLD = 3;

    private final StringRedisTemplate stringRedisTemplate;
    private final ConcurrentMap<String, AtomicLong> epochCache = new ConcurrentHashMap<>();
    private final ConcurrentMap<String, AtomicInteger> redisOkCounters = new ConcurrentHashMap<>();
    private final ConcurrentMap<String, AtomicLong> localEpochSnapshot = new ConcurrentHashMap<>();

    public MemorySearchCacheEpoch(StringRedisTemplate stringRedisTemplate) {
        this.stringRedisTemplate = stringRedisTemplate;
    }

    public long current(String scope) {
        try {
            String value = stringRedisTemplate.opsForValue().get(EPOCH_KEY_PREFIX + scope);
            if (value != null) {
                long redisEpoch = Long.parseLong(value);

                AtomicLong local = epochCache.get(scope);
                long localEpoch = local != null ? local.get() : 0L;

                if (redisEpoch > localEpoch) {
                    log.info("Redis epoch restored for scope {}: redis={} > local={}", scope, redisEpoch, localEpoch);
                    epochCache.computeIfAbsent(scope, k -> new AtomicLong(redisEpoch)).set(redisEpoch);
                } else if (redisEpoch < localEpoch) {
                    log.debug("Redis epoch behind local for scope {}: redis={} < local={}, keeping local", scope, redisEpoch, localEpoch);
                }

                incrementRedisOk(scope);
            }
        } catch (Exception e) {
            log.debug("Redis unavailable for epoch scope {}, using local copy: {}", scope, e.getMessage());
            resetRedisOk(scope);
        }

        AtomicLong local = epochCache.get(scope);
        if (local == null) {
            local = fallbackLocalEpoch(scope);
        }
        return local.get();
    }

    public boolean isDegraded(String scope) {
        AtomicInteger okCount = redisOkCounters.get(scope);
        return okCount == null || okCount.get() < DEGRADED_CLEAR_THRESHOLD;
    }

    public long advance(String scope) {
        try {
            Long value = stringRedisTemplate.opsForValue().increment(EPOCH_KEY_PREFIX + scope);
            long newEpoch = value == null ? 0L : value;
            epochCache.computeIfAbsent(scope, k -> new AtomicLong(newEpoch)).set(newEpoch);
            log.debug("Advanced memory search cache epoch for scope {}: {}", scope, newEpoch);
            return newEpoch;
        } catch (Exception e) {
            log.warn("Failed to advance memory search cache epoch for scope {}: {}", scope, e.getMessage());
            AtomicLong local = epochCache.computeIfAbsent(scope, k -> new AtomicLong(0));
            return local.incrementAndGet();
        }
    }

    private void incrementRedisOk(String scope) {
        AtomicInteger counter = redisOkCounters.computeIfAbsent(scope, k -> new AtomicInteger(0));
        int current = counter.incrementAndGet();
        if (current >= DEGRADED_CLEAR_THRESHOLD) {
            counter.set(DEGRADED_CLEAR_THRESHOLD);
        }
    }

    private void resetRedisOk(String scope) {
        redisOkCounters.computeIfAbsent(scope, k -> new AtomicInteger(0)).set(0);
    }

    private AtomicLong fallbackLocalEpoch(String scope) {
        AtomicLong fallback = localEpochSnapshot.get(scope);
        if (fallback == null) {
            AtomicLong created = new AtomicLong(0);
            localEpochSnapshot.put(scope, created);
            epochCache.put(scope, created);
            return created;
        }
        epochCache.put(scope, fallback);
        return fallback;
    }
}
