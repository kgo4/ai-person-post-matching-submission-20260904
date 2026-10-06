package com.example.matching.service.evolution.impl;

import com.example.matching.dto.evolution.ExternalTrendResourceDTO;
import com.example.matching.service.evolution.ExternalResourceCleaningService;
import com.example.matching.service.post.impl.PostCleaningRulesEngine;
import com.example.matching.service.post.support.TextSanitizationPolicy;
import com.example.matching.service.rag.KnowledgeDocumentDeduplicator;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.util.*;

@Service
@RequiredArgsConstructor
public class ExternalResourceCleaningServiceImpl implements ExternalResourceCleaningService {
    private static final int MIN_TEXT_LENGTH = 20;
    private static final int MAX_TITLE_LENGTH = 200;
    private static final int MAX_SUMMARY_LENGTH = 2000;
    private static final List<String> NOISE_TERMS = List.of("广告", "推广", "加微信", "扫码", "招聘导流", "开户链接");

    private final KnowledgeDocumentDeduplicator deduplicator;
    private final PostCleaningRulesEngine postCleaningRulesEngine;

    @Override
    public CleaningResult clean(List<ExternalTrendResourceDTO> items) {
        if (items == null || items.isEmpty()) return new CleaningResult(List.of(), 0, 0, 0);
        int filtered = 0, noise = 0, duplicates = 0;
        Map<String, ExternalTrendResourceDTO> unique = new LinkedHashMap<>();
        for (ExternalTrendResourceDTO item : items) {
            if (item == null) { filtered++; continue; }
            // Keep only the post-cleaning text. This DTO is passed directly to the
            // evolution Agent, so validation-only cleaning would leak raw external content.
            String title = normalize(postCleaningRulesEngine.cleanText(normalize(item.title(), MAX_TITLE_LENGTH)), MAX_TITLE_LENGTH);
            String summary = normalize(postCleaningRulesEngine.cleanText(normalize(item.summary(), MAX_SUMMARY_LENGTH)), MAX_SUMMARY_LENGTH);
            String url = normalizeUrl(item.url());
            if (title.isBlank() || summary.length() < MIN_TEXT_LENGTH || url.isBlank()) { filtered++; continue; }
            String cleaned = title + "\n" + summary;
            if (cleaned.length() < MIN_TEXT_LENGTH || containsNoise(cleaned)) { noise++; continue; }
            ExternalTrendResourceDTO normalized = item.withText(title, summary, url);
            String key = !url.isBlank() ? "url:" + url : "content:" + item.contentType() + ":" + item.contentId();
            if (item.contentId() != null && !item.contentId().isBlank()) key = "content:" + item.contentType() + ":" + item.contentId();
            String fingerprint = deduplicator.canonicalHash(title + "\n" + summary);
            ExternalTrendResourceDTO previous = unique.get(key);
            if (previous == null) previous = unique.get("hash:" + fingerprint);
            if (previous == null) {
                unique.put(key, normalized);
                unique.put("hash:" + fingerprint, normalized);
            } else {
                duplicates++;
                ExternalTrendResourceDTO better = score(normalized) > score(previous) ? normalized : previous;
                ExternalTrendResourceDTO old = previous;
                unique.values().removeIf(v -> v == old);
                unique.put(key, better);
                unique.put("hash:" + fingerprint, better);
            }
        }
        List<ExternalTrendResourceDTO> output = unique.entrySet().stream()
                .filter(e -> !e.getKey().startsWith("hash:"))
                .map(Map.Entry::getValue).toList();
        return new CleaningResult(output, filtered, duplicates, noise);
    }

    private int score(ExternalTrendResourceDTO item) {
        return (item.summary() == null ? 0 : item.summary().length())
                + Math.max(0, Optional.ofNullable(item.voteUpCount()).orElse(0))
                + Math.max(0, Optional.ofNullable(item.commentCount()).orElse(0));
    }

    private boolean containsNoise(String text) {
        String lower = text.toLowerCase(Locale.ROOT);
        return NOISE_TERMS.stream().anyMatch(lower::contains);
    }

    private String normalize(String value, int max) {
        if (value == null) return "";
        String cleaned = TextSanitizationPolicy.removeControlChars(value)
                .replaceAll("<[^>]+>", " ")
                .replaceAll("\\s+", " ").trim();
        return cleaned.length() > max ? cleaned.substring(0, max) : cleaned;
    }

    private String normalizeUrl(String value) {
        if (value == null || value.isBlank()) return "";
        try {
            URI uri = URI.create(value.trim());
            String scheme = uri.getScheme() == null ? "" : uri.getScheme().toLowerCase(Locale.ROOT);
            String host = uri.getHost() == null ? "" : uri.getHost().toLowerCase(Locale.ROOT);
            if (!("http".equals(scheme) || "https".equals(scheme)) || host.isBlank()) return "";
            String path = uri.getPath() == null ? "" : uri.getPath();
            return scheme + "://" + host + path;
        } catch (Exception e) { return ""; }
    }
}
