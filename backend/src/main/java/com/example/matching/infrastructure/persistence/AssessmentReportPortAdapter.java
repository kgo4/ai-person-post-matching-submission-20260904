package com.example.matching.infrastructure.persistence;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.example.matching.entity.ability.PersonAbilityClaim;
import com.example.matching.entity.workflow.AssessmentReport;
import com.example.matching.entity.workflow.PersonAbilityClaimGroup;
import com.example.matching.entity.workflow.PersonCapabilityWorkflow;
import com.example.matching.entity.employee.EmpVideoInterviewQuestion;
import com.example.matching.entity.interview.InterviewFollowUpQuestion;
import com.example.matching.mapper.employee.EmpVideoInterviewQuestionMapper;
import com.example.matching.mapper.interview.InterviewFollowUpQuestionMapper;
import com.example.matching.mapper.ability.PersonAbilityClaimMapper;
import com.example.matching.mapper.workflow.AssessmentReportMapper;
import com.example.matching.mapper.workflow.PersonAbilityClaimGroupMapper;
import com.example.matching.mapper.workflow.PersonCapabilityWorkflowMapper;
import com.example.matching.port.assessment.AssessmentReportPort;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 评估报告数据端口适配器：内部使用 Mapper，实现 {@link AssessmentReportPort}。
 */
@Component
public class AssessmentReportPortAdapter implements AssessmentReportPort {

    private final PersonAbilityClaimMapper claimMapper;
    private final AssessmentReportMapper reportMapper;
    private final PersonCapabilityWorkflowMapper workflowMapper;
    private final PersonAbilityClaimGroupMapper claimGroupMapper;
    private final EmpVideoInterviewQuestionMapper interviewQuestionMapper;
    private final InterviewFollowUpQuestionMapper followUpQuestionMapper;

    public AssessmentReportPortAdapter(PersonAbilityClaimMapper claimMapper,
                                       AssessmentReportMapper reportMapper,
                                       PersonCapabilityWorkflowMapper workflowMapper,
                                       PersonAbilityClaimGroupMapper claimGroupMapper,
                                       EmpVideoInterviewQuestionMapper interviewQuestionMapper,
                                       InterviewFollowUpQuestionMapper followUpQuestionMapper) {
        this.claimMapper = claimMapper;
        this.reportMapper = reportMapper;
        this.workflowMapper = workflowMapper;
        this.claimGroupMapper = claimGroupMapper;
        this.interviewQuestionMapper = interviewQuestionMapper;
        this.followUpQuestionMapper = followUpQuestionMapper;
    }

    @Override
    public List<ClaimDTO> listClaims(Long workflowId, String sourceType) {
        return claimMapper.selectList(new LambdaQueryWrapper<PersonAbilityClaim>()
                        .eq(PersonAbilityClaim::getWorkflowId, workflowId)
                        .eq(PersonAbilityClaim::getSourceType, sourceType)
                        .eq(PersonAbilityClaim::getIsDeleted, 0))
                .stream()
                .map(c -> new ClaimDTO(c.getTagId(), c.getAbilityName(), c.getClaimedLevel(),
                        c.getConfidenceScore(), c.getEvidenceText(), c.getHarnessDecision()))
                .toList();
    }

    @Override
    public List<InterviewQuestionAnswerDTO> listInterviewQuestionAnswers(Long sessionId) {
        if (sessionId == null) {
            return List.of();
        }
        List<InterviewFollowUpQuestion> followUps = followUpQuestionMapper.selectList(
                new LambdaQueryWrapper<InterviewFollowUpQuestion>()
                        .eq(InterviewFollowUpQuestion::getSessionId, sessionId)
                        .orderByAsc(InterviewFollowUpQuestion::getParentQuestionId)
                        .orderByAsc(InterviewFollowUpQuestion::getFollowUpOrder));
        return interviewQuestionMapper.selectList(new LambdaQueryWrapper<EmpVideoInterviewQuestion>()
                        .eq(EmpVideoInterviewQuestion::getSessionId, sessionId)
                        .orderByAsc(EmpVideoInterviewQuestion::getQuestionOrder))
                .stream()
                .map(question -> new InterviewQuestionAnswerDTO(question.getQuestionOrder(), question.getQuestionType(),
                        question.getQuestionText(), question.getAnswerTranscript(), question.getDurationSeconds(),
                        question.getEndedBy(), question.getAnswerScore(), question.getAnalysisComment(),
                        followUps.stream().filter(followUp -> question.getId().equals(followUp.getParentQuestionId()))
                                .map(followUp -> new FollowUpAnswerDTO(followUp.getFollowUpOrder(), followUp.getQuestionText(),
                                        followUp.getAnswerText(), followUp.getTriggerReason(), followUp.getBoundaryJudgement(),
                                        followUp.getAnswerQualityScore())).toList()))
                .toList();
    }

