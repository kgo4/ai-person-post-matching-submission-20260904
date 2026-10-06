package com.example.matching.common.exception;

import lombok.Getter;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 业务异常
 * <p>
 * 所有业务层异常应使用此类或其子类抛出，由 {@link com.example.matching.common.handler.GlobalExceptionHandler} 统一处理。
 * 支持可选的结构化上下文（detail），用于日志和问题溯源。
 */
@Getter
public class BusinessException extends RuntimeException {

    private final int code;

    /** 结构化上下文：如 entityType=EMP_ABILITY, entityId=123, operation=PARSE_RESUME */
    private final Map<String, Object> detail;

    public BusinessException(int code, String message) {
        super(message);
        this.code = code;
        this.detail = null;
    }

    public BusinessException(ErrorCodeEnum errorCode) {
        super(errorCode.getMessage());
        this.code = errorCode.getCode();
        this.detail = null;
    }

    public BusinessException(ErrorCodeEnum errorCode, String detailMessage) {
        super(detailMessage);
        this.code = errorCode.getCode();
        this.detail = null;
    }

    public BusinessException(ErrorCodeEnum errorCode, String detailMessage, Map<String, Object> detail) {
        super(detailMessage);
        this.code = errorCode.getCode();
        this.detail = detail != null ? new LinkedHashMap<>(detail) : null;
    }

    /**
     * 创建携带根因的异常；根因仅用于日志记录，不会出现在响应体
     */
    public BusinessException(ErrorCodeEnum errorCode, String detailMessage, Throwable cause) {
        super(detailMessage, cause);
        this.code = errorCode.getCode();
        this.detail = null;
    }

    /**
     * 创建一个携带上下文的异常构建器
     */
    public static Builder of(ErrorCodeEnum errorCode) {
        return new Builder(errorCode);
    }

    public static Builder of(ErrorCodeEnum errorCode, String message) {
        return new Builder(errorCode, message);
    }

    public static Builder of(ErrorCodeEnum errorCode, String message, Throwable cause) {
        return new Builder(errorCode, message, cause);
    }

    public static class Builder {
        private final ErrorCodeEnum errorCode;
        private final String message;
        private final Throwable cause;
        private final Map<String, Object> detail = new LinkedHashMap<>();

        Builder(ErrorCodeEnum errorCode) {
            this.errorCode = errorCode;
            this.message = errorCode.getMessage();
            this.cause = null;
        }

        Builder(ErrorCodeEnum errorCode, String message) {
            this.errorCode = errorCode;
            this.message = message;
            this.cause = null;
        }

        Builder(ErrorCodeEnum errorCode, String message, Throwable cause) {
            this.errorCode = errorCode;
            this.message = message;
            this.cause = cause;
        }

        public Builder entity(String entityType, Object entityId) {
            detail.put("entityType", entityType);
            detail.put("entityId", entityId);
            return this;
        }

        public Builder operation(String operation) {
            detail.put("operation", operation);
            return this;
        }

        public Builder provider(String provider) {
            detail.put("provider", provider);
            return this;
        }

        public Builder put(String key, Object value) {
            detail.put(key, value);
            return this;
        }

        public BusinessException build() {
            if (cause != null) {
                return new BusinessException(errorCode, message, cause);
            }
            return new BusinessException(errorCode, message, detail.isEmpty() ? null : detail);
        }
    }
}
