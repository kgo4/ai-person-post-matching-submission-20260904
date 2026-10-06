package com.example.matching.common.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * Persisted integer states for matching_task.
 */
@Getter
@AllArgsConstructor
public enum MatchingTaskStatus {

    PENDING(0),
    RUNNING(1),
    COMPLETED(2),
    FAILED(3),
    CANCELLED(4);

    private final int code;
}
