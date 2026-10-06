package com.example.matching.common.exception;

import lombok.Getter;

/**
 * 可重试的简历解析异常。
 * <p>
 * 表示瞬时故障（如 AI 超时、网络抖动），可通过延迟重试恢复。
 */
@Getter
public class RetryableResumeParseException extends RuntimeException {

    private final String errorType;
    private final Long employeeId;
    private final String fileName;
    private final int retryCount;

    public RetryableResumeParseException(String message) {
        this("RETRYABLE", message, null, null, null, 0);
    }

    public RetryableResumeParseException(String message, Throwable cause) {
        this("RETRYABLE", message, cause, null, null, 0);
    }

    public RetryableResumeParseException(String errorType, String message) {
        this(errorType, message, null, null, null, 0);
    }

    public RetryableResumeParseException(String errorType, String message, Throwable cause) {
        this(errorType, message, cause, null, null, 0);
    }

    private RetryableResumeParseException(String errorType, String message, Throwable cause,
                                          Long employeeId, String fileName, int retryCount) {
        super(message, cause);
        this.errorType = errorType;
        this.employeeId = employeeId;
        this.fileName = fileName;
        this.retryCount = retryCount;
    }

    public static Builder forEmployee(Long employeeId) {
        return new Builder(employeeId);
    }

    public static class Builder {
        private final Long employeeId;
        private String fileName;
        private String errorType = "RETRYABLE";
        private String message;
        private Throwable cause;
        private int retryCount;

        Builder(Long employeeId) {
            this.employeeId = employeeId;
        }

        public Builder fileName(String fileName) { this.fileName = fileName; return this; }
        public Builder errorType(String errorType) { this.errorType = errorType; return this; }
        public Builder message(String message) { this.message = message; return this; }
        public Builder cause(Throwable cause) { this.cause = cause; return this; }
        public Builder retryCount(int retryCount) { this.retryCount = retryCount; return this; }

        public RetryableResumeParseException build() {
            return new RetryableResumeParseException(errorType,
                    message != null ? message : "简历解析暂时失败", cause, employeeId, fileName, retryCount);
        }
    }
}
