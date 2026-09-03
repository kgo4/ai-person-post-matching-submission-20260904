package com.example.matching.dto.system.api;

public record AbilityTagPlacementProposalUpdateRequest(
        String action,
        Long targetParentDomainId,
        Long targetTagId,
        String rationale
) {
}
