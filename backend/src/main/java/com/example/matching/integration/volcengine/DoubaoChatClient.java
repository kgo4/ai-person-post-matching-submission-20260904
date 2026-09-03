package com.example.matching.integration.volcengine;

import com.volcengine.ark.runtime.model.responses.constant.ResponsesConstants;
import com.volcengine.ark.runtime.model.responses.content.InputContentItemImage;
import com.volcengine.ark.runtime.model.responses.content.InputContentItemText;
import com.volcengine.ark.runtime.model.responses.content.OutputContentItem;
import com.volcengine.ark.runtime.model.responses.content.OutputContentItemText;
import com.volcengine.ark.runtime.model.responses.item.BaseItem;
import com.volcengine.ark.runtime.model.responses.item.ItemEasyMessage;
import com.volcengine.ark.runtime.model.responses.item.ItemOutputMessage;
import com.volcengine.ark.runtime.model.responses.item.MessageContent;
import com.volcengine.ark.runtime.model.responses.request.CreateResponsesRequest;
import com.volcengine.ark.runtime.model.responses.request.ResponsesInput;
import com.volcengine.ark.runtime.model.responses.response.ResponseObject;
import com.volcengine.ark.runtime.service.ArkService;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import io.github.resilience4j.ratelimiter.RateLimiter;
import io.github.resilience4j.ratelimiter.RateLimiterConfig;
import io.github.resilience4j.retry.Retry;
import io.github.resilience4j.retry.RetryConfig;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import okhttp3.ConnectionPool;
import okhttp3.Dispatcher;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

/**
 * 豆包AI客户端
 * <p>
 * 使用火山引擎Ark SDK的Responses API，支持：
 * - 文本分析（纯文本）
 * - 视觉分析（文本+图片，多模态理解）
 * <p>
 * 文本请求使用 volcengine.ark.model；视觉请求使用独立的 volcengine.ark.vision-model。
 * <p>
 * 可靠性：所有入口经 Resilience4j 断路器 + 指数退避重试包装；
 * 重试耗尽或熔断打开时返回 null（调用方判空降级）。
 */
@Slf4j
@Component
public class DoubaoChatClient {

    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(90);
    private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(10);

    @Value("${volcengine.ark.api-key}")
    private String apiKey;

    @Value("${volcengine.ark.model:doubao-seed-2-0-pro-260215}")
    private String model;

    @Value("${volcengine.ark.vision-model:doubao-seed-2-1-turbo-260628}")
    private String visionModel;

    /** 重试次数（不含首次尝试） */
    private static final int MAX_ATTEMPTS = 3;

    /** 限流：每 1 秒最多放行请求数（配额控制，防止上游 API 被压垮） */
    private static final int RATE_LIMIT_PER_SECOND = 10;

    /** 限流等待超时：超过后抛 RateLimitExceededException，快速失败 */
    private static final java.time.Duration RATE_LIMIT_TIMEOUT = java.time.Duration.ofSeconds(2);

    private ArkService arkService;
    private CircuitBreaker circuitBreaker;
    private Retry retry;
    private RateLimiter rateLimiter;

    public DoubaoChatClient() {
    }

    @PostConstruct
    public void init() {
        // Retain TLS connections between user actions so OkHttp can reuse them.
        ConnectionPool connectionPool = new ConnectionPool(20, 5, TimeUnit.MINUTES);
        Dispatcher dispatcher = new Dispatcher();
        arkService = ArkService.builder()
                .dispatcher(dispatcher)
                .connectionPool(connectionPool)
                .baseUrl("https://ark.cn-beijing.volces.com/api/v3")
                .apiKey(apiKey)
                // The SDK otherwise allows a stalled HTTP/2 response to wait indefinitely.
                .timeout(REQUEST_TIMEOUT)
                .callTimeout(REQUEST_TIMEOUT)
                .connectTimeout(CONNECT_TIMEOUT)
                .build();
        initResilience();
        log.info("DoubaoChatClient初始化完成，model={}", model);
    }

    /** 测试用构造：注入 mock ArkService 与自定义熔断/重试/限流，跳过 @Value/@PostConstruct。 */
    DoubaoChatClient(ArkService arkService, CircuitBreaker circuitBreaker,
                     Retry retry, RateLimiter rateLimiter) {
        this.arkService = arkService;
        this.circuitBreaker = circuitBreaker;
        this.retry = retry;
        this.rateLimiter = rateLimiter;
    }

    private void initResilience() {
        CircuitBreakerConfig cbConfig = CircuitBreakerConfig.custom()
                .failureRateThreshold(50)
                .slidingWindowSize(10)
                .minimumNumberOfCalls(5)
                .waitDurationInOpenState(Duration.ofSeconds(30))
                .permittedNumberOfCallsInHalfOpenState(2)
                .build();
        circuitBreaker = CircuitBreaker.of("doubao-chat", cbConfig);

        RetryConfig retryConfig = RetryConfig.custom()
                .maxAttempts(MAX_ATTEMPTS)
                .waitDuration(Duration.ofSeconds(1))
                .build();
        retry = Retry.of("doubao-chat", retryConfig);

        RateLimiterConfig rlConfig = RateLimiterConfig.custom()
                .limitForPeriod(RATE_LIMIT_PER_SECOND)
                .limitRefreshPeriod(Duration.ofSeconds(1))
                .timeoutDuration(RATE_LIMIT_TIMEOUT)
                .build();
        rateLimiter = RateLimiter.of("doubao-chat", rlConfig);
    }

