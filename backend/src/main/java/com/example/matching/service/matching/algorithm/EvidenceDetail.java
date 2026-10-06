package com.example.matching.service.matching.algorithm;

import java.time.LocalDate;

@lombok.Data
public class EvidenceDetail {
    private Long tagId;
    private Integer masteryLevel;
    private String source;
    private double credibility;
    private double sourceWeight;
    private double timeFactor;
    private LocalDate evaluationDate;
}
