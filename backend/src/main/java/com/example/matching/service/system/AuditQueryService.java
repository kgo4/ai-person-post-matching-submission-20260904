package com.example.matching.service.system;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.example.matching.entity.employee.EmpEmployee;
import com.example.matching.entity.employee.EmpAbility;
import com.example.matching.entity.employee.EmpResumeParse;
import com.example.matching.entity.harness.AiHarnessCheckLog;
import com.example.matching.entity.interview.InterviewAbilityObservation;
import com.example.matching.entity.system.PromptInvocationLog;
import com.example.matching.entity.workflow.PersonAbilityClaimGroup;
import com.example.matching.mapper.employee.EmpEmployeeMapper;
import com.example.matching.mapper.employee.EmpAbilityMapper;
import com.example.matching.mapper.employee.EmpResumeParseMapper;
import com.example.matching.mapper.harness.AiHarnessCheckLogMapper;
import com.example.matching.mapper.interview.InterviewAbilityObservationMapper;
import com.example.matching.mapper.system.PromptInvocationLogMapper;
import com.example.matching.mapper.workflow.PersonAbilityClaimGroupMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 审计查询服务 — 统一处理 Harness 与 Prompt 审计日志查询。
 * Controller 不应直接注入 Mapper。
 */
@Service
@RequiredArgsConstructor
public class AuditQueryService {

    private static final Set<String> PERSONNEL_GOVERNANCE_SCENARIOS = Set.of(
            "PERSON_ABILITY",
            "PERSON_ABILITY_AGGREGATE",
            "PERSON_ABILITY_EXTRACTION",
            "PMS_ANALYSIS",
            "AI_INTERVIEW_OBSERVATION",
            "RESUME_PARSE",
            "EMP_ABILITY_RESUME_PARSE",
            "EMP_ABILITY_AI_TEST",
            "EMP_ABILITY_VIDEO_INTERVIEW",
            "EMP_ABILITY_PMS_ANALYSIS",
            "AI_TEST",
            "AI_TEST_EVALUATION",
            "AI_TEST_QUESTION",
            "VIDEO_INTERVIEW",
            "AI_INTERVIEW",
            "INTERVIEW_OBSERVATION",
            "INTERVIEW_ANSWER_QUALITY",
            "INTERVIEW_FOLLOW_UP",
            "PERSON_ABILITY_CLAIM",
            "PERSON_ABILITY_PROFILE",
            "PERSON_ABILITY_LEVEL_CONFIRMATION",
            "EMPLOYEE_ABILITY_EXTRACTION",
            // 岗位演化的 Harness 结果在岗位演化详情页统一处理，不进入通用审核列表
            "POST_EVOLUTION"
    );

    private final AiHarnessCheckLogMapper harnessMapper;
    private final PromptInvocationLogMapper promptInvocationLogMapper;
    private final PersonAbilityClaimGroupMapper claimGroupMapper;
    private final EmpResumeParseMapper empResumeParseMapper;
    private final InterviewAbilityObservationMapper observationMapper;
    private final EmpEmployeeMapper empEmployeeMapper;
    private final EmpAbilityMapper empAbilityMapper;

    // --- Harness ---
    public IPage<AiHarnessCheckLog> pageHarness(Page<AiHarnessCheckLog> page, String decision, String scenario,
                                                String reviewStatus, String riskLevel, String claimType,
                                                Integer isSelfEvidence, Boolean assessmentOnly) {
        var w = new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<AiHarnessCheckLog>();
        if (hasText(decision)) w.eq(AiHarnessCheckLog::getDecision, decision);
        // scenario 支持逗号分隔多值（用于按「人员/岗位」维度批量过滤）
        if (hasText(scenario)) {
            String[] scenarios = scenario.split(",");
            if (scenarios.length == 1) {
                w.eq(AiHarnessCheckLog::getScenario, scenarios[0]);
            } else {
                w.in(AiHarnessCheckLog::getScenario, (Object[]) scenarios);
            }
        }
        if (hasText(reviewStatus)) w.eq(AiHarnessCheckLog::getReviewStatus, reviewStatus);
        if (hasText(riskLevel)) w.eq(AiHarnessCheckLog::getRiskLevel, riskLevel);
        if (hasText(claimType)) w.eq(AiHarnessCheckLog::getClaimType, claimType);
        if (isSelfEvidence != null) w.eq(AiHarnessCheckLog::getIsSelfEvidence, isSelfEvidence);
        applyAssessmentFilter(w, assessmentOnly);
        w.orderByDesc(AiHarnessCheckLog::getCreatedTime);
        return harnessMapper.selectPage(page, w);
    }

