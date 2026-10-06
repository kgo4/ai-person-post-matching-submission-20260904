package com.example.matching.common.exception;

import lombok.Getter;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * AI 服务异常
 * <p>
 * 用于封装所有 AI 服务调用（LLM、Embedding、ASR 等）的失败。
 * 区分可重试和不可重试两类，由 {@link com.example.matching.common.handler.GlobalExceptionHandler}
 * 分别返回 503（可重试）和 502（不可重试）。
 */
@Getter
public class AiServiceException extends BusinessException {

    /** AI 服务提供商标识：DeepSeek, DashScope, Volcengine, SiliconFlow, Ollama */
    private final String provider;

    /** 操作名称：chat, embedding, analyze, transcribe */
    private final String operation;

    /** 是否可重试（临时故障如超时、限流为 true；认证失败、模型不存在为 false） */
    private final boolean retryable;

    public AiServiceException(String provider, String operation, boolean retryable, String message) {
        super(ErrorCodeEnum.AI_SERVICE_ERROR, message);
        this.provider = provider;
        this.operation = operation;
        this.retryable = retryable;
    }

    public AiServiceException(String provider, String operation, boolean retryable, String message, Throwable cause) {
        super(ErrorCodeEnum.AI_SERVICE_ERROR, message);
        this.provider = provider;
        this.operation = operation;
        this.retryable = retryable;
        initCause(cause);
    }

    /**
     * 创建一个 AI 服务异常，带结构化上下文
     */
    public static AiServiceException of(String provider, String operation, boolean retryable, String message) {
        return new AiServiceException(provider, operation, retryable, message);
    }

    public static AiServiceException of(String provider, String operation, boolean retryable, String message, Throwable cause) {
        return new AiServiceException(provider, operation, retryable, message, cause);
    }

    /**
     * 创建可重试的异常（超时、限流、网络瞬断）
     */
    public static AiServiceException retryable(String provider, String operation, String message) {
        return new AiServiceException(provider, operation, true, message);
    }

    public static AiServiceException retryable(String provider, String operation, String message, Throwable cause) {
        return new AiServiceException(provider, operation, true, message, cause);
    }

    /**
     * 创建不可重试的异常（认证失败、模型不存在、参数错误）
     */
    public static AiServiceException permanent(String provider, String operation, String message) {
        return new AiServiceException(provider, operation, false, message);
    }

    public static AiServiceException permanent(String provider, String operation, String message, Throwable cause) {
        return new AiServiceException(provider, operation, false, message, cause);
    }

    @Override
    public Map<String, Object> getDetail() {
        Map<String, Object> detail = new LinkedHashMap<>();
        detail.put("provider", provider);
        detail.put("operation", operation);
        detail.put("retryable", retryable);
        return detail;
    }

    /**
     * 暴露给 GlobalExceptionHandler 的日志格式
     */
    public String toLogMessage() {
        return String.format("AI服务[%s] %s 失败 (retryable=%s): %s", provider, operation, retryable, getMessage());
    }
}
