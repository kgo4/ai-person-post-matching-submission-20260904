package com.example.matching.dto.kg;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class GraphRelationCandidateRevokeDTO {

    @NotBlank
    private String revokeReason;
}
