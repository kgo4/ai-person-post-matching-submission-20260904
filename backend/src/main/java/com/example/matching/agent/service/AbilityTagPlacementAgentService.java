package com.example.matching.agent.service;

import com.example.matching.entity.system.AbilityTagCandidatePlacementProposal;

import java.util.Optional;

/** Generates a read-only taxonomy placement proposal for one governed candidate. */
public interface AbilityTagPlacementAgentService {
    Optional<AbilityTagCandidatePlacementProposal> generateProposal(Long candidateId);
}
