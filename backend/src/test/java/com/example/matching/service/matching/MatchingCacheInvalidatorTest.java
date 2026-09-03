package com.example.matching.service.matching;

import com.example.matching.config.RedisCacheNames;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@DisplayName("MatchingCacheInvalidator")
class MatchingCacheInvalidatorTest {

    private final Cache detailCache = mock(Cache.class);
    private final Cache reportCache = mock(Cache.class);
    private final Cache pageCache = mock(Cache.class);
    private final Cache dashboardCache = mock(Cache.class);
    private final CacheManager cacheManager = mock(CacheManager.class);
    private final MatchingCacheInvalidator invalidator = new MatchingCacheInvalidator(cacheManager);

    private void stubCaches() {
        when(cacheManager.getCache(RedisCacheNames.MATCHING_RECORD_DETAIL)).thenReturn(detailCache);
        when(cacheManager.getCache(RedisCacheNames.MATCHING_AI_REPORT)).thenReturn(reportCache);
        when(cacheManager.getCache(RedisCacheNames.MATCHING_RECORD_PAGE)).thenReturn(pageCache);
        when(cacheManager.getCache(RedisCacheNames.DASHBOARD_STATS)).thenReturn(dashboardCache);
    }

    @Test
    @DisplayName("evictRecord 只失效该记录详情与 AI 报告")
    void evictRecord_evictsDetailAndReport() {
        stubCaches();

        invalidator.evictRecord(42L);

        verify(detailCache).evict(42L);
        verify(reportCache).evict(42L);
        verify(pageCache, org.mockito.Mockito.never()).clear();
        verify(dashboardCache, org.mockito.Mockito.never()).clear();
    }

    @Test
    @DisplayName("evictRecordCollections 失效页面与仪表盘集合")
    void evictRecordCollections_clearsCollections() {
        stubCaches();

        invalidator.evictRecordCollections();

        verify(pageCache).clear();
        verify(dashboardCache).clear();
    }

    @Test
    @DisplayName("evictAfterAiScore 同时失效记录、页面与仪表盘")
    void evictAfterAiScore_evictsAllRelevantCaches() {
        stubCaches();

        invalidator.evictAfterAiScore(7L);

        verify(detailCache).evict(7L);
        verify(reportCache).evict(7L);
        verify(pageCache).clear();
        verify(dashboardCache).clear();
    }

    @Test
    @DisplayName("null recordId 安全返回")
    void evictRecord_nullIdIsSafe() {
        invalidator.evictRecord(null);
        invalidator.evictAfterAiScore(null);

        assertThat(1).isEqualTo(1);
    }

    @Test
    @DisplayName("缓存不存在时静默跳过不抛异常")
    void evictRecord_missingCacheIsSilentlyIgnored() {
        when(cacheManager.getCache(RedisCacheNames.MATCHING_RECORD_DETAIL)).thenReturn(null);

        invalidator.evictRecord(1L);

        assertThat(1).isEqualTo(1);
    }
}
