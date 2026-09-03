package com.example.matching.service.system;

import com.example.matching.entity.employee.EmpAbility;
import com.example.matching.entity.employee.EmpEmployee;
import com.example.matching.entity.harness.AiHarnessCheckLog;
import com.example.matching.mapper.employee.EmpAbilityMapper;
import com.example.matching.mapper.employee.EmpEmployeeMapper;
import com.example.matching.mapper.employee.EmpResumeParseMapper;
import com.example.matching.mapper.harness.AiHarnessCheckLogMapper;
import com.example.matching.mapper.interview.InterviewAbilityObservationMapper;
import com.example.matching.mapper.system.PromptInvocationLogMapper;
import com.example.matching.mapper.workflow.PersonAbilityClaimGroupMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuditQueryServiceTest {

    @Mock private AiHarnessCheckLogMapper harnessMapper;
    @Mock private PromptInvocationLogMapper promptInvocationLogMapper;
    @Mock private PersonAbilityClaimGroupMapper claimGroupMapper;
    @Mock private EmpResumeParseMapper empResumeParseMapper;
    @Mock private InterviewAbilityObservationMapper observationMapper;
    @Mock private EmpEmployeeMapper empEmployeeMapper;
    @Mock private EmpAbilityMapper empAbilityMapper;

    @Test
    void resolvesEvidenceBackfillTargetThroughEmployeeAbility() {
        AiHarnessCheckLog log = new AiHarnessCheckLog();
        log.setId(1313L);
        log.setScenario("PERSON_ABILITY");
        log.setClaimType("EMP_ABILITY");
        log.setSourceRefs("[\"fact:EMP_ABILITY:99\"]");
        log.setBusinessTargetType("EMP_ABILITY");
        log.setBusinessTargetId(99L);

        EmpAbility ability = new EmpAbility();
        ability.setId(99L);
        ability.setEmpId(7L);
        EmpEmployee employee = new EmpEmployee();
        employee.setId(7L);
        employee.setRealName("Alice");
        employee.setEmpCode("E007");
        when(empAbilityMapper.selectBatchIds(anyCollection())).thenReturn(List.of(ability));
        when(empEmployeeMapper.selectBatchIds(anyCollection())).thenReturn(List.of(employee));

        var persons = service().resolveHarnessPersons(List.of(log));

        assertThat(persons.get(1313L)).isEqualTo(new AuditQueryService.HarnessPerson(7L, "Alice", "E007"));
    }

    @Test
    void distinguishesPersonnelScenariosFromTagAndDataGovernanceScenarios() {
        assertThat(AuditQueryService.isPersonnelGovernanceScenario("EMP_ABILITY_RESUME_PARSE")).isTrue();
        assertThat(AuditQueryService.isPersonnelGovernanceScenario("AI_INTERVIEW_OBSERVATION")).isTrue();
        assertThat(AuditQueryService.isPersonnelGovernanceScenario("PMS_ANALYSIS")).isTrue();
        assertThat(AuditQueryService.isPersonnelGovernanceScenario("POST_ABILITY_JD_EXTRACT")).isFalse();
        assertThat(AuditQueryService.isPersonnelGovernanceScenario("ABILITY_TAG_GOVERNANCE")).isFalse();
    }

    @Test
    void leavesAutoPassedAggregateLogAsAuditOnly() {
        AiHarnessCheckLog log = new AiHarnessCheckLog();
        log.setId(3133L);
        log.setScenario("PERSON_ABILITY_AGGREGATE");
        log.setReviewStatus("AUTO_PASSED");
        when(harnessMapper.selectList(org.mockito.ArgumentMatchers.any())).thenReturn(List.of(log));

        var result = service().listAssessmentHarnessByReviewStatuses(Set.of("PENDING"));

        assertThat(result).singleElement().extracting(AiHarnessCheckLog::getReviewStatus)
                .isEqualTo("AUTO_PASSED");
    }

    private AuditQueryService service() {
        return new AuditQueryService(harnessMapper, promptInvocationLogMapper,
                claimGroupMapper, empResumeParseMapper, observationMapper, empEmployeeMapper, empAbilityMapper);
    }
}
