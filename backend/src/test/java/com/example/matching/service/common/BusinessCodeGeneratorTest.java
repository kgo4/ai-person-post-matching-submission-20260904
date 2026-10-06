package com.example.matching.service.common;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class BusinessCodeGeneratorTest {

    @Test
    void generates_prefixed_unique_codes() {
        BusinessCodeGenerator generator = new BusinessCodeGenerator();

        String employeeCode = generator.nextEmployeeCode();
        String anotherEmployeeCode = generator.nextEmployeeCode();
        String postCode = generator.nextPostCode();
        String templateCode = generator.nextTemplateCode();
        String abilityTagCode = generator.nextAbilityTagCode();

        assertThat(employeeCode).startsWith("EMP_");
        assertThat(postCode).startsWith("POST_");
        assertThat(templateCode).startsWith("TPL_");
        assertThat(abilityTagCode).startsWith("TAG_");
        assertThat(anotherEmployeeCode).startsWith("EMP_");
        assertThat(anotherEmployeeCode).isNotEqualTo(employeeCode);
    }
}
