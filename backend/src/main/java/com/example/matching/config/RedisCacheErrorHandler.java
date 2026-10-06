package com.example.matching.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.Cache;
import org.springframework.cache.interceptor.CacheErrorHandler;
import org.springframework.stereotype.Component;

/**
 * Redis 缓存异常降级处理器
 * <p>
 * 当 Redis 不可用时（连接失败、超时等），缓存操作自动降级：
 * - 读操作（get）：返回 null → 走 DB 查询（不影响主流程）
 * - 写操作（put/evict）：仅记录日志，不抛异常
 * <p>
 * 配合 {@link RedisConfig} 中的 @EnableCaching 使用。
 */
@Slf4j
@Component
public class RedisCacheErrorHandler implements CacheErrorHandler {

    @Override
    public void handleCacheGetError(RuntimeException exception, Cache cache, Object key) {
        log.warn("[缓存降级] 读取缓存失败，降级走DB。cache={}, key={}, error={}",
                cache.getName(), key, exception.getMessage());
    }

    @Override
    public void handleCachePutError(RuntimeException exception, Cache cache, Object key, Object value) {
        log.warn("[缓存降级] 写入缓存失败，跳过缓存。cache={}, key={}, error={}",
                cache.getName(), key, exception.getMessage());
    }

    @Override
    public void handleCacheEvictError(RuntimeException exception, Cache cache, Object key) {
        log.error("[缓存一致性风险] 清除缓存失败，缓存可能过期，数据可能不一致。cache={}, key={}, error={}",
                cache.getName(), key, exception.getMessage());
    }

    @Override
    public void handleCacheClearError(RuntimeException exception, Cache cache) {
        log.error("[缓存一致性风险] 清空缓存失败，缓存可能过期，数据可能不一致。cache={}, error={}",
                cache.getName(), exception.getMessage());
    }
}
