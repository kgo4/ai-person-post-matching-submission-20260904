package com.example.matching.infrastructure.llm;

import com.fasterxml.jackson.databind.JsonNode;

import java.math.BigDecimal;

/**
 * Stateless utility for reading typed values from {@link JsonNode}.
 * <p>
 * Provides consistent handling of null, numeric strings, range clamping,
 * and invalid field values — eliminating duplicated helper methods across
 * LLM response parsers and Agent service implementations.
 */
public final class JsonNodeValueReader {

    private JsonNodeValueReader() {
    }

    public static String getText(JsonNode node, String field) {
        JsonNode value = node.get(field);
        if (value == null || value.isNull()) {
            return null;
        }
        return value.asText();
    }

    public static Integer getInt(JsonNode node, String field) {
        return getInt(node, field, null, null);
    }

    public static Integer getInt(JsonNode node, String field, Integer min, Integer max) {
        JsonNode value = node.get(field);
        if (value == null || value.isNull()) {
            return null;
        }
        try {
            int intValue;
            if (value.isInt()) {
                intValue = value.asInt();
            } else if (value.isTextual()) {
                intValue = Integer.parseInt(value.asText().trim());
            } else if (value.isNumber()) {
                intValue = value.asInt();
            } else {
                return null;
            }
            if (min != null && intValue < min) {
                intValue = min;
            }
            if (max != null && intValue > max) {
                intValue = max;
            }
            return intValue;
        } catch (NumberFormatException e) {
            return null;
        }
    }

    public static Long getLong(JsonNode node, String field) {
        JsonNode value = node.get(field);
        if (value == null || value.isNull()) {
            return null;
        }
        try {
            if (value.isLong()) {
                return value.asLong();
            } else if (value.isTextual()) {
                return Long.parseLong(value.asText().trim());
            } else if (value.isNumber()) {
                return value.asLong();
            }
            return null;
        } catch (NumberFormatException e) {
            return null;
        }
    }

    public static BigDecimal getBigDecimal(JsonNode node, String field) {
        return getBigDecimal(node, field, null, null);
    }

    public static BigDecimal getBigDecimal(JsonNode node, String field, BigDecimal min, BigDecimal max) {
        JsonNode value = node.get(field);
        if (value == null || value.isNull()) {
            return null;
        }
        try {
            BigDecimal result;
            if (value.isNumber()) {
                result = value.decimalValue();
            } else if (value.isTextual()) {
                result = new BigDecimal(value.asText().trim());
            } else {
                return null;
            }
            if (min != null && result.compareTo(min) < 0) {
                result = min;
            }
            if (max != null && result.compareTo(max) > 0) {
                result = max;
            }
            return result;
        } catch (NumberFormatException e) {
            return null;
        }
    }

    public static Boolean getBoolean(JsonNode node, String field) {
        JsonNode value = node.get(field);
        if (value == null || value.isNull()) {
            return null;
        }
        if (value.isBoolean()) {
            return value.asBoolean();
        }
        String text = value.asText().trim().toLowerCase();
        if ("true".equals(text) || "1".equals(text) || "yes".equals(text)) {
            return true;
        }
        if ("false".equals(text) || "0".equals(text) || "no".equals(text)) {
            return false;
        }
        return null;
    }

    public static BigDecimal getScore(JsonNode node, String field) {
        return getBigDecimal(node, field, BigDecimal.ZERO, new BigDecimal("100"));
    }
}