    public AiHarnessCheckLog getHarnessById(Long id) {
        return harnessMapper.selectById(id);
    }

    /**
     * The aggregate log may be AUTO_PASSED while its level decision is still
     * waiting for a person-level manual review.
     */
    public boolean hasPendingLevelDecision(Long harnessLogId) {
        return harnessLogId != null && harnessMapper.countPendingLevelDecision(harnessLogId) > 0;
    }

    /**
     * Loads a complete personnel-assessment queue before employee grouping.
     * Record pagination is deliberately avoided because it can split one
     * person's assessment across pages.
     */
    public List<AiHarnessCheckLog> listAssessmentHarnessByReviewStatuses(Set<String> reviewStatuses) {
        if (reviewStatuses == null || reviewStatuses.isEmpty()) {
            return List.of();
        }
        var wrapper = new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<AiHarnessCheckLog>();
        wrapper.in(AiHarnessCheckLog::getReviewStatus, reviewStatuses);
        wrapper.orderByDesc(AiHarnessCheckLog::getReviewedTime)
                .orderByDesc(AiHarnessCheckLog::getCreatedTime);
        applyAssessmentFilter(wrapper, true);
        List<AiHarnessCheckLog> logs = harnessMapper.selectList(wrapper);
        return logs;
    }

    /**
     * 归属人员信息(非人员场景为 null)。
     */
    public record HarnessPerson(Long empId, String empName, String empCode) {
    }

    /**
     * 批量解析 Harness 日志的归属人员(logId -&gt; 人员)。
     * 各人员场景的 sourceRefId 语义：
     * PERSON_ABILITY_AGGREGATE -&gt; claim_group.id；PERSON_ABILITY -&gt; empId；
     * RESUME_PARSE -&gt; emp_resume_parse.id；AI_INTERVIEW_OBSERVATION -&gt; 面试 sessionId。
     * 其余场景(岗位能力模型、JD 能力提取等)无人员归属，返回空 map。
     */
    public Map<Long, HarnessPerson> resolveHarnessPersons(List<AiHarnessCheckLog> logs) {
        Map<Long, HarnessPerson> result = new HashMap<>();
        if (logs == null || logs.isEmpty()) {
            return result;
        }

        // logId -&gt; empId（第一步：按场景解析）
        Map<Long, Long> logIdToEmpId = new LinkedHashMap<>();
        List<Long> aggregateGroupIds = new ArrayList<>();
        List<Long> resumeParseIds = new ArrayList<>();
        List<Long> observationSessionIds = new ArrayList<>();
        List<Long> employeeAbilityIds = new ArrayList<>();

        for (AiHarnessCheckLog log : logs) {
            String scenario = log.getScenario();
            Long ref = log.getSourceRefId();

            // 聚合审核记录通常没有直接 empId，但会保留简历解析证据；
            // 收集该引用，供后续从简历解析记录反查员工，避免落入“待关联人员”。
            if (log.getSourceRefs() != null) {
                java.util.regex.Matcher resumeRef = java.util.regex.Pattern
                        .compile("source:RESUME_PARSE:(\\d+)")
                        .matcher(log.getSourceRefs());
                if (resumeRef.find()) {
                    resumeParseIds.add(Long.valueOf(resumeRef.group(1)));
                }
            }

            // Evidence backfill verifies an existing emp_ability fact. Its
            // businessTargetId is an emp_ability.id, not an empId.
            if (isEmployeeAbilityFactLog(log) && log.getBusinessTargetId() != null) {
                employeeAbilityIds.add(log.getBusinessTargetId());
                continue;
            }

            // 优先从 businessTargetId 取 empId（新治理准入流程在此字段存 empId）
            // 适用于 PERSON_ABILITY / EMP_ABILITY 等人员场景
            if (isPersonScenario(scenario) && log.getBusinessTargetId() != null
                    && "EMP_ABILITY".equals(log.getBusinessTargetType())) {
                logIdToEmpId.put(log.getId(), log.getBusinessTargetId());
                continue;
            }

            // 聚合审核历史中曾存在旧场景名 AGGREGATE_HARNESS；其 sourceRefId
            // 仍然稳定指向 person_ability_claim_group.id，必须按业务语义解析。
            boolean aggregateLog = "PERSON_ABILITY_AGGREGATE".equals(scenario)
                    || "AGGREGATE_HARNESS".equals(scenario)
                    || "AGGREGATE_HARNESS".equals(log.getSourceType());
            if (aggregateLog && ref != null) {
                aggregateGroupIds.add(ref);
                continue;
            }
            if (scenario == null || (ref == null && !"RESUME_PARSE".equals(scenario))) {
                continue;
            }
            switch (scenario) {
                case "PERSON_ABILITY" -> {
                    // 向后兼容：旧数据 businessTargetId 可能为空，此时回退尝试 sourceRefId
                    if (log.getBusinessTargetId() != null) {
                        logIdToEmpId.put(log.getId(), log.getBusinessTargetId());
                    } else {
                        logIdToEmpId.put(log.getId(), ref);
                    }
                }
                case "PERSON_ABILITY_AGGREGATE" -> aggregateGroupIds.add(ref);
                case "RESUME_PARSE" -> {
                    Long resumeParseId = resolveResumeParseId(log);
                    if (resumeParseId != null) resumeParseIds.add(resumeParseId);
                }
                case "AI_INTERVIEW_OBSERVATION" -> observationSessionIds.add(ref);
                default -> { /* 非人员场景 */ }
            }
        }

        fillEmpIdsFromClaimGroups(logs, aggregateGroupIds, logIdToEmpId);
        fillEmpIdsFromResumeParses(logs, resumeParseIds, logIdToEmpId);
        fillEmpIdsFromInterviewSessions(logs, observationSessionIds, logIdToEmpId);
        fillEmpIdsFromEmployeeAbilities(logs, employeeAbilityIds, logIdToEmpId);

        if (logIdToEmpId.isEmpty()) {
            return result;
        }

        // 第二步：批量查询员工信息
        Set<Long> empIds = new HashSet<>(logIdToEmpId.values());
        Map<Long, EmpEmployee> empMap = empEmployeeMapper.selectBatchIds(empIds).stream()
                .collect(Collectors.toMap(EmpEmployee::getId, e -> e, (a, b) -> a));
        for (Map.Entry<Long, Long> entry : logIdToEmpId.entrySet()) {
            EmpEmployee emp = empMap.get(entry.getValue());
            if (emp != null) {
                result.put(entry.getKey(), new HarnessPerson(emp.getId(), emp.getRealName(), emp.getEmpCode()));
            }
        }
        return result;
    }

