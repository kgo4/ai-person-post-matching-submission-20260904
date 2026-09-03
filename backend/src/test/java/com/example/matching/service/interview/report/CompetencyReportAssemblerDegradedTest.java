package com.example.matching.service.interview.report;

import com.example.matching.entity.employee.EmpVideoInterviewSession;
import com.example.matching.entity.interview.InterviewAbilityObservation;
import com.example.matching.entity.post.PostAbilityModel;
import com.example.matching.agent.lc4j.InterviewReportAiService;
import com.example.matching.mapper.employee.EmpVideoInterviewSessionMapper;
import com.example.matching.mapper.employee.EmpVideoInterviewEvidenceMapper;
import com.example.matching.mapper.interview.InterviewAbilityObservationMapper;
import com.example.matching.mapper.post.PostAbilityModelMapper;
import com.example.matching.dto.interview.CompetencyReport;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * H6 行为测试：无任何能力观察时，报告必须明确标记 degraded 并给出原因，
 * 不允许静默生成看似正常的报告。
 */
class CompetencyReportAssemblerDegradedTest {

    private EmpVideoInterviewSessionMapper sessionMapper;
    private InterviewAbilityObservationMapper observationMapper;
    private EmpVideoInterviewEvidenceMapper evidenceMapper;
    private ObjectProvider<InterviewReportAiService> reportAiServiceProvider;
    private CompetencyReportAssembler assembler;

    @BeforeEach
    void setUp() {
        sessionMapper = mock(EmpVideoInterviewSessionMapper.class);
        observationMapper = mock(InterviewAbilityObservationMapper.class);
        evidenceMapper = mock(EmpVideoInterviewEvidenceMapper.class);
        reportAiServiceProvider = mock(ObjectProvider.class);
        PostAbilityModelMapper postAbilityModelMapper = mock(PostAbilityModelMapper.class);
        when(postAbilityModelMapper.selectList(any())).thenReturn(List.of());
        assembler = new CompetencyReportAssembler(
                sessionMapper, observationMapper, evidenceMapper, postAbilityModelMapper, new ObjectMapper(),
                reportAiServiceProvider, mock(com.example.matching.agent.service.impl.AgentOutputValidator.class),
                new com.example.matching.infrastructure.llm.LlmResponseParser(new ObjectMapper()));

        EmpVideoInterviewSession session = new EmpVideoInterviewSession();
        session.setId(7L);
        session.setEmpId(11L);
        session.setPostId(22L);
        when(sessionMapper.selectById(7L)).thenReturn(session);
        when(observationMapper.selectList(any())).thenReturn(List.of());
        when(evidenceMapper.selectList(any())).thenReturn(List.of());
    }

    @Test
    void reportMarksDegradedWhenNoObservationsDerived() {
        CompetencyReport report = assembler.generateCompetencyReport(7L);

        assertThat(report.degraded()).isTrue();
        assertThat(report.degradedReason()).contains("规则兜底无法派生任何能力观察");
        assertThat(report.observations()).isEmpty();
    }

    @Test
    void noObservationsDoesNotStartReportAgentOrToolCalls() {
        when(reportAiServiceProvider.getIfAvailable()).thenReturn(mock(InterviewReportAiService.class));

        CompetencyReport report = assembler.generateCompetencyReport(7L);

        assertThat(report.degraded()).isTrue();
        verify(reportAiServiceProvider, never()).getIfAvailable();
    }

    @Test
    void reportNotDegradedWhenObservationsExist() {
        InterviewAbilityObservation obs = new InterviewAbilityObservation();
        obs.setSessionId(7L);
        obs.setTagId(1L);
        obs.setAbilityName("Java");
        when(observationMapper.selectList(any())).thenReturn(List.of(obs));

        CompetencyReport report = assembler.generateCompetencyReport(7L);

        assertThat(report.degraded()).isFalse();
        assertThat(report.degradedReason()).isNull();
        assertThat(report.observations()).hasSize(1);
    }

    @Test
    void pendingAggregateHarnessObservationStillContributesToEvidenceScore() {
        InterviewAbilityObservation obs = new InterviewAbilityObservation();
        obs.setSessionId(7L);
        obs.setTagId(1L);
        obs.setAbilityName("Java");
        obs.setObservedLevel(3);
        // Aggregate Harness has not run yet, so harnessDecision is intentionally null.
        when(observationMapper.selectList(any())).thenReturn(List.of(obs));

        CompetencyReport report = assembler.generateCompetencyReport(7L);

        assertThat(report.overallScore()).isEqualTo(60);
    }

    @Test
    void validVisualEvidenceContributesTenPercentToOverallScoreOnly() {
        InterviewAbilityObservation obs = new InterviewAbilityObservation();
        obs.setSessionId(7L);
        obs.setTagId(1L);
        obs.setAbilityName("Java");
        obs.setObservedLevel(3);
        when(observationMapper.selectList(any())).thenReturn(List.of(obs));
        com.example.matching.entity.employee.EmpVideoInterviewEvidence visual =
                new com.example.matching.entity.employee.EmpVideoInterviewEvidence();
        visual.setQuestionId(101L);
        visual.setEvidenceType("VISUAL");
        visual.setConfidenceScore(java.math.BigDecimal.valueOf(0.8));
        visual.setRawScore(java.math.BigDecimal.valueOf(80));
        when(evidenceMapper.selectList(any())).thenReturn(List.of(visual));

        CompetencyReport report = assembler.generateCompetencyReport(7L);

        assertThat(report.overallScore()).isEqualTo(62);
        assertThat(report.radarItems()).allMatch(item -> item.score() == 60);
    }
}
