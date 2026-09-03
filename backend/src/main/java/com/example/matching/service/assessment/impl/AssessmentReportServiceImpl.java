package com.example.matching.service.assessment.impl;

import com.example.matching.entity.workflow.PersonCapabilityWorkflow;
import com.example.matching.port.assessment.AssessmentReportPort;
import com.example.matching.service.assessment.AbilityLevelConfirmationService;
import com.example.matching.service.assessment.AggregateAbilityHarnessService;
import com.example.matching.service.assessment.AssessmentReportService;
import com.example.matching.service.assessment.CapabilityAssessmentWorkflowService;
import com.example.matching.dto.interview.CompetencyReport;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
public class AssessmentReportServiceImpl implements AssessmentReportService {

    private static final String STATUS_READY = "READY";
    private static final String STATUS_FAILED = "FAILED";

    private final AssessmentReportPort reportPort;
    private final CapabilityAssessmentWorkflowService workflowService;
    private final AggregateAbilityHarnessService aggregateHarnessService;
    private final AbilityLevelConfirmationService levelConfirmationService;
    private final ObjectMapper objectMapper;

    public AssessmentReportServiceImpl(AssessmentReportPort reportPort,
                                       CapabilityAssessmentWorkflowService workflowService,
                                       AggregateAbilityHarnessService aggregateHarnessService,
                                       AbilityLevelConfirmationService levelConfirmationService,
                                       ObjectMapper objectMapper) {
        this.reportPort = reportPort;
        this.workflowService = workflowService;
        this.aggregateHarnessService = aggregateHarnessService;
        this.levelConfirmationService = levelConfirmationService;
        this.objectMapper = objectMapper;
    }

    @Override
    public void generateAndPersist(Long workflowId, Long sessionId, CompetencyReport report) {
        PersonCapabilityWorkflow workflow = null;
        try {
            workflow = workflowService.getWorkflow(workflowId);
            if (workflow == null) {
                log.warn("评估工作流不存在，跳过报告生成: workflowId={}", workflowId);
                return;
            }
            reportPort.saveReport(new AssessmentReportPort.ReportDTO(
                    workflowId,
                    workflow.getEmpId(),
                    workflow.getPostId(),
                    sessionId,
                    STATUS_READY,
                    report.overallScore(),
                    report.postMatchScore(),
                    buildClaimSummaryJson(workflowId, "RESUME_PARSE"),
                    buildClaimSummaryJson(workflowId, "AI_TEST"),
                    buildInterviewSummaryJson(report),
                    null,
                    null,
                    report.conclusion(),
                    report.recommendation()));
            log.info("评估报告主体已生成: workflowId={}, sessionId={}", workflowId, sessionId);
        } catch (Exception e) {
            log.error("评估报告主体生成失败: workflowId={}, error={}", workflowId, e.getMessage(), e);
            markFailed(workflowId, sessionId, workflow);
        }
    }

    @Override
    public void refreshAggregateConclusion(Long workflowId) {
        try {
            Map<Long, String> groupNames = resolveGroupNames(workflowId);
            List<Map<String, Object>> items = aggregateHarnessService.getHarnessResults(workflowId).stream()
                    .map(d -> {
                        Map<String, Object> m = new LinkedHashMap<>();
                        m.put("claimGroupId", d.getClaimGroupId());
                        m.put("abilityName", groupNames.getOrDefault(d.getClaimGroupId(), "能力组" + d.getClaimGroupId()));
                        m.put("decision", d.getDecision());
                        m.put("riskLevel", d.getRiskLevel());
                        m.put("abilitySupported", d.getAbilitySupported());
                        m.put("supportedLevelCeiling", d.getSupportedLevelCeiling());
                        m.put("reasonCodes", d.getReasonCodes());
                        return m;
                    })
                    .toList();
            reportPort.updateAggregateSummary(workflowId, toJson(items));
            log.info("报告聚合审核结论已回填: workflowId={}", workflowId);
        } catch (Exception e) {
            log.warn("报告聚合审核结论回填失败: workflowId={}, error={}", workflowId, e.getMessage());
        }
    }

