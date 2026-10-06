package com.example.matching.agent.json;

import java.util.concurrent.ThreadLocalRandom;

/** 第 4 层重试策略：指数退避 + 抖动。 */
public final class JsonRetryPolicy {

    private final int maxAttempts;          // 最大纠错重试次数
    private final long baseBackoffMillis;   // 基础退避毫秒

    public JsonRetryPolicy(int maxAttempts, long baseBackoffMillis) {
        this.maxAttempts = Math.max(0, maxAttempts);
        this.baseBackoffMillis = Math.max(1, baseBackoffMillis);
    }

    /** 已经失败过 attempt 次时，是否允许再重试一次。 */
    public boolean shouldRetry(int attempt) {
        return attempt < maxAttempts;
    }

    /** 第 attempt 次重试前的等待毫秒数（指数退避 + 抖动）。 */
    public long backoffMillis(int attempt) {
        long base = baseBackoffMillis * (1L << Math.min(attempt, 10));
        return ThreadLocalRandom.current().nextLong(base);
    }
}
