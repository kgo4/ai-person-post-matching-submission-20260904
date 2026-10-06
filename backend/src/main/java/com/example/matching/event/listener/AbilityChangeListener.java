package com.example.matching.event.listener;

import com.example.matching.config.RedisCacheNames;
import com.example.matching.event.AbilityChangeEvent;
import com.example.matching.service.common.VectorRecallCacheEpoch;
import com.example.matching.service.common.VectorSyncTaskService;
import com.example.matching.service.kg.GraphChangeSetService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 能力变更事件监听器
 * <p>
 * 监听器只负责入队向量同步任务（唯一业务键 EMPLOYEE:{empId}），
 * 向量写入由后台任务执行，失败自动指数退避重试，避免"WARN 后结束"丢更新。
 * <p>
 * 每个执行单元独立try-catch，一个失败不阻断其他。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AbilityChangeListener {

    private final GraphChangeSetService graphChangeSetService;
    private final RedisTemplate<String, Object> redisTemplate;
    private final VectorSyncTaskService vectorSyncTaskService;
    private final VectorRecallCacheEpoch vectorRecallCacheEpoch;
    private final CacheManager cacheManager;

    private static final AtomicLong RECOMMENDATION_EPOCH_FAILURES = new AtomicLong(0);
    private static final AtomicLong VECTOR_EPOCH_FAILURES = new AtomicLong(0);
    private static final AtomicLong GRAPH_ENQUEUE_FAILURES = new AtomicLong(0);
    private static final AtomicLong VECTOR_ENQUEUE_FAILURES = new AtomicLong(0);

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void handleAbilityChange(AbilityChangeEvent event) {
        log.info("收到能力变更事件：changeType={}, entityId={}", event.getChangeType(), event.getEntityId());

        switch (event.getChangeType()) {
            case "EMP_ABILITY":
                executeAdvanceRecommendationCacheEpoch();
                executeAdvanceVectorRecallEpoch();
                executeGraphChangeEnqueue(event);
                executeVectorSyncEnqueue(event);
                break;
            case "TAG_CONFIG":
                executeAdvanceVectorRecallEpoch();
                break;
            default:
                log.warn("未知的能力变更类型：{}", event.getChangeType());
        }
    }

    private void executeAdvanceVectorRecallEpoch() {
        try {
            if (vectorRecallCacheEpoch.advance() >= 0) {
                return;
            }
        } catch (Exception e) {
            long failures = VECTOR_EPOCH_FAILURES.incrementAndGet();
            log.error("[LISTENER_ERROR] 向量召回epoch递增失败: totalFailures={}, errorType={}",
                    failures, e.getClass().getSimpleName());
        }
        try {
            Cache cache = cacheManager.getCache(RedisCacheNames.VECTOR_RECALL);
            if (cache != null) {
                cache.clear();
            }
        } catch (Exception e) {
            log.warn("向量召回缓存全量清空失败: errorType={}", e.getClass().getSimpleName());
        }
    }

    private void executeAdvanceRecommendationCacheEpoch() {
        try {
            redisTemplate.opsForValue().increment(RedisCacheNames.EMP_VECTOR_CACHE_EPOCH);
        } catch (Exception e) {
            long failures = RECOMMENDATION_EPOCH_FAILURES.incrementAndGet();
            log.error("[LISTENER_ERROR] 推荐缓存epoch递增失败: totalFailures={}, errorType={}",
                    failures, e.getClass().getSimpleName());
        }
    }

    private void executeGraphChangeEnqueue(AbilityChangeEvent event) {
        try {
            graphChangeSetService.requestChange("EMP_ABILITY", "EMPLOYEE", event.getEntityId(),
                    "UPSERT", Map.of("trigger", "AbilityChangeEvent"), null);
        } catch (Exception e) {
            long failures = GRAPH_ENQUEUE_FAILURES.incrementAndGet();
            log.error("[LISTENER_ERROR] 图变更入队失败: changeType={}, entityId={}, totalFailures={}, errorType={}",
                    event.getChangeType(), event.getEntityId(), failures, e.getClass().getSimpleName());
        }
    }

    private void executeVectorSyncEnqueue(AbilityChangeEvent event) {
        try {
            vectorSyncTaskService.enqueue(VectorSyncTaskService.ENTITY_EMPLOYEE, event.getEntityId(), Map.of());
        } catch (Exception e) {
            long failures = VECTOR_ENQUEUE_FAILURES.incrementAndGet();
            log.error("[LISTENER_ERROR] 向量同步任务入队失败: changeType={}, entityId={}, totalFailures={}, errorType={}",
                    event.getChangeType(), event.getEntityId(), failures, e.getClass().getSimpleName());
        }
    }
}
