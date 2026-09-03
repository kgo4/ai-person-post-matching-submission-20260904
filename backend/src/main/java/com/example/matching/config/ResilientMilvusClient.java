package com.example.matching.config;

import io.milvus.client.MilvusServiceClient;
import io.milvus.param.ConnectParam;
import io.milvus.param.R;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

/**
 * 可重连的 Milvus 客户端包装器。
 * <p>
 * 解决两个问题：
 * 1. 启动时 Milvus 不可用 → 不阻塞应用启动，降级运行
 * 2. 运行时 Milvus 恢复 → 自动重连，无需重启应用
 * <p>
 * 使用方通过 {@link #getClient()} 获取客户端，返回 null 表示当前不可用。
 * 内部按退避策略自动尝试重连（默认 60 秒冷却）。
 */
@Slf4j
@Component
@ConditionalOnProperty(name = "milvus.enabled", havingValue = "true")
@ConditionalOnExpression("'${milvus.uri:}'.trim().length() > 0")
public class ResilientMilvusClient {

    private static final int MAX_CONNECT_ATTEMPTS = 3;
    private static final long RECONNECT_COOLDOWN_MS = 60_000;

    private final ConnectParam connectParam;
    private final AtomicReference<MilvusServiceClient> clientRef = new AtomicReference<>();
    private final AtomicBoolean reconnecting = new AtomicBoolean(false);
    private volatile long lastAttemptTime = 0;
    private volatile boolean everConnected = false;

    public ResilientMilvusClient(MilvusConfig config) {
        this.connectParam = ConnectParam.newBuilder()
                .withUri(config.getUri())
                .withToken(config.getToken())
                .withDatabaseName(config.getDatabase())
                // 快速失败：每次连接尝试最多 5 秒，配合启动期 3 次重试避免长时间阻塞
                .withConnectTimeout(5, TimeUnit.SECONDS)
                .build();
        tryConnect();
    }

    /**
     * 获取 Milvus 客户端，不可用时返回 null。
     * 每次调用都会检查连接状态，冷却期内自动尝试重连。
     */
    public MilvusServiceClient getClient() {
        MilvusServiceClient existing = clientRef.get();
        if (existing != null) {
            return existing;
        }
        long now = System.currentTimeMillis();
        if (now - lastAttemptTime >= RECONNECT_COOLDOWN_MS) {
            tryConnect();
        }
        return clientRef.get();
    }

    public boolean isAvailable() {
        return getClient() != null;
    }

    /**
     * 使当前连接失效（RPC 失败/连接被重置时由调用方触发）。
     * <p>
     * Serverless Milvus 空闲连接会被服务端断开，但本地 client 对象仍存活，
     * 复用死连接会导致每次检索都失败并打印 RPC 错误堆栈。
     * 失效后：冷却期内 {@link #getClient()} 返回 null（调用方走 MySQL 降级，无 RPC 噪音），
     * 冷却结束后自动尝试重连。
     */
    public void invalidate() {
        MilvusServiceClient client = clientRef.getAndSet(null);
        if (client != null) {
            try {
                client.close();
            } catch (Exception e) {
                log.debug("Milvus invalidated client close error: {}", e.getMessage());
            }
            log.warn("Milvus connection invalidated (RPC failure); vector operations fall back to MySQL until reconnect");
        }
    }

    private void tryConnect() {
        if (!reconnecting.compareAndSet(false, true)) {
            return;
        }
        try {
            lastAttemptTime = System.currentTimeMillis();
            for (int attempt = 1; attempt <= MAX_CONNECT_ATTEMPTS; attempt++) {
                MilvusServiceClient newClient = null;
                try {
                    newClient = new MilvusServiceClient(connectParam);
                    R<io.milvus.grpc.CheckHealthResponse> health = newClient.checkHealth();
                    if (!R.Status.Success.equals(R.Status.valueOf(health.getStatus()))
                            || health.getData() == null || !health.getData().getIsHealthy()) {
                        throw new IllegalStateException(health.getMessage());
                    }

                    MilvusServiceClient oldClient = clientRef.getAndSet(newClient);
                    if (oldClient != null && oldClient != newClient) {
                        oldClient.close();
                    }
                    if (!everConnected) {
                        everConnected = true;
                        log.info("Milvus client connected: uri={}", connectParam.getHost());
                    } else {
                        log.info("Milvus client reconnected: uri={}", connectParam.getHost());
                    }
                    return;
                } catch (Exception exception) {
                    if (newClient != null) {
                        try {
                            newClient.close();
                        } catch (Exception closeException) {
                            log.debug("Milvus failed client close error: {}", closeException.getMessage());
                        }
                    }
                    if (attempt == MAX_CONNECT_ATTEMPTS) {
                        log.warn("Milvus unavailable after {} attempts; vector operations will use MySQL until it recovers: {}",
                                MAX_CONNECT_ATTEMPTS, exception.getMessage());
                    } else {
                        log.warn("Milvus connection attempt {}/{} failed: {}", attempt, MAX_CONNECT_ATTEMPTS,
                                exception.getMessage());
                    }
                }
            }
        } finally {
            reconnecting.set(false);
        }
    }

    @PreDestroy
    public void close() {
        MilvusServiceClient client = clientRef.getAndSet(null);
        if (client != null) {
            try {
                client.close();
                log.info("Milvus client closed");
            } catch (Exception e) {
                log.debug("Milvus client close error: {}", e.getMessage());
            }
        }
    }
}
