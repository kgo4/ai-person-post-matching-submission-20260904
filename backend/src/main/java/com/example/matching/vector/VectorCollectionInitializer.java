package com.example.matching.vector;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * 向量集合初始化器
 * <p>
 * 应用启动后自动创建 Milvus 集合和索引。
 * 支持幂等操作：集合已存在则跳过。
 * <p>
 * 容错：Milvus 不可达时按退避策略重试 3 次（3s/6s），全部失败后仅记录 WARN，
 * 应用以降级模式继续启动，向量检索在 Milvus 恢复后由 {@link ResilientMilvusClient} 自动重连。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class VectorCollectionInitializer {

    private static final int MAX_ATTEMPTS = 3;
    private static final long BACKOFF_BASE_MS = 3_000;

    private final MilvusVectorService milvusVectorService;

    @EventListener(ApplicationReadyEvent.class)
    public void init() {
        Throwable lastError = null;
        for (int attempt = 1; attempt <= MAX_ATTEMPTS; attempt++) {
            try {
                milvusVectorService.initCollections();
                log.info("Milvus向量集合初始化完成");
                return;
            } catch (Exception e) {
                lastError = e;
                if (attempt < MAX_ATTEMPTS) {
                    long backoff = BACKOFF_BASE_MS * attempt;
                    log.warn("Milvus向量集合初始化失败（第{}/{}次），{}ms 后重试: {}",
                            attempt, MAX_ATTEMPTS, backoff, e.getMessage());
                    try {
                        Thread.sleep(backoff);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        return;
                    }
                }
            }
        }
        log.warn("Milvus向量集合初始化失败（已重试{}次），向量检索降级运行，Milvus 恢复后自动重连: {}",
                MAX_ATTEMPTS, lastError != null ? lastError.getMessage() : "unknown");
    }
}
