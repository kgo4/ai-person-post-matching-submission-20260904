package com.example.matching.agent.json;

import java.util.ArrayDeque;
import java.util.Deque;

/**
 * 第 3 层硬性拦截：从 LLM 原始输出中提取并修复 JSON。
 * 步骤：剥 markdown 围栏 → 定位首个 { / [ → 平衡括号截取 → token 级污染修复。
 */
public final class JsonExtractor {

    private JsonExtractor() {
    }

    /** 提取并修复 JSON；找不到合法 JSON 时返回 null。 */
    public static String clean(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        String candidate = stripFence(raw.trim());
        candidate = extractBalanced(candidate);
        if (candidate == null) {
            return null;
        }
        return repairTokens(candidate);
    }

    private static String stripFence(String text) {
        if (text.startsWith("```")) {
            int firstNewline = text.indexOf('\n');
            if (firstNewline >= 0) {
                String body = text.substring(firstNewline + 1);
                int fenceEnd = body.lastIndexOf("```");
                if (fenceEnd >= 0) {
                    body = body.substring(0, fenceEnd);
                }
                return body.trim();
            }
        }
        return text;
    }

    /**
     * 定位并提取第一个平衡的 JSON 候选。
     * 滑动重试：每个 { / [ 都是一个候选起点；候选失败（类型不匹配/未闭合）时
     * 从下一个 { / [ 继续尝试，直到成功或没有候选。
     */
    private static String extractBalanced(String text) {
        for (int start = 0; start < text.length(); start++) {
            char c = text.charAt(start);
            if (c != '{' && c != '[') {
                continue;
            }
            String candidate = scanBalanced(text, start);
            if (candidate != null) {
                return candidate;
            }
        }
        return null;
    }

    /**
     * 从 start 扫描平衡括号并返回闭包文本；忽略字符串内括号与转义。
     * pop 时校验 { ↔ }、[ ↔ ] 类型匹配；不匹配、栈空时遇闭括号、或未闭合，
     * 一律返回 null 表示该候选失败（由调用方滑动重试）。
     */
    private static String scanBalanced(String text, int start) {
        Deque<Character> stack = new ArrayDeque<>();
        boolean inString = false;
        boolean escaped = false;
        char quoteChar = '\0';
        for (int i = start; i < text.length(); i++) {
            char c = text.charAt(i);
            if (inString) {
                if (escaped) {
                    escaped = false;
                } else if (c == '\\') {
                    escaped = true;
                } else if (c == quoteChar) {
                    inString = false;
                }
                continue;
            }
            if (c == '"' || c == '\'') {
                inString = true;
                quoteChar = c; // 双引号与单引号都视为字符串，避免字符串内括号被误判为候选
            } else if (c == '{' || c == '[') {
                stack.push(c);
            } else if (c == '}' || c == ']') {
                if (stack.isEmpty()) {
                    return null;
                }
                char top = stack.pop();
                if ((top == '{' && c != '}') || (top == '[' && c != ']')) {
                    return null; // 括号类型不匹配，候选失败
                }
                if (stack.isEmpty()) {
                    return text.substring(start, i + 1);
                }
            }
        }
        return null; // 未闭合
    }

    private static String repairTokens(String json) {
        String repaired = json;
        repaired = removeLineComments(repaired);
        repaired = removeTrailingCommas(repaired);
        // 先统一引号，再按字符串状态机替换非标准数字，避免误伤字符串内的 "NaN"/"Infinity" 文本
        repaired = replaceSingleQuotes(repaired);
        repaired = replaceNonStdNumbers(repaired);
        return repaired;
    }

    private static String removeLineComments(String json) {
        StringBuilder sb = new StringBuilder(json.length());
        boolean inString = false;
        boolean escaped = false;
        char quoteChar = '\0';
        for (int i = 0; i < json.length(); i++) {
            char c = json.charAt(i);
            if (inString) {
                sb.append(c);
                if (escaped) {
                    escaped = false;
                } else if (c == '\\') {
                    escaped = true;
                } else if (c == quoteChar) {
                    inString = false;
                }
            } else if (c == '"' || c == '\'') {
                inString = true;
                quoteChar = c;
                sb.append(c);
            } else if (c == '/' && i + 1 < json.length() && json.charAt(i + 1) == '/') {
                // 删除注释前已追加的尾部空白（缩进/空格），避免残留
                while (sb.length() > 0 && Character.isWhitespace(sb.charAt(sb.length() - 1))) {
                    sb.deleteCharAt(sb.length() - 1);
                }
                while (i < json.length() && json.charAt(i) != '\n') {
                    i++;
                }
            } else {
                sb.append(c);
            }
        }
        return sb.toString();
    }

