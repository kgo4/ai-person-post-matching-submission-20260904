package com.example.matching.resilience;

import com.example.matching.ai.validation.DeterministicAiFallbacks;
import com.example.matching.common.exception.AiServiceException;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.github.resilience4j.retry.Retry;
import io.github.resilience4j.retry.RetryConfig;
import io.github.resilience4j.retry.RetryRegistry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.Supplier;

/**
 * AI 服务 Resilience 封装
 * <p>
 * 程序化熔断 + 重试（不使用注解，避免自调用失效）。
 * <ul>
 *   <li>熔断：滑动窗口内失败率达到阈值后打开，30 秒后进入半开探测</li>
 *   <li>重试：最多 3 次，间隔 500ms</li>
 *   <li>降级：只能使用 {@link DeterministicAiFallbacks} 中的受控 fallback 名称，
 *       不得随意传入 Supplier（防 fallback 再次调用 LLM/MQ/数据库/网络）</li>
 * </ul>
 */
@Slf4j
@Service
public class AiServiceResilience {

    private static final int MAX_RETRY_ATTEMPTS = 3;
    private static final Duration RETRY_INTERVAL = Duration.ofMillis(500);
    private static final int CIRCUIT_FAILURE_RATE_THRESHOLD = 50;
    private static final int CIRCUIT_SLIDING_WINDOW = 10;
    private static final int CIRCUIT_MIN_CALLS = 5;
    private static final Duration CIRCUIT_WAIT_OPEN = Duration.ofSeconds(30);

    private final CircuitBreakerRegistry circuitBreakerRegistry;
    private final RetryRegistry retryRegistry;
    /** 专用 AI 任务执行器（M9）：隔离 AI 调用，不占用 ForkJoinPool.commonPool */
    private final java.util.concurrent.Executor aiTaskExecutor;
    @org.springframework.beans.factory.annotation.Value("${ai.request-timeout-seconds:300}")
    private long timeoutSeconds = 300;

    public AiServiceResilience(CircuitBreakerRegistry circuitBreakerRegistry,
                               @org.springframework.beans.factory.annotation.Qualifier("aiTaskExecutor")
                               java.util.concurrent.Executor aiTaskExecutor) {
        this.circuitBreakerRegistry = circuitBreakerRegistry;
        this.aiTaskExecutor = aiTaskExecutor;
        this.retryRegistry = RetryRegistry.of(defaultRetryConfig());
    }

    private static RetryConfig defaultRetryConfig() {
        return RetryConfig.custom()
                .maxAttempts(MAX_RETRY_ATTEMPTS)
                .waitDuration(RETRY_INTERVAL)
                .build();
    }

    private CircuitBreaker circuitBreaker(String name) {
        CircuitBreakerConfig config = CircuitBreakerConfig.custom()
                .failureRateThreshold(CIRCUIT_FAILURE_RATE_THRESHOLD)
                .slidingWindowSize(CIRCUIT_SLIDING_WINDOW)
                .minimumNumberOfCalls(CIRCUIT_MIN_CALLS)
                .waitDurationInOpenState(CIRCUIT_WAIT_OPEN)
                .build();
        return circuitBreakerRegistry.circuitBreaker(name, config);
    }

    /**
     * 带熔断和重试的 AI 调用；失败时使用受控 fallback 名称降级。
     *
     * @param name          AI 服务名称（用于熔断器/重试器标识）
     * @param call          实际 AI 调用（需为可重试的纯调用）
     * @param fallbackName  受控降级名称，见 {@link DeterministicAiFallbacks}
     * @return AI 响应或确定性降级结果
     */
    public String callWithResilience(String name, Supplier<String> call, String fallbackName) {
        return callWithResilience(name, call, fallbackName, timeoutSeconds);
    }

    /**
     * Uses a scenario-specific timeout while retaining the same retry, circuit breaker and fallback policy.
     */
    public String callWithResilience(String name, Supplier<String> call, String fallbackName, long requestTimeoutSeconds) {
        try {
            return execute(name, call, requestTimeoutSeconds);
        } catch (Exception e) {
            log.warn("AI服务[{}]调用失败，使用确定性降级[{}]。原因: {}", name, fallbackName, e.getMessage());
            return DeterministicAiFallbacks.get(fallbackName).get();
        }
    }