    @Override
    public void refreshLevelConclusion(Long workflowId) {
        try {
            Map<Long, String> groupNames = resolveGroupNames(workflowId);
            List<Map<String, Object>> items = levelConfirmationService.listDecisions(workflowId).stream()
                    .map(d -> {
                        Map<String, Object> m = new LinkedHashMap<>();
                        m.put("claimGroupId", d.getClaimGroupId());
                        m.put("abilityName", groupNames.getOrDefault(d.getClaimGroupId(), "能力组" + d.getClaimGroupId()));
                        m.put("tagId", d.getTagId());
                        m.put("finalLevel", d.getFinalLevel());
                        m.put("finalConfidence", d.getFinalConfidence());
                        m.put("decisionStatus", d.getDecisionStatus());
                        return m;
                    })
                    .toList();
            reportPort.updateLevelSummary(workflowId, toJson(items));
            log.info("报告等级确认结论已回填: workflowId={}", workflowId);
        } catch (Exception e) {
            log.warn("报告等级确认结论回填失败: workflowId={}, error={}", workflowId, e.getMessage());
        }
    }

    @Override
    public List<AssessmentReportPort.WorkflowReportDTO> listByEmpId(Long empId) {
        return reportPort.listWorkflowReports(empId);
    }

    @Override
    public AssessmentReportPort.ReportDTO getByWorkflowId(Long workflowId) {
        return reportPort.findReport(workflowId);
    }

    private String buildClaimSummaryJson(Long workflowId, String sourceType) {
        List<Map<String, Object>> items = new ArrayList<>();
        for (AssessmentReportPort.ClaimDTO c : reportPort.listClaims(workflowId, sourceType)) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("tagId", c.tagId());
            m.put("abilityName", c.abilityName());
            m.put("claimedLevel", c.claimedLevel());
            m.put("confidenceScore", c.confidenceScore());
            m.put("evidenceText", c.evidenceText());
            m.put("harnessDecision", c.harnessDecision());
            items.add(m);
        }
        return toJson(items);
    }

    private String buildInterviewSummaryJson(CompetencyReport report) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("sessionId", report.sessionId());
        m.put("overallScore", report.overallScore());
        m.put("postMatchScore", report.postMatchScore());
        m.put("radarItems", report.radarItems());
        m.put("observations", report.observations());
        m.put("questionAnswers", safeList(reportPort.listInterviewQuestionAnswers(report.sessionId())));
        m.put("strengths", report.strengths());
        m.put("weaknesses", report.weaknesses());
        m.put("riskSignals", report.riskSignals());
        m.put("improvementSuggestions", report.improvementSuggestions());
        m.put("learningPathSuggestions", report.learningPathSuggestions());
        m.put("degraded", report.degraded());
        m.put("degradedReason", report.degradedReason());
        return toJson(m);
    }

    private <T> List<T> safeList(List<T> values) {
        return values != null ? values : List.of();
    }

    private Map<Long, String> resolveGroupNames(Long workflowId) {
        return reportPort.listClaimGroups(workflowId).stream()
                .collect(java.util.stream.Collectors.toMap(
                        AssessmentReportPort.ClaimGroupDTO::claimGroupId,
                        g -> g.normalizedAbilityName() != null ? g.normalizedAbilityName() : "能力组" + g.claimGroupId(),
                        (a, b) -> a));
    }

    private void markFailed(Long workflowId, Long sessionId, PersonCapabilityWorkflow workflow) {
        try {
            reportPort.saveReport(new AssessmentReportPort.ReportDTO(
                    workflowId,
                    workflow != null ? workflow.getEmpId() : null,
                    workflow != null ? workflow.getPostId() : null,
                    sessionId, STATUS_FAILED,
                    null, null, null, null, null, null, null, null, null));
        } catch (Exception e) {
            log.warn("报告失败状态落库失败: workflowId={}", workflowId, e);
        }
    }

    private String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception e) {
            return "[]";
        }
    }
}