    private void fillEmpIdsFromClaimGroups(List<AiHarnessCheckLog> logs, List<Long> groupIds,
                                           Map<Long, Long> logIdToEmpId) {
        if (groupIds.isEmpty()) {
            return;
        }
        Map<Long, Long> groupEmp = claimGroupMapper.selectList(
                        new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<PersonAbilityClaimGroup>()
                                .in(PersonAbilityClaimGroup::getId, groupIds))
                .stream()
                .collect(Collectors.toMap(PersonAbilityClaimGroup::getId, PersonAbilityClaimGroup::getEmpId, (a, b) -> a));
        for (AiHarnessCheckLog log : logs) {
            if (("PERSON_ABILITY_AGGREGATE".equals(log.getScenario())
                    || "AGGREGATE_HARNESS".equals(log.getScenario())
                    || "AGGREGATE_HARNESS".equals(log.getSourceType()))
                    && log.getSourceRefId() != null) {
                Long empId = groupEmp.get(log.getSourceRefId());
                if (empId != null) {
                    logIdToEmpId.put(log.getId(), empId);
                }
            }
        }
    }

    private void fillEmpIdsFromResumeParses(List<AiHarnessCheckLog> logs, List<Long> parseIds,
                                            Map<Long, Long> logIdToEmpId) {
        if (parseIds.isEmpty()) {
            return;
        }
        Map<Long, Long> parseEmp = empResumeParseMapper.selectList(
                        new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<EmpResumeParse>()
                                .in(EmpResumeParse::getId, parseIds))
                .stream()
                .collect(Collectors.toMap(EmpResumeParse::getId, EmpResumeParse::getEmpId, (a, b) -> a));
        for (AiHarnessCheckLog log : logs) {
            if ("RESUME_PARSE".equals(log.getScenario())) {
                Long resumeParseId = resolveResumeParseId(log);
                Long empId = resumeParseId == null ? null : parseEmp.get(resumeParseId);
                if (empId != null) {
                    logIdToEmpId.put(log.getId(), empId);
                }
            } else if (logIdToEmpId.get(log.getId()) == null && log.getSourceRefs() != null) {
                java.util.regex.Matcher matcher = java.util.regex.Pattern
                        .compile("source:RESUME_PARSE:(\\d+)")
                        .matcher(log.getSourceRefs());
                if (matcher.find()) {
                    Long empId = parseEmp.get(Long.valueOf(matcher.group(1)));
                    if (empId != null) logIdToEmpId.put(log.getId(), empId);
                }
            }
            // 某些历史聚合日志只保留 sourceRefs，没有标准 scenario；从其中的
            // RESUME_PARSE 引用反查员工后，仍按同一条审核记录归属到该员工。
        }
    }

