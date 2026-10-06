package com.example.matching.infrastructure.llm;

import com.example.matching.agent.config.LangChain4jAgentProperties;
import com.example.matching.entity.system.SystemAiModelConfig;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.Content;
import dev.langchain4j.data.message.ImageContent;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.TextContent;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.openai.OpenAiChatModel;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

/**
 * 企业全局视觉模型门面：承载图片/视频帧的多模态理解。
 * <p>
 * 与 {@link EnterpriseChatLanguageModel}（文本）同构但互相独立：
 * <ul>
 *   <li>复用同一套模型凭据（base-url / api-key），仅模型名不同 —— 因此只需一个视觉模型名即可切换；</li>
 *   <li>持有 {@link AtomicReference} 中的当前实例，保存配置后原子替换，已注入的调用方自动生效；</li>
 *   <li>未配置时降级为 {@link NoOpVisionChatModel}，调用抛异常，由上层业务判空降级。</li>
 * </ul>
 * <p>
 * <b>协议约束</b>：图片只能出现在 user 消息中（OpenAI 兼容约定，DeepSeek 等厂商会拒绝 system 消息带图），
 * 因此 systemPrompt 以纯文本 system 消息下发，图片与用户文本作为 user 消息的 content 块。
 */
@Slf4j
@Component
public class EnterpriseVisionChatModel {

    private static final int VISION_REQUEST_TIMEOUT_SECONDS = 90;
    private static final int DEFAULT_MAX_OUTPUT_TOKENS = 4096;

    /** 视觉分析输出上限（结构化短结论，无需长文本）。 */
    @org.springframework.beans.factory.annotation.Value("${ai.vision.max-output-tokens:4096}")
    private int maxOutputTokens = DEFAULT_MAX_OUTPUT_TOKENS;

    /** 视觉请求超时（秒），独立于文本链路。 */
    @org.springframework.beans.factory.annotation.Value("${ai.vision.request-timeout-seconds:90}")
    private int defaultRequestTimeoutSeconds = VISION_REQUEST_TIMEOUT_SECONDS;

    private final AtomicReference<ChatModel> delegate = new AtomicReference<>(new NoOpVisionChatModel());
    private final AiProviderConcurrencyGate providerConcurrencyGate;

    private volatile String currentModelName;
    private volatile boolean enabled;

    public EnterpriseVisionChatModel(AiProviderConcurrencyGate providerConcurrencyGate) {
        this.providerConcurrencyGate = providerConcurrencyGate;
    }

    /**
     * 用全局配置刷新视觉模型实例（原子替换）。
     * <p>
     * 视觉模型复用文本模型的 baseUrl / apiKey，只取独立配置的模型名；
     * 模型名缺失时视为「未启用视觉」，走降级而不是复用文本模型名 ——
     * 文本模型通常不具备视觉能力，复用只会让每次请求都以 400 失败。
     */
    public void refreshFromConfig(SystemAiModelConfig config, String decryptedApiKey, String visionModelName) {
        boolean usable = config != null
                && Boolean.TRUE.equals(config.getEnabled())
                && hasText(config.getBaseUrl())
                && hasText(decryptedApiKey)
                && hasText(visionModelName);
        if (!usable) {
            this.enabled = false;
            this.currentModelName = null;
            this.delegate.set(new NoOpVisionChatModel());
            log.info("Enterprise vision model is not configured, visual analysis will degrade: "
                    + "baseUrlPresent={}, apiKeyPresent={}, visionModelName={}",
                    config != null && hasText(config.getBaseUrl()),
                    hasText(decryptedApiKey),
                    visionModelName);
            return;
        }

        int configuredTimeout = config.getTimeoutSeconds() != null
                ? config.getTimeoutSeconds() : defaultRequestTimeoutSeconds;
        int timeout = Math.max(5, configuredTimeout);
        ChatModel model = OpenAiChatModel.builder()
                .baseUrl(config.getBaseUrl())
                .apiKey(decryptedApiKey)
                .modelName(visionModelName)
                // 视觉证据需要稳定复现，温度压到最低。
                .temperature(0.1d)
                .timeout(Duration.ofSeconds(timeout))
                .maxRetries(0)
                .maxTokens(maxOutputTokens)
                .build();
        this.enabled = true;
        this.currentModelName = visionModelName;
        this.delegate.set(model);
        log.info("Enterprise vision model activated: modelName={}, baseUrl={}", visionModelName, config.getBaseUrl());
    }

