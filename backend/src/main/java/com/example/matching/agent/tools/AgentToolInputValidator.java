package com.example.matching.agent.tools;

import java.util.Collection;
import java.util.Optional;

public final class AgentToolInputValidator {

    private AgentToolInputValidator() {
    }

    public static Optional<String> validateNotNull(String name, Object value) {
        if (value == null) {
            return Optional.of(String.format("invalid_input: %s must not be null", name));
        }
        return Optional.empty();
    }

    public static Optional<String> validatePositive(String name, Long value) {
        if (value == null) {
            return Optional.of(String.format("invalid_input: %s must not be null", name));
        }
        if (value <= 0) {
            return Optional.of(String.format("invalid_input: %s must be positive, got %d", name, value));
        }
        return Optional.empty();
    }

    public static Optional<String> validateNotEmpty(String name, String value) {
        if (value == null || value.isBlank()) {
            return Optional.of(String.format("invalid_input: %s must not be empty", name));
        }
        return Optional.empty();
    }

    public static Optional<String> validateNotEmpty(String name, Collection<?> collection) {
        if (collection == null || collection.isEmpty()) {
            return Optional.of(String.format("invalid_input: %s must not be empty", name));
        }
        return Optional.empty();
    }

    /** 字符串最大长度校验（防模型传入超长输入触发大查询） */
    public static Optional<String> validateMaxLength(String name, String value, int maxLength) {
        if (value != null && value.length() > maxLength) {
            return Optional.of(String.format("invalid_input: %s exceeds max length %d", name, maxLength));
        }
        return Optional.empty();
    }

    /** 集合最大条数校验 */
    public static Optional<String> validateMaxSize(String name, Collection<?> collection, int maxSize) {
        if (collection != null && collection.size() > maxSize) {
            return Optional.of(String.format("invalid_input: %s exceeds max size %d", name, maxSize));
        }
        return Optional.empty();
    }

    /** 转义 LIKE 通配符（% _ \\），防止模型构造通配前缀触发全表扫描 */
    public static String escapeLike(String value) {
        if (value == null) {
            return null;
        }
        return value.replace("\\", "\\\\")
                .replace("%", "\\%")
                .replace("_", "\\_");
    }
}
