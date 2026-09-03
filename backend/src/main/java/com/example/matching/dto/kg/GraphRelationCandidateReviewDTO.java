package com.example.matching.dto.kg;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class GraphRelationCandidateReviewDTO {

    @NotBlank
    private String decision;

    private String reviewReason;
}
