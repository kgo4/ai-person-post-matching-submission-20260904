package com.example.matching.application.kg;

import com.example.matching.dto.kg.GraphRelationCandidateCreateDTO;
import com.example.matching.dto.kg.GraphRelationCandidateReviewDTO;
import com.example.matching.dto.kg.GraphRelationCandidateRevokeDTO;
import com.example.matching.dto.kg.api.GraphRelationCandidateResponse;
import com.example.matching.entity.kg.KgRelationCandidate;
import com.example.matching.service.kg.GraphRelationCandidateService;
import com.example.matching.service.kg.GraphRelationGovernanceService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class GraphRelationCandidateApiFacade {

    private final GraphRelationCandidateService graphRelationCandidateService;
    private final GraphRelationGovernanceService graphRelationGovernanceService;

    public GraphRelationCandidateResponse create(GraphRelationCandidateCreateDTO dto, Long createdBy) {
        KgRelationCandidate entity = graphRelationCandidateService.createCandidate(dto, createdBy);
        return toResponse(entity);
    }

    public GraphRelationCandidateResponse review(Long candidateId, GraphRelationCandidateReviewDTO dto, Long reviewedBy) {
        KgRelationCandidate entity = graphRelationCandidateService.reviewCandidate(candidateId, dto, reviewedBy);
        return toResponse(entity);
    }

    public GraphRelationCandidateResponse revoke(Long candidateId, GraphRelationCandidateRevokeDTO dto, Long revokedBy) {
        KgRelationCandidate entity = graphRelationCandidateService.revokeCandidate(candidateId, dto, revokedBy);
        return toResponse(entity);
    }

    public List<GraphRelationCandidateResponse> list(String reviewStatus) {
        List<KgRelationCandidate> entities = graphRelationCandidateService.listCandidates(reviewStatus);
        return entities.stream().map(this::toResponse).toList();
    }

    public Map<String, Object> policies() {
        return graphRelationGovernanceService.getPolicies();
    }

    public Map<String, Object> check() {
        return graphRelationGovernanceService.inspectActiveGraph();
    }

    private GraphRelationCandidateResponse toResponse(KgRelationCandidate e) {
        if (e == null) return null;
        return new GraphRelationCandidateResponse(
                e.getId(), e.getCandidateCode(), e.getSourceNodeKey(), e.getTargetNodeKey(),
                e.getRelationType(), e.getDiscoveryMethod(), e.getSemanticScore(),
                e.getSourceRefsJson(), e.getReviewStatus(), e.getReviewReason(),
                e.getReviewedBy(), e.getReviewedTime(), e.getCreatedBy(),
                e.getCreatedTime(), e.getUpdatedTime()
        );
    }
}
