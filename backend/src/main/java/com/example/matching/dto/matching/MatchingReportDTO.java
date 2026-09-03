package com.example.matching.dto.matching;

import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

@Data
public class MatchingReportDTO {

    private String empName;
    private String postName;
    private BigDecimal l2Score;
    private BigDecimal rankScore;
    private BigDecimal calibrationAdjustment;
    private BigDecimal qualityAdjustment;
    private BigDecimal feedbackAdjustment;
    private BigDecimal aiMatchScore;
    private String matchStatus;
    private List<AbilityDetail> abilityDetails;

    @Data
    public static class AbilityDetail {
        private Long tagId;
        private String tagName;
        private Integer requiredLevel;
        private BigDecimal actualLevel;
        private BigDecimal effectiveLevel;
        private String matchType;
        private String matchTypeDesc;
        private BigDecimal matchCoefficient;
        private BigDecimal similarityScore;
        private boolean passed;
        private String passedDesc;
        private Integer isCore;
        private Integer isRequired;
        private BigDecimal scoreContribution;
        private Long matchedEmpTagId;
        private String matchedEmpTagName;
        private Long matchedEmpAbilityId;
        private String matchedEmpAbilityName;
        private List<EvidenceItem> evidences;
        private boolean weakEvidence;
    }

    @Data
    public static class EvidenceItem {
        private String source;
        private Integer level;
        private BigDecimal credibility;
        private BigDecimal timeFactor;
    }
}
