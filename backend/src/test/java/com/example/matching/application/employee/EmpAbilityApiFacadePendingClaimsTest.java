package com.example.matching.application.employee;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatCode;

class EmpAbilityApiFacadePendingClaimsTest {

    @Test
    void exposesPendingClaimsForAnEmployeeProfile() {
        assertThatCode(() -> EmpAbilityApiFacade.class.getMethod("listPendingClaims", Long.class))
                .doesNotThrowAnyException();
    }
}
