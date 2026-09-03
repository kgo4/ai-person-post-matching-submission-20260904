package com.example.matching.event.listener;

import com.example.matching.config.RedisCacheNames;
import com.example.matching.event.PostModelChangeEvent;
import com.example.matching.service.common.VectorSyncTaskService;
import com.example.matching.service.kg.GraphChangeSetService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 岗位模型变更事件监听器
 * <p>
 * 监听器只负责入队向量同步任务（唯一业务键 POST:{postId}），
 * 向量写入由后台任务执行，失败自动指数退避重试，避免"WARN 后结束"丢更新。
 * <p>
 * 每个执行单元独立try-catch，一个失败不阻断其他。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PostModelChangeListener {

    private final GraphChangeSetService graphChangeSetService;
    private final VectorSyncTaskService vectorSyncTaskService;
    private final CacheManager cacheManager;
    private final StringRedisTemplate stringRedisTemplate;

    private static final AtomicLong GRAPH_ENQUEUE_FAILURES = new AtomicLong(0);
    private static final AtomicLong VECTOR_ENQUEUE_FAILURES = new AtomicLong(0);
    private static final AtomicLong VECTOR_EPOCH_FAILURES = new AtomicLong(0);

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void handlePostModelChange(PostModelChangeEvent event) {
        log.info("收到岗位模型变更事件：changeType={}, entityId={}", event.getChangeType(), event.getEntityId());

        switch (event.getChangeType()) {
            case "MODEL_CONFIG":
                evictPostModelCache(event.getEntityId());
                bumpVectorCacheEpoch();
                executeGraphChangeEnqueue(event);
                executeVectorSyncEnqueue(event);
                break;
            case "TEMPLATE_CHANGE":
                break;
            default:
                log.warn("未知的岗位模型变更类型：{}", event.getChangeType());
        }
    }

    private void executeGraphChangeEnqueue(PostModelChangeEvent event) {
        try {
            graphChangeSetService.requestChange("POST_MODEL", "POST", event.getEntityId(),
                    "UPSERT", Map.of("trigger", "PostModelChangeEvent"), null);
        } catch (Exception e) {
            long failures = GRAPH_ENQUEUE_FAILURES.incrementAndGet();
            log.error("[LISTENER_ERROR] 图变更入队失败: changeType={}, entityId={}, totalFailures={}, errorType={}",
                    event.getChangeType(), event.getEntityId(), failures, e.getClass().getSimpleName());
        }
    }

    private void executeVectorSyncEnqueue(PostModelChangeEvent event) {
        try {
            vectorSyncTaskService.enqueue(VectorSyncTaskService.ENTITY_POST, event.getEntityId(), Map.of());
        } catch (Exception e) {
            long failures = VECTOR_ENQUEUE_FAILURES.incrementAndGet();
            log.error("[LISTENER_ERROR] 向量同步任务入队失败: changeType={}, entityId={}, totalFailures={}, errorType={}",
                    event.getChangeType(), event.getEntityId(), failures, e.getClass().getSimpleName());
        }
    }

    private void evictPostModelCache(Long postId) {
        if (postId == null) return;
        try {
            Cache cache = cacheManager.getCache(RedisCacheNames.POST_MODEL);
            if (cache != null) cache.evict(postId);
        } catch (Exception e) {
            log.warn("[LISTENER_WARN] 岗位模型缓存清理失败: postId={}, error={}", postId, e.getMessage());
        }
    }

    /**
     * 递增员工向量缓存 epoch，使推荐缓存 key 失效（与 AbilityChangeListener 同一链路）。
     * 监听器失败不影响岗位模型更新本身，但必须记录错误指标。
     */
    private void bumpVectorCacheEpoch() {
        try {
            stringRedisTemplate.opsForValue().increment(RedisCacheNames.EMP_VECTOR_CACHE_EPOCH);
        } catch (Exception e) {
            long failures = VECTOR_EPOCH_FAILURES.incrementAndGet();
            log.error("[LISTENER_ERROR] 员工向量缓存 epoch 递增失败: totalFailures={}, errorType={}",
                    failures, e.getClass().getSimpleName());
        }
    }
}
