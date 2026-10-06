package com.example.matching.service.rag;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * RAG 上下文组装器 —— 按 token 预算组装带来源标注的上下文。
 * <p>
 * 规则：
 * <ul>
 *   <li>丢弃规范化后文本重复的块</li>
 *   <li>同一文档最多保留 2 块</li>
 *   <li>按排名顺序添加直到预算耗尽</li>
 *   <li>仅对最后选中的块在句子边界截断并追加 {@code [truncated]}</li>
 *   <li>输出结构化头：来源类型、文档 id、块 id、标题、归一化分数</li>
 * </ul>
 * 预算为估算 token 数（{@link TokenEstimator}），非精确 tokenizer。
 */
public final class RagContextAssembler {

    private static final int MAX_CHUNKS_PER_DOCUMENT = 2;

    private RagContextAssembler() {
    }

    /**
     * 组装上下文。
     *
     * @param hits    按排名排序的命中
     * @param budget  估算 token 预算
     * @return 组装后的上下文文本（无命中时返回空串）
     */
    public static String assemble(List<KnowledgeSearchHit> hits, int budget) {
        if (hits == null || hits.isEmpty()) {
            return "";
        }

        List<KnowledgeSearchHit> selected = selectWithinBudget(hits, budget);
        if (selected.isEmpty()) {
            return "";
        }

        StringBuilder sb = new StringBuilder("<retrieved_context>\n");
        int used = TokenEstimator.estimate("<retrieved_context>\n</retrieved_context>");
        for (int i = 0; i < selected.size(); i++) {
            KnowledgeSearchHit hit = selected.get(i);
            String header = renderHeader(hit, i + 1);
            String body = escapeXml(hit.content() != null ? hit.content() : "");
            boolean isLast = (i == selected.size() - 1);

            int headerTokens = TokenEstimator.estimate(header);
            int bodyTokens = TokenEstimator.estimate(body);
            int needed = headerTokens + bodyTokens + TokenEstimator.estimate("\n</evidence>\n");

            // 预算不足时对最后一块在句子边界截断
            if (used + needed > budget) {
                int remaining = Math.max(0, budget - used - headerTokens - TokenEstimator.estimate("\n</evidence>\n"));
                String truncated = truncateAtSentence(body, remaining);
                if (!truncated.isBlank()) {
                    sb.append(header);
                    if (!sb.isEmpty() && sb.charAt(sb.length() - 1) != '\n') {
                        sb.append('\n');
                    }
                    sb.append(truncated).append(" [truncated]\n</evidence>");
                }
                break;
            }

            sb.append(header);
            if (sb.charAt(sb.length() - 1) != '\n') {
                sb.append('\n');
            }
            sb.append(body).append("\n</evidence>");
            used += needed;
            if (i < selected.size() - 1) {
                sb.append("\n");
            }
            if (isLast) {
                break;
            }
        }
        return sb.append("\n</retrieved_context>").toString().trim();
    }

    /**
     * 在预算内选择块：去重 + 每文档限 2 块 + 按排名。
     * <p>
     * 完整块超出剩余预算时仍选入（作为末块），由 {@link #assemble} 在句子边界截断；
     * 若剩余预算不足以容纳 header 则停止。
     */
    static List<KnowledgeSearchHit> selectWithinBudget(List<KnowledgeSearchHit> hits, int budget) {
        if (budget <= 0) {
            return List.of();
        }
        Set<String> seenNormalized = new HashSet<>();
        Map<String, Integer> docCount = new LinkedHashMap<>();
        List<KnowledgeSearchHit> selected = new ArrayList<>();
        int used = 0;

        for (KnowledgeSearchHit hit : hits) {
            String normalized = normalize(hit.content());
            if (normalized.isBlank() || !seenNormalized.add(normalized)) {
                continue;
            }
            String docKey = hit.documentId() != null ? hit.documentId() : hit.chunkId();
            int count = docCount.merge(docKey, 1, Integer::sum);
            if (count > MAX_CHUNKS_PER_DOCUMENT) {
                continue;
            }

            int headerTokens = TokenEstimator.estimate(renderHeader(hit, 1));
            if (used + headerTokens > budget) {
                break;
            }
            int bodyTokens = TokenEstimator.estimate(escapeXml(hit.content() != null ? hit.content() : ""));
            int needed = headerTokens + bodyTokens + TokenEstimator.estimate("\n</evidence>\n");
            // 完整块放不下则仍选入作为可截断的末块
            selected.add(hit);
            used += Math.min(needed, budget - used);
            if (used >= budget) {
                break;
            }
        }
        return selected;
    }

    private static String renderHeader(KnowledgeSearchHit hit, int index) {
        return "<evidence index=\"" + index + "\"" +
                " sourceType=\"" + escapeXml(safe(hit.sourceType())) + "\"" +
                " documentId=\"" + escapeXml(safe(hit.documentId())) + "\"" +
                " chunkId=\"" + escapeXml(safe(hit.chunkId())) + "\"" +
                " title=\"" + escapeXml(safe(hit.title())) + "\"" +
                " score=\"" + formatScore((float) hit.effectiveScore()) + "\">";
    }

    private static String formatScore(float score) {
        return String.format(java.util.Locale.ROOT, "%.4f", score);
    }

    private static String safe(String value) {
        return value != null ? value : "UNKNOWN";
    }

    private static String escapeXml(String value) {
        return value.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&apos;");
    }

    static String normalize(String text) {
        if (text == null) {
            return "";
        }
        return text.trim().replaceAll("\\s+", " ");
    }

    /**
     * 在句子边界截断（。？！.!?；;），找不到边界则硬截断。
     */
    static String truncateAtSentence(String text, int tokenBudget) {
        if (text == null || text.isBlank() || tokenBudget <= 0) {
            return "";
        }
        // 按字符推进直到超出预算（估算近似），并停在最近的句子边界
        int maxChars = Math.max(1, text.length());
        StringBuilder candidate = new StringBuilder();
        int used = 0;
        int lastSentenceBoundary = -1;
        for (int i = 0; i < text.length(); ) {
            int cp = text.codePointAt(i);
            candidate.appendCodePoint(cp);
            used = TokenEstimator.estimate(candidate.toString());
            if (isSentenceBoundary(cp)) {
                lastSentenceBoundary = i + Character.charCount(cp);
            }
            if (used >= tokenBudget) {
                break;
            }
            i += Character.charCount(cp);
        }
        if (lastSentenceBoundary > 0 && lastSentenceBoundary < text.length()) {
            return text.substring(0, lastSentenceBoundary);
        }
        int end = Math.min(text.length(), Math.max(1, candidate.length()));
        return text.substring(0, end);
    }

    private static boolean isSentenceBoundary(int cp) {
        return cp == '。' || cp == '？' || cp == '！'
                || cp == '.' || cp == '?' || cp == '!'
                || cp == '；' || cp == ';';
    }
}
