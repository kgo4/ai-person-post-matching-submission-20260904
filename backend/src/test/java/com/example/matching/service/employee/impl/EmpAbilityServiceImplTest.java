package com.example.matching.service.employee.impl;

import com.example.matching.entity.employee.EmpAbility;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class EmpAbilityServiceImplTest {

    @Test
    void profileUsesFormalAbilityNameWhenAssessmentAbilityHasNoSystemTag() {
        EmpAbility ability = new EmpAbility();
        ability.setTagId(null);
        ability.setAbilityName("Kubernetes 运维");

        assertThat(EmpAbilityServiceImpl.resolveProfileAbilityName(ability, null))
                .isEqualTo("Kubernetes 运维");
    }
}
