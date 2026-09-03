package com.example.matching.agent.service;

import java.util.ArrayList;
import java.util.List;

/**
 * 提取长文本分块器（Task8：开放词表提取支持长文本）。
 * <p>
 * 优先按段落（连续换行）切分，段落超过上限时再按句子边界/字符硬切；
 * 每个分块保留 {@code chunkIndex} 与原文起止偏移 {@code start/end}，
 * 供服务端合并 claims 时修正证据偏移（同一 sourceRefId）。
 */
public final class ExtractionChunker {

    /**
     * 默认单块字符上限。为 Agent 请求的 JSON 包装和输入安全边界预留空间，避免块尾被
     * 外层消息守卫再次截断；超长源文本仍由连续分块完整覆盖并在服务端合并。
     */
    public static final int DEFAULT_MAX_CHARS = 9000;

    /** 分块结果 */
    public record Chunk(int chunkIndex, int start, int end, String text) {
    }

    private ExtractionChunker() {
    }

    /**
     * 将长文本切分为不超过 maxChars 的分块。
     *
     * @param sourceText 原文（可为 null）
     * @param maxChars   单块字符上限（<=0 时视为 1）
     * @return 分块列表；原文为空时返回空列表
     */
    public static List<Chunk> chunk(String sourceText, int maxChars) {
        if (sourceText == null || sourceText.isBlank()) {
            return List.of();
        }
        int limit = maxChars > 0 ? maxChars : 1;
        List<Chunk> chunks = new ArrayList<>();
        int length = sourceText.length();
        if (length <= limit) {
            return List.of(new Chunk(0, 0, length, sourceText));
        }

        int index = 0;
        int start = 0;
        while (start < length) {
            int end = Math.min(start + limit, length);
            // 尝试在段落边界处回退（优先 \n\n，其次 \n，再次空格），保证语义完整
            if (end < length) {
                int boundary = findBoundary(sourceText, start, end);
                if (boundary > start) {
                    end = boundary;
                }
            }
            chunks.add(new Chunk(index, start, end, sourceText.substring(start, end)));
            start = end;
            index++;
        }
        return chunks;
    }

    /**
     * 在 [start, end) 内寻找最靠后的段落/句子/空格边界；找不到时返回 end（硬切）。
     */
    private static int findBoundary(String text, int start, int end) {
        for (int i = end; i > start; i--) {
            char c = text.charAt(i - 1);
            if (c == '\n') {
                // 段落边界：\n\n 完整归入当前块（返回 i），避免产生以 \n 开头的微块
                if (i >= start + 2 && text.charAt(i - 2) == '\n') {
                    return i;
                }
                return i;
            }
        }
        for (int i = end; i > start; i--) {
            if (text.charAt(i - 1) == ' ' || text.charAt(i - 1) == '\t') {
                return i;
            }
        }
        return end;
    }
}
