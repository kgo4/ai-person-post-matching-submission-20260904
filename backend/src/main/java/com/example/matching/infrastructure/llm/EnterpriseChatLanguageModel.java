package com.example.matching.infrastructure.llm;

import com.example.matching.agent.json.LogitBiasAdapter;
import com.example.matching.entity.system.SystemAiModelConfig;
import dev.langchain4j.model.chat.Capability;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.openai.OpenAiChatModel;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;

/**
 * 浼佷笟鍏ㄥ眬妯″瀷闂ㄩ潰锛氬疄鐜?{@link ChatLanguageModel}锛屼粠鍏ㄥ眬妯″瀷閰嶇疆鍒涘缓瀹為檯妯″瀷骞舵寔鏈夊綋鍓嶅疄渚嬨€? * <p>
 * 淇濆瓨鍏ㄥ眬閰嶇疆鍚庤皟鐢?{@link #refreshFromConfig} 鍘熷瓙鏇挎崲鍐呴儴妯″瀷瀹炰緥锛? * 鎵€鏈夊凡娉ㄥ叆鐨?Agent Service 鑷姩浣跨敤鏂版ā鍨嬶紝鏃犻渶閲嶅惎銆? * <p>
 * 搴旂敤鍚姩鏃朵粠鏁版嵁搴撴仮澶嶅凡淇濆瓨鐨勪紒涓氭ā鍨嬮厤缃紝淇濊瘉閲嶅惎鍚庢ā鍨嬩粛鐒剁敓鏁堛€? */
@Slf4j
@Component
public class EnterpriseChatLanguageModel implements ChatModel {

    private static final int AGENT_REQUEST_TIMEOUT_SECONDS = 300;

    /** 结构化长输出（简历能力提取 JSON 等）默认输出上限，避免被服务端截断导致 JSON 未闭合。
     *  模型支持 1M 上下文窗口，但单次输出（max_tokens）由请求参数控制——不设置会走服务端
     *  保守默认值（约 4K~8K），长 JSON 易被截断。16384 覆盖长简历场景，可按需调高。 */
    private static final int DEFAULT_MAX_OUTPUT_TOKENS = 16384;

    /** kill-switch 或未配置时的模型输出 token 上限（默认 16384，可经 ai.max-output-tokens 覆盖） */
    @Value("${ai.max-output-tokens:16384}")
    private int maxOutputTokens = DEFAULT_MAX_OUTPUT_TOKENS;

    private final AtomicReference<ChatModel> delegate = new AtomicReference<>();
    private final AiProviderConcurrencyGate providerConcurrencyGate;
    private volatile String currentModelName;
    private volatile boolean enabled;

    /** kill-switch 或未配置时的默认请求超时（秒） */
    @Value("${ai.request-timeout-seconds:300}")
    private int defaultRequestTimeoutSeconds = AGENT_REQUEST_TIMEOUT_SECONDS;

    /** 第 3 层可选增强：是否对支持 logit_bias 的 OpenAI 兼容厂商启用字典级偏置（默认关闭） */
    @Value("${ai.json.logit-bias.enabled:false}")
    private boolean logitBiasEnabled;

    /**
     * Some reasoning-capable OpenAI-compatible providers can consume the whole
     * completion budget in reasoning_content and return an empty content field.
     * Enable their documented thinking switch only when the deployment opts in.
     */
    @Value("${ai.thinking.disabled:false}")
    private boolean thinkingDisabled;

    public EnterpriseChatLanguageModel(AiProviderConcurrencyGate providerConcurrencyGate) {
        this.providerConcurrencyGate = providerConcurrencyGate;
        this.delegate.set(new NoOpChatLanguageModel());
    }

    /**
     * 浠庡叏灞€閰嶇疆鍒涘缓/鍒锋柊鍐呴儴妯″瀷瀹炰緥锛堝師瀛愭浛鎹級銆?     */
    public void refreshFromConfig(SystemAiModelConfig config, String decryptedApiKey) {
        boolean configEnabled = Boolean.TRUE.equals(config.getEnabled())
                && config.getBaseUrl() != null && !config.getBaseUrl().isBlank()
                && config.getModelName() != null && !config.getModelName().isBlank()
                && decryptedApiKey != null && !decryptedApiKey.isBlank();
        if (!configEnabled) {
            this.enabled = false;
            this.currentModelName = null;
            this.delegate.set(new NoOpChatLanguageModel());
            log.warn("Enterprise AI model is disabled or incomplete, AI text services will degrade");
            return;
        }
        int configuredTimeout = config.getTimeoutSeconds() != null
                ? config.getTimeoutSeconds() : defaultRequestTimeoutSeconds;
        int timeout = Math.max(5, configuredTimeout);
        double temperature = config.getTemperature() != null ? config.getTemperature().doubleValue() : 0.1d;
        OpenAiChatModel.OpenAiChatModelBuilder builder = OpenAiChatModel.builder()
                .baseUrl(config.getBaseUrl())
                .apiKey(decryptedApiKey)
                .modelName(config.getModelName())
                .temperature(temperature)
                .timeout(Duration.ofSeconds(timeout))
                // Network retries are centralized in AiProviderConcurrencyGate so retries
                // cannot multiply across LangChain4j and business-level resilience wrappers.
                .maxRetries(0)
                .maxTokens(maxOutputTokens);        Map<String, Integer> logitBias = LogitBiasAdapter.biasMap(logitBiasEnabled);
        if (!logitBias.isEmpty()) {
            builder.logitBias(logitBias);
        }
        if (thinkingDisabled) {
            builder.customParameters(Map.of("thinking", Map.of("type", "disabled")));
        }
        ChatModel model = builder.build();
        this.enabled = true;
        this.currentModelName = config.getModelName();
        this.delegate.set(model);
        log.info("Enterprise AI model activated: modelName={}, baseUrl={}", config.getModelName(), config.getBaseUrl());
    }

    public boolean isEnabled() {
        return enabled;
    }

    public String getCurrentModelName() {
        return currentModelName;
    }

    /** 鍋ュ悍妫€鏌ョ敤锛氱洿鎺ユ瀯閫犱竴娆℃€ф帰娴嬫ā鍨嬶紝涓嶆浛鎹㈠唴閮ㄥ疄渚嬨€?*/
    public OpenAiChatModel buildProbeModel() {
        ChatModel current = delegate.get();
        return current instanceof OpenAiChatModel openAi ? openAi : null;
    }

    @Override
    public ChatResponse chat(ChatRequest request) {
        return providerConcurrencyGate.execute(() -> delegate.get().chat(request));
    }

    @Override
    public Set<Capability> supportedCapabilities() {
        return delegate.get().supportedCapabilities();
    }

    /**
     * 鏈厤缃紒涓氭ā鍨嬫椂鐨勫崰浣嶅疄鐜帮細璋冪敤涓€寰嬫姏寮傚父锛岀敱涓婂眰涓氬姟 fallback 鍏滃簳锛?     * 缁濅笉鍥為€€鍒扮‖缂栫爜鍘傚晢妯″瀷銆?     */
    private static final class NoOpChatLanguageModel implements ChatModel {
        @Override
        public ChatResponse chat(ChatRequest request) {
            throw new IllegalStateException("Enterprise AI model is not configured or disabled");
        }

        @Override
        public Set<Capability> supportedCapabilities() {
            return Set.of();
        }
    }
}