    @PreDestroy
    public void destroy() {
        if (arkService != null) {
            arkService.shutdownExecutor();
            log.info("DoubaoChatClient已关闭");
        }
    }

    /**
     * 视觉分析（文本+图片）- 使用 Responses API
     *
     * @param systemPrompt 系统提示词
     * @param userPrompt   用户提示词
     * @param imageUrls    图片URL列表（data:image/jpeg;base64,... 或 http/https URL）
     * @return 模型返回的文本；重试耗尽或熔断打开时为 null
     */
    public String analyzeVision(String systemPrompt, String userPrompt, List<String> imageUrls) {
        return callWithResilience("视觉分析", () -> {
            MessageContent.Builder contentBuilder = MessageContent.builder();
            if (imageUrls != null) {
                for (String url : imageUrls) {
                    contentBuilder.addListItem(
                            InputContentItemImage.builder().imageUrl(url).build()
                    );
                }
            }
            contentBuilder.addListItem(
                    InputContentItemText.builder().text(userPrompt).build()
            );

            CreateResponsesRequest.Builder requestBuilder = CreateResponsesRequest.builder()
                    .model(visionModel);
            if (systemPrompt != null && !systemPrompt.isBlank()) {
                requestBuilder.instructions(systemPrompt);
            }
            CreateResponsesRequest request = requestBuilder
                    .input(ResponsesInput.builder()
                            .addListItem(ItemEasyMessage.builder()
                                    .role(ResponsesConstants.MESSAGE_ROLE_USER)
                                    .content(contentBuilder.build())
                                    .build())
                            .build())
                    .build();

            ResponseObject response = arkService.createResponse(request);
            return extractResponseContent(response);
        });
    }

    /**
     * 文本分析（纯文本）- 使用 Responses API
     *
     * @param systemPrompt 系统提示词
     * @param userPrompt   用户提示词
     * @return 模型返回的文本；重试耗尽或熔断打开时为 null
     */
    public String analyzeText(String systemPrompt, String userPrompt) {
        return callWithResilience("文本分析", () -> {
            CreateResponsesRequest.Builder requestBuilder = CreateResponsesRequest.builder()
                    .model(model);

            if (systemPrompt != null && !systemPrompt.isBlank()) {
                requestBuilder.instructions(systemPrompt);
            }

            CreateResponsesRequest request = requestBuilder
                    .input(ResponsesInput.builder()
                            .addListItem(ItemEasyMessage.builder()
                                    .role(ResponsesConstants.MESSAGE_ROLE_USER)
                                    .content(MessageContent.builder()
                                            .addListItem(InputContentItemText.builder()
                                                    .text(userPrompt)
                                                    .build())
                                            .build())
                                    .build())
                            .build())
                    .build();

            ResponseObject response = arkService.createResponse(request);
            return extractResponseContent(response);
        });
    }

    /**
     * 限流 + 熔断 + 重试包装：
     * 限流器最内层（每次上游调用消耗一个配额，重试也计入配额）；
     * 断路器在重试链外层（只按最终结果计数）；熔断打开时快速失败。
     * 全部失败返回 null，由调用方判空降级。
     */
    private String callWithResilience(String operation, Supplier<String> call) {
        try {
            // 内层：限流（每次尝试消耗配额）→ 中层：重试 → 外层：熔断
            Supplier<String> limited = RateLimiter.decorateSupplier(rateLimiter, call);
            Supplier<String> retried = Retry.decorateSupplier(retry, limited);
            return CircuitBreaker.decorateSupplier(circuitBreaker, retried).get();
        } catch (Exception e) {
            log.error("{}调用失败（重试{}次后仍失败、熔断打开或触发限流）: {}", operation, MAX_ATTEMPTS - 1, e.getMessage(), e);
            return null;
        }
    }

    /**
     * 提取 Responses API 响应内容
     */
    private String extractResponseContent(ResponseObject response) {
        if (response != null && response.getOutput() != null) {
            StringBuilder text = new StringBuilder();
            for (BaseItem item : response.getOutput()) {
                if (item instanceof ItemOutputMessage message && message.getContent() != null) {
                    for (OutputContentItem contentItem : message.getContent()) {
                        if (contentItem instanceof OutputContentItemText textItem && textItem.getText() != null) {
                            text.append(textItem.getText());
                        }
                    }
                }
            }
            if (!text.isEmpty()) {
                return text.toString();
            }
        }
        log.warn("ResponseObject响应为空");
        return null;
    }
}
