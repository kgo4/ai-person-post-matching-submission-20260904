package com.example.matching.service.matching.evaluation;

import com.example.matching.dto.matching.MatchingExecuteDTO.HardCondition;
import com.example.matching.entity.employee.EmpEmployee;
import com.example.matching.port.talent.TalentQueryPort;
import com.example.matching.service.matching.MatchingAlgorithmService;
import com.example.matching.service.post.PostHardConditionRuleService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class HardConditionEvaluatorTest {

    private final MatchingAlgorithmService matchingAlgorithmService = mock(MatchingAlgorithmService.class);
    private final PostHardConditionRuleService postHardConditionRuleService = mock(PostHardConditionRuleService.class);
    private final TalentQueryPort talentQueryPort = mock(TalentQueryPort.class);
    private final HardConditionEvaluator evaluator = new HardConditionEvaluator(
            matchingAlgorithmService, postHardConditionRuleService, talentQueryPort, new ObjectMapper());

    @Test
    void returnsPassWithoutConfiguredConditions() {
        HardConditionEvaluator.HardConditionEvalResult result = evaluator.evaluate(employee(), List.of());

        assertThat(result.getStatus()).isEqualTo(HardConditionEvaluator.HardConditionStatus.PASS);
        assertThat(result.getScore()).isEqualByComparingTo("100");
    }

    @Test
    void returnsPassWhenMatchingServicePassesAndLoadsResumeFallback() {
        when(talentQueryPort.findLatestCompletedResumeParse(7L)).thenReturn(
                new TalentQueryPort.ResumeParseDetailDTO(1L, 7L, "", "{\"basicInfo\":{\"city\":\"Hefei\"}}"));
        when(matchingAlgorithmService.checkHardConditions(any(), any(), eq(Map.of("city", "Hefei"))))
                .thenReturn(result(true, List.of()));

        HardConditionEvaluator.HardConditionEvalResult result = evaluator.evaluate(employee(), List.of(condition()));

        assertThat(result.getStatus()).isEqualTo(HardConditionEvaluator.HardConditionStatus.PASS);
        verify(talentQueryPort).findLatestCompletedResumeParse(7L);
    }

    @Test
    void returnsRiskWhenARequiredValueIsMissing() {
        when(talentQueryPort.findLatestCompletedResumeParse(7L)).thenReturn(null);
        when(matchingAlgorithmService.checkHardConditions(any(), any(), eq(Map.of())))
                .thenReturn(result(false, List.of(detail(false, "未填写"))));

        HardConditionEvaluator.HardConditionEvalResult result = evaluator.evaluate(employee(), List.of(condition()));

        assertThat(result.getStatus()).isEqualTo(HardConditionEvaluator.HardConditionStatus.RISK);
        assertThat(result.getScore()).isEqualByComparingTo("60");
        assertThat(evaluator.toScore(result)).isEqualByComparingTo(new BigDecimal("60"));
    }

    @Test
    void returnsFailWhenKnownValueDoesNotMeetCondition() {
        when(talentQueryPort.findLatestCompletedResumeParse(7L)).thenThrow(new IllegalStateException("temporary"));
        when(matchingAlgorithmService.checkHardConditions(any(), any(), eq(Map.of())))
                .thenReturn(result(false, List.of(detail(false, "员工档案"))));

        HardConditionEvaluator.HardConditionEvalResult result = evaluator.evaluate(employee(), List.of(condition()));

        assertThat(result.getStatus()).isEqualTo(HardConditionEvaluator.HardConditionStatus.FAIL);
        assertThat(result.getScore()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(evaluator.toScore(result)).isZero();
    }

    @Test
    void resolvesRulesFromPostBeforeEvaluation() {
        when(postHardConditionRuleService.toHardConditions(91L)).thenReturn(List.of(condition()));
        when(talentQueryPort.findLatestCompletedResumeParse(7L)).thenReturn(null);
        when(matchingAlgorithmService.checkHardConditions(any(), any(), eq(Map.of())))
                .thenReturn(result(true, List.of()));

        assertThat(evaluator.evaluate(employee(), 91L).getStatus())
                .isEqualTo(HardConditionEvaluator.HardConditionStatus.PASS);

        verify(postHardConditionRuleService).toHardConditions(91L);
    }

    private EmpEmployee employee() {
        EmpEmployee employee = new EmpEmployee();
        employee.setId(7L);
        employee.setRealName("Test");
        return employee;
    }

    private HardCondition condition() {
        HardCondition condition = new HardCondition();
        condition.setField("city");
        condition.setOperator("equals");
        condition.setValue("Hefei");
        return condition;
    }

    private MatchingAlgorithmService.HardConditionResult result(
            boolean passed, List<MatchingAlgorithmService.ConditionDetail> details) {
        MatchingAlgorithmService.HardConditionResult result = new MatchingAlgorithmService.HardConditionResult();
        result.setPassed(passed);
        result.setDetails(details);
        return result;
    }

    private MatchingAlgorithmService.ConditionDetail detail(boolean passed, String source) {
        MatchingAlgorithmService.ConditionDetail detail = new MatchingAlgorithmService.ConditionDetail();
        detail.setPassed(passed);
        detail.setSource(source);
        return detail;
    }
}
