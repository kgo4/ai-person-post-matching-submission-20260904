package com.example.matching.common.exception;

import lombok.Getter;

/**
 * 不可重试的简历解析异常。
 * <p>
 * 表示永久性故障（如文件格式损坏、内容为空），重试无法恢复。
 */
@Getter
public class PermanentResumeParseException extends RuntimeException {

    private final String errorType;
    private final Long employeeId;
    private final String fileName;

    public PermanentResumeParseException(String message) {
        this("PERMANENT", message, null, null, null);
    }

    public PermanentResumeParseException(String message, Throwable cause) {
        this("PERMANENT", message, cause, null, null);
    }

    public PermanentResumeParseException(String errorType, String message) {
        this(errorType, message, null, null, null);
    }

    public PermanentResumeParseException(String errorType, String message, Throwable cause) {
        this(errorType, message, cause, null, null);
    }

    private PermanentResumeParseException(String errorType, String message, Throwable cause,
                                          Long employeeId, String fileName) {
        super(message, cause);
        this.errorType = errorType;
        this.employeeId = employeeId;
        this.fileName = fileName;
    }

    public static Builder forEmployee(Long employeeId) {
        return new Builder(employeeId);
    }

    public static class Builder {
        private final Long employeeId;
        private String fileName;
        private String errorType = "PERMANENT";
        private String message;
        private Throwable cause;

        Builder(Long employeeId) {
            this.employeeId = employeeId;
        }

        public Builder fileName(String fileName) { this.fileName = fileName; return this; }
        public Builder errorType(String errorType) { this.errorType = errorType; return this; }
        public Builder message(String message) { this.message = message; return this; }
        public Builder cause(Throwable cause) { this.cause = cause; return this; }

        public PermanentResumeParseException build() {
            return new PermanentResumeParseException(errorType,
                    message != null ? message : "简历解析失败", cause, employeeId, fileName);
        }
    }
}
