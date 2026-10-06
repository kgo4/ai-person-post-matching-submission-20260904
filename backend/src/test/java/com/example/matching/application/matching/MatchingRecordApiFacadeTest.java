package com.example.matching.application.matching;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.example.matching.service.common.ExcelService;
import com.example.matching.service.matching.MatchingExecuteService;
import com.example.matching.service.matching.MatchingRecordService;
import com.example.matching.service.matching.MatchingTaskService;
import com.example.matching.service.matching.StructuredReviewService;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class MatchingRecordApiFacadeTest {

    @Test
    void dashboardSummaryUsesTheAggregateMapperQuery() {
        MatchingRecordService recordService = mock(MatchingRecordService.class);
        when(recordService.dashboardSummary()).thenReturn(Map.of(
                "totalCount", 8L,
                "score90", 1L, "score75", 2L, "score60", 3L, "scoreBelow60", 2L,
                "status0", 0L, "status1", 1L, "status2", 2L, "status3", 3L, "status4", 2L));
        when(recordService.pageRecords(any(), isNull(), isNull(), isNull()))
                .thenReturn(new Page<>(1, 10));

        MatchingRecordApiFacade facade = new MatchingRecordApiFacade(
                recordService, mock(MatchingExecuteService.class), mock(MatchingTaskService.class),
                mock(StructuredReviewService.class), mock(ExcelService.class),
                new com.example.matching.converter.matching.MatchingRecordConverterImpl());
        Map<String, Object> summary = facade.dashboardSummary();

        assertThat(summary).containsEntry("total", 8L).containsEntry("score60", 3L)
                .containsEntry("status3", 3L);
        verify(recordService).dashboardSummary();
    }

    @Test
    void modifyResultMapsManualCorrectionToRecordAndFeedbackFields() {
        MatchingRecordService recordService = mock(MatchingRecordService.class);
        MatchingRecordApiFacade facade = new MatchingRecordApiFacade(
                recordService, mock(MatchingExecuteService.class), mock(MatchingTaskService.class),
                mock(StructuredReviewService.class), mock(ExcelService.class),
                new com.example.matching.converter.matching.MatchingRecordConverterImpl());

        facade.modifyResult(42L, new com.example.matching.dto.matching.api.ModifyResultRequest(
                new java.math.BigDecimal("76.50"), 3, "人工认为项目经验不足"));

        verify(recordService).modifyResult(eq(42L), org.mockito.ArgumentMatchers.argThat(record ->
                record.getFinalMatchScore().compareTo(new java.math.BigDecimal("76.50")) == 0
                        && record.getMatchStatus() == 3
                        && "人工认为项目经验不足".equals(record.getManualRemark())
                        && "人工认为项目经验不足".equals(record.getFeedbackComment())));
    }
}
