package com.example.matching.service.common;

import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

/**
 * 向量召回缓存 epoch —— 用 Redis INCR 替代全量缓存清空。
 * <p>
 * 相关岗位/能力变更时递增 epoch 而不是调用 {@code cache.clear()}，
 * 避免一次变更让所有岗位的召回缓存全部失效。缓存 key 由
 * {@link EmployeeVectorRecallService} 拼入 {@code epoch.current()}。
 * <p>
 * Redis 不可用时退化为 0（不做精确失效），并记录警告指标。
 */
@Slf4j
@Component
public class VectorRecallCacheEpoch {

    private static final String EPOCH_KEY = "matching:vector-recall:epoch";

    private final StringRedisTemplate stringRedisTemplate;

    public VectorRecallCacheEpoch(StringRedisTemplate stringRedisTemplate) {
        this.stringRedisTemplate = stringRedisTemplate;
    }

    /**
     * 当前 epoch 值；Redis 不可用时返回 0 并告警。
     */
    public long current() {
        try {
            String value = stringRedisTemplate.opsForValue().get(EPOCH_KEY);
            return value == null ? 0L : Long.parseLong(value);
        } catch (Exception e) {
            log.warn("Failed to read vector recall epoch, using 0: {}", e.getMessage());
            return 0L;
        }
    }

    /**
     * 递增 epoch（幂等安全，多个节点并发 INCR 仍单调）。
     *
     * @return 递增后的 epoch；Redis 不可用时返回 -1（调用方应退化为缓存清空）
     */
    public long advance() {
        try {
            Long value = stringRedisTemplate.opsForValue().increment(EPOCH_KEY);
            return value == null ? 0L : value;
        } catch (Exception e) {
            log.warn("Failed to advance vector recall epoch: {}", e.getMessage());
            return -1L;
        }
    }

    public boolean available() {
        try {
            stringRedisTemplate.opsForValue().increment(EPOCH_KEY, 0L);
            return true;
        } catch (Exception e) {
            return false;
        }
    }
}
