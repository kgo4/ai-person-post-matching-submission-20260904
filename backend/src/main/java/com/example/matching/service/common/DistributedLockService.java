package com.example.matching.service.common;

import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.stereotype.Service;
import java.time.Duration;
import java.util.concurrent.TimeUnit;

@Slf4j
@Service
public class DistributedLockService {

    private final RedissonClient redissonClient;
    private static final String LOCK_PREFIX = "matching:lock:";
    /** 降级告警节流：Redis 不可用时每 60 秒最多打印一次 WARN，避免定时任务每轮刷屏 */
    private static final long DEGRADE_WARN_INTERVAL_MS = 60_000;
    private volatile long lastDegradeWarnAt = 0;

    public DistributedLockService(RedissonClient redissonClient) {
        this.redissonClient = redissonClient;
    }

    public LockHandle tryAcquire(String lockName) {
        return tryAcquire(lockName, 0);
    }

    public LockHandle tryAcquire(String lockName, long waitSeconds) {
        try {
            RLock lock = redissonClient.getLock(LOCK_PREFIX + lockName);
            boolean acquired = lock.tryLock(waitSeconds, TimeUnit.SECONDS);
            return acquired ? new LockHandle(lockName, lock) : null;
        } catch (org.redisson.client.RedisException | IllegalStateException e) {
            // Redis 不可用（真实连接失败或降级客户端）：跳过本轮，调度器照常运行
            long now = System.currentTimeMillis();
            if (now - lastDegradeWarnAt >= DEGRADE_WARN_INTERVAL_MS) {
                lastDegradeWarnAt = now;
                log.warn("分布式锁不可用（Redis 降级），本轮调度跳过: lock={}, error={}", lockName, e.getMessage());
            }
            return null;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return null;
        }
    }

    public LockHandle tryAcquire(String lockName, Duration duration) {
        return tryAcquire(lockName, duration.getSeconds());
    }

    public static class LockHandle implements AutoCloseable {
        private final String lockName;
        private final RLock lock;

        public LockHandle(String lockName, RLock lock) {
            this.lockName = lockName;
            this.lock = lock;
        }

        public String getLockName() { return lockName; }
        public RLock getLock() { return lock; }
        public boolean acquired() { return true; }

        @Override
        public void close() {
            if (lock.isHeldByCurrentThread()) {
                try { lock.unlock(); } catch (Exception e) {
                    log.warn("Failed to unlock: lockName={}", lockName, e);
                }
            }
        }
    }
}
