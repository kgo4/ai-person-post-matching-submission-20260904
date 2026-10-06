package com.example.matching.service.rag.impl;

import com.example.matching.service.rag.ChunkingProfile;
import com.example.matching.service.rag.KnowledgeChunker;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 段落感知型知识文档分块器
 * <p>
 * 策略：
 * <ol>
 *   <li>先按段落边界（连续换行）拆分</li>
 *   <li>短段落保持完整，不切碎</li>
 *   <li>长段落用配置窗口 + 标点断点切分，前后按配置重叠</li>
 *   <li>过短段落合并到相邻块，避免碎片化</li>
 * </ol>
 * 分块配置按来源类型选择（{@link ChunkingProfile}）。
 */
@Component
public class SimpleKnowledgeChunker implements KnowledgeChunker {

    private static final int DEFAULT_CHUNK_SIZE = 800;
    private static final int DEFAULT_OVERLAP_SIZE = 120;
    private static final int DEFAULT_MIN_CHUNK_LENGTH = 30;

    @Override
    public List<String> chunk(String text) {
        return chunk(text, ChunkingProfile.GENERAL);
    }

    @Override
    public List<String> chunk(String text, ChunkingProfile profile) {
        if (text == null || text.isBlank()) {
            return Collections.emptyList();
        }
        int chunkSize = profile != null ? profile.chunkSize() : DEFAULT_CHUNK_SIZE;
        int overlapSize = profile != null ? profile.overlapSize() : DEFAULT_OVERLAP_SIZE;
        int minChunkLength = profile != null ? profile.minChunkLength() : DEFAULT_MIN_CHUNK_LENGTH;

        // 1. 按段落拆分
        String[] paragraphs = text.split("\\R{2,}");
        List<String> rawChunks = new ArrayList<>();

        for (String para : paragraphs) {
            String cleaned = para.trim();
            if (cleaned.isEmpty()) continue;

            if (cleaned.length() <= chunkSize) {
                rawChunks.add(cleaned);
            } else {
                rawChunks.addAll(splitLongText(cleaned, chunkSize, overlapSize, minChunkLength));
            }
        }

        // 2. 合并过短的块
        return mergeShortChunks(rawChunks, minChunkLength);
    }

    /**
     * 滑动窗口切分长文本（保留段落内换行）
     */
    private List<String> splitLongText(String text, int chunkSize, int overlapSize, int minChunkLength) {
        List<String> chunks = new ArrayList<>();
        int length = text.length();
        int start = 0;

        while (start < length) {
            int end = Math.min(start + chunkSize, length);

            if (end < length) {
                int breakPoint = findBreakPoint(text, Math.max(start, end - 200), end);
                if (breakPoint > start) {
                    end = breakPoint;
                }
            }

            String chunk = text.substring(start, end).trim();
            if (chunk.length() >= minChunkLength) {
                chunks.add(chunk);
            }

            if (end >= length) break;
            start = end - overlapSize;
            if (start < 0) start = 0;
            if (start >= end) break;
        }
        return chunks;
    }

    /**
     * 合并过短块：将长度 < minChunkLength 的块合并到前面或后面
     */
    private List<String> mergeShortChunks(List<String> raw, int minChunkLength) {
        if (raw.size() <= 1) return raw;

        List<String> result = new ArrayList<>();
        StringBuilder pending = null;

        for (String chunk : raw) {
            if (chunk.length() < minChunkLength) {
                if (pending == null) {
                    pending = new StringBuilder(chunk);
                } else {
                    pending.append(" ").append(chunk);
                }
            } else {
                if (pending != null) {
                    result.add(pending + " " + chunk);
                    pending = null;
                } else {
                    result.add(chunk);
                }
            }
        }

        if (pending != null && !result.isEmpty()) {
            int last = result.size() - 1;
            result.set(last, result.get(last) + " " + pending);
        } else if (pending != null) {
            result.add(pending.toString());
        }

        return result;
    }

    /**
     * 在合理位置断开文本
     */
    private int findBreakPoint(String text, int searchStart, int end) {
        for (int i = end - 1; i >= searchStart; i--) {
            char c = text.charAt(i);
            if (c == '。' || c == '？' || c == '！'
                    || c == '.' || c == '?' || c == '!') {
                return i + 1;
            }
        }
        for (int i = end - 1; i >= searchStart; i--) {
            char c = text.charAt(i);
            if (c == '；' || c == ';') return i + 1;
        }
        for (int i = end - 1; i >= searchStart; i--) {
            char c = text.charAt(i);
            if (c == '，' || c == ',') return i + 1;
        }
        for (int i = end - 1; i >= searchStart; i--) {
            if (text.charAt(i) == ' ') return i + 1;
        }
        return end;
    }
}
