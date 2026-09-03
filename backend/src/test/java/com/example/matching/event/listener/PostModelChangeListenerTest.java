package com.example.matching.event.listener;

import com.example.matching.config.RedisCacheNames;
import com.example.matching.event.PostModelChangeEvent;
import com.example.matching.service.common.VectorSyncTaskService;
import com.example.matching.service.kg.GraphChangeSetService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.cache.CacheManager;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * M22 行为测试：岗位模型变更（MODEL_CONFIG）必须递增员工向量缓存 epoch，
 * 使推荐缓存 key 失效；epoch 递增失败不影响其他入队单元，但需记录错误指标。
 */
class PostModelChangeListenerTest {

    private GraphChangeSetService graphChangeSetService;
    private VectorSyncTaskService vectorSyncTaskService;
    private CacheManager cacheManager;
    private StringRedisTemplate stringRedisTemplate;
    private ValueOperations<String, String> valueOperations;
    private PostModelChangeListener listener;

    @BeforeEach
    void setUp() {
        graphChangeSetService = mock(GraphChangeSetService.class);
        vectorSyncTaskService = mock(VectorSyncTaskService.class);
        cacheManager = mock(CacheManager.class);
        stringRedisTemplate = mock(StringRedisTemplate.class);
        valueOperations = mock(ValueOperations.class);
        when(stringRedisTemplate.opsForValue()).thenReturn(valueOperations);
        listener = new PostModelChangeListener(
                graphChangeSetService, vectorSyncTaskService, cacheManager, stringRedisTemplate);
    }

    @Test
    void modelConfigChangeBumpsEmployeeVectorCacheEpoch() {
        listener.handlePostModelChange(new PostModelChangeEvent(this, "MODEL_CONFIG", 5L));

        verify(valueOperations).increment(RedisCacheNames.EMP_VECTOR_CACHE_EPOCH);
        verify(graphChangeSetService).requestChange(eq("POST_MODEL"), eq("POST"), eq(5L),
                eq("UPSERT"), any(Map.class), any());
        verify(vectorSyncTaskService).enqueue(VectorSyncTaskService.ENTITY_POST, 5L, Map.of());
    }

    @Test
    void epochBumpFailureDoesNotBlockGraphAndVectorEnqueue() {
        when(valueOperations.increment(RedisCacheNames.EMP_VECTOR_CACHE_EPOCH))
                .thenThrow(new RuntimeException("redis down"));

        listener.handlePostModelChange(new PostModelChangeEvent(this, "MODEL_CONFIG", 5L));

        // epoch 递增失败只记录错误指标，不影响岗位模型更新与其他入队单元
        verify(graphChangeSetService).requestChange(anyString(), anyString(), eq(5L),
                eq("UPSERT"), any(Map.class), any());
        verify(vectorSyncTaskService).enqueue(VectorSyncTaskService.ENTITY_POST, 5L, Map.of());
    }
}