    private void fillEmpIdsFromInterviewSessions(List<AiHarnessCheckLog> logs, List<Long> sessionIds,
                                                 Map<Long, Long> logIdToEmpId) {
        if (sessionIds.isEmpty()) {
            return;
        }
        Map<Long, Long> sessionEmp = observationMapper.selectList(
                        new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<InterviewAbilityObservation>()
                                .in(InterviewAbilityObservation::getSessionId, sessionIds)
                                .select(InterviewAbilityObservation::getSessionId, InterviewAbilityObservation::getEmpId))
                .stream()
                .collect(Collectors.toMap(InterviewAbilityObservation::getSessionId, InterviewAbilityObservation::getEmpId, (a, b) -> a));
        for (AiHarnessCheckLog log : logs) {
            if ("AI_INTERVIEW_OBSERVATION".equals(log.getScenario()) && log.getSourceRefId() != null) {
                Long empId = sessionEmp.get(log.getSourceRefId());
                if (empId != null) {
                    logIdToEmpId.put(log.getId(), empId);
                }
            }
        }
    }

    private void fillEmpIdsFromEmployeeAbilities(List<AiHarnessCheckLog> logs, List<Long> abilityIds,
                                                  Map<Long, Long> logIdToEmpId) {
        if (abilityIds.isEmpty()) {
            return;
        }
        Map<Long, Long> abilityEmp = empAbilityMapper.selectBatchIds(abilityIds).stream()
                .collect(Collectors.toMap(EmpAbility::getId, EmpAbility::getEmpId, (a, b) -> a));
        for (AiHarnessCheckLog log : logs) {
            if (isEmployeeAbilityFactLog(log) && log.getBusinessTargetId() != null) {
                Long empId = abilityEmp.get(log.getBusinessTargetId());
                if (empId != null) {
                    logIdToEmpId.put(log.getId(), empId);
                }
            }
        }
    }

    public long countHarnessByStatus(String decision, Boolean assessmentOnly) {
        var wrapper = new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<AiHarnessCheckLog>()
                .eq(AiHarnessCheckLog::getDecision, decision);
        applyAssessmentFilter(wrapper, assessmentOnly);
        return harnessMapper.selectCount(wrapper);
    }

    public void updateHarnessLog(AiHarnessCheckLog log) {
        harnessMapper.updateById(log);
    }

    public long countHarnessByRiskLevel(String riskLevel, Boolean assessmentOnly) {
        var wrapper = new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<AiHarnessCheckLog>()
                .eq(AiHarnessCheckLog::getRiskLevel, riskLevel);
        applyAssessmentFilter(wrapper, assessmentOnly);
        return harnessMapper.selectCount(wrapper);
    }

    public long countHarnessSelfEvidence(Boolean assessmentOnly) {
        var wrapper = new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<AiHarnessCheckLog>()
                .eq(AiHarnessCheckLog::getIsSelfEvidence, 1);
        applyAssessmentFilter(wrapper, assessmentOnly);
        return harnessMapper.selectCount(wrapper);
    }

    public long countHarnessByReviewStatus(String reviewStatus, Boolean assessmentOnly) {
        var wrapper = new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<AiHarnessCheckLog>()
                .eq(AiHarnessCheckLog::getReviewStatus, reviewStatus);
        applyAssessmentFilter(wrapper, assessmentOnly);
        return harnessMapper.selectCount(wrapper);
    }

    // --- Prompt ---
    public IPage<PromptInvocationLog> pagePromptLogs(Page<PromptInvocationLog> page, String promptName, Boolean success) {
        var w = new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<PromptInvocationLog>();
        if (hasText(promptName)) w.eq(PromptInvocationLog::getPromptName, promptName);
        if (success != null) w.eq(PromptInvocationLog::getSuccess, success);
        w.orderByDesc(PromptInvocationLog::getCreatedTime);
        return promptInvocationLogMapper.selectPage(page, w);
    }

