package com.example.matching.application.system;

import com.example.matching.dto.system.api.SourceWeightConfigRequest;
import com.example.matching.dto.system.api.SourceWeightConfigResponse;
import com.example.matching.entity.system.SourceWeightConfig;
import com.example.matching.service.system.SourceWeightConfigService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class SourceWeightConfigApiFacade {

    private final SourceWeightConfigService sourceWeightConfigService;

    public List<SourceWeightConfigResponse> listAll() {
        return sourceWeightConfigService.listAll().stream().map(this::toResponse).toList();
    }

    public List<SourceWeightConfigResponse> batchUpdate(List<SourceWeightConfigRequest> requests) {
        List<SourceWeightConfig> configs = requests.stream().map(r -> {
            SourceWeightConfig c = new SourceWeightConfig();
            c.setId(r.id());
            c.setSourceType(r.sourceType());
            c.setSourceLabel(r.sourceLabel());
            c.setWeight(r.weight());
            c.setIsActive(r.isActive());
            c.setSortOrder(r.sortOrder());
            c.setRemark(r.remark());
            return c;
        }).toList();
        List<SourceWeightConfig> updated = sourceWeightConfigService.batchUpdate(configs);
        return updated.stream().map(this::toResponse).toList();
    }

    private SourceWeightConfigResponse toResponse(SourceWeightConfig entity) {
        return new SourceWeightConfigResponse(
            entity.getId(), entity.getSourceType(), entity.getSourceLabel(),
            entity.getWeight(), entity.getIsActive(), entity.getSortOrder(),
            entity.getRemark(), entity.getCreatedTime(), entity.getUpdatedTime()
        );
    }
}
