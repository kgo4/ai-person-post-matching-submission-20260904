package com.example.matching.service.matching;

import com.example.matching.config.RedisCacheNames;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.stereotype.Component;

/**
 * 匹配缓存失效器 —— 集中管理匹配相关缓存的失效时机。
 * <p>
 * 同步 service 方法继续使用声明式 {@code @CacheEvict}；本 bean 仅用于异步/重试代码，
 * 因为 AOP 注解无法表达"AI 评分完成之后"这一失效时机。
 */
@Slf4j
@Component
public class MatchingCacheInvalidator {

    private final CacheManager cacheManager;

    public MatchingCacheInvalidator(CacheManager cacheManager) {
        this.cacheManager = cacheManager;
    }

    /** 失效单条记录详情与 AI 报告。 */
    public void evictRecord(Long recordId) {
        if (recordId == null) {
            return;
        }
        evict(RedisCacheNames.MATCHING_RECORD_DETAIL, recordId);
        evict(RedisCacheNames.MATCHING_AI_REPORT, recordId);
    }

    /** 失效匹配记录列表与仪表盘统计（页面级集合）。 */
    public void evictRecordCollections() {
        clear(RedisCacheNames.MATCHING_RECORD_PAGE);
        clear(RedisCacheNames.DASHBOARD_STATS);
    }

    /**
     * AI 评分完成后调用：失效该记录详情/报告以及页面与仪表盘集合。
     */
    public void evictAfterAiScore(Long recordId) {
        evictRecord(recordId);
        evictRecordCollections();
    }

    private void evict(String cacheName, Object key) {
        try {
            Cache cache = cacheManager.getCache(cacheName);
            if (cache != null) {
                cache.evict(key);
            }
        } catch (Exception e) {
            log.warn("缓存驱逐失败: cache={}, key={}, error={}", cacheName, key, e.getMessage());
        }
    }

    private void clear(String cacheName) {
        try {
            Cache cache = cacheManager.getCache(cacheName);
            if (cache != null) {
                cache.clear();
            }
        } catch (Exception e) {
            log.warn("缓存清空失败: cache={}, error={}", cacheName, e.getMessage());
        }
    }
}
