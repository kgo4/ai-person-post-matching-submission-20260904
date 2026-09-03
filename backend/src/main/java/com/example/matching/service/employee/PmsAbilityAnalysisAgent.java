package com.example.matching.service.employee;

import com.example.matching.agent.dto.person.PersonAbilityExtractionResult;

import java.util.Map;

/**
 * PMS Ability Analysis Agent Interface
 * <p>
 * Dedicated agent for analyzing PMS (Project Management System) data
 * to extract employee ability claims.
 * <p>
 * Responsibilities:
 * - Pull PMS work orders, defects, test cases, project participation data
 * - Organize PMS-specific context
 * - Call PMS analysis model/tools
 * - Output unified PersonAbilityExtractionResult
 * - Do NOT directly write EmpAbility
 * - Do NOT directly create tags
 *
 * @author system
 */
public interface PmsAbilityAnalysisAgent {

    /**
     * Extract combined abilities from all PMS sources
     *
     * @param empId  Employee ID
     * @param taskId Analysis task ID
     * @return Combined extraction result
     */
    PersonAbilityExtractionResult extractCombined(Long empId, Long taskId);

    /**
     * Extract only from the PMS records collected for this task. The payload is deliberately
     * supplied by the caller so the agent cannot recycle existing employee abilities as evidence.
     */
    default PersonAbilityExtractionResult extractCombined(Long empId, Long taskId,
                                                           Map<String, Object> sourcePayload) {
        return extractCombined(empId, taskId);
    }
}
