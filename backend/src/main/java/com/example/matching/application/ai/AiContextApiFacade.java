package com.example.matching.application.ai;

import com.example.matching.ai.context.dto.AiContextPackageDTO;
import com.example.matching.ai.context.dto.AiContextSourceRefDTO;
import com.example.matching.ai.context.dto.ValidateSourceRefsDTO;
import com.example.matching.ai.context.entity.AiContextPackageSnapshot;
import com.example.matching.ai.context.service.AiContextPackageService;
import com.example.matching.ai.context.service.AiContextSnapshotService;
import com.example.matching.ai.context.service.AiContextSourceRefService;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class AiContextApiFacade {

    private final AiContextPackageService aiContextPackageService;
    private final AiContextSnapshotService aiContextSnapshotService;
    private final AiContextSourceRefService aiContextSourceRefService;
    private final ObjectMapper objectMapper;

    public AiContextPackageDTO buildForMatching(Long matchingRecordId) {
        return aiContextPackageService.buildForMatching(matchingRecordId);
    }

    public AiContextPackageSnapshot findLatestSnapshot(String scenario, String businessKey) {
        return aiContextSnapshotService.findLatest(scenario, businessKey);
    }

    public AiContextSourceRefDTO resolveSourceRef(String ref) {
        return aiContextSourceRefService.resolve(ref);
    }

    public AiContextPackageSnapshot findByHash(String contextHash) {
        return aiContextSnapshotService.findByHash(contextHash);
    }

    public AiContextPackageDTO parsePackageJson(String json) {
        try {
            return objectMapper.readValue(json, AiContextPackageDTO.class);
        } catch (Exception e) {
            log.warn("Failed to parse context package JSON", e);
            return null;
        }
    }
}
