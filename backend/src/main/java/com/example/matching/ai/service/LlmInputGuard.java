package com.example.matching.ai.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.regex.Pattern;

/** Normalizes untrusted model input before it crosses the LLM provider boundary. */
@Component
public class LlmInputGuard {

    private static final Pattern EMAIL = Pattern.compile("(?i)[a-z0-9._%+-]+@[a-z0-9.-]+\\.[a-z]{2,}");
    private static final Pattern PHONE = Pattern.compile("(?<!\\d)1[3-9]\\d{9}(?!\\d)");
    // 英文 + 中文 + 常见变体（忽略/无视/不要遵守/忘掉 + 之前/以上/系统/前面 + 指令/提示/要求/规则/上下文）。
    // 匹配整个注入指令短语，替换为 [PROMPT_OVERRIDE_REMOVED]，防止候选人可控文本夹带指令覆盖系统提示词。
    private static final Pattern PROMPT_OVERRIDE = Pattern.compile(
            "(?i)(ignore|disregard|override|forget)\\s+(?:all\\s+)?(?:previous|prior|system|above)\\s+(?:instructions?|prompts?|rules?|context)"
            + "|(?:忽略|无视|忘掉|不要(?:遵守|遵循|执行|理会)|别管)[^，。；;\\r\\n]*?(?:指令|指示|提示|提示词|要求|规则|规定|对话|上下文|内容)");

    @Value("${ai.input.max-chars:12000}")
    private int maxChars = 12000;

    public String untrusted(String text) {
        if (text == null || text.isBlank()) {
            return "[UNTRUSTED_DATA]\n[/UNTRUSTED_DATA]";
        }
        String bounded = text.length() <= maxChars ? text : text.substring(0, maxChars) + "\n[TRUNCATED]";
        String redacted = EMAIL.matcher(bounded).replaceAll("[REDACTED_EMAIL]");
        redacted = PHONE.matcher(redacted).replaceAll("[REDACTED_PHONE]");
        redacted = PROMPT_OVERRIDE.matcher(redacted).replaceAll("[PROMPT_OVERRIDE_REMOVED]");
        return "[UNTRUSTED_DATA]\n" + redacted + "\n[/UNTRUSTED_DATA]";
    }
}
