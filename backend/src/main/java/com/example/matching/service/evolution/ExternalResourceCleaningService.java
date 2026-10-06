package com.example.matching.service.evolution;

import com.example.matching.dto.evolution.ExternalTrendResourceDTO;
import java.util.List;

public interface ExternalResourceCleaningService {
    CleaningResult clean(List<ExternalTrendResourceDTO> items);

    record CleaningResult(List<ExternalTrendResourceDTO> items, int filteredCount,
                          int deduplicatedCount, int noiseRemovedCount) {}
}
