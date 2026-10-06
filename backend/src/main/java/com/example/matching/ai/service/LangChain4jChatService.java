package com.example.matching.ai.service;

import com.example.matching.common.exception.AiServiceException;
import com.example.matching.entity.system.PromptInvocationLog;
import com.example.matching.infrastructure.llm.EnterpriseChatLanguageModel;
import com.example.matching.resilience.AiServiceResilience;
import com.example.matching.utils.SecurityUtils;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.ChatModel;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.function.Supplier;

@Service
@Slf4j
public class LangChain4jChatService {

    private static final String AI_MODEL_DISABLED = "AI_MODEL_DISABLED";

    private final ObjectProvider<EnterpriseChatLanguageModel> chatLanguageModelProvider;
    private final AiServiceResilience aiServiceResilience;

    @Value("${ai.enabled:true}")
    private boolean aiEnabled;

    @Value("${ai.request-timeout-seconds:300}")
    private long requestTimeoutSeconds = 300L;

    @org.springframework.beans.factory.annotation.Autowired
    private PromptInvocationLogger invocationLogger;

    @org.springframework.beans.factory.annotation.Autowired
    private PromptMetadataResolver metadataResolver;

    @org.springframework.beans.factory.annotation.Autowired(required = false)
    private LlmInputGuard llmInputGuard;

    public LangChain4jChatService(ObjectProvider<EnterpriseChatLanguageModel> chatLanguageModelProvider,
                                  AiServiceResilience aiServiceResilience) {
        this.chatLanguageModelProvider = chatLanguageModelProvider;
        this.aiServiceResilience = aiServiceResilience;
    }

    public String chat(String name, String userMessage, Supplier<String> fallback) {
        return chatWithLogging(name, null, userMessage, fallback, requestTimeoutSeconds);
    }

    public String chat(String name, String systemMessage, String userMessage, Supplier<String> fallback) {
        return chatWithLogging(name, systemMessage, userMessage, fallback, requestTimeoutSeconds);
    }

    /** Executes a guarded chat with an explicit deadline for slow, independent scenarios. */
    public String chat(String name, String systemMessage, String userMessage, Supplier<String> fallback,
                       long timeoutSeconds) {
        return chatWithLogging(name, systemMessage, userMessage, fallback, timeoutSeconds);
    }

    /**
     * Executes a scenario with its own deadline. Callers must not wrap this method in
     * {@link AiServiceResilience}: doing so submits an outer task which waits for an
     * inner task on the same bounded AI executor and can deadlock the pool.
     */
    public String chat(String name, String userMessage, Supplier<String> fallback, long timeoutSeconds) {
        return chatWithLogging(name, null, userMessage, fallback, timeoutSeconds);
    }

    private String chatWithLogging(String name, String systemMessage, String userMessage, Supplier<String> fallback,
                                   long timeoutSeconds) {
        String guardedUserMessage = llmInputGuard == null ? userMessage : llmInputGuard.untrusted(userMessage);
        long start = System.currentTimeMillis();
        boolean success = false;
        int outputChars = 0;
        int inputChars = (systemMessage != null ? systemMessage.length() : 0) + (guardedUserMessage != null ? guardedUserMessage.length() : 0);

        ChatModel chatLanguageModel = resolveChatLanguageModel();
        if (chatLanguageModel == null) {
            // kill-switch 生效：AI 被禁用或模型不可用，直接确定性降级，不调用模型
            log.warn("[{}] AI 已禁用或模型不可用，name={}, 直接走确定性降级", AI_MODEL_DISABLED, name);
            String degraded = fallback != null ? fallback.get() : null;
            logInvocation(name, start, success, inputChars, outputChars);
            return degraded;
        }

        String result;
        try {
            result = aiServiceResilience.callWithResilienceOrThrow(name, () -> {
                if (systemMessage != null) {
                    List<ChatMessage> messages = List.of(
                            SystemMessage.systemMessage(systemMessage),
                            UserMessage.userMessage(guardedUserMessage));
                    String text = chatLanguageModel.chat(messages).aiMessage().text();
                    return text;
                }
                return chatLanguageModel.chat(guardedUserMessage);
            }, Math.max(1L, timeoutSeconds));
        } catch (AiServiceException e) {
            // 熔断/重试耗尽：业务层应用受控的确定性降级函数
            log.warn("LLM 调用[{}]重试耗尽，应用确定性降级: {}", name, e.getMessage());
            result = fallback != null ? fallback.get() : null;
        } catch (Exception e) {
            log.warn("LLM 调用[{}]失败，应用确定性降级: {}", name, e.getMessage());
            result = fallback != null ? fallback.get() : null;
        }

        outputChars = result != null ? result.length() : 0;
        success = outputChars > 0;

        long latency = System.currentTimeMillis() - start;
        try {
            PromptInvocationLog entry = invocationLogger.buildEntry(
                    name.replaceAll("[^a-zA-Z0-9-]", ""), resolveVersion(name), cleanScenario(name),
                    success, false, latency, inputChars, outputChars);
            invocationLogger.logInvocation(entry);
        } catch (Exception ex) {
            log.warn("LLM 调用埋点失败: {}", ex.getMessage());
        }

        return result;
    }

    private void logInvocation(String name, long start, boolean success, int inputChars, int outputChars) {
        long latency = System.currentTimeMillis() - start;
        try {
            PromptInvocationLog entry = invocationLogger.buildEntry(
                    name.replaceAll("[^a-zA-Z0-9-]", ""), resolveVersion(name), cleanScenario(name),
                    success, false, latency, inputChars, outputChars);
            invocationLogger.logInvocation(entry);
        } catch (Exception ex) {
            log.warn("LLM 调用埋点失败: {}", ex.getMessage());
        }
    }

    /**
     * 解析当前可用的企业全局模型。
     * <p>
     * 模型 Bean 不可用（应用配置 kill-switch ai.enabled=false 或模型内部禁用）时返回 null，
     * 调用方直接确定性降级，应用不得启动失败。
     */
    private ChatModel resolveChatLanguageModel() {
        if (!aiEnabled) {
            return null;
        }
        EnterpriseChatLanguageModel model = chatLanguageModelProvider.getIfAvailable();
        if (model == null || !model.isEnabled()) {
            return null;
        }
        return model;
    }

    public long getRequestTimeoutSeconds() {
        return requestTimeoutSeconds;
    }

    private String cleanScenario(String name) {
        return name.length() > 50 ? name.substring(0, 50) : name;
    }

    private String resolveVersion(String name) {
        if (metadataResolver == null) return "unknown";
        try {
            String resourceName = name.endsWith(".txt") ? name : name + ".txt";
            return metadataResolver.resolve(resourceName).version();
        } catch (Exception e) {
            return "unknown";
        }
    }
}