    @Override
    public ReportDTO findReport(Long workflowId) {
        AssessmentReport r = reportMapper.selectOne(new LambdaQueryWrapper<AssessmentReport>()
                .eq(AssessmentReport::getWorkflowId, workflowId));
        return toDto(r);
    }

    @Override
    public void saveReport(ReportDTO dto) {
        AssessmentReport existing = reportMapper.selectOne(new LambdaQueryWrapper<AssessmentReport>()
                .eq(AssessmentReport::getWorkflowId, dto.workflowId()));
        AssessmentReport entity = existing != null ? existing : new AssessmentReport();
        entity.setWorkflowId(dto.workflowId());
        entity.setEmpId(dto.empId());
        entity.setPostId(dto.postId());
        entity.setSessionId(dto.sessionId());
        entity.setStatus(dto.status());
        entity.setOverallScore(dto.overallScore());
        entity.setPostMatchScore(dto.postMatchScore());
        entity.setResumeSummaryJson(dto.resumeSummaryJson());
        entity.setTestSummaryJson(dto.testSummaryJson());
        entity.setInterviewSummaryJson(dto.interviewSummaryJson());
        entity.setAggregateSummaryJson(dto.aggregateSummaryJson());
        entity.setLevelSummaryJson(dto.levelSummaryJson());
        entity.setConclusion(dto.conclusion());
        entity.setRecommendation(dto.recommendation());
        if (entity.getGeneratedAt() == null) {
            entity.setGeneratedAt(LocalDateTime.now());
        }
        if (existing != null) {
            reportMapper.updateById(entity);
        } else {
            reportMapper.insert(entity);
        }
    }

    @Override
    public void updateAggregateSummary(Long workflowId, String aggregateSummaryJson) {
        reportMapper.update(null, new LambdaUpdateWrapper<AssessmentReport>()
                .eq(AssessmentReport::getWorkflowId, workflowId)
                .set(AssessmentReport::getAggregateSummaryJson, aggregateSummaryJson)
                .set(AssessmentReport::getCompletedAt, LocalDateTime.now()));
    }

    @Override
    public void updateLevelSummary(Long workflowId, String levelSummaryJson) {
        reportMapper.update(null, new LambdaUpdateWrapper<AssessmentReport>()
                .eq(AssessmentReport::getWorkflowId, workflowId)
                .set(AssessmentReport::getLevelSummaryJson, levelSummaryJson)
                .set(AssessmentReport::getCompletedAt, LocalDateTime.now()));
    }

    @Override
    public List<WorkflowReportDTO> listWorkflowReports(Long empId) {
        List<PersonCapabilityWorkflow> workflows = workflowMapper.selectList(
                new LambdaQueryWrapper<PersonCapabilityWorkflow>()
                        .eq(PersonCapabilityWorkflow::getEmpId, empId)
                        .orderByDesc(PersonCapabilityWorkflow::getCreatedTime));
        List<AssessmentReport> reports = reportMapper.selectList(
                new LambdaQueryWrapper<AssessmentReport>()
                        .eq(AssessmentReport::getEmpId, empId));
        return workflows.stream().map(w -> {
            AssessmentReport r = reports.stream()
                    .filter(x -> x.getWorkflowId().equals(w.getId()))
                    .findFirst().orElse(null);
            return new WorkflowReportDTO(
                    w.getId(), w.getStatus(), w.getStartedAt(), w.getCompletedAt(),
                    r != null ? r.getStatus() : null,
                    r != null ? r.getOverallScore() : null,
                    r != null ? r.getPostMatchScore() : null);
        }).toList();
    }

    @Override
    public List<ClaimGroupDTO> listClaimGroups(Long workflowId) {
        return claimGroupMapper.selectList(new LambdaQueryWrapper<PersonAbilityClaimGroup>()
                        .eq(PersonAbilityClaimGroup::getWorkflowId, workflowId))
                .stream()
                .map(g -> new ClaimGroupDTO(g.getId(), g.getCanonicalTagId(),
                        g.getNormalizedAbilityName(), g.getStatus()))
                .toList();
    }

    private ReportDTO toDto(AssessmentReport r) {
        if (r == null) return null;
        return new ReportDTO(r.getWorkflowId(), r.getEmpId(), r.getPostId(), r.getSessionId(),
                r.getStatus(), r.getOverallScore(), r.getPostMatchScore(),
                r.getResumeSummaryJson(), r.getTestSummaryJson(), r.getInterviewSummaryJson(),
                r.getAggregateSummaryJson(), r.getLevelSummaryJson(),
                r.getConclusion(), r.getRecommendation());
    }
}
