package com.example.matching.controller.rag;

import com.example.matching.application.rag.AiHarnessAuditApiFacade;
import com.example.matching.common.dto.PageResponse;
import com.example.matching.common.result.R;
import com.example.matching.dto.rag.api.AiHarnessCheckLogResponse;
import com.example.matching.dto.rag.api.HarnessCheckRequest;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AiHarnessAuditControllerTest {

    private static AiHarnessCheckLogResponse createLog(String checkCode, String scenario, String decision, String riskLevel, String reviewStatus) {
        return new AiHarnessCheckLogResponse(
                null, checkCode, scenario, null, null, null, null, null, null, null,
                null, null, null, riskLevel, decision, null, null, reviewStatus, null, null,
                null, null, null, null, null, null, null, null, null, null,
                null, null, null
        );
    }

    @Test
    void pageChecksReturnsHarnessAuditLogs() {
        AiHarnessAuditApiFacade facade = mock(AiHarnessAuditApiFacade.class);
        AiHarnessAuditController controller = new AiHarnessAuditController(facade);

        AiHarnessCheckLogResponse log = createLog("HNS_001", "MATCH_GAP_DIAGNOSIS", "BLOCK", "HIGH", "PENDING");

        PageResponse<AiHarnessCheckLogResponse> page = new PageResponse<>(
                List.of(log), 1, 1, 10, 1
        );
        when(facade.pageChecks(1, 10, "MATCH_GAP_DIAGNOSIS", "BLOCK"))
                .thenReturn(page);

        R<PageResponse<AiHarnessCheckLogResponse>> response = controller.pageChecks(
                1, 10, "MATCH_GAP_DIAGNOSIS", "BLOCK");

        assertThat(response.getData().records()).containsExactly(log);
        assertThat(response.getData().total()).isEqualTo(1);
    }

    @Test
    void summaryReturnsDecisionAndRiskDistribution() {
        AiHarnessAuditApiFacade facade = mock(AiHarnessAuditApiFacade.class);
        AiHarnessAuditController controller = new AiHarnessAuditController(facade);

        Map<String, Object> summaryMap = Map.of(
                "passCount", 3L,
                "reviewCount", 2L,
                "blockCount", 1L,
                "highRiskCount", 4L,
                "selfEvidenceCount", 0L
        );
        when(facade.summary()).thenReturn(summaryMap);

        R<Map<String, Object>> response = controller.summary();

        assertThat(response.getData())
                .containsEntry("passCount", 3L)
                .containsEntry("reviewCount", 2L)
                .containsEntry("blockCount", 1L)
                .containsEntry("highRiskCount", 4L)
                .containsEntry("selfEvidenceCount", 0L);
    }

    @Test
    void updateReviewStatusPersistsManualDisposition() {
        AiHarnessAuditApiFacade facade = mock(AiHarnessAuditApiFacade.class);
        AiHarnessAuditController controller = new AiHarnessAuditController(facade);

        HarnessCheckRequest request = new HarnessCheckRequest(
                "ACCEPTED", "contest manual accept"
        );
        when(facade.updateReviewStatus(10L, request)).thenReturn(true);

        R<Boolean> response = controller.updateReviewStatus(10L, request);

        assertThat(response.getData()).isTrue();
    }
}
