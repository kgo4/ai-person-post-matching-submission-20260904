package com.example.matching.service.matching.algorithm;

import com.example.matching.dto.matching.MatchDetailDTO;
import com.example.matching.dto.matching.MatchingAbilitySnapshot;
import com.example.matching.dto.matching.MatchingReportDTO;
import com.example.matching.dto.matching.MatchingRequirementSnapshot;
import com.example.matching.entity.matching.MatchingRecord;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
public class MatchingReportAssembler {

    private final ObjectMapper objectMapper;
    private final AbilityEvidenceFusionService fusionService;
    private final SemanticMatchEngine matchEngine;

    @Autowired
    public MatchingReportAssembler(ObjectMapper objectMapper,
                                   AbilityEvidenceFusionService fusionService,
                                   SemanticMatchEngine matchEngine) {
        this.objectMapper = objectMapper;
        this.fusionService = fusionService;
        this.matchEngine = matchEngine;
    }

    public String generateReport(MatchingRecord record, String empName, String postName,
                                  List<MatchingAbilitySnapshot> empAbilities,
                                  List<MatchingRequirementSnapshot> postRequirements,
                                  Map<Long, String> tagNameMap) {
        Map<Long, BigDecimal> fusedLevels = fusionService.fuseAbilityLevel(empAbilities);
        List<MatchDetailDTO> matchDetails = matchEngine.performSemanticMatching(fusedLevels, empAbilities, postRequirements);
        return generateReport(record, empName, postName, empAbilities, postRequirements, tagNameMap, matchDetails);
    }

    public String generateReport(MatchingRecord record, String empName, String postName,
                                 List<MatchingAbilitySnapshot> empAbilities,
                                 List<MatchingRequirementSnapshot> postRequirements,
                                 Map<Long, String> tagNameMap,
                                 List<MatchDetailDTO> matchDetails) {
        if (matchDetails == null || matchDetails.size() != postRequirements.size()) {
            Map<Long, BigDecimal> fusedLevels = fusionService.fuseAbilityLevel(empAbilities);
            matchDetails = matchEngine.performSemanticMatching(fusedLevels, empAbilities, postRequirements);
        }
        Map<Long, List<EvidenceDetail>> evidenceMap = fusionService.generateEvidenceDetail(empAbilities);

        MatchingReportDTO report = new MatchingReportDTO();
        report.setEmpName(empName);
        report.setPostName(postName);
        report.setL2Score(record.getL2Score());
        report.setRankScore(record.getRankScore());
        report.setCalibrationAdjustment(record.getCalibrationAdjustment());
        report.setQualityAdjustment(record.getQualityAdjustment());
        report.setFeedbackAdjustment(record.getFeedbackAdjustment());
        report.setAiMatchScore(record.getAiMatchScore());
        report.setMatchStatus(getStatusName(record.getMatchStatus()));

        List<MatchingReportDTO.AbilityDetail> abilityDetails = new ArrayList<>();
        for (int i = 0; i < postRequirements.size(); i++) {
            MatchingRequirementSnapshot requirement = postRequirements.get(i);
            MatchDetailDTO matchDetail = matchDetails.get(i);

            MatchingReportDTO.AbilityDetail abilityDetail = new MatchingReportDTO.AbilityDetail();
            abilityDetail.setTagId(requirement.tagId());
            abilityDetail.setTagName(requirement.abilityName() != null ? requirement.abilityName()
                    : resolveTagName(requirement.tagId(), tagNameMap));
            abilityDetail.setRequiredLevel(requirement.minRequiredLevel());
            abilityDetail.setActualLevel(matchDetail.getEmployeeRawLevel());
            abilityDetail.setEffectiveLevel(matchDetail.getEffectiveLevel());
            abilityDetail.setMatchType(matchDetail.getMatchType().getCode());
            abilityDetail.setMatchTypeDesc(matchDetail.getMatchTypeDescription());
            abilityDetail.setMatchCoefficient(matchDetail.getMatchCoefficient());
            abilityDetail.setSimilarityScore(matchDetail.getSimilarityScore());
            abilityDetail.setPassed(matchDetail.isPassed());
            abilityDetail.setPassedDesc(matchDetail.getPassedDescription());
            abilityDetail.setIsCore(requirement.isCore());
            abilityDetail.setIsRequired(requirement.isRequired());
            abilityDetail.setScoreContribution(matchDetail.getScoreContribution());

            if (matchDetail.getMatchedEmpTagId() != null) {
                abilityDetail.setMatchedEmpTagId(matchDetail.getMatchedEmpTagId());
                abilityDetail.setMatchedEmpTagName(resolveTagName(matchDetail.getMatchedEmpTagId(), tagNameMap));
            }
            abilityDetail.setMatchedEmpAbilityId(matchDetail.getMatchedEmpAbilityId());
            abilityDetail.setMatchedEmpAbilityName(matchDetail.getMatchedEmpAbilityName());

            Long evidenceAbilityId = matchDetail.getMatchedEmpAbilityId() != null
                    ? matchDetail.getMatchedEmpAbilityId()
                    : matchDetail.getMatchedEmpTagId();
            List<EvidenceDetail> evidences = evidenceAbilityId == null ? List.of()
                    : evidenceMap.getOrDefault(evidenceAbilityId, List.of());
            abilityDetail.setEvidences(toEvidenceItems(evidences));
            abilityDetail.setWeakEvidence(!evidences.isEmpty()
                    && evidences.stream().allMatch(e -> "RESUME_PARSE".equals(e.getSource())));
            abilityDetails.add(abilityDetail);
        }
        report.setAbilityDetails(abilityDetails);

        try {
            return objectMapper.writeValueAsString(report);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to serialize matching report", e);
        }
    }

    private List<MatchingReportDTO.EvidenceItem> toEvidenceItems(List<EvidenceDetail> evidences) {
        List<MatchingReportDTO.EvidenceItem> items = new ArrayList<>();
        for (EvidenceDetail evidence : evidences) {
            MatchingReportDTO.EvidenceItem item = new MatchingReportDTO.EvidenceItem();
            item.setSource(evidence.getSource());
            item.setLevel(evidence.getMasteryLevel());
            item.setCredibility(BigDecimal.valueOf(evidence.getCredibility()).setScale(2, RoundingMode.HALF_UP));
            item.setTimeFactor(BigDecimal.valueOf(evidence.getTimeFactor()).setScale(1, RoundingMode.HALF_UP));
            items.add(item);
        }
        return items;
    }

    private String resolveTagName(Long tagId, Map<Long, String> tagNameMap) {
        if (tagId == null) return null;
        if (tagNameMap == null) return "标签#" + tagId;
        return tagNameMap.getOrDefault(tagId, "标签#" + tagId);
    }

    private String getStatusName(int status) {
        return switch (status) {
            case 1 -> "强适配";
            case 2 -> "适配";
            case 3 -> "待观察";
            case 4 -> "不适配";
            default -> "待审核";
        };
    }
}
