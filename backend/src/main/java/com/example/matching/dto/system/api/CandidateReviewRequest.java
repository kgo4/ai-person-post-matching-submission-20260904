package com.example.matching.dto.system.api;

import jakarta.validation.constraints.NotBlank;

public record CandidateReviewRequest(
    @NotBlank String action,
    String comment,
    Long targetTagId
) {}
