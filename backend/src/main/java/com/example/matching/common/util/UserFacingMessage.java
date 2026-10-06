package com.example.matching.common.util;

import java.util.List;
import java.util.regex.Pattern;

/**
 * 用户可读文案的最后一道闸门。
 *
 * <p>背景（真实事故）：材料上传时索引撞了唯一键，代码把 JDBC 的原始异常文本
 * 直接拼进了 {@code BusinessException}，于是界面上出现了
 * {@code Duplicate entry '1003-0-1' for key 'rag_knowledge_chunk.uk_doc_chunk_revision'}。
 * 这种英文技术文案对使用者毫无意义，也暴露了内部表结构。
 *
 * <p>两道防线：
 * <ol>
 *   <li>抛出点：新代码一律自己写中文文案，原始异常只进日志（见
 *       {@code EvolutionSourceIngestionServiceImpl#indexKnowledgeSource}）；</li>
 *   <li>出口：{@code GlobalExceptionHandler} 统一过一遍 {@link #sanitize}，
 *       兜住既有代码里遗留的 {@code "…失败: " + e.getMessage()} 写法。</li>
 * </ol>
 *
 * <p>刻意做成「按特征识别技术文案」而不是「只放行白名单」：异常文本形态无穷，
 * 白名单一定会误伤正常中文提示；而技术文案的特征（SQL 关键字、异常类名、堆栈行）
 * 相对稳定，命中即换兜底文案，漏判也只是保持原样，不会变差。
 *
 * <p>特征分两档，避免「误伤带英文单词的正常中文提示」：
 * <ul>
 *   <li><b>强特征</b>：SQL / 堆栈 / 异常类名 —— 只要出现就判定为技术文案，与是否含中文无关；</li>
 *   <li><b>弱特征</b>：{@code timeout} / {@code connection refused} 这类词 ——
 *       只有在整条文案没有中文时才判定为技术文案。否则「AI 调用 timeout，请稍后重试」
 *       这种本来就写给用户看的中文提示会被误换成通用兜底。</li>
 * </ul>
 */
public final class UserFacingMessage {

    /** 强特征：SQL、异常类名、堆栈行。出现即技术文案。 */
    private static final List<Pattern> STRONG_MARKERS = List.of(
            Pattern.compile("(?i)\\bduplicate entry\\b"),
            Pattern.compile("(?i)\\bfor key\\b"),
            Pattern.compile("(?i)\\bsqlstate\\b"),
            Pattern.compile("(?i)\\bsql syntax\\b"),
            Pattern.compile("(?i)\\bsql(?:exception|error)\\b"),
            Pattern.compile("(?i)\\bjdbc\\b"),
            Pattern.compile("(?i)\\bdeadlock\\b"),
            Pattern.compile("(?i)\\block wait timeout\\b"),
            Pattern.compile("(?i)\\bjava\\.[a-z0-9_.]+\\b"),
            Pattern.compile("(?i)\\bat [a-z0-9_$.]+\\([a-z0-9_$.]+\\.java:\\d+\\)"),
            Pattern.compile("(?i)\\b[a-z0-9_]{2,}(?:exception|throwable)\\b"),
            Pattern.compile("(?i)\\b(?:nested exception|root cause)\\b"));

    /** 弱特征：基础设施类英文词。仅在整条文案无中文时才判定为技术文案。 */
    private static final List<Pattern> WEAK_MARKERS = List.of(
            Pattern.compile("(?i)\\bconnection (?:refused|reset|closed)\\b"),
            Pattern.compile("(?i)\\btimeout\\b"),
            Pattern.compile("(?i)\\bconstraint\\b"),
            Pattern.compile("(?i)\\brequest failed\\b"));

    /** 无中文时的长度阈值：超过它基本可以断定是没翻译的英文技术文案。 */
    private static final int ENGLISH_ONLY_LENGTH_LIMIT = 24;

    private UserFacingMessage() {
    }

    /**
     * 这条文案是否明显是给机器看的技术文本。
     *
     * @param text 待判定文案，可空
     * @return true 表示不该直接展示给用户
     */
    public static boolean looksTechnical(String text) {
        if (text == null || text.isBlank()) {
            return true;
        }
        String value = text.trim();
        for (Pattern marker : STRONG_MARKERS) {
            if (marker.matcher(value).find()) {
                return true;
            }
        }
        boolean hasCjk = containsCjk(value);
        if (!hasCjk) {
            for (Pattern marker : WEAK_MARKERS) {
                if (marker.matcher(value).find()) {
                    return true;
                }
            }
            return value.length() > ENGLISH_ONLY_LENGTH_LIMIT;
        }
        return false;
    }

    /**
     * 把技术文案换成兜底文案；本来就是友好中文时原样返回。
     *
     * @param text     原始文案
     * @param fallback 命中技术文案时的替代文案（应为完整中文句子）
     * @return 可安全展示的文案
     */
    public static String sanitize(String text, String fallback) {
        if (looksTechnical(text)) {
            return fallback;
        }
        return text.trim();
    }

    /**
     * 拼接「中文前缀 + 原始原因」，原始原因命中技术文案时不再拼原始原因。
     *
     * <p>用于 {@code "文档索引失败: " + e.getMessage()} 这类既有写法 ——
     * 保留中文前缀，只在原因本身是技术文案时丢弃它。
     *
     * @param prefix   中文前缀，例如「文档索引失败」
     * @param rawCause 原始异常文案
     * @return {@code prefix} 或 {@code prefix + "：" + rawCause}
     */
    public static String withCause(String prefix, String rawCause) {
        if (looksTechnical(rawCause)) {
            return prefix;
        }
        return prefix + "：" + rawCause.trim();
    }

    private static boolean containsCjk(String text) {
        for (int i = 0; i < text.length(); i++) {
            if (Character.UnicodeScript.of(text.charAt(i)) == Character.UnicodeScript.HAN) {
                return true;
            }
        }
        return false;
    }
}
