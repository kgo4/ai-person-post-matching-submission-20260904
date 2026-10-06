package com.example.matching.config;

import lombok.extern.slf4j.Slf4j;
import org.redisson.Redisson;
import org.redisson.api.RedissonClient;
import org.redisson.config.Config;
import org.redisson.config.SingleServerConfig;
import org.redisson.spring.data.connection.RedissonConnectionFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.util.StringUtils;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.concurrent.atomic.AtomicReference;

/**
 * 容忍型 Redisson 配置（替代被排除的 {@code RedissonAutoConfigurationV2}）。
 * <p>
 * 目标：Redis 不可用时应用必须能启动并降级运行，而不是在 Bean 创建阶段直接失败。
 * <ul>
 *   <li>启动时最多重试 {@value #MAX_ATTEMPTS} 次（快速超时），成功则使用真实客户端
 *       （Redisson 自身具备断线自动重连）；</li>
 *   <li>全部失败则返回动态代理降级客户端：任何连接操作先按 30s 冷却尝试重建真实客户端
 *       （Redis 恢复后自动恢复），仍不可用则抛出 {@code IllegalStateException}，
 *       由 {@link RedisCacheErrorHandler}（缓存降级走 DB）与各调度任务 try/catch
 *       （分布式锁跳过本轮）兜底；</li>
 *   <li>空密码视为未配置（本地 Redis 无鉴权），避免 AUTH 报错。</li>
 * </ul>
 */
@Slf4j
@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(name = "spring.redis.enabled", havingValue = "true", matchIfMissing = true)
public class RedissonTolerantConfig {

    private static final int MAX_ATTEMPTS = 3;
    private static final long ATTEMPT_BACKOFF_MS = 2_000;
    private static final long RECONNECT_COOLDOWN_MS = 30_000;

    @Bean(destroyMethod = "shutdown")
    public RedissonClient redissonClient(Environment environment) {
        Supplier configSupplier = () -> buildConfig(environment);

        Exception lastError = null;
        for (int attempt = 1; attempt <= MAX_ATTEMPTS; attempt++) {
            try {
                RedissonClient client = Redisson.create(configSupplier.get());
                if (!client.getNodesGroup().pingAll(2, java.util.concurrent.TimeUnit.SECONDS)) {
                    client.shutdown();
                    throw new IllegalStateException("Redis ping failed");
                }
                log.info("Redis connected (attempt {}/{}): {}:{}",
                        attempt, MAX_ATTEMPTS,
                        environment.getProperty("spring.data.redis.host", "localhost"),
                        environment.getProperty("spring.data.redis.port", Integer.class, 6379));
                return client;
            } catch (Exception e) {
                lastError = e;
                if (attempt < MAX_ATTEMPTS) {
                    log.warn("Redis 连接失败（第{}/{}次），{}ms 后重试: {}",
                            attempt, MAX_ATTEMPTS, ATTEMPT_BACKOFF_MS, e.getMessage());
                    try {
                        Thread.sleep(ATTEMPT_BACKOFF_MS);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        break;
                    }
                }
            }
        }

        log.warn("Redis 不可用，系统以降级模式启动（缓存走 DB、分布式锁跳过），Redis 恢复后自动重连: {}",
                lastError != null ? lastError.getMessage() : "unknown");
        return DegradedRedissonClientFactory.create(configSupplier);
    }

    @Bean
    public RedissonConnectionFactory redissonConnectionFactory(RedissonClient redissonClient) {
        return new RedissonConnectionFactory(redissonClient);
    }

    private static Config buildConfig(Environment environment) {
        String host = environment.getProperty("spring.data.redis.host", "localhost");
        int port = environment.getProperty("spring.data.redis.port", Integer.class, 6379);
        String password = environment.getProperty("spring.data.redis.password");
        int database = environment.getProperty("spring.data.redis.database", Integer.class, 0);

        Config config = new Config();
        SingleServerConfig server = config.useSingleServer()
                .setAddress("redis://" + host + ":" + port)
                .setDatabase(database)
                .setConnectTimeout(2_000)
                .setRetryAttempts(1)
                .setRetryInterval(500);
        if (StringUtils.hasText(password)) {
            server.setPassword(password);
        } else {
            // Redisson 将空字符串视为已配置密码并发送 AUTH，本地无鉴权 Redis 会报错
            server.setPassword(null);
        }
        return config;
    }

    @FunctionalInterface
    private interface Supplier {
        Config get();
    }

    /**
     * 降级 RedissonClient：动态代理实现整个接口，首次使用任一连接方法时按冷却期
     * 尝试重建真实客户端，成功则委托；仍不可用则抛 {@link IllegalStateException}。
     * 生命周期方法（shutdown/close/isShutdown 等）安全返回，避免容器关闭时报错。
     */
    private static final class DegradedRedissonClientFactory {

        static RedissonClient create(Supplier configSupplier) {
            AtomicReference<RedissonClient> delegateRef = new AtomicReference<>();
            AtomicReference<Throwable> lastErrorRef = new AtomicReference<>();
            java.util.concurrent.atomic.AtomicLong lastAttemptRef = new java.util.concurrent.atomic.AtomicLong(0);

            InvocationHandler handler = (proxy, method, args) -> {
                String name = method.getName();
                Class<?>[] paramTypes = method.getParameterTypes();

                // Object 基础方法直接委托
                if ("equals".equals(name) && paramTypes.length == 1) {
                    return proxy == args[0];
                }
                if ("hashCode".equals(name)) {
                    return System.identityHashCode(proxy);
                }
                if ("toString".equals(name)) {
                    return "DegradedRedissonClient(redis unavailable)";
                }
                // 生命周期方法安全放行
                if (("shutdown".equals(name) || "close".equals(name))
                        || ("isShutdown".equals(name) || "isShuttingDown".equals(name))) {
                    return method.getReturnType() == boolean.class ? Boolean.FALSE : null;
                }
                // 拓扑探测需要真实 Config（RedissonConnectionFactory 依赖它判断单机/集群模式）
                if ("getConfig".equals(name)) {
                    return configSupplier.get();
                }

                RedissonClient delegate = delegateRef.get();
                if (delegate != null) {
                    return method.invoke(delegate, args);
                }

                long now = System.currentTimeMillis();
                long lastAttempt = lastAttemptRef.get();
                if (now - lastAttempt >= RECONNECT_COOLDOWN_MS
                        && lastAttemptRef.compareAndSet(lastAttempt, now)) {
                    try {
                        RedissonClient newClient = Redisson.create(configSupplier.get());
                        if (!newClient.getNodesGroup().pingAll(2, java.util.concurrent.TimeUnit.SECONDS)) {
                            newClient.shutdown();
                            throw new IllegalStateException("Redis ping failed");
                        }
                        if (delegateRef.compareAndSet(null, newClient)) {
                            log.info("Redis 已恢复，降级客户端切换为真实客户端");
                            return method.invoke(newClient, args);
                        }
                        newClient.shutdown();
                        delegate = delegateRef.get();
                        if (delegate != null) {
                            return method.invoke(delegate, args);
                        }
                    } catch (Throwable e) {
                        lastErrorRef.set(e);
                        log.warn("Redis 自动重连仍不可用: {}", e.getMessage());
                    }
                }
                throw new IllegalStateException(
                        "Redis unavailable, degraded mode" + (lastErrorRef.get() != null
                                ? ": " + lastErrorRef.get().getMessage() : ""));
            };

            return (RedissonClient) Proxy.newProxyInstance(
                    RedissonClient.class.getClassLoader(),
                    new Class<?>[]{RedissonClient.class},
                    handler);
        }
    }
}
