package com.example.matching.integration.volcengine;

import com.volcengine.ark.runtime.model.responses.request.CreateResponsesRequest;
import com.volcengine.ark.runtime.model.responses.response.ResponseObject;
import com.volcengine.ark.runtime.service.ArkService;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import io.github.resilience4j.ratelimiter.RateLimiter;
import io.github.resilience4j.ratelimiter.RateLimiterConfig;
import io.github.resilience4j.retry.Retry;
import io.github.resilience4j.retry.RetryConfig;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@DisplayName("DoubaoChatClient resilience")
class DoubaoChatClientTest {

    private static final Retry NO_WAIT_RETRY = Retry.of("test", RetryConfig.custom()
            .maxAttempts(1)
            .waitDuration(Duration.ZERO)
            .build());
    private static final CircuitBreaker PERMISSIVE_BREAKER = CircuitBreaker.of("test",
            CircuitBreakerConfig.custom()
                    .failureRateThreshold(99)
                    .slidingWindowSize(100)
                    .minimumNumberOfCalls(100)
                    .build());

    @Test
    @DisplayName("失败时重试 MAX_ATTEMPTS 次后返回 null")
    void productionClientBuildsItsOwnArkService() {
        new ApplicationContextRunner()
                .withPropertyValues("volcengine.ark.api-key=test-key")
                .withBean(DoubaoChatClient.class)
                .run(context -> assertThat(context).hasNotFailed());
    }

    @Test
    @DisplayName("production client initializes without an externally registered ArkService")
    void retriesThenReturnsNull() {
        ArkService arkService = mock(ArkService.class);
        when(arkService.createResponse(any(CreateResponsesRequest.class)))
                .thenThrow(new RuntimeException("upstream 500"));
        Retry retry = Retry.of("test", RetryConfig.custom()
                .maxAttempts(3)
                .waitDuration(Duration.ZERO)
                .build());
        DoubaoChatClient client = new DoubaoChatClient(arkService, PERMISSIVE_BREAKER, retry,
                unlimitedRateLimiter());

        String result = client.analyzeText("sys", "hello");

        assertThat(result).isNull();
        verify(arkService, times(3)).createResponse(any(CreateResponsesRequest.class));
    }

    @Test
    @DisplayName("熔断打开后快速失败，不再发起上游调用")
    void circuitBreakerOpenFailsFast() {
        ArkService arkService = mock(ArkService.class);
        when(arkService.createResponse(any(CreateResponsesRequest.class)))
                .thenThrow(new RuntimeException("upstream 500"));
        CircuitBreaker breaker = CircuitBreaker.of("test", CircuitBreakerConfig.custom()
                .failureRateThreshold(50)
                .slidingWindowSize(10)
                .minimumNumberOfCalls(5)
                .waitDurationInOpenState(Duration.ofMinutes(1))
                .build());
        DoubaoChatClient client = new DoubaoChatClient(arkService, breaker, NO_WAIT_RETRY,
                unlimitedRateLimiter());

        for (int i = 0; i < 5; i++) {
            client.analyzeText("sys", "hello");
        }
        assertThat(breaker.getState()).isEqualTo(CircuitBreaker.State.OPEN);

        int callsBefore = org.mockito.Mockito.mockingDetails(arkService).getInvocations().size();
        String result = client.analyzeText("sys", "hello");

        assertThat(result).isNull();
        assertThat(org.mockito.Mockito.mockingDetails(arkService).getInvocations().size())
                .isEqualTo(callsBefore);
    }

    @Test
    @DisplayName("触发限流时返回 null 且不再消耗上游配额")
    void rateLimitExceededReturnsNull() {
        ArkService arkService = mock(ArkService.class);
        when(arkService.createResponse(any(CreateResponsesRequest.class)))
                .thenReturn(new ResponseObject());
        RateLimiter limiter = RateLimiter.of("test", RateLimiterConfig.custom()
                .limitForPeriod(2)
                .limitRefreshPeriod(Duration.ofMinutes(1))
                .timeoutDuration(Duration.ofMillis(100))
                .build());
        DoubaoChatClient client = new DoubaoChatClient(arkService, PERMISSIVE_BREAKER, NO_WAIT_RETRY, limiter);

        // 前两次调用消耗配额（mock 空响应返回 null 属正常），第三次被限流拦截
        client.analyzeText("sys", "hello");
        client.analyzeText("sys", "hello");
        String third = client.analyzeText("sys", "hello");

        assertThat(third).isNull();
        verify(arkService, times(2)).createResponse(any(CreateResponsesRequest.class));
    }

    private RateLimiter unlimitedRateLimiter() {
        return RateLimiter.of("test", RateLimiterConfig.custom()
                .limitForPeriod(100_000)
                .limitRefreshPeriod(Duration.ofSeconds(1))
                .timeoutDuration(Duration.ofMillis(10))
                .build());
    }
}
