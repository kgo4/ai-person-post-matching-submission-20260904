package com.example.matching.service.post.support;

import java.util.regex.Pattern;

public final class TextSanitizationPolicy {

    private TextSanitizationPolicy() {
    }

    public static final Pattern CONTROL_CHARS_PATTERN = Pattern.compile("[\\x00-\\x08\\x0B\\x0C\\x0E-\\x1F\\x7F]");

    public static final Pattern LINE_BREAK_PATTERN = Pattern.compile("\\r\\n|\\r|\\n");

    public static String removeControlChars(String text) {
        if (text == null) {
            return null;
        }
        return CONTROL_CHARS_PATTERN.matcher(text).replaceAll("");
    }

    public static String normalizeLineBreaks(String text) {
        if (text == null) {
            return null;
        }
        return LINE_BREAK_PATTERN.matcher(text).replaceAll("\n");
    }
}
