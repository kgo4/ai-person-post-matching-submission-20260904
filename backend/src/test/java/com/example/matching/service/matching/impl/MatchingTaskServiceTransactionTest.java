package com.example.matching.service.matching.impl;

import org.junit.jupiter.api.Test;
import org.springframework.transaction.annotation.Transactional;

import java.lang.reflect.Method;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class MatchingTaskServiceTransactionTest {

    @Test
    void stateChangingMethodsAreTransactional() {
        for (Method method : MatchingTaskServiceImpl.class.getDeclaredMethods()) {
            if (List.of("claimTask", "updateProgress", "completeTask", "failTask").contains(method.getName())) {
                assertThat(method.isAnnotationPresent(Transactional.class))
                        .as("%s must run inside a transaction", method.getName())
                        .isTrue();
            }
        }
    }
}