    private static String removeTrailingCommas(String json) {
        StringBuilder sb = new StringBuilder(json.length());
        boolean inString = false;
        boolean escaped = false;
        char quoteChar = '\0';
        for (int i = 0; i < json.length(); i++) {
            char c = json.charAt(i);
            if (inString) {
                sb.append(c);
                if (escaped) {
                    escaped = false;
                } else if (c == '\\') {
                    escaped = true;
                } else if (c == quoteChar) {
                    inString = false;
                }
            } else if (c == '"' || c == '\'') {
                inString = true;
                quoteChar = c;
                sb.append(c);
            } else if (c == ',') {
                int j = i + 1;
                while (j < json.length() && Character.isWhitespace(json.charAt(j))) {
                    j++;
                }
                if (j < json.length() && (json.charAt(j) == '}' || json.charAt(j) == ']')) {
                    continue; // 跳过尾逗号
                }
                sb.append(c);
            } else {
                sb.append(c);
            }
        }
        return sb.toString();
    }

    /**
     * 字符串状态机扫描，仅在字符串外替换独立的 NaN / Infinity 字面量为 null。
     * token 边界：字面量前后均不是字母/数字/下划线，避免误伤字符串值或标识符文本。
     */
    private static String replaceNonStdNumbers(String json) {
        StringBuilder sb = new StringBuilder(json.length());
        boolean inString = false;
        boolean escaped = false;
        for (int i = 0; i < json.length(); i++) {
            char c = json.charAt(i);
            if (inString) {
                sb.append(c);
                if (escaped) {
                    escaped = false;
                } else if (c == '\\') {
                    escaped = true;
                } else if (c == '"') {
                    inString = false;
                }
                continue;
            }
            if (c == '"') {
                inString = true;
                sb.append(c);
            } else if (c == 'N' && matchesLiteral(json, i, "NaN")) {
                replaceWithNull(sb);
                i += "NaN".length() - 1;
            } else if (c == 'I' && matchesLiteral(json, i, "Infinity")) {
                replaceWithNull(sb);
                i += "Infinity".length() - 1;
            } else {
                sb.append(c);
            }
        }
        return sb.toString();
    }

    /** 追加 null，并吞掉紧邻的前导 +/- 符号，避免产出 -null 这类非法 JSON。 */
    private static void replaceWithNull(StringBuilder sb) {
        int len = sb.length();
        if (len > 0 && (sb.charAt(len - 1) == '-' || sb.charAt(len - 1) == '+')) {
            sb.deleteCharAt(len - 1);
        }
        sb.append("null");
    }

    private static boolean matchesLiteral(String text, int start, String literal) {
        if (start + literal.length() > text.length()) {
            return false;
        }
        for (int k = 0; k < literal.length(); k++) {
            if (text.charAt(start + k) != literal.charAt(k)) {
                return false;
            }
        }
        char prev = start > 0 ? text.charAt(start - 1) : '\0';
        char next = start + literal.length() < text.length()
                ? text.charAt(start + literal.length()) : '\0';
        return !isIdentChar(prev) && !isIdentChar(next);
    }

    private static boolean isIdentChar(char c) {
        return Character.isLetterOrDigit(c) || c == '_';
    }

    /**
     * 单引号 → 双引号，带完整状态跟踪（inString / escaped / quoteChar），
     * 与其余修复函数的状态机行为一致：
     *  - \\ 与 \' 按转义处理，不结束字符串；
     *  - \' 无论所在字符串是单引号还是双引号都撤销反斜杠、保留 '（JSON 无 \' 转义）；
     *  - 单引号字符串内的裸双引号转义为 \"，保证产物是合法 JSON；
     *  - 双引号字符串原样保留（含其中的单引号）。
     */
    private static String replaceSingleQuotes(String json) {
        StringBuilder sb = new StringBuilder(json.length());
        boolean inString = false;
        boolean escaped = false;
        char quoteChar = '\0';
        for (int i = 0; i < json.length(); i++) {
            char c = json.charAt(i);
            if (inString) {
                if (escaped) {
                    if (c == '\'') {
                        // \' 是转义撇号：无论当前字符串是单引号还是双引号都撤掉反斜杠、
                        // 保留 '（JSON 中单引号无需转义，且 \' 本身是非法转义，数据保真）
                        sb.deleteCharAt(sb.length() - 1);
                        sb.append('\'');
                    } else {
                        sb.append(c);
                    }
                    escaped = false;
                } else if (c == '\\') {
                    sb.append(c);
                    escaped = true;
                } else if (c == quoteChar) {
                    inString = false;
                    sb.append(c == '\'' ? '"' : c);
                } else if (c == '"' && quoteChar == '\'') {
                    sb.append("\\\""); // 单引号字符串内的裸双引号
                } else {
                    sb.append(c);
                }
            } else if (c == '"' || c == '\'') {
                inString = true;
                quoteChar = c;
                sb.append('"'); // 统一转成双引号
            } else {
                sb.append(c);
            }
        }
        return sb.toString();
    }
}
