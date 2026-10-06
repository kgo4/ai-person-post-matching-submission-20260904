package com.example.matching.dto.governance.api;

import java.util.Set;

/** Personnel assessment Harness review queues exposed to human reviewers. */
public enum AssessmentHarnessReviewView {
    PENDING(Set.of("PENDING")),
    HISTORY(Set.of("ACCEPTED", "REJECTED", "RESOLVED"));

    private final Set<String> reviewStatuses;

    AssessmentHarnessReviewView(Set<String> reviewStatuses) {
        this.reviewStatuses = reviewStatuses;
    }

    public Set<String> reviewStatuses() {
        return reviewStatuses;
    }
}
