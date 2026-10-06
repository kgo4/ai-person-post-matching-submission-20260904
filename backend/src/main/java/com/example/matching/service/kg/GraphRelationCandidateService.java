package com.example.matching.service.kg;

import com.example.matching.dto.kg.GraphRelationCandidateCreateDTO;
import com.example.matching.dto.kg.GraphRelationCandidateReviewDTO;
import com.example.matching.dto.kg.GraphRelationCandidateRevokeDTO;
import com.example.matching.entity.kg.KgRelationCandidate;

import java.util.List;

public interface GraphRelationCandidateService {

    KgRelationCandidate createCandidate(GraphRelationCandidateCreateDTO request, Long createdBy);

    KgRelationCandidate reviewCandidate(Long candidateId, GraphRelationCandidateReviewDTO request, Long reviewedBy);

    KgRelationCandidate revokeCandidate(Long candidateId, GraphRelationCandidateRevokeDTO request, Long revokedBy);

    List<KgRelationCandidate> listCandidates(String reviewStatus);
}
