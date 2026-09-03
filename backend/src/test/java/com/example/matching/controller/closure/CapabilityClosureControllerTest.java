package com.example.matching.controller.closure;

import com.example.matching.application.closure.CapabilityClosureApiFacade;
import com.example.matching.common.result.R;
import com.example.matching.dto.closure.CapabilityClosureResult;
import com.example.matching.dto.closure.ComprehensiveDiagnosisResultDTO;
import com.example.matching.dto.closure.LearningOutcomeConfirmDTO;
import com.example.matching.dto.closure.MatchDiagnosisResult;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class CapabilityClosureControllerTest {

    private static CapabilityClosureResult closureResult(String businessKey, String status) {
        CapabilityClosureResult result = new CapabilityClosureResult();
        result.setEventType("LEARNING_OUTCOME_CONFIRMED");
        result.setBusinessKey(businessKey);
        result.setClosureStatus(status);
        return result;
    }

    @Test
    void diagnoseMatchingRecordReturnsDiagnosisResult() {
        CapabilityClosureApiFacade facade = mock(CapabilityClosureApiFacade.class);
        CapabilityClosureController controller = new CapabilityClosureController(facade);
        MatchDiagnosisResult diagnosis = new MatchDiagnosisResult();
        diagnosis.setMatchingRecordId(1L);
        diagnosis.setEmpId(100L);
        diagnosis.setPostId(200L);
        when(facade.diagnoseMatchingRecord(anyLong())).thenReturn(diagnosis);

        R<MatchDiagnosisResult> response = controller.diagnoseMatchingRecord(1L);

        assertThat(response.getCode()).isEqualTo(200);
        assertThat(response.getData().getMatchingRecordId()).isEqualTo(1L);
        assertThat(response.getData().getEmpId()).isEqualTo(100L);
    }

    @Test
    void comprehensiveDiagnosisReturnsDiagnosisResult() {
        CapabilityClosureApiFacade facade = mock(CapabilityClosureApiFacade.class);
        CapabilityClosureController controller = new CapabilityClosureController(facade);
        ComprehensiveDiagnosisResultDTO diagnosis = new ComprehensiveDiagnosisResultDTO();
        diagnosis.setMatchingRecordId(1L);
        diagnosis.setEmpId(100L);
        diagnosis.setPostId(200L);
        when(facade.comprehensiveDiagnosis(anyLong())).thenReturn(diagnosis);

        R<ComprehensiveDiagnosisResultDTO> response = controller.comprehensiveDiagnosis(1L);

        assertThat(response.getCode()).isEqualTo(200);
        assertThat(response.getData().getMatchingRecordId()).isEqualTo(1L);
        assertThat(response.getData().getEmpId()).isEqualTo(100L);
    }

    @Test
    void confirmLearningOutcomeReturnsClosureResult() {
        CapabilityClosureApiFacade facade = mock(CapabilityClosureApiFacade.class);
        CapabilityClosureController controller = new CapabilityClosureController(facade);
        LearningOutcomeConfirmDTO dto = new LearningOutcomeConfirmDTO();
        dto.setEmpId(100L);
        dto.setAbilityName("Java");
        dto.setConfirmedLevel(4);
        when(facade.confirmLearningOutcome(any())).thenReturn(closureResult("BK-1", "CLOSED"));

        R<CapabilityClosureResult> response = controller.confirmLearningOutcome(dto);

        assertThat(response.getCode()).isEqualTo(200);
        assertThat(response.getData().getBusinessKey()).isEqualTo("BK-1");
        assertThat(response.getData().getClosureStatus()).isEqualTo("CLOSED");
    }

    @Test
    void getLogReturnsLatestClosureResult() {
        CapabilityClosureApiFacade facade = mock(CapabilityClosureApiFacade.class);
        CapabilityClosureController controller = new CapabilityClosureController(facade);
        when(facade.getLatestByBusinessKey(anyString())).thenReturn(closureResult("BK-9", "IN_PROGRESS"));

        R<CapabilityClosureResult> response = controller.getLog("BK-9");

        assertThat(response.getCode()).isEqualTo(200);
        assertThat(response.getData().getBusinessKey()).isEqualTo("BK-9");
        assertThat(response.getData().getClosureStatus()).isEqualTo("IN_PROGRESS");
    }
}
