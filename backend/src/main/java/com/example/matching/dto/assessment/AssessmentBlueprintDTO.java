package com.example.matching.dto.assessment;

import java.util.List;

/** Server-built question slots; agents supply content only and cannot alter bindings. */
public record AssessmentBlueprintDTO(Long workflowId, String scopeHash, List<QuestionSlot> slots) {
    public record QuestionSlot(Long questionSlotId, List<Long> assessmentAbilityIds,
                               List<Long> sourceClaimIds, List<Long> postRequirementIds,
                               String questionType, Integer targetLevel, String scoringRubric,
                               Integer priority, Integer weight) {
    }
}
