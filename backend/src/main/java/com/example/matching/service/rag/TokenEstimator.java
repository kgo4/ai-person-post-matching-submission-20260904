package com.example.matching.service.rag;

/**
 * 确定性、无依赖的 token 估算器。
 * <p>
 * 规则：CJK 码点计 1.5 token，非 CJK 单词（连续字母/数字）计 1 token，
 * 最后加 10% 开销（结构化头、标点、空白）。仅用于上下文预算控制，
 * 不是精确的模型 tokenizer。
 */
public final class TokenEstimator {

    /** CJK 码点每字 token 数 */
    private static final double CJK_TOKEN_PER_CHAR = 1.5;
    /** 非 CJK 单词每词 token 数 */
    private static final double WORD_TOKEN = 1.0;
    /** 开销系数（结构化头、标点、空白） */
    private static final double OVERHEAD_MARKUP = 1.1;

    private TokenEstimator() {
    }

    /**
     * 估算文本的 token 数（向上取整）。
     */
    public static int estimate(String text) {
        if (text == null || text.isBlank()) {
            return 0;
        }
        double cjk = 0;
        int nonCjkWords = 0;
        boolean inWord = false;
        for (int i = 0; i < text.length(); ) {
            int cp = text.codePointAt(i);
            if (isCjk(cp)) {
                cjk++;
                inWord = false;
            } else if (Character.isLetterOrDigit(cp)) {
                if (!inWord) {
                    nonCjkWords++;
                    inWord = true;
                }
            } else {
                inWord = false;
            }
            i += Character.charCount(cp);
        }
        double raw = cjk * CJK_TOKEN_PER_CHAR + nonCjkWords * WORD_TOKEN;
        return (int) Math.ceil(raw * OVERHEAD_MARKUP);
    }

    private static boolean isCjk(int cp) {
        var script = Character.UnicodeScript.of(cp);
        return script == Character.UnicodeScript.HAN
                || script == Character.UnicodeScript.HIRAGANA
                || script == Character.UnicodeScript.KATAKANA;
    }
}
