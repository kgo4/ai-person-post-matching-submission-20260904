package com.example.matching.controller.evolution;

import com.example.matching.application.evolution.MarketJdImportApiFacade;
import com.example.matching.common.dto.PageResponse;
import com.example.matching.common.result.R;
import com.example.matching.dto.evolution.api.MarketJdImportRequest;
import com.example.matching.dto.evolution.api.MarketJdResponse;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class MarketJdImportControllerTest {

    private final MarketJdImportApiFacade facade = mock(MarketJdImportApiFacade.class);
    private final MarketJdImportController controller = new MarketJdImportController(facade);

    @Test
    void importTextsReturnsSummary() {
        when(facade.importTexts(List.of("JD1"), "OTHER"))
                .thenReturn(Map.of("imported", 1));

        R<Map<String, Object>> response = controller.importTexts(List.of("JD1"), "OTHER");

        assertThat(response.getCode()).isEqualTo(200);
        assertThat(response.getData()).containsEntry("imported", 1);
    }

    @Test
    void importExcelReturnsSummary() {
        MarketJdImportRequest request = mock(MarketJdImportRequest.class);
        when(facade.importExcel(List.of(request))).thenReturn(Map.of("imported", 1));

        R<Map<String, Object>> response = controller.importExcel(List.of(request));

        assertThat(response.getData()).containsEntry("imported", 1);
    }

    @Test
    void pageMarketJdsReturnsPage() {
        PageResponse<MarketJdResponse> expected = new PageResponse<>(List.of(), 0, 1, 20, 0);
        when(facade.pageMarketJds(1L, 20L, "Java", "B1")).thenReturn(expected);

        R<PageResponse<MarketJdResponse>> response = controller.pageMarketJds(1L, 20L, "Java", "B1");

        assertThat(response.getData()).isSameAs(expected);
    }

    @Test
    void getMarketJdsByPostIdReturnsList() {
        MarketJdResponse jd = mock(MarketJdResponse.class);
        when(facade.getMarketJdsByPostId(3L, 50)).thenReturn(List.of(jd));

        R<List<MarketJdResponse>> response = controller.getMarketJdsByPostId(3L, 50);

        assertThat(response.getData()).containsExactly(jd);
    }

    @Test
    void deduplicateReturnsSummary() {
        when(facade.deduplicate("B1")).thenReturn(Map.of("removed", 2));

        R<Map<String, Object>> response = controller.deduplicate("B1");

        assertThat(response.getCode()).isEqualTo(200);
        assertThat(response.getData()).containsEntry("removed", 2);
    }

    @Test
    void getBatchStatisticsReturnsStats() throws Exception {
        Class<?> statsType = Class.forName(
                "com.example.matching.service.evolution.MarketJdImportService$BatchStatistics");
        Object stats = org.mockito.Mockito.mock(statsType);
        when(facade.getBatchStatistics("B1")).thenAnswer(invocation -> stats);

        R<?> response = controller.getBatchStatistics("B1");

        assertThat(response.getCode()).isEqualTo(200);
        assertThat(response.getData()).isNotNull();
    }

    @Test
    void analyzeBatchReturnsResult() throws Exception {
        Class<?> resultType = Class.forName(
                "com.example.matching.service.evolution.MarketJdImportService$BatchAnalysisResult");
        Object result = org.mockito.Mockito.mock(resultType);
        when(facade.analyzeBatch("B1")).thenAnswer(invocation -> result);

        R<?> response = controller.analyzeBatch("B1");

        assertThat(response.getCode()).isEqualTo(200);
        assertThat(response.getData()).isNotNull();
    }
}
