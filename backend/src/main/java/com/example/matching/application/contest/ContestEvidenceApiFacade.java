package com.example.matching.application.contest;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.example.matching.common.dto.PageResponse;
import com.example.matching.dto.contest.EvidenceCreateDTO;
import com.example.matching.dto.contest.EvidenceQueryDTO;
import com.example.matching.dto.contest.EvidenceReviewDTO;
import com.example.matching.dto.contest.api.ContestEvidenceResponse;
import com.example.matching.dto.contest.api.EvidenceQuery;
import com.example.matching.entity.contest.ContestEvidenceItem;
import com.example.matching.service.contest.EvidenceCenterService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class ContestEvidenceApiFacade {

    private final EvidenceCenterService evidenceCenterService;

    public ContestEvidenceResponse create(EvidenceCreateDTO dto) {
        ContestEvidenceItem entity = evidenceCenterService.createEvidence(dto);
        return toResponse(entity);
    }

    public PageResponse<ContestEvidenceResponse> page(long current, long size, EvidenceQuery query) {
        EvidenceQueryDTO serviceQuery = new EvidenceQueryDTO();
        serviceQuery.setSourceType(query.sourceType());
        serviceQuery.setTargetType(query.targetType());
        serviceQuery.setEvidenceStatus(query.evidenceStatus());
        serviceQuery.setAbilityName(query.abilityName());
        IPage<ContestEvidenceItem> page = evidenceCenterService.pageEvidence(new Page<>(current, size), serviceQuery);
        return PageResponse.from(page, ContestEvidenceApiFacade::toResponse);
    }

    public ContestEvidenceResponse detail(Long id) {
        ContestEvidenceItem entity = evidenceCenterService.getEvidenceById(id);
        return toResponse(entity);
    }

    public void review(Long id, EvidenceReviewDTO dto, Long userId) {
        evidenceCenterService.reviewEvidence(id, dto, userId);
    }

    public Map<String, Object> summary() {
        return evidenceCenterService.getEvidenceSummary();
    }

    public Map<String, Object> employeeChain(Long empId) {
        return evidenceCenterService.getEmployeeEvidenceChain(empId);
    }

    public Map<String, Object> postChain(Long postId) {
        return evidenceCenterService.getPostEvidenceChain(postId);
    }

    public Map<String, Object> backfill(String sourceType, int limit) {
        int created = evidenceCenterService.backfillEvidence(sourceType, limit);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("sourceType", sourceType);
        result.put("created", created);
        return result;
    }

    static ContestEvidenceResponse toResponse(ContestEvidenceItem e) {
        if (e == null) return null;
        return new ContestEvidenceResponse(
                e.getId(), e.getEvidenceCode(), e.getSourceType(), e.getSourceRefId(),
                e.getSourceTitle(), e.getSourceText(), e.getTargetType(), e.getTargetRefId(),
                e.getAbilityName(), e.getTagId(), e.getConfidenceScore(), e.getCredibilityScore(),
                e.getEvidenceStatus(), e.getReviewComment(), e.getRagChunkIds(), e.getRagDocumentIds(),
                e.getReviewedBy(), e.getReviewedTime(), e.getCreatedBy(), e.getCreatedTime(),
                e.getUpdatedBy(), e.getUpdatedTime()
        );
    }
}
