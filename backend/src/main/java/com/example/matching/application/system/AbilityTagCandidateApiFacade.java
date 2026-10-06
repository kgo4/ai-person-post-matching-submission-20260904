package com.example.matching.application.system;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.example.matching.common.dto.PageResponse;
import com.example.matching.dto.system.api.AbilityTagCandidateResponse;
import com.example.matching.entity.system.AbilityTagCandidate;
import com.example.matching.service.system.AbilityTagCandidateService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class AbilityTagCandidateApiFacade {

    private final AbilityTagCandidateService tagCandidateService;

    public PageResponse<AbilityTagCandidateResponse> page(long current, long size,
                                                           String status, String sourceType, String keyword) {
        IPage<AbilityTagCandidate> page = tagCandidateService.pageCandidates(
                new Page<>(current, size), status, sourceType, keyword);
        return PageResponse.from(page, this::toResponse);
    }

    public List<AbilityTagCandidateResponse> getHighFrequency(int threshold) {
        return tagCandidateService.getHighFrequencyCandidates(threshold).stream()
                .map(this::toResponse).toList();
    }

    public Map<String, Long> countByStatus() {
        return tagCandidateService.countByStatus();
    }

    public Long approve(Long id, String comment) {
        Long reviewerId = 1L;
        return tagCandidateService.approve(id, reviewerId, comment);
    }

    public Long approve(Long id, Long parentDomainId, String comment) {
        Long reviewerId = 1L;
        return tagCandidateService.approve(id, parentDomainId, reviewerId, comment);
    }

    public void reject(Long id, String comment) {
        Long reviewerId = 1L;
        tagCandidateService.reject(id, reviewerId, comment);
    }

    public void merge(Long id, Long targetTagId, String comment) {
        Long reviewerId = 1L;
        tagCandidateService.merge(id, targetTagId, reviewerId, comment);
    }

    public void delete(Long id) {
        tagCandidateService.removeById(id);
    }

    private AbilityTagCandidateResponse toResponse(AbilityTagCandidate entity) {
        return new AbilityTagCandidateResponse(
            entity.getId(), entity.getCandidateName(), entity.getTagCategory(),
            entity.getDomain(), entity.getDescription(), entity.getReason(),
            entity.getEvidenceText(), entity.getSourceType(), entity.getSourceRefId(),
            entity.getSourcePostId(), entity.getSourceEmpId(), entity.getOccurrenceCount(),
            entity.getRelatedPostCount(), entity.getRelatedEmpCount(),
            entity.getSimilarTagId(), entity.getSimilarTagName(), entity.getSimilarityScore(),
            entity.getStatus(), entity.getReviewComment(), entity.getReviewedBy(),
            entity.getReviewedTime(), entity.getMergedTagId(),
            entity.getCreatedTime(), entity.getUpdatedTime()
        );
    }
}