    /**
     * 带熔断和重试的 AI 调用；失败时抛出 {@link AiServiceException}，由调用方决定降级策略。
     *
     * @param name AI 服务名称
     * @param call 实际 AI 调用
     * @return AI 响应
     * @throws AiServiceException 重试耗尽或熔断打开
     */
    public String callWithResilience(String name, Supplier<String> call) {
        try {
            return execute(name, call);
        } catch (AiServiceException e) {
            throw e;
        } catch (Exception e) {
            throw AiServiceException.retryable("ai-resilience", name, e.getMessage(), e);
        }
    }

    /** Executes a typed AI call with the standard retry/circuit-breaker policy and no string fallback. */
    public <T> T callWithResilienceOrThrow(String name, Supplier<T> call, long requestTimeoutSeconds) {
        CircuitBreaker circuitBreaker = circuitBreaker(name);
        Retry retry = retryRegistry.retry(name);
        Supplier<T> decorated = CircuitBreaker.decorateSupplier(
                circuitBreaker, Retry.decorateSupplier(retry, call));
        CompletableFuture<T> future = CompletableFuture.supplyAsync(decorated::get, aiTaskExecutor);
        try {
            return future.get(Math.max(1L, requestTimeoutSeconds), TimeUnit.SECONDS);
        } catch (TimeoutException e) {
            future.cancel(true);
            throw AiServiceException.retryable("ai-resilience", name,
                    "AI call timed out after " + requestTimeoutSeconds + " seconds", e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw AiServiceException.retryable("ai-resilience", name, "AI call interrupted", e);
        } catch (java.util.concurrent.ExecutionException e) {
            Throwable cause = e.getCause();
            if (cause instanceof RuntimeException runtimeException) {
                throw runtimeException;
            }
            throw AiServiceException.retryable("ai-resilience", name, "AI call failed", cause);
        }
    }

    private String execute(String name, Supplier<String> call) {
        return execute(name, call, timeoutSeconds);
    }

    private String execute(String name, Supplier<String> call, long requestTimeoutSeconds) {
        CircuitBreaker circuitBreaker = circuitBreaker(name);
        Retry retry = retryRegistry.retry(name);
        Supplier<String> decorated = CircuitBreaker.decorateSupplier(
                circuitBreaker, Retry.decorateSupplier(retry, call));
        CompletableFuture<String> future = CompletableFuture.supplyAsync(decorated::get, aiTaskExecutor);
        try {
            return future.get(Math.max(1L, requestTimeoutSeconds), TimeUnit.SECONDS);
        } catch (TimeoutException e) {
            future.cancel(true);
            throw AiServiceException.retryable("ai-resilience", name,
                    "AI call timed out after " + requestTimeoutSeconds + " seconds", e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw AiServiceException.retryable("ai-resilience", name, "AI call interrupted", e);
        } catch (java.util.concurrent.ExecutionException e) {
            Throwable cause = e.getCause();
            if (cause instanceof RuntimeException runtimeException) {
                throw runtimeException;
            }
            throw AiServiceException.retryable("ai-resilience", name, "AI call failed", cause);
        }
    }

    /**
     * 检查指定断路器状态
     */
    public boolean isCircuitBreakerOpen(String name) {
        try {
            CircuitBreaker cb = circuitBreakerRegistry.circuitBreaker(name);
            return cb.getState() == CircuitBreaker.State.OPEN;
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * 检查默认断路器状态（兼容旧调用方）
     */
    public boolean isCircuitBreakerOpen() {
        return isCircuitBreakerOpen("aiService");
    }

    /**
     * 获取断路器状态描述
     */
    public String getCircuitBreakerStatus() {
        try {
            CircuitBreaker cb = circuitBreakerRegistry.circuitBreaker("aiService");
            return String.format("state=%s, failureRate=%d/%d",
                    cb.getState(), cb.getMetrics().getNumberOfFailedCalls(),
                    cb.getMetrics().getNumberOfBufferedCalls());
        } catch (Exception e) {
            return "unavailable";
        }
    }
}
