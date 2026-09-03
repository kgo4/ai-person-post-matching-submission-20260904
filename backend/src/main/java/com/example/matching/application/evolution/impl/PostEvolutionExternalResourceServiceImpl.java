package com.example.matching.application.evolution.impl;

import com.example.matching.application.evolution.PostEvolutionExternalResourceService;
import com.example.matching.dto.evolution.ExternalTrendResourceDTO;
import com.example.matching.integration.zhihu.ZhihuSearchClient;
import com.example.matching.integration.zhihu.ZhihuSearchItem;
import com.example.matching.integration.zhihu.ZhihuSearchResponse;
import com.example.matching.integration.zhihu.ZhihuApiProperties;
import com.example.matching.service.evolution.ExternalResourceCleaningService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import java.util.List;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Service
@RequiredArgsConstructor
public class PostEvolutionExternalResourceServiceImpl implements PostEvolutionExternalResourceService {
    private static final String SOURCE = "ZHIHU_TREND";
    private final ZhihuSearchClient client;
    private final ExternalResourceCleaningService cleaningService;
    private final ZhihuApiProperties properties;
    private final Map<String, CachedResult> cache = new ConcurrentHashMap<>();

    @Override
    public Result search(String query, int count) {
        if (query == null || query.isBlank()) return new Result(false, true, "query_required", SOURCE, List.of(), 0, 0, 0);
        try {
            String normalizedQuery = query.trim();
            int normalizedCount = Math.max(1, Math.min(10, count));
            String cacheKey = normalizedQuery + "|" + normalizedCount;
            CachedResult cached = cache.get(cacheKey);
            if (cached != null && cached.expiresAt().isAfter(Instant.now())) return cached.result();

            ZhihuSearchResponse response = client.search(normalizedQuery, normalizedCount);
            List<ExternalTrendResourceDTO> raw = response == null || response.items() == null ? List.of() : response.items().stream()
                    .filter(java.util.Objects::nonNull).map(this::map).toList();
            ExternalResourceCleaningService.CleaningResult cleaned = cleaningService.clean(raw);
            Result result = new Result(true, false, response == null ? "empty_response" : response.emptyReason(), SOURCE,
                    cleaned.items(), cleaned.filteredCount(), cleaned.deduplicatedCount(), cleaned.noiseRemovedCount());
            cache.put(cacheKey, new CachedResult(result, Instant.now().plusSeconds(Math.max(0, properties.getCacheTtlSeconds()))));
            return result;
        } catch (Exception e) {
            log.warn("Zhihu trend resource collection degraded: reason={}", e.getClass().getSimpleName());
            return new Result(false, true, "external_source_unavailable", SOURCE, List.of(), 0, 0, 0);
        }
    }

    private record CachedResult(Result result, Instant expiresAt) {}

    private ExternalTrendResourceDTO map(ZhihuSearchItem item) {
        return new ExternalTrendResourceDTO(item.title(), item.contentType(), item.contentId(), item.contentText(), item.url(),
                item.commentCount(), item.voteUpCount(), SOURCE, false, false);
    }
}
