package com.example.matching.common.util;

import java.text.Normalizer;
import java.util.Locale;

/**
 * SimHash 64-bit 文本指纹工具。
 * <p>
 * 用于近似去重：两条文本的汉明距离越小越相似，典型阈值 ≤3 判定为近似重复（如 JD 模板抄袭、
 * 措辞略有差异但内容本质相同）。与 SHA-256 精确去重互补——后者只能命中完全一致的文本，
 * 前者可命中「同源模板改写」场景。
 * <p>
 * 中文与英文混合文本采用「字符 unigram + bigram」切分，无需引入外部分词器。
 *
 * @author system
 */
public final class SimHash {

    /** 汉明距离阈值：≤3 判定为近似重复 */
    public static final int DEFAULT_HAMMING_THRESHOLD = 3;

    private static final long FNV_OFFSET_BASIS = 0xcbf29ce484222325L;
    private static final long FNV_PRIME = 0x100000001b3L;

    private SimHash() {
    }

    /**
     * 计算文本的 64-bit SimHash 指纹。
     *
     * @param text 原始文本（可为 null）
     * @return 指纹；空文本返回 0L
     */
    public static long compute(String text) {
        if (text == null || text.isBlank()) {
            return 0L;
        }
        String normalized = normalize(text);
        char[] chars = normalized.toCharArray();
        int[] vector = new int[64];

        for (int i = 0; i < chars.length; i++) {
            addWeight(vector, fnv1a64(chars, i, i + 1));
            if (i + 1 < chars.length) {
                addWeight(vector, fnv1a64(chars, i, i + 2));
            }
        }

        long fingerprint = 0L;
        for (int i = 0; i < 64; i++) {
            if (vector[i] > 0) {
                fingerprint |= (1L << i);
            }
        }
        return fingerprint;
    }

    /**
     * 计算两个指纹之间的汉明距离（不同 bit 数）。
     */
    public static int hammingDistance(long a, long b) {
        return Long.bitCount(a ^ b);
    }

    /**
     * 判断两个指纹是否近似重复（汉明距离 ≤ 阈值）。
     */
    public static boolean isNearDuplicate(long a, long b) {
        return a != 0L && b != 0L && hammingDistance(a, b) <= DEFAULT_HAMMING_THRESHOLD;
    }

    private static void addWeight(int[] vector, long hash) {
        for (int i = 0; i < 64; i++) {
            if (((hash >>> i) & 1L) == 1L) {
                vector[i]++;
            } else {
                vector[i]--;
            }
        }
    }

    /**
     * 归一化：NFKC 折叠全角/兼容字符 → 转小写 → 仅保留字母数字与 CJK 字符，
     * 使标点、空白、全角/半角差异不干扰指纹。
     */
    private static String normalize(String text) {
        String normalized = Normalizer.normalize(text, Normalizer.Form.NFKC)
                .toLowerCase(Locale.ROOT);
        StringBuilder sb = new StringBuilder(normalized.length());
        for (int i = 0; i < normalized.length(); i++) {
            char c = normalized.charAt(i);
            if (Character.isLetterOrDigit(c)) {
                sb.append(c);
            }
        }
        return sb.toString();
    }

    /** FNV-1a 64-bit 散列，用于把字符序列映射为均匀分布的 64-bit 值。 */
    private static long fnv1a64(char[] chars, int from, int to) {
        long hash = FNV_OFFSET_BASIS;
        for (int i = from; i < to; i++) {
            hash ^= chars[i];
            hash *= FNV_PRIME;
        }
        return hash;
    }
}
