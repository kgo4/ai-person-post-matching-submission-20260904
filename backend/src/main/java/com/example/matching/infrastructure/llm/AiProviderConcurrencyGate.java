package com.example.matching.infrastructure.llm;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.concurrent.Semaphore;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

/**
 * Provider-level bulkhead shared by every enterprise text-model call.
 * It deliberately sits below business services so synchronous and asynchronous callers
 * consume the same provider budget.
 */
@Slf4j
@Component
public class AiProviderConcurrencyGate {

    private static final ThreadLocal<Boolean> BACKGROUND = ThreadLocal.withInitial(() -> false);
    private final Semaphore permits;
    private final Semaphore backgroundPermits;
    private final long acquireTimeoutMillis;
    private final long backgroundAcquireTimeoutMillis;
    private final int maxAttempts;
    private final long retryBaseBackoffMillis;

    public AiProviderConcurrencyGate(
            @Value("${ai.provider.max-concurrency:2}") int maxConcurrency,
            @Value("${ai.provider.governance-max-concurrency:1}") int governanceConcurrency,
            @Value("${ai.provider.governance-acquire-timeout-millis:0}") long governanceAcquireTimeoutMillis,
            @Value("${ai.provider.acquire-timeout-millis:5000}") long acquireTimeoutMillis,
            @Value("${ai.provider.retry.max-attempts:3}") int maxAttempts,
            @Value("${ai.provider.retry.base-backoff-millis:1000}") long retryBaseBackoffMillis) {
        this.permits = new Semaphore(Math.max(1, maxConcurrency), true);
        this.backgroundPermits = new Semaphore(Math.max(1, governanceConcurrency), true);
        this.acquireTimeoutMillis = Math.max(0, acquireTimeoutMillis);
        this.backgroundAcquireTimeoutMillis = Math.max(0, governanceAcquireTimeoutMillis);
        this.maxAttempts = Math.max(1, maxAttempts);
        this.retryBaseBackoffMillis = Math.max(0, retryBaseBackoffMillis);
    }

    public <T> T execute(Supplier<T> call) {
        boolean background = Boolean.TRUE.equals(BACKGROUND.get());
        Semaphore activePermits = background ? backgroundPermits : permits;
        acquirePermit(activePermits, background ? backgroundAcquireTimeoutMillis : acquireTimeoutMillis);
        try {
            RuntimeException lastFailure = null;
            for (int attempt = 1; attempt <= maxAttempts; attempt++) {
                try {
                    return call.get();
                } catch (RuntimeException e) {
                    lastFailure = e;
                    if (!isRetryable(e) || attempt == maxAttempts) {
                        throw e;
                    }
                    long backoff = backoffMillis(attempt);
                    log.warn("AI provider connection failed; retrying: attempt={}/{}, backoffMs={}, reason={}",
                            attempt, maxAttempts, backoff, rootMessage(e));
                    sleep(backoff);
                }
            }
            throw lastFailure == null ? new IllegalStateException("AI provider call failed") : lastFailure;
        } finally {
            activePermits.release();
        }
    }

    /** Executes a model call in the isolated governance budget. */
    public <T> T executeInBackground(Supplier<T> call) {
        boolean previous = Boolean.TRUE.equals(BACKGROUND.get());
        BACKGROUND.set(true);
        try {
            return execute(call);
        } finally {
            BACKGROUND.set(previous);
        }
    }

    private void acquirePermit(Semaphore activePermits, long timeoutMillis) {
        try {
            if (!activePermits.tryAcquire(timeoutMillis, TimeUnit.MILLISECONDS)) {
                throw new AiProviderBusyException("AI provider is busy; concurrency queue wait expired");
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new AiProviderBusyException("AI provider queue wait interrupted");
        }
    }

    private long backoffMillis(int attempt) {
        long exponential = retryBaseBackoffMillis * (1L << Math.min(attempt - 1, 4));
        long jitter = exponential == 0 ? 0 : ThreadLocalRandom.current().nextLong(Math.max(1, exponential / 4));
        return exponential + jitter;
    }

    private void sleep(long millis) {
        if (millis <= 0) return;
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new AiProviderBusyException("AI provider retry interrupted");
        }
    }

    private boolean isRetryable(Throwable error) {
        for (Throwable current = error; current != null; current = current.getCause()) {
            if (current instanceof IOException) return true;
        }
        String message = rootMessage(error).toLowerCase();
        return message.contains("connection reset") || message.contains("rate limit")
                || message.contains("too many requests") || message.contains(" 429")
                || message.contains(" 502") || message.contains(" 503") || message.contains(" 504");
    }

    private String rootMessage(Throwable error) {
        Throwable current = error;
        while (current.getCause() != null) current = current.getCause();
        return current.getMessage() == null ? current.getClass().getSimpleName() : current.getMessage();
    }
}
