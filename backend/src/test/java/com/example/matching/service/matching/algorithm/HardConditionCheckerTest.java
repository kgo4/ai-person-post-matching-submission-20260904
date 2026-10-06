package com.example.matching.service.matching.algorithm;

import com.example.matching.dto.matching.MatchingExecuteDTO.HardCondition;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class HardConditionCheckerTest {

    private final HardConditionChecker checker = new HardConditionChecker(new ObjectMapper());

    @Test
    void comparesFormattedNumbersWithTheSameUnitNumerically() {
        HardCondition condition = new HardCondition();
        condition.setOperator("gte");
        condition.setValue("2 years");

        assertThat(checker.evaluateCondition("10 years", condition)).isTrue();
    }
}
