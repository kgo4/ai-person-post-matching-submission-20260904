package com.example.matching.agent.json;

import com.example.matching.agent.config.AgentObservationMetrics;
import com.example.matching.ai.service.LlmInputGuard;
import com.example.matching.infrastructure.llm.ModelResponseParseException;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.ToolExecutionResultMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.Capability;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.request.ResponseFormat;
import dev.langchain4j.model.chat.response.ChatResponse;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * 四层 JSON 输出防御编排器（格式层）。
 * 第 2 层：按能力注入 responseFormat；第 3 层：JsonExtractor 硬拦截；
 * 第 4 层：语法失败 → 纠错消息回喂 → 重试（JsonRetryPolicy 控制上限）。
 * 业务语义校验由服务层现有 Validator 承担。
 */
@Slf4j
public class JsonGuardChatModel implements ChatModel {

    private final ChatModel delegate;
    private final CapabilityProbe capabilityProbe;
    private final JsonRetryPolicy retryPolicy;
    private final AgentObservationMetrics metrics;
    private final LlmInputGuard inputGuard;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public JsonGuardChatModel(ChatModel delegate, CapabilityProbe capabilityProbe, JsonRetryPolicy retryPolicy) {
        this(delegate, capabilityProbe, retryPolicy, null, null);
    }

    public JsonGuardChatModel(ChatModel delegate, CapabilityProbe capabilityProbe, JsonRetryPolicy retryPolicy,
                              AgentObservationMetrics metrics) {
        this(delegate, capabilityProbe, retryPolicy, metrics, null);
    }

    public JsonGuardChatModel(ChatModel delegate, CapabilityProbe capabilityProbe, JsonRetryPolicy retryPolicy,
                              AgentObservationMetrics metrics, LlmInputGuard inputGuard) {
        this.delegate = delegate;
        this.capabilityProbe = capabilityProbe;
        this.retryPolicy = retryPolicy;
        this.metrics = metrics;
        this.inputGuard = inputGuard;
    }

    @Override
    public ChatResponse chat(ChatRequest request) {
        ChatRequest effective = injectResponseFormat(guardUntrusted(request));
        int attempt = 0;
        while (true) {
            recordJsonGuard("attempt");
            ChatResponse response = delegate.chat(effective);
            // 工具中间轮次：模型发起工具调用（text 为 null、toolExecutionRequests 非空），原样透传，不做 JSON 校验
            if (response.aiMessage() != null && response.aiMessage().hasToolExecutionRequests()) {
                return response;
            }
            String raw = response.aiMessage() != null ? response.aiMessage().text() : null;
            if (raw == null) {
                // 空响应重试无法凭空造出内容，立即失败，交由上层 fallback 处理
                recordJsonGuard("invalid_final");
                throw new ModelResponseParseException("LLM returned empty response");
            }
            String clean = JsonExtractor.clean(raw);
            if (clean != null && isParseable(clean)) {
                return response.toBuilder()
                        .aiMessage(AiMessage.from(clean))
                        .build();
            }
            String reason = clean == null ? "no JSON found" : "invalid JSON syntax";
            if (!retryPolicy.shouldRetry(attempt)) {
                log.warn("[JSON_GUARD] retry exhausted, raw output: {}", truncate(raw));
                recordJsonGuard("invalid_final");
                throw new ModelResponseParseException(
                        "LLM JSON output invalid after " + (attempt + 1) + " attempts, last output: "
                                + truncate(raw));
            }
            log.warn("[JSON_GUARD] attempt {} failed ({}), correcting and retrying", attempt, reason);
            recordJsonGuard("corrected");
            effective = withCorrection(effective, reason);
            try {
                Thread.sleep(retryPolicy.backoffMillis(attempt));
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                recordJsonGuard("invalid_final");
                throw new ModelResponseParseException("Interrupted while retrying JSON output", e);
            }
            attempt++;
        }
    }

    /**
     * 对进入模型的用户消息与工具结果统一施加不可信输入守卫（脱敏 + 注入指令剥离 + 边界标记）。
     * 主路径（LangChain4j AiServices）经由此模型发出，候选人可控数据（简历声明/面试回答/JD）
     * 会以 {@code @UserMessage} 或 {@code ToolExecutionResultMessage}（工具返回的简历原文 sourceText）
     * 进入此处，此前仅 FreeMarker 回退路径接入过守卫。
     */
    private ChatRequest guardUntrusted(ChatRequest request) {
        if (inputGuard == null) {
            return request;
        }
        List<ChatMessage> messages = request.messages();
        if (messages == null || messages.isEmpty()) {
            return request;
        }
        boolean changed = false;
        List<ChatMessage> guarded = new ArrayList<>(messages.size());
        for (ChatMessage message : messages) {
            if (message instanceof UserMessage userMessage && userMessage.hasSingleText()) {
                String original = userMessage.singleText();
                String safe = inputGuard.untrusted(original);
                guarded.add(userMessage.name() != null
                        ? UserMessage.from(userMessage.name(), safe)
                        : UserMessage.from(safe));
                changed = true;
            } else if (message instanceof ToolExecutionResultMessage toolResult && toolResult.hasSingleText()) {
                guarded.add(ToolExecutionResultMessage.from(
                        toolResult.id(), toolResult.toolName(), inputGuard.untrusted(toolResult.text())));
                changed = true;
            } else {
                guarded.add(message);
            }
        }
        return changed ? request.toBuilder().messages(guarded).build() : request;
    }

    private ChatRequest injectResponseFormat(ChatRequest request) {
        CapabilityProbe.Level level = capabilityProbe.probe(delegate);
        // JSON_SCHEMA 与 JSON_OBJECT 当前产出完全等价：均注入通用 JSON 响应格式。
        // CapabilityProbe 当前不会返回 NONE（无显式能力时按 JSON_OBJECT 兜底），
        // 保留 NONE 分支仅作防御；真正的 json_schema 注入将在 schema 阶段（后续任务）扩展。
        ResponseFormat format = switch (level) {
            case JSON_SCHEMA, JSON_OBJECT -> ResponseFormat.JSON;
            case NONE -> null;
        };
        if (format == null || request.responseFormat() != null) {
            return request;
        }
        return request.toBuilder().responseFormat(format).build();
    }

    private ChatRequest withCorrection(ChatRequest request, String reason) {
        List<ChatMessage> messages = new ArrayList<>(request.messages());
        messages.add(UserMessage.userMessage(
                "Your previous response was not valid JSON (" + reason + "). "
                        + "Respond with ONLY a single valid JSON value, no markdown, no explanation."));
        return request.toBuilder().messages(messages).build();
    }

    private boolean isParseable(String json) {
        try {
            JsonNode node = objectMapper.readTree(json);
            // 只接受对象/数组根节点，拒绝 [1,2,3] 之外的标量（数字、字符串、布尔、null）
            return node.isObject() || node.isArray();
        } catch (JsonProcessingException e) {
            return false;
        }
    }

    private void recordJsonGuard(String outcome) {
        if (metrics != null) {
            metrics.recordJsonGuard(outcome);
        }
    }

    @Override
    public Set<Capability> supportedCapabilities() {
        return delegate.supportedCapabilities();
    }

    private String truncate(String s) {
        if (s == null) {
            return "null";
        }
        return s.length() <= 200 ? s : s.substring(0, 200) + "...";
    }
}
