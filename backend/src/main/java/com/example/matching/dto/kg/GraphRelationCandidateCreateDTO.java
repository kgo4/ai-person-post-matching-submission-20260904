package com.example.matching.dto.kg;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

@Data
public class GraphRelationCandidateCreateDTO {

    @NotBlank
    private String sourceNodeKey;

    @NotBlank
    private String targetNodeKey;

    @NotBlank
    private String discoveryMethod;

    @DecimalMin("0.0")
    @DecimalMax("1.0")
    private BigDecimal semanticScore;

    @NotEmpty
    private List<String> sourceRefs;
}
