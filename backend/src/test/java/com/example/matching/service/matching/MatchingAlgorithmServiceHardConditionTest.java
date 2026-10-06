package com.example.matching.service.matching;

import com.example.matching.dto.matching.MatchingEmployeeProfile;
import com.example.matching.dto.matching.MatchingExecuteDTO.HardCondition;
import com.example.matching.service.matching.impl.MatchingAlgorithmServiceImpl;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class MatchingAlgorithmServiceHardConditionTest {

    private final MatchingAlgorithmService service = new MatchingAlgorithmServiceImpl(null, null, null, new ObjectMapper());

    private static MatchingEmployeeProfile employeeWithExtendFields(String extendFields) {
        return new MatchingEmployeeProfile(null, null, null, null, null, extendFields, List.of());
    }

    @Test
    void evaluatesDynamicEmployeeExtendFieldRule() {
        MatchingEmployeeProfile employee = employeeWithExtendFields("""
                {"skillDirection":"Java Backend","projectCount":3}
                """);

        HardCondition direction = new HardCondition();
        direction.setField("skillDirection");
        direction.setOperator("contains");
        direction.setValue("Java");
        direction.setLabel("Skill direction contains Java");

        HardCondition projectCount = new HardCondition();
        projectCount.setField("projectCount");
        projectCount.setOperator("gte");
        projectCount.setValue("2");
        projectCount.setLabel("Project count at least 2");

        MatchingAlgorithmService.HardConditionResult result =
                service.checkHardConditions(employee, List.of(direction, projectCount));

        assertThat(result.isPassed()).isTrue();
        assertThat(result.getDetails()).hasSize(2);
        assertThat(result.getDetails()).allMatch(MatchingAlgorithmService.ConditionDetail::isPassed);
    }

    @Test
    void supportsCaseInsensitiveInOperator() {
        MatchingEmployeeProfile employee = employeeWithExtendFields("""
                {"education":"bachelor"}
                """);

        HardCondition condition = new HardCondition();
        condition.setField("education");
        condition.setOperator("in");
        condition.setValue("master,bachelor");

        MatchingAlgorithmService.HardConditionResult result =
                service.checkHardConditions(employee, List.of(condition));

        assertThat(result.isPassed()).isTrue();
    }

    @Test
    void comparesRankFieldByConfiguredRankMap() {
        MatchingEmployeeProfile employee = employeeWithExtendFields("""
                {"education":"本科"}
                """);

        HardCondition condition = new HardCondition();
        condition.setField("education");
        condition.setOperator("gte");
        condition.setValue("大专");
        condition.setFieldType("rank");
        condition.setValueRankJson("{\"大专\":2,\"本科\":3,\"硕士\":4}");

        MatchingAlgorithmService.HardConditionResult result =
                service.checkHardConditions(employee, List.of(condition));

        assertThat(result.isPassed()).isTrue();
        assertThat(result.getDetails().get(0).isPassed()).isTrue();
    }

    @Test
    void rejectsRankFieldBelowConfiguredRank() {
        MatchingEmployeeProfile employee = employeeWithExtendFields("""
                {"education":"本科"}
                """);

        HardCondition condition = new HardCondition();
        condition.setField("education");
        condition.setOperator("gte");
        condition.setValue("硕士");
        condition.setFieldType("rank");
        condition.setValueRankJson("{\"大专\":2,\"本科\":3,\"硕士\":4}");

        MatchingAlgorithmService.HardConditionResult result =
                service.checkHardConditions(employee, List.of(condition));

        assertThat(result.isPassed()).isFalse();
        assertThat(result.getDetails().get(0).isPassed()).isFalse();
    }
}
