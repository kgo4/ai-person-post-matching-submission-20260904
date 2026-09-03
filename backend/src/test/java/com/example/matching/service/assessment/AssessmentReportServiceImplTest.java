package com.example.matching.service.assessment;

import com.example.matching.dto.interview.CompetencyReport;
import com.example.matching.entity.workflow.PersonCapabilityWorkflow;
import com.example.matching.port.assessment.AssessmentReportPort;
import com.example.matching.service.assessment.impl.AssessmentReportServiceImpl;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.assertj.core.api.Assertions.assertThat;

class AssessmentReportServiceImplTest {

    private final AssessmentReportPort port = mock(AssessmentReportPort.class);
    private final CapabilityAssessmentWorkflowService workflowService = mock(CapabilityAssessmentWorkflowService.class);
    private final AggregateAbilityHarnessService harnessService = mock(AggregateAbilityHarnessService.class);
    private final AbilityLevelConfirmationService levelService = mock(AbilityLevelConfirmationService.class);
    private final AssessmentReportService service = new AssessmentReportServiceImpl(
            port, workflowService, harnessService, levelService, new ObjectMapper());

    private CompetencyReport sampleReport() {
        return new CompetencyReport(
                1L, 100L, 200L, 78, 70,
                List.of(), List.of(), List.of("强项"), List.of("弱项"), List.of(),
                List.of("建议"), List.of(), "结论", "建议文案", false, null);
    }

    @Test
    void generateAndPersist_savesReadyReport() {
        PersonCapabilityWorkflow w = new PersonCapabilityWorkflow();
        w.setId(1L);
        w.setEmpId(100L);
        w.setPostId(200L);
        when(workflowService.getWorkflow(1L)).thenReturn(w);
        when(port.listClaims(eq(1L), any())).thenReturn(List.of());

        service.generateAndPersist(1L, 1L, sampleReport());

        verify(port).saveReport(argThat(dto ->
                dto != null && "READY".equals(dto.status())
                        && dto.overallScore() == 78
                        && dto.interviewSummaryJson() != null));
    }

    @Test
    void generateAndPersist_includesInterviewObservationsAndDegradedStateInSummary() {
        PersonCapabilityWorkflow w = new PersonCapabilityWorkflow();
        w.setId(1L);
        w.setEmpId(100L);
        w.setPostId(200L);
        when(workflowService.getWorkflow(1L)).thenReturn(w);
        when(port.listClaims(eq(1L), any())).thenReturn(List.of());

        service.generateAndPersist(1L, 1L, sampleReport());

        verify(port).saveReport(argThat(dto -> {
            assertThat(dto.interviewSummaryJson()).contains("observations");
            assertThat(dto.interviewSummaryJson()).contains("degraded");
            assertThat(dto.interviewSummaryJson()).contains("sessionId");
            assertThat(dto.interviewSummaryJson()).contains("questionAnswers");
            return true;
        }));
    }

    @Test
    void generateAndPersist_marksFailed_onWorkflowMissing() {
        when(workflowService.getWorkflow(1L)).thenReturn(null);
        service.generateAndPersist(1L, 1L, sampleReport());
        verify(port, never()).saveReport(argThat(dto -> dto != null && "READY".equals(dto.status())));
    }

    @Test
    void refreshLevelConclusion_callsUpdate() {
        when(levelService.listDecisions(1L)).thenReturn(List.of());
        service.refreshLevelConclusion(1L);
        verify(port).updateLevelSummary(eq(1L), any());
    }
}
