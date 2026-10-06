package com.example.matching.common.enums;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class MatchingTaskStatusTest {

    @Test
    void preservesPersistedMatchingTaskStatusCodes() {
        assertThat(MatchingTaskStatus.PENDING.getCode()).isZero();
        assertThat(MatchingTaskStatus.RUNNING.getCode()).isEqualTo(1);
        assertThat(MatchingTaskStatus.COMPLETED.getCode()).isEqualTo(2);
        assertThat(MatchingTaskStatus.FAILED.getCode()).isEqualTo(3);
        assertThat(MatchingTaskStatus.CANCELLED.getCode()).isEqualTo(4);
    }
}
