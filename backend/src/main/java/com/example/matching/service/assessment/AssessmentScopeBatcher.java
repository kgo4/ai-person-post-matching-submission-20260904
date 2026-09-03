package com.example.matching.service.assessment;

import com.example.matching.dto.assessment.AssessmentScopeDTO;

import java.util.ArrayList;
import java.util.List;

/** Splits an immutable assessment scope into independently verifiable batches. */
public final class AssessmentScopeBatcher {

    private AssessmentScopeBatcher() {
    }

    public static List<AssessmentScopeDTO> partition(AssessmentScopeDTO scope, int maxItemsPerBatch) {
        if (scope == null || scope.items() == null || scope.items().isEmpty()) {
            return List.of();
        }
        if (maxItemsPerBatch < 1) {
            throw new IllegalArgumentException("maxItemsPerBatch must be positive");
        }
        List<AssessmentScopeDTO> batches = new ArrayList<>();
        for (int from = 0; from < scope.items().size(); from += maxItemsPerBatch) {
            int to = Math.min(from + maxItemsPerBatch, scope.items().size());
            batches.add(new AssessmentScopeDTO(scope.workflowId(), scope.empId(), scope.postId(),
                    List.copyOf(scope.items().subList(from, to)), scope.uncoveredRequirements(), scope.scopeHash()));
        }
        return List.copyOf(batches);
    }
}
