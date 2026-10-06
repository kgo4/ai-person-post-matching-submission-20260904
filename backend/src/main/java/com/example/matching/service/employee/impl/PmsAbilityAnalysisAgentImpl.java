package com.example.matching.service.employee.impl;

import com.example.matching.agent.dto.person.PersonAbilityClaim;
import com.example.matching.agent.dto.person.PersonAbilityExtractionResult;
import com.example.matching.agent.lc4j.PmsAbilityAnalysisAiService;
import com.example.matching.common.enums.AbilitySourceType;
import com.example.matching.service.employee.PmsAbilityAnalysisAgent;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * PMS Ability Analysis Agent Implementation
 * <p>
 * Dedicated agent for analyzing PMS data to extract employee ability claims.
 * Delegates to PersonAbilityExtractionAgent for actual extraction logic.
 *
 * @author system
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PmsAbilityAnalysisAgentImpl implements PmsAbilityAnalysisAgent {

    private static final String SOURCE_TYPE = AbilitySourceType.AI_PROJECT;

    private final ObjectProvider<PmsAbilityAnalysisAiService> pmsAbilityAnalysisAiServiceProvider;
    private final ObjectMapper objectMapper;

    @Override
    public PersonAbilityExtractionResult extractCombined(Long empId, Long taskId) {
        return extractCombined(empId, taskId, Map.of());
    }

    @Override
    public PersonAbilityExtractionResult extractCombined(Long empId, Long taskId,
                                                          Map<String, Object> sourcePayload) {
        log.info("PMS Agent: extracting combined abilities for empId={}, taskId={}", empId, taskId);

        if (sourcePayload == null || sourcePayload.isEmpty()) {
            log.warn("PMS Agent: no raw PMS evidence supplied, skip extraction: empId={}, taskId={}", empId, taskId);
            return createEmptyResult(empId, taskId, SOURCE_TYPE);
        }

        PmsAbilityAnalysisAiService aiService = pmsAbilityAnalysisAiServiceProvider.getIfAvailable();
        if (aiService != null) {
            try {
                String context = buildPmsAiContext(empId, taskId, "PMS_COMBINED", sourcePayload);
                PersonAbilityExtractionResult aiResult = normalizePmsExtractionResult(
                        com.example.matching.agent.config.AgentToolProvider
                                .withScope(() -> aiService.extractAbilities(context)),
                        empId, taskId, SOURCE_TYPE);

                if (aiResult != null && aiResult.getClaims() != null && !aiResult.getClaims().isEmpty()) {
                    log.info("PMS Agent: LangChain4j extracted {} claims", aiResult.getClaims().size());
                    return aiResult;
                }
            } catch (Exception e) {
                log.warn("LangChain4j PMS ability extraction failed, falling back to rule extraction: {}", e.getMessage());
            }
        }

        // Never fall back to prior AI_PROJECT abilities: that would turn an old inference into
        // new "PMS evidence". No raw-evidence result is safer than a fabricated extraction.
        return createEmptyResult(empId, taskId, SOURCE_TYPE);
    }

    /**
     * Create empty extraction result
     */
    private PersonAbilityExtractionResult createEmptyResult(Long empId, Long taskId, String sourceType) {
        PersonAbilityExtractionResult result = new PersonAbilityExtractionResult();
        result.setEmpId(empId);
        result.setSourceType(sourceType);
        result.setSourceRefId(taskId);
        result.setClaims(new ArrayList<>());
        return result;
    }

    /**
     * 构建 PMS AI 上下文
     */
    private String buildPmsAiContext(Long empId, Long taskId, String sourceKind, Object sourcePayload) {
        Map<String, Object> context = new LinkedHashMap<>();
        context.put("empId", empId);
        context.put("taskId", taskId);
        context.put("sourceKind", sourceKind);
        context.put("sourcePayload", sourcePayload);
        context.put("rules", List.of(
                "Extract only claims supported by sourcePayload.",
                "Every claim must include sourceRefs.",
                "Do not create tags or write database records."
        ));
        try {
            return objectMapper.writeValueAsString(context);
        } catch (Exception e) {
            return String.valueOf(context);
        }
    }

    /**
     * 解析 LangChain4j 返回的人能力提取结果
     */
    private PersonAbilityExtractionResult parsePersonAbilityExtractionResult(
            String response, Long empId, Long taskId, String sourceType) {
        try {
            Map<String, Object> result = objectMapper.readValue(response, new TypeReference<>() {});
            List<Map<String, Object>> claimsList = (List<Map<String, Object>>) result.get("claims");
            if (claimsList == null || claimsList.isEmpty()) {
                return createEmptyResult(empId, taskId, sourceType);
            }

            List<PersonAbilityClaim> claims = new ArrayList<>();
            for (Map<String, Object> claimMap : claimsList) {
                PersonAbilityClaim claim = new PersonAbilityClaim();
                claim.setEmpId(empId);
                claim.setAbilityName((String) claimMap.get("abilityName"));
                claim.setMasteryLevel(claimMap.get("masteryLevel") != null ?
                        ((Number) claimMap.get("masteryLevel")).intValue() : null);
                claim.setConfidenceScore(claimMap.get("confidenceScore") != null ?
                        new java.math.BigDecimal(claimMap.get("confidenceScore").toString()) : null);
                claim.setEvidenceText((String) claimMap.get("evidenceText"));
                claim.setSourceType(sourceType);
                claim.setSourceRefId(taskId);

                // 保留 sourceRefs
                List<Map<String, Object>> sourceRefsList = (List<Map<String, Object>>) claimMap.get("sourceRefs");
                if (sourceRefsList != null && !sourceRefsList.isEmpty()) {
                    List<String> sourceRefs = new ArrayList<>();
                    for (Map<String, Object> ref : sourceRefsList) {
                        String refType = (String) ref.get("sourceType");
                        String refId = (String) ref.get("sourceId");
                        if (refType != null && refId != null) {
                            sourceRefs.add("source:" + refType + ":" + refId);
                        }
                    }
                    claim.setSourceRefs(sourceRefs);
                } else {
                    claim.setSourceRefs(List.of("source:" + sourceType + ":" + taskId));
                }

                claims.add(claim);
            }

            PersonAbilityExtractionResult extractionResult = createEmptyResult(empId, taskId, sourceType);
            extractionResult.setClaims(claims);
            return extractionResult;
        } catch (Exception e) {
            log.warn("Failed to parse LangChain4j PMS extraction result: {}", e.getMessage());
            return createEmptyResult(empId, taskId, sourceType);
        }
    }

    private PersonAbilityExtractionResult normalizePmsExtractionResult(
            PersonAbilityExtractionResult result, Long empId, Long taskId, String sourceType) {
        if (result == null || result.getClaims() == null) {
            return createEmptyResult(empId, taskId, sourceType);
        }
        result.setEmpId(empId);
        result.setSourceType(sourceType);
        result.setSourceRefId(taskId);
        for (PersonAbilityClaim claim : result.getClaims()) {
            claim.setEmpId(empId);
            claim.setSourceType(sourceType);
            claim.setSourceRefId(taskId);
            if (claim.getNormalizedAbilityName() == null) {
                claim.setNormalizedAbilityName(claim.getAbilityName());
            }
            if (claim.getSourceRefs() == null || claim.getSourceRefs().isEmpty()
                    || claim.getEvidenceText() == null || claim.getEvidenceText().isBlank()) {
                claim.setValidationResult(com.example.matching.agent.dto.person.EvidenceValidationResult.SOURCE_REF_INVALID);
            }
        }
        result.setClaims(result.getClaims().stream()
                .filter(claim -> claim.getAbilityName() != null && !claim.getAbilityName().isBlank()
                        && claim.getMasteryLevel() != null && claim.getMasteryLevel() >= 1 && claim.getMasteryLevel() <= 5
                        && claim.getEvidenceText() != null && !claim.getEvidenceText().isBlank()
                        && claim.getSourceRefs() != null && !claim.getSourceRefs().isEmpty())
                .toList());
        return result;
    }
}