    /**
     * 用 Agent 默认配置（环境变量）刷新视觉模型实例。
     * <p>
     * 部署环境未在后台保存企业模型时，视觉能力仍可经 env 单独开启。
     */
    public void refreshFromProperties(LangChain4jAgentProperties properties, String visionModelName) {
        if (properties == null || !properties.isEnabled()
                || !hasText(properties.getBaseUrl())
                || !hasText(properties.getApiKey())
                || !hasText(visionModelName)) {
            this.enabled = false;
            this.currentModelName = null;
            this.delegate.set(new NoOpVisionChatModel());
            log.info("Enterprise vision model is not configured from properties, visual analysis will degrade: "
                    + "visionModelName={}", visionModelName);
            return;
        }
        SystemAiModelConfig config = new SystemAiModelConfig();
        config.setEnabled(true);
        config.setBaseUrl(properties.getBaseUrl());
        config.setModelName(visionModelName);
        config.setTimeoutSeconds(Math.toIntExact(properties.getTimeoutSeconds()));
        refreshFromConfig(config, properties.getApiKey(), visionModelName);
    }

    public boolean isEnabled() {
        return enabled;
    }

    public String getCurrentModelName() {
        return currentModelName;
    }

    /**
     * 多模态分析：文本 + 图片。
     * <p>
     * 保持与既有视觉调用完全一致的方法签名，便于调用方零改动切换模型。
     *
     * @param systemPrompt 系统提示词，纯文本；为空时不发送 system 消息
     * @param userPrompt   用户提示词
     * @param imageUrls    图片地址，支持 {@code data:image/jpeg;base64,...} 与 http(s) URL
     * @return 模型返回文本；未启用、调用失败或返回为空时为 null（调用方判空降级）
     */
    public String analyzeVision(String systemPrompt, String userPrompt, List<String> imageUrls) {
        if (!enabled) {
            log.debug("Enterprise vision model is disabled, skipping vision call");
            return null;
        }
        List<Content> contents = new ArrayList<>();
        if (imageUrls != null) {
            for (String url : imageUrls) {
                if (hasText(url)) {
                    contents.add(ImageContent.from(url));
                }
            }
        }
        if (hasText(userPrompt)) {
            contents.add(TextContent.from(userPrompt));
        }
        if (contents.isEmpty()) {
            return null;
        }

        List<ChatMessage> messages = new ArrayList<>();
        if (hasText(systemPrompt)) {
            messages.add(SystemMessage.from(systemPrompt));
        }
        messages.add(UserMessage.from(contents));

        try {
            ChatResponse response = providerConcurrencyGate.execute(
                    () -> delegate.get().chat(ChatRequest.builder().messages(messages).build()));
            return extractText(response);
        } catch (Exception e) {
            log.error("Vision call failed (model={}): {}", currentModelName, e.getMessage(), e);
            return null;
        }
    }

    private String extractText(ChatResponse response) {
        if (response != null && response.aiMessage() != null) {
            String text = response.aiMessage().text();
            if (hasText(text)) {
                return text;
            }
        }
        log.warn("Vision model returned an empty response");
        return null;
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    /**
     * 未配置视觉模型时的占位实现：调用一律抛异常，绝不回退到硬编码厂商模型。
     */
    private static final class NoOpVisionChatModel implements ChatModel {
        @Override
        public ChatResponse chat(ChatRequest request) {
            throw new IllegalStateException("Enterprise vision model is not configured or disabled");
        }
    }
}