    public PromptInvocationLog getPromptLogById(Long id) {
        return promptInvocationLogMapper.selectById(id);
    }

    public List<PromptInvocationLog> listPromptLogsSince(LocalDateTime since, String promptName) {
        var w = new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<PromptInvocationLog>();
        w.ge(PromptInvocationLog::getCreatedTime, since);
        if (hasText(promptName)) w.eq(PromptInvocationLog::getPromptName, promptName);
        return promptInvocationLogMapper.selectList(w);
    }

    public List<com.example.matching.dto.system.PromptLogDTO> listPromptLogDtosSince(LocalDateTime since, String promptName) {
        return listPromptLogsSince(since, promptName).stream()
                .map(l -> new com.example.matching.dto.system.PromptLogDTO(
                        l.getPromptName(), l.getPromptVersion(),
                        l.getSuccess(), l.getLatencyMs(), l.getFeedbackScore()))
                .collect(java.util.stream.Collectors.toList());
    }

    private static boolean hasText(String v) {
        return v != null && !v.isBlank();
    }

    /**
     * Assessment final Harness is identified by its persisted batch-item linkage,
     * not by a mutable display scenario. This keeps data/tag governance out of
     * the personnel-assessment approval path.
     */
    private static void applyAssessmentFilter(
            com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<AiHarnessCheckLog> wrapper,
            Boolean assessmentOnly) {
        if (assessmentOnly == null) {
            return;
        }
        String existsSql = "SELECT 1 FROM ability_harness_batch_item item "
                + "WHERE item.harness_log_id = ai_harness_check_log.id";
        if (assessmentOnly) {
            wrapper.exists(existsSql);
        } else {
            wrapper.notExists(existsSql);
            applyNonPersonnelGovernanceFilter(wrapper);
        }
    }

    /**
     * The tag/data governance view audits non-personnel AI outputs. Personnel
     * assessment evidence remains available through the final Harness view.
     */
    private static void applyNonPersonnelGovernanceFilter(
            com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<AiHarnessCheckLog> wrapper) {
        wrapper.and(q -> q.isNull(AiHarnessCheckLog::getScenario)
                .or()
                .notIn(AiHarnessCheckLog::getScenario, PERSONNEL_GOVERNANCE_SCENARIOS));
        wrapper.and(q -> q.isNull(AiHarnessCheckLog::getClaimType)
                .or()
                .ne(AiHarnessCheckLog::getClaimType, "EMP_ABILITY")
                .ne(AiHarnessCheckLog::getClaimType, "INTERVIEW_ABILITY_OBSERVATION"));
        wrapper.and(q -> q.isNull(AiHarnessCheckLog::getBusinessTargetType)
                .or()
                .ne(AiHarnessCheckLog::getBusinessTargetType, "EMP_ABILITY"));
    }

    static boolean isPersonnelGovernanceScenario(String scenario) {
        return scenario != null && PERSONNEL_GOVERNANCE_SCENARIOS.contains(scenario);
    }

    /** 判断是否属于人员能力场景 */
    private static boolean isPersonScenario(String scenario) {
        if (scenario == null) return false;
        return "PERSON_ABILITY".equals(scenario)
                || "PERSON_ABILITY_AGGREGATE".equals(scenario)
                || "PERSON_ABILITY_EXTRACTION".equals(scenario)
                || "PMS_ANALYSIS".equals(scenario)
                || "AI_INTERVIEW_OBSERVATION".equals(scenario)
                || "RESUME_PARSE".equals(scenario);
    }

    private static boolean isEmployeeAbilityFactLog(AiHarnessCheckLog log) {
        return "EMP_ABILITY".equals(log.getBusinessTargetType())
                && log.getSourceRefs() != null
                && log.getSourceRefs().contains("fact:EMP_ABILITY:");
    }

    private static Long resolveResumeParseId(AiHarnessCheckLog log) {
        if (log.getSourceRefId() != null) return log.getSourceRefId();
        if (log.getSourceRefs() == null) return null;
        java.util.regex.Matcher matcher = java.util.regex.Pattern
                .compile("(?:source|fact):RESUME_PARSE:(\\d+)")
                .matcher(log.getSourceRefs());
        return matcher.find() ? Long.valueOf(matcher.group(1)) : null;
    }
}
