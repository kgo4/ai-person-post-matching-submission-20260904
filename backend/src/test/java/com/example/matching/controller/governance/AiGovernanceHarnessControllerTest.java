package com.example.matching.controller.governance;

import com.example.matching.application.governance.AiGovernanceHarnessApiFacade;
import com.example.matching.common.dto.PageResponse;
import com.example.matching.common.result.R;
import com.example.matching.dto.governance.api.AiHarnessCheckLogResponse;
import com.example.matching.dto.governance.api.HarnessCheckRequest;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AiGovernanceHarnessControllerTest {

    private static AiHarnessCheckLogResponse log(Long id, String code, String scenario,
                                                 String decision, String riskLevel, String reviewStatus) {
        return new AiHarnessCheckLogResponse(
                id, code, scenario, "ABILITY", "声明文本", "RESUME_PARSE", 500L,
                "证据文本", "1,2", "REF-1", 10L, 11L, BigDecimal.valueOf(88),
                riskLevel, decision, 0, "{}", reviewStatus, null, null,
                null, null, null, null, null, null, null, null, null, null,
                null, null, null
        );
    }

    @Test
    void pageChecksReturnsHarnessLogs() {
        AiGovernanceHarnessApiFacade facade = mock(AiGovernanceHarnessApiFacade.class);
        AiGovernanceHarnessController controller = new AiGovernanceHarnessController(facade);
        PageResponse<AiHarnessCheckLogResponse> page = new PageResponse<>(
                List.of(log(1L, "CHK-001", "MATCH_GAP_DIAGNOSIS", "BLOCK", "HIGH", "PENDING")),
                1, 1, 10, 1
        );
        when(facade.pageChecks(anyLong(), anyLong(), any(), any(), any(), any(), any(), any(), any())).thenReturn(page);

        R<PageResponse<AiHarnessCheckLogResponse>> response = controller.pageChecks(1, 10, "MATCH_GAP_DIAGNOSIS", "BLOCK", null, null, null, null, false);

        assertThat(response.getCode()).isEqualTo(200);
        assertThat(response.getData().records()).hasSize(1);
        assertThat(response.getData().records().get(0).checkCode()).isEqualTo("CHK-001");
    }

    @Test
    void summaryReturnsSummaryMap() {
        AiGovernanceHarnessApiFacade facade = mock(AiGovernanceHarnessApiFacade.class);
        AiGovernanceHarnessController controller = new AiGovernanceHarnessController(facade);
        when(facade.summary(null)).thenReturn(Map.of("passCount", 3L, "reviewCount", 2L, "blockCount", 1L));

        R<Map<String, Object>> response = controller.summary(null);

        assertThat(response.getCode()).isEqualTo(200);
        assertThat(response.getData()).containsEntry("passCount", 3L);
    }

    @Test
    void updateReviewStatusReturnsOk() {
        AiGovernanceHarnessApiFacade facade = mock(AiGovernanceHarnessApiFacade.class);
        AiGovernanceHarnessController controller = new AiGovernanceHarnessController(facade);
        HarnessCheckRequest request = new HarnessCheckRequest("ACCEPTED", "采纳通过", null, true);

        R<Void> response = controller.updateReviewStatus(1L, request);

        assertThat(response.getCode()).isEqualTo(200);
        assertThat(response.getData()).isNull();
    }

    @Test
    void acceptReviewReturnsTrueWhenSuccessAndRequestProvided() {
        AiGovernanceHarnessApiFacade facade = mock(AiGovernanceHarnessApiFacade.class);
        AiGovernanceHarnessController controller = new AiGovernanceHarnessController(facade);
        HarnessCheckRequest request = new HarnessCheckRequest("ACCEPTED", "采纳意见", null, true);
        when(facade.acceptReview(anyLong(), any())).thenReturn(true);

        R<Boolean> response = controller.acceptReview(1L, request);

        assertThat(response.getCode()).isEqualTo(200);
        assertThat(response.getData()).isTrue();
    }

    @Test
    void acceptReviewReturnsFailWhenFacadeReturnsFalse() {
        AiGovernanceHarnessApiFacade facade = mock(AiGovernanceHarnessApiFacade.class);
        AiGovernanceHarnessController controller = new AiGovernanceHarnessController(facade);
        HarnessCheckRequest request = new HarnessCheckRequest("ACCEPTED", "采纳意见", null, true);
        when(facade.acceptReview(anyLong(), any())).thenReturn(false);

        R<Boolean> response = controller.acceptReview(1L, request);

        assertThat(response.getCode()).isEqualTo(500);
        assertThat(response.getData()).isNull();
        assertThat(response.getMessage()).isEqualTo("应用失败，请检查记录状态");
    }

    @Test
    void acceptReviewWithNullRequestPassesNullComment() {
        AiGovernanceHarnessApiFacade facade = mock(AiGovernanceHarnessApiFacade.class);
        AiGovernanceHarnessController controller = new AiGovernanceHarnessController(facade);
        when(facade.acceptReview(anyLong(), isNull())).thenReturn(true);

        R<Boolean> response = controller.acceptReview(1L, null);

        assertThat(response.getCode()).isEqualTo(200);
        assertThat(response.getData()).isTrue();
    }

    @Test
    void applyToBusinessReturnsTrueWhenSuccess() {
        AiGovernanceHarnessApiFacade facade = mock(AiGovernanceHarnessApiFacade.class);
        AiGovernanceHarnessController controller = new AiGovernanceHarnessController(facade);
        HarnessCheckRequest request = new HarnessCheckRequest("ACCEPTED", "采纳意见", null, true);
        when(facade.applyToBusiness(anyLong(), any())).thenReturn(true);

        R<Boolean> response = controller.applyToBusiness(1L, request);

        assertThat(response.getCode()).isEqualTo(200);
        assertThat(response.getData()).isTrue();
    }

    @Test
    void applyToBusinessReturnsFailWhenFacadeReturnsFalse() {
        AiGovernanceHarnessApiFacade facade = mock(AiGovernanceHarnessApiFacade.class);
        AiGovernanceHarnessController controller = new AiGovernanceHarnessController(facade);
        when(facade.applyToBusiness(anyLong(), isNull())).thenReturn(false);

        R<Boolean> response = controller.applyToBusiness(1L, null);

        assertThat(response.getCode()).isEqualTo(500);
        assertThat(response.getMessage()).isEqualTo("应用失败，请检查记录状态");
    }
}
