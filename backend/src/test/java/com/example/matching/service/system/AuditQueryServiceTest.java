package com.example.matching.service.system;

import com.example.matching.entity.employee.EmpAbility;
import com.example.matching.entity.employee.EmpEmployee;
import com.example.matching.entity.employee.EmpResumeParse;
import com.example.matching.entity.harness.AiHarnessCheckLog;
import com.example.matching.entity.interview.InterviewAbilityObservation;
import com.example.matching.entity.system.PromptInvocationLog;
import com.example.matching.entity.workflow.PersonAbilityClaimGroup;
import com.example.matching.mapper.employee.EmpAbilityMapper;
import com.example.matching.mapper.employee.EmpEmployeeMapper;
import com.example.matching.mapper.employee.EmpResumeParseMapper;
import com.example.matching.mapper.harness.AiHarnessCheckLogMapper;
import com.example.matching.mapper.interview.InterviewAbilityObservationMapper;
import com.example.matching.mapper.system.PromptInvocationLogMapper;
import com.example.matching.mapper.workflow.PersonAbilityClaimGroupMapper;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
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

    /**
     * MyBatis-Plus 的 LambdaQueryWrapper 需要实体已登记表信息，
     * 否则会抛 "can not find lambda cache for this entity"（纯单测无 Spring 上下文）。
     */
    @BeforeAll
    static void initMybatisPlusLambdaCache() {
        var cfg = new com.baomidou.mybatisplus.core.MybatisConfiguration();
        var assistant = new org.apache.ibatis.builder.MapperBuilderAssistant(cfg, "");
        com.baomidou.mybatisplus.core.metadata.TableInfoHelper.initTableInfo(assistant, AiHarnessCheckLog.class);
        com.baomidou.mybatisplus.core.metadata.TableInfoHelper.initTableInfo(assistant, PromptInvocationLog.class);
        com.baomidou.mybatisplus.core.metadata.TableInfoHelper.initTableInfo(assistant, PersonAbilityClaimGroup.class);
        com.baomidou.mybatisplus.core.metadata.TableInfoHelper.initTableInfo(assistant, EmpResumeParse.class);
        com.baomidou.mybatisplus.core.metadata.TableInfoHelper.initTableInfo(assistant, InterviewAbilityObservation.class);
    }

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

    // ==================== Harness 分页 / 明细 / 计数 ====================

    @Test
    @DisplayName("pageHarness：全部过滤条件生效 → 透传 mapper 分页结果")
    void pageHarness_appliesAllFilters() {
        var page = new com.baomidou.mybatisplus.extension.plugins.pagination.Page<AiHarnessCheckLog>(1, 10);
        var result = new com.baomidou.mybatisplus.extension.plugins.pagination.Page<AiHarnessCheckLog>(1, 10);
        when(harnessMapper.selectPage(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any()))
                .thenReturn(result);

        var out = service().pageHarness(page, "PASS", "PERSON_ABILITY", "PENDING", "HIGH",
                "EMP_ABILITY", 1, Boolean.TRUE);

        assertThat(out).isSameAs(result);
        verify(harnessMapper).selectPage(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any());
    }

    @Test
    @DisplayName("pageHarness：多值 scenario 逗号分隔 → 走 in 分支；空条件不追加")
    void pageHarness_multiScenarioAndBlankFilters() {
        var page = new com.baomidou.mybatisplus.extension.plugins.pagination.Page<AiHarnessCheckLog>(1, 10);
        when(harnessMapper.selectPage(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any()))
                .thenReturn(page);

        var out = service().pageHarness(page, null, " A , B ", null, null, null, null, null);

        assertThat(out).isSameAs(page);
    }

    @Test
    @DisplayName("pageHarness：assessmentOnly=false → 走非人员治理过滤分支")
    void pageHarness_assessmentOnlyFalse() {
        var page = new com.baomidou.mybatisplus.extension.plugins.pagination.Page<AiHarnessCheckLog>(1, 10);
        when(harnessMapper.selectPage(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any()))
                .thenReturn(page);

        assertThat(service().pageHarness(page, null, null, null, null, null, null, Boolean.FALSE))
                .isSameAs(page);
    }

    @Test
    @DisplayName("getHarnessById：直接透传 mapper 结果")
    void getHarnessById_delegates() {
        AiHarnessCheckLog log = new AiHarnessCheckLog();
        log.setId(5L);
        when(harnessMapper.selectById(5L)).thenReturn(log);

        assertThat(service().getHarnessById(5L)).isSameAs(log);
    }

    @Test
    @DisplayName("hasPendingLevelDecision：null 入参 → false 且不查库")
    void hasPendingLevelDecision_nullId() {
        assertThat(service().hasPendingLevelDecision(null)).isFalse();
        verify(harnessMapper, never()).countPendingLevelDecision(any());
    }

    @Test
    @DisplayName("hasPendingLevelDecision：存在待审决策 → true；不存在 → false")
    void hasPendingLevelDecision_bothBranches() {
        when(harnessMapper.countPendingLevelDecision(7L)).thenReturn(2);
        assertThat(service().hasPendingLevelDecision(7L)).isTrue();

        when(harnessMapper.countPendingLevelDecision(8L)).thenReturn(0);
        assertThat(service().hasPendingLevelDecision(8L)).isFalse();
    }

    @Test
    @DisplayName("listAssessmentHarnessByReviewStatuses：null/空集合 → 直接返回空列表")
    void listAssessmentHarnessByReviewStatuses_emptyStatuses() {
        assertThat(service().listAssessmentHarnessByReviewStatuses(null)).isEmpty();
        assertThat(service().listAssessmentHarnessByReviewStatuses(Set.of())).isEmpty();
        verify(harnessMapper, never()).selectList(any());
    }

    @Test
    @DisplayName("listAssessmentHarnessByReviewStatuses：有状态 → 返回 mapper 结果")
    void listAssessmentHarnessByReviewStatuses_returnsLogs() {
        AiHarnessCheckLog log = new AiHarnessCheckLog();
        log.setId(1L);
        when(harnessMapper.selectList(any())).thenReturn(List.of(log));

        assertThat(service().listAssessmentHarnessByReviewStatuses(Set.of("PENDING")))
                .extracting(AiHarnessCheckLog::getId).containsExactly(1L);
    }

    @Test
    @DisplayName("countHarnessByStatus/ByRiskLevel/SelfEvidence/ByReviewStatus：透传计数并带评估过滤")
    void harnessCounts_delegateWithAssessmentFilter() {
        when(harnessMapper.selectCount(any())).thenReturn(3L);

        assertThat(service().countHarnessByStatus("PASS", Boolean.TRUE)).isEqualTo(3L);
        assertThat(service().countHarnessByRiskLevel("HIGH", null)).isEqualTo(3L);
        assertThat(service().countHarnessSelfEvidence(Boolean.FALSE)).isEqualTo(3L);
        assertThat(service().countHarnessByReviewStatus("PENDING", Boolean.TRUE)).isEqualTo(3L);
        verify(harnessMapper, times(4)).selectCount(any());
    }

    @Test
    @DisplayName("updateHarnessLog：透传 updateById")
    void updateHarnessLog_delegates() {
        AiHarnessCheckLog log = new AiHarnessCheckLog();
        log.setId(9L);
        service().updateHarnessLog(log);
        verify(harnessMapper).updateById(log);
    }

    // ==================== Prompt 查询与聚合 ====================

    @Test
    @DisplayName("pagePromptLogs：带 promptName+success 过滤 → 透传分页结果")
    void pagePromptLogs_withFilters() {
        var page = new com.baomidou.mybatisplus.extension.plugins.pagination.Page<PromptInvocationLog>(1, 5);
        when(promptInvocationLogMapper.selectPage(any(), any())).thenReturn(page);

        assertThat(service().pagePromptLogs(page, "match-prompt", Boolean.TRUE)).isSameAs(page);
    }

    @Test
    @DisplayName("pagePromptLogs：promptName 空白且 success 为 null → 不追加过滤")
    void pagePromptLogs_blankFilters() {
        var page = new com.baomidou.mybatisplus.extension.plugins.pagination.Page<PromptInvocationLog>(1, 5);
        when(promptInvocationLogMapper.selectPage(any(), any())).thenReturn(page);

        assertThat(service().pagePromptLogs(page, "  ", null)).isSameAs(page);
    }

    @Test
    @DisplayName("getPromptLogById：透传 mapper 结果")
    void getPromptLogById_delegates() {
        PromptInvocationLog log = new PromptInvocationLog();
        log.setId(3L);
        when(promptInvocationLogMapper.selectById(3L)).thenReturn(log);

        assertThat(service().getPromptLogById(3L)).isSameAs(log);
    }

    @Test
    @DisplayName("listPromptLogsSince：带 promptName → 返回列表；不带 → 返回全量")
    void listPromptLogsSince_withAndWithoutName() {
        PromptInvocationLog log = new PromptInvocationLog();
        log.setId(1L);
        when(promptInvocationLogMapper.selectList(any())).thenReturn(List.of(log));

        var since = java.time.LocalDateTime.now().minusDays(1);
        assertThat(service().listPromptLogsSince(since, "p")).hasSize(1);
        assertThat(service().listPromptLogsSince(since, null)).hasSize(1);
    }

    @Test
    @DisplayName("listPromptLogDtosSince：实体映射为 DTO，字段一一对应")
    void listPromptLogDtosSince_mapsFields() {
        PromptInvocationLog log = new PromptInvocationLog();
        log.setPromptName("match");
        log.setPromptVersion("v2");
        log.setSuccess(Boolean.FALSE);
        log.setLatencyMs(120L);
        log.setFeedbackScore(4);
        when(promptInvocationLogMapper.selectList(any())).thenReturn(List.of(log));

        var dtos = service().listPromptLogDtosSince(java.time.LocalDateTime.now().minusHours(1), "match");

        assertThat(dtos).singleElement().satisfies(dto -> {
            assertThat(dto.promptName()).isEqualTo("match");
            assertThat(dto.promptVersion()).isEqualTo("v2");
            assertThat(dto.success()).isFalse();
            assertThat(dto.latencyMs()).isEqualTo(120L);
            assertThat(dto.feedbackScore()).isEqualTo(4);
        });
    }

    @Test
    @DisplayName("promptLatencyTrend/promptBreakdown/promptScenarioBreakdown：透传聚合结果")
    void promptAggregations_delegate() {
        var rows = List.of(java.util.Map.<String, Object>of("hour", 9, "count", 3L));
        var since = java.time.LocalDateTime.now().minusDays(7);
        when(promptInvocationLogMapper.selectLatencyTrend(since)).thenReturn(rows);
        when(promptInvocationLogMapper.selectPromptBreakdown(since)).thenReturn(rows);
        when(promptInvocationLogMapper.selectScenarioBreakdown(since)).thenReturn(rows);

        assertThat(service().promptLatencyTrend(since)).isSameAs(rows);
        assertThat(service().promptBreakdown(since)).isSameAs(rows);
        assertThat(service().promptScenarioBreakdown(since)).isSameAs(rows);
    }

    // ==================== resolveHarnessPersons 归属解析 ====================

    @Test
    @DisplayName("resolveHarnessPersons：null/空日志 → 返回空 map")
    void resolveHarnessPersons_emptyLogs() {
        assertThat(service().resolveHarnessPersons(null)).isEmpty();
        assertThat(service().resolveHarnessPersons(List.of())).isEmpty();
    }

    @Test
    @DisplayName("resolveHarnessPersons：PERSON_ABILITY 带 EMP_ABILITY 业务目标 → 直接取 empId")
    void resolveHarnessPersons_personAbilityWithBusinessTarget() {
        AiHarnessCheckLog log = harnessLog(1L, "PERSON_ABILITY");
        log.setBusinessTargetType("EMP_ABILITY");
        log.setBusinessTargetId(50L);
        EmpEmployee emp = employee(50L, "张三", "E050");
        when(empEmployeeMapper.selectBatchIds(anyCollection())).thenReturn(List.of(emp));

        var result = service().resolveHarnessPersons(List.of(log));

        assertThat(result.get(1L)).isEqualTo(new AuditQueryService.HarnessPerson(50L, "张三", "E050"));
    }

    @Test
    @DisplayName("resolveHarnessPersons：PERSON_ABILITY 无业务目标 → 回退 sourceRefId")
    void resolveHarnessPersons_personAbilityFallsBackToSourceRef() {
        AiHarnessCheckLog log = harnessLog(2L, "PERSON_ABILITY");
        log.setSourceRefId(60L);
        when(empEmployeeMapper.selectBatchIds(anyCollection())).thenReturn(List.of(employee(60L, "李四", "E060")));

        var result = service().resolveHarnessPersons(List.of(log));

        assertThat(result.get(2L)).isEqualTo(new AuditQueryService.HarnessPerson(60L, "李四", "E060"));
    }

    @Test
    @DisplayName("resolveHarnessPersons：聚合场景 → 通过 claim_group 反查员工")
    void resolveHarnessPersons_aggregateViaClaimGroup() {
        AiHarnessCheckLog log = harnessLog(3L, "PERSON_ABILITY_AGGREGATE");
        log.setSourceRefId(700L);
        PersonAbilityClaimGroup group = new PersonAbilityClaimGroup();
        group.setId(700L);
        group.setEmpId(70L);
        when(claimGroupMapper.selectList(any())).thenReturn(List.of(group));
        when(empEmployeeMapper.selectBatchIds(anyCollection())).thenReturn(List.of(employee(70L, "王五", "E070")));

        var result = service().resolveHarnessPersons(List.of(log));

        assertThat(result.get(3L)).isEqualTo(new AuditQueryService.HarnessPerson(70L, "王五", "E070"));
    }

    @Test
    @DisplayName("resolveHarnessPersons：AGGREGATE_HARNESS 旧场景名 → 同样按 claim_group 归属")
    void resolveHarnessPersons_legacyAggregateScenario() {
        AiHarnessCheckLog log = harnessLog(4L, "AGGREGATE_HARNESS");
        log.setSourceRefId(701L);
        PersonAbilityClaimGroup group = new PersonAbilityClaimGroup();
        group.setId(701L);
        group.setEmpId(71L);
        when(claimGroupMapper.selectList(any())).thenReturn(List.of(group));
        when(empEmployeeMapper.selectBatchIds(anyCollection())).thenReturn(List.of(employee(71L, "赵六", "E071")));

        var result = service().resolveHarnessPersons(List.of(log));

        assertThat(result.get(4L)).isEqualTo(new AuditQueryService.HarnessPerson(71L, "赵六", "E071"));
    }

    @Test
    @DisplayName("resolveHarnessPersons：RESUME_PARSE 场景 → 通过简历解析记录反查员工")
    void resolveHarnessPersons_resumeParseScenario() {
        AiHarnessCheckLog log = harnessLog(5L, "RESUME_PARSE");
        log.setSourceRefId(800L);
        EmpResumeParse parse = new EmpResumeParse();
        parse.setId(800L);
        parse.setEmpId(80L);
        when(empResumeParseMapper.selectList(any())).thenReturn(List.of(parse));
        when(empEmployeeMapper.selectBatchIds(anyCollection())).thenReturn(List.of(employee(80L, "钱七", "E080")));

        var result = service().resolveHarnessPersons(List.of(log));

        assertThat(result.get(5L)).isEqualTo(new AuditQueryService.HarnessPerson(80L, "钱七", "E080"));
    }

    @Test
    @DisplayName("resolveHarnessPersons：非人员场景且 sourceRefs 无简历引用 → 不产生归属")
    void resolveHarnessPersons_nonPersonScenario() {
        AiHarnessCheckLog log = harnessLog(6L, "ABILITY_TAG_GOVERNANCE");
        log.setSourceRefId(1L);

        assertThat(service().resolveHarnessPersons(List.of(log))).isEmpty();
    }

    @Test
    @DisplayName("resolveHarnessPersons：AI 面试观察场景 → 通过 sessionId 反查员工")
    void resolveHarnessPersons_interviewObservation() {
        AiHarnessCheckLog log = harnessLog(7L, "AI_INTERVIEW_OBSERVATION");
        log.setSourceRefId(900L);
        InterviewAbilityObservation obs = new InterviewAbilityObservation();
        obs.setSessionId(900L);
        obs.setEmpId(90L);
        when(observationMapper.selectList(any())).thenReturn(List.of(obs));
        when(empEmployeeMapper.selectBatchIds(anyCollection())).thenReturn(List.of(employee(90L, "周九", "E090")));

        var result = service().resolveHarnessPersons(List.of(log));

        assertThat(result.get(7L)).isEqualTo(new AuditQueryService.HarnessPerson(90L, "周九", "E090"));
    }

    @Test
    @DisplayName("resolveHarnessPersons：员工记录查不到 → 不写入结果")
    void resolveHarnessPersons_employeeMissing() {
        AiHarnessCheckLog log = harnessLog(8L, "PERSON_ABILITY");
        log.setBusinessTargetType("EMP_ABILITY");
        log.setBusinessTargetId(999L);
        when(empEmployeeMapper.selectBatchIds(anyCollection())).thenReturn(List.of());

        assertThat(service().resolveHarnessPersons(List.of(log))).isEmpty();
    }

    private static AiHarnessCheckLog harnessLog(Long id, String scenario) {
        AiHarnessCheckLog log = new AiHarnessCheckLog();
        log.setId(id);
        log.setScenario(scenario);
        return log;
    }

    private static EmpEmployee employee(Long id, String name, String code) {
        EmpEmployee emp = new EmpEmployee();
        emp.setId(id);
        emp.setRealName(name);
        emp.setEmpCode(code);
        return emp;
    }

    private AuditQueryService service() {
        return new AuditQueryService(harnessMapper, promptInvocationLogMapper,
                claimGroupMapper, empResumeParseMapper, observationMapper, empEmployeeMapper, empAbilityMapper);
    }
}