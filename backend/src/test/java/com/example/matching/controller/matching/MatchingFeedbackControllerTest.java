package com.example.matching.controller.matching;

import com.example.matching.application.matching.FeedbackApiFacade;
import com.example.matching.common.dto.PageResponse;
import com.example.matching.common.exception.BusinessException;
import com.example.matching.common.exception.ErrorCodeEnum;
import com.example.matching.common.result.R;
import com.example.matching.dto.matching.MatchingFeedbackExportDTO;
import com.example.matching.dto.matching.api.FeedbackDatasetRequest;
import com.example.matching.dto.matching.api.FeedbackDatasetResponse;
import jakarta.servlet.ServletOutputStream;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class MatchingFeedbackControllerTest {

    @Test
    void submitCallsFacadeAndReturnsOk() {
        FeedbackApiFacade facade = mock(FeedbackApiFacade.class);
        MatchingFeedbackController controller = new MatchingFeedbackController(facade);

        FeedbackDatasetRequest request = new FeedbackDatasetRequest(
                5001L, new BigDecimal("85.50"), "补充说明", "[\"能力匹配\"]", 1);

        R<Void> response = controller.submit(request);

        verify(facade).submit(request);
        assertThat(response.getCode()).isEqualTo(200);
    }

    @Test
    void pageReturnsFacadePage() {
        FeedbackApiFacade facade = mock(FeedbackApiFacade.class);
        MatchingFeedbackController controller = new MatchingFeedbackController(facade);

        FeedbackDatasetResponse item = new FeedbackDatasetResponse(
                1L, 5001L, 10001L, 20001L, new BigDecimal("80.00"), new BigDecimal("85.50"),
                1, 1, "[\"能力匹配\"]", "补充说明", 1, LocalDateTime.of(2024, 1, 10, 9, 0));
        PageResponse<FeedbackDatasetResponse> page = new PageResponse<>(List.of(item), 1, 1, 10, 1);
        when(facade.page(1, 10, 1)).thenReturn(page);

        R<PageResponse<FeedbackDatasetResponse>> response = controller.page(1, 10, 1);

        assertThat(response.getData()).isEqualTo(page);
        assertThat(response.getData().records()).containsExactly(item);
    }

    @Test
    void summaryReturnsFacadeSummary() {
        FeedbackApiFacade facade = mock(FeedbackApiFacade.class);
        MatchingFeedbackController controller = new MatchingFeedbackController(facade);

        Map<String, Object> summary = Map.of("adoptedCount", 10L, "avgDeviation", new BigDecimal("2.5"));
        when(facade.summary(100)).thenReturn(summary);

        R<Map<String, Object>> response = controller.summary(100);

        assertThat(response.getData()).isEqualTo(summary);
    }

    @Test
    void examplesReturnsFacadeSamples() {
        FeedbackApiFacade facade = mock(FeedbackApiFacade.class);
        MatchingFeedbackController controller = new MatchingFeedbackController(facade);

        when(facade.examples(5)).thenReturn(List.of("sample-1", "sample-2"));

        R<List<String>> response = controller.examples(5);

        assertThat(response.getData()).containsExactly("sample-1", "sample-2");
    }

    @Test
    void exportFeedbackWritesExcelWhenExportSucceeds() throws Exception {
        FeedbackApiFacade facade = mock(FeedbackApiFacade.class);
        MatchingFeedbackController controller = new MatchingFeedbackController(facade);

        MatchingFeedbackExportDTO dto = new MatchingFeedbackExportDTO();
        dto.setId(1L);
        dto.setMatchingRecordId(5001L);
        when(facade.export(1)).thenReturn(List.of(dto));

        HttpServletResponse response = mock(HttpServletResponse.class);
        ServletOutputStream sos = mock(ServletOutputStream.class);
        when(response.getOutputStream()).thenReturn(sos);

        controller.exportFeedback(1, response);

        verify(response).setContentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
        verify(facade).export(1);
    }

    @Test
    void exportFeedbackThrowsBusinessExceptionWhenExportFails() {
        FeedbackApiFacade facade = mock(FeedbackApiFacade.class);
        MatchingFeedbackController controller = new MatchingFeedbackController(facade);

        HttpServletResponse response = mock(HttpServletResponse.class);
        when(facade.export(1)).thenThrow(new IllegalStateException("boom"));

        assertThatThrownBy(() -> controller.exportFeedback(1, response))
                .isInstanceOf(BusinessException.class)
                .satisfies(e -> {
                    BusinessException ex = (BusinessException) e;
                    assertThat(ex.getCode()).isEqualTo(ErrorCodeEnum.EXPORT_ERROR.getCode());
                    assertThat(ex.getMessage()).isEqualTo("导出失败");
                });
    }

    @Test
    void trendReturnsFacadeTrend() {
        FeedbackApiFacade facade = mock(FeedbackApiFacade.class);
        MatchingFeedbackController controller = new MatchingFeedbackController(facade);

        Map<String, Object> trend = Map.of("2024-01-10", 3L);
        when(facade.trend(30)).thenReturn(trend);

        R<Map<String, Object>> response = controller.trend(30);

        assertThat(response.getData()).isEqualTo(trend);
    }

    @Test
    void deviationDistributionReturnsFacadeDistribution() {
        FeedbackApiFacade facade = mock(FeedbackApiFacade.class);
        MatchingFeedbackController controller = new MatchingFeedbackController(facade);

        Map<String, Object> distribution = Map.of("0-5", 8L, "5-10", 2L);
        when(facade.deviationDistribution(100)).thenReturn(distribution);

        R<Map<String, Object>> response = controller.deviationDistribution(100);

        assertThat(response.getData()).isEqualTo(distribution);
    }

    @Test
    void calibrationReplayReturnsFacadeSummary() {
        FeedbackApiFacade facade = mock(FeedbackApiFacade.class);
        MatchingFeedbackController controller = new MatchingFeedbackController(facade);

        Map<String, Object> replay = Map.of("items", List.of());
        when(facade.calibrationReplay(100)).thenReturn(replay);

        R<Map<String, Object>> response = controller.calibrationReplay(100);

        assertThat(response.getData()).isEqualTo(replay);
    }

    @Test
    void batchUpdateExportStatusCallsFacadeAndReturnsOk() {
        FeedbackApiFacade facade = mock(FeedbackApiFacade.class);
        MatchingFeedbackController controller = new MatchingFeedbackController(facade);

        R<Void> response = controller.batchUpdateExportStatus(List.of(1L, 2L), 1);

        verify(facade).batchUpdateExportStatus(List.of(1L, 2L), 1);
        assertThat(response.getCode()).isEqualTo(200);
    }
}
