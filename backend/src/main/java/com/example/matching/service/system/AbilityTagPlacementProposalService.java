package com.example.matching.service.system;

import com.example.matching.entity.system.AbilityTagCandidatePlacementProposal;

import java.util.List;

public interface AbilityTagPlacementProposalService {

    AbilityTagCandidatePlacementProposal createPending(AbilityTagCandidatePlacementProposal proposal);

    List<AbilityTagCandidatePlacementProposal> listByCandidateId(Long candidateId);

    AbilityTagCandidatePlacementProposal updatePending(Long candidateId, Long proposalId,
                                                        String action, Long parentDomainId,
                                                        Long targetTagId, String rationale);

    PlacementApplyResult apply(Long candidateId, Long proposalId, Integer proposalVersion, Long operatorId);
}
