package com.example.matching.service.assessment;

import com.example.matching.dto.assessment.AssessmentBlueprintDTO;
import com.example.matching.dto.assessment.AssessmentScopeDTO;

/** Single application entry point for immutable assessment contracts. */
public interface CapabilityAssessmentOrchestrator {
    AssessmentScopeDTO freezeScope(Long workflowId, Long empId, Long postId);
    AssessmentScopeDTO loadScope(Long workflowId);
    AssessmentBlueprintDTO loadOrCreateBlueprint(Long workflowId, Long empId, Long postId);
}
