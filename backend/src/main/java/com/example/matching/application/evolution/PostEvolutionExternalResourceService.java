package com.example.matching.application.evolution;

import com.example.matching.dto.evolution.ExternalTrendResourceDTO;
import java.util.List;

public interface PostEvolutionExternalResourceService {
    Result search(String query, int count);
    record Result(boolean available, boolean degraded, String reason, String sourceType,
                  List<ExternalTrendResourceDTO> items, int filteredCount,
                  int deduplicatedCount, int noiseRemovedCount) {}
}
