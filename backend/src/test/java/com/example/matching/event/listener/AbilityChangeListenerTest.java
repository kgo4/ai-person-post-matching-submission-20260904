package com.example.matching.event.listener;

import com.example.matching.config.RedisCacheNames;
import com.example.matching.event.AbilityChangeEvent;
import com.example.matching.service.common.VectorRecallCacheEpoch;
import com.example.matching.service.common.VectorSyncTaskService;
import com.example.matching.service.kg.GraphChangeSetService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AbilityChangeListenerTest {

    @Mock private GraphChangeSetService graphChangeSetService;
    @Mock private RedisTemplate<String, Object> redisTemplate;
    @Mock private ValueOperations<String, Object> valueOperations;
    @Mock private VectorSyncTaskService vectorSyncTaskService;
    @Mock private VectorRecallCacheEpoch vectorRecallCacheEpoch;
    @Mock private CacheManager cacheManager;

    @Test
    void employeeAbilityChange_advancesEpochsAndEnqueuesVectorSync() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(vectorRecallCacheEpoch.advance()).thenReturn(1L);
        AbilityChangeListener listener = new AbilityChangeListener(
                graphChangeSetService, redisTemplate, vectorSyncTaskService,
                vectorRecallCacheEpoch, cacheManager);

        listener.handleAbilityChange(new AbilityChangeEvent(this, "EMP_ABILITY", 12L));

        verify(valueOperations).increment(RedisCacheNames.EMP_VECTOR_CACHE_EPOCH);
        verify(vectorRecallCacheEpoch).advance();
        verify(vectorSyncTaskService).enqueue(eq(VectorSyncTaskService.ENTITY_EMPLOYEE), eq(12L), any());
        verify(graphChangeSetService).requestChange(any(), any(), any(), any(), any(), any());
    }

    @Test
    void tagConfigChange_advancesVectorRecallEpochWithoutVectorSync() {
        when(vectorRecallCacheEpoch.advance()).thenReturn(1L);
        AbilityChangeListener listener = new AbilityChangeListener(
                graphChangeSetService, redisTemplate, vectorSyncTaskService,
                vectorRecallCacheEpoch, cacheManager);

        listener.handleAbilityChange(new AbilityChangeEvent(this, "TAG_CONFIG", 12L));

        verify(vectorRecallCacheEpoch).advance();
        org.mockito.Mockito.verify(vectorSyncTaskService, org.mockito.Mockito.never())
                .enqueue(any(), any(), any());
    }

    @Test
    void epochUnavailable_fallsBackToFullCacheClear() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(vectorRecallCacheEpoch.advance()).thenReturn(-1L);
        Cache cache = org.mockito.Mockito.mock(Cache.class);
        when(cacheManager.getCache(RedisCacheNames.VECTOR_RECALL)).thenReturn(cache);
        AbilityChangeListener listener = new AbilityChangeListener(
                graphChangeSetService, redisTemplate, vectorSyncTaskService,
                vectorRecallCacheEpoch, cacheManager);

        listener.handleAbilityChange(new AbilityChangeEvent(this, "EMP_ABILITY", 12L));

        verify(cache).clear();
    }
}
