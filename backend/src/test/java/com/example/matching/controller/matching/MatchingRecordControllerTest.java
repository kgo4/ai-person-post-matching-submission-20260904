package com.example.matching.controller.matching;

import com.example.matching.application.matching.MatchingRecordApiFacade;
import com.example.matching.common.dto.PageResponse;
import com.example.matching.common.result.R;
import com.example.matching.dto.matching.MatchingExecuteDTO;
import com.example.matching.dto.matching.StructuredReviewDTO;
import com.example.matching.dto.matching.api.MatchingExecuteResultResponse;
import com.example.matching.dto.matching.api.MatchingRecordResponse;
import com.example.matching.dto.matching.api.MatchingTaskResponse;
import com.example.matching.dto.matching.api.ModifyResultRequest;
import jakarta.servlet.ServletOutputStream;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class MatchingRecordControllerTest {

    private static MatchingRecordResponse recordResponse(Long id) {
        return new MatchingRecordResponse(
                id, null, null, null, null, null, null, null, null, null,
                null, null, null, null, null, null, null, null, null, null,
                null, null, null, null, null, null, null, null, null, null,
                null, null, null, null, null, null, null, null, null, null,
                null, null, null, null, null
        );
    }

    private static MatchingTaskResponse taskResponse() {
        return new MatchingTaskResponse(
                1L, "task-1", 20001L, "[10001,10002]", 2, 100,
                10, 10, "完成", null, LocalDateTime.of(2024, 1, 10, 9, 0),
                LocalDateTime.of(2024, 1, 10, 9, 5));
    }

    @Test
    void executeReturnsFacadeResultWithSummaryMessage() {
        MatchingRecordApiFacade facade = mock(MatchingRecordApiFacade.class);
        MatchingRecordController controller = new MatchingRecordController(facade);

        MatchingExecuteDTO dto = new MatchingExecuteDTO();
        MatchingExecuteResultResponse result = new MatchingExecuteResultResponse(
                List.of(recordResponse(1L)), "ALL_ACTIVE", 10, 100, false, null, false, 0);
        when(facade.execute(dto)).thenReturn(result);

        R<MatchingExecuteResultResponse> response = controller.execute(dto);

        assertThat(response.getData()).isEqualTo(result);
        assertThat(response.getMessage()).isEqualTo("匹配完成，共处理1条记录");
    }

    @Test
    void executeAsyncReturnsTaskIdMap() {
        MatchingRecordApiFacade facade = mock(MatchingRecordApiFacade.class);
        MatchingRecordController controller = new MatchingRecordController(facade);

        MatchingExecuteDTO dto = new MatchingExecuteDTO();
        Map<String, String> result = Map.of("taskId", "task-9");
        when(facade.executeAsync(dto)).thenReturn(result);

        R<Map<String, String>> response = controller.executeAsync(dto);

        assertThat(response.getData()).isEqualTo(result);
        assertThat(response.getMessage()).isEqualTo("匹配任务已提交");
    }

    @Test
    void getTaskStatusReturnsTaskWhenFound() {
        MatchingRecordApiFacade facade = mock(MatchingRecordApiFacade.class);
        MatchingRecordController controller = new MatchingRecordController(facade);

        MatchingTaskResponse task = taskResponse();
        when(facade.getTaskStatus("task-1")).thenReturn(task);

        R<MatchingTaskResponse> response = controller.getTaskStatus("task-1");

        assertThat(response.getData()).isEqualTo(task);
    }

    @Test
    void getTaskStatusFailsWhenTaskMissing() {
        MatchingRecordApiFacade facade = mock(MatchingRecordApiFacade.class);
        MatchingRecordController controller = new MatchingRecordController(facade);

        when(facade.getTaskStatus("missing")).thenReturn(null);

        R<MatchingTaskResponse> response = controller.getTaskStatus("missing");

        assertThat(response.getData()).isNull();
        assertThat(response.getMessage()).isEqualTo("任务不存在");
    }

    @Test
    void cancelTaskReturnsOkWhenCancelled() {
        MatchingRecordApiFacade facade = mock(MatchingRecordApiFacade.class);
        MatchingRecordController controller = new MatchingRecordController(facade);

        when(facade.cancelMatchingTask("task-1")).thenReturn(true);

        R<Void> response = controller.cancelTask("task-1");

        assertThat(response.getCode()).isEqualTo(200);
    }

    @Test
    void cancelTaskFailsWhenNotCancelled() {
        MatchingRecordApiFacade facade = mock(MatchingRecordApiFacade.class);
        MatchingRecordController controller = new MatchingRecordController(facade);

        when(facade.cancelMatchingTask("task-1")).thenReturn(false);

        R<Void> response = controller.cancelTask("task-1");

        assertThat(response.getCode()).isEqualTo(500);
        assertThat(response.getMessage()).isEqualTo("任务不存在或已处于终态，无法取消");
    }

    @Test
    void deleteTaskReturnsOkWhenDeleted() {
        MatchingRecordApiFacade facade = mock(MatchingRecordApiFacade.class);
        MatchingRecordController controller = new MatchingRecordController(facade);

        when(facade.deleteMatchingTask("task-1")).thenReturn(true);

        R<Void> response = controller.deleteTask("task-1");

        assertThat(response.getCode()).isEqualTo(200);
    }

    @Test
    void deleteTaskFailsWhenNotDeleted() {
        MatchingRecordApiFacade facade = mock(MatchingRecordApiFacade.class);
        MatchingRecordController controller = new MatchingRecordController(facade);

        when(facade.deleteMatchingTask("task-1")).thenReturn(false);

        R<Void> response = controller.deleteTask("task-1");

        assertThat(response.getCode()).isEqualTo(500);
        assertThat(response.getMessage()).isEqualTo("任务不存在，无法删除");
    }

    @Test
    void pageTasksClampsCurrentAndSize() {
        MatchingRecordApiFacade facade = mock(MatchingRecordApiFacade.class);
        MatchingRecordController controller = new MatchingRecordController(facade);

        PageResponse<MatchingTaskResponse> page = new PageResponse<>(List.of(taskResponse()), 1, 1, 200, 1);
        when(facade.pageMatchingTasks(1, 200, 2)).thenReturn(page);

        R<PageResponse<MatchingTaskResponse>> response = controller.pageTasks(0, 1000, 2);

        verify(facade).pageMatchingTasks(1, 200, 2);
        assertThat(response.getData()).isEqualTo(page);
    }

    @Test
    void pageReturnsFacadePage() {
        MatchingRecordApiFacade facade = mock(MatchingRecordApiFacade.class);
        MatchingRecordController controller = new MatchingRecordController(facade);

        MatchingRecordResponse record = recordResponse(1L);
        PageResponse<MatchingRecordResponse> page = new PageResponse<>(List.of(record), 1, 1, 10, 1);
        when(facade.page(1, 10, 20001L, 10001L, 2)).thenReturn(page);

        R<PageResponse<MatchingRecordResponse>> response = controller.page(1, 10, 20001L, 10001L, 2);

        assertThat(response.getData()).isEqualTo(page);
        assertThat(response.getData().records()).containsExactly(record);
    }

    @Test
    void dashboardSummaryReturnsFacadeSummary() {
        MatchingRecordApiFacade facade = mock(MatchingRecordApiFacade.class);
        MatchingRecordController controller = new MatchingRecordController(facade);

        Map<String, Object> summary = Map.of("total", 100L, "recent", List.of());
        when(facade.dashboardSummary()).thenReturn(summary);

        R<Map<String, Object>> response = controller.dashboardSummary();

        assertThat(response.getData()).isEqualTo(summary);
    }

    @Test
    void getByIdReturnsFacadeRecord() {
        MatchingRecordApiFacade facade = mock(MatchingRecordApiFacade.class);
        MatchingRecordController controller = new MatchingRecordController(facade);

        MatchingRecordResponse record = recordResponse(1L);
        when(facade.getById(1L)).thenReturn(record);

        R<MatchingRecordResponse> response = controller.getById(1L);

        assertThat(response.getData()).isEqualTo(record);
    }

    @Test
    void modifyResultCallsFacadeAndReturnsOk() {
        MatchingRecordApiFacade facade = mock(MatchingRecordApiFacade.class);
        MatchingRecordController controller = new MatchingRecordController(facade);

        ModifyResultRequest request = new ModifyResultRequest(new BigDecimal("85.50"), 2, "人工修正");
        R<Void> response = controller.modifyResult(1L, request);

        verify(facade).modifyResult(1L, request);
        assertThat(response.getCode()).isEqualTo(200);
    }

    @Test
    void structuredReviewCallsFacadeAndReturnsOk() {
        MatchingRecordApiFacade facade = mock(MatchingRecordApiFacade.class);
        MatchingRecordController controller = new MatchingRecordController(facade);

        StructuredReviewDTO request = new StructuredReviewDTO();
        request.setMatchingRecordId(1L);
        R<Void> response = controller.structuredReview(request);

        verify(facade).submitStructuredReview(request);
        assertThat(response.getCode()).isEqualTo(200);
    }

    @Test
    void retryAiScoringReturnsOkWhenReset() {
        MatchingRecordApiFacade facade = mock(MatchingRecordApiFacade.class);
        MatchingRecordController controller = new MatchingRecordController(facade);

        when(facade.retryAiScoring(1L)).thenReturn(true);

        R<Void> response = controller.retryAiScoring(1L);

        assertThat(response.getCode()).isEqualTo(200);
    }

    @Test
    void retryAiScoringFailsWhenNotAllowed() {
        MatchingRecordApiFacade facade = mock(MatchingRecordApiFacade.class);
        MatchingRecordController controller = new MatchingRecordController(facade);

        when(facade.retryAiScoring(1L)).thenReturn(false);

        R<Void> response = controller.retryAiScoring(1L);

        assertThat(response.getCode()).isEqualTo(500);
        assertThat(response.getMessage()).isEqualTo("状态不允许重试（已完成/评分中/已锁定）");
    }

    @Test
    void lockCallsFacadeAndReturnsOk() {
        MatchingRecordApiFacade facade = mock(MatchingRecordApiFacade.class);
        MatchingRecordController controller = new MatchingRecordController(facade);

        R<Void> response = controller.lock(1L);

        verify(facade).lockResult(1L);
        assertThat(response.getCode()).isEqualTo(200);
    }

    @Test
    void unlockCallsFacadeAndReturnsOk() {
        MatchingRecordApiFacade facade = mock(MatchingRecordApiFacade.class);
        MatchingRecordController controller = new MatchingRecordController(facade);

        R<Void> response = controller.unlock(1L);

        verify(facade).unlockResult(1L);
        assertThat(response.getCode()).isEqualTo(200);
    }

    @Test
    void reportReturnsFacadeReport() {
        MatchingRecordApiFacade facade = mock(MatchingRecordApiFacade.class);
        MatchingRecordController controller = new MatchingRecordController(facade);

        when(facade.generateReport(1L)).thenReturn("量化报告");

        R<String> response = controller.report(1L);

        assertThat(response.getData()).isEqualTo("量化报告");
    }

    @Test
    void aiReportReturnsFacadeAiReport() {
        MatchingRecordApiFacade facade = mock(MatchingRecordApiFacade.class);
        MatchingRecordController controller = new MatchingRecordController(facade);

        when(facade.generateAiReport(1L)).thenReturn("AI分析报告");

        R<String> response = controller.aiReport(1L);

        assertThat(response.getData()).isEqualTo("AI分析报告");
    }

    @Test
    void deleteCallsFacadeAndReturnsOk() {
        MatchingRecordApiFacade facade = mock(MatchingRecordApiFacade.class);
        MatchingRecordController controller = new MatchingRecordController(facade);

        R<Void> response = controller.delete(1L);

        verify(facade).deleteRecord(1L);
        assertThat(response.getCode()).isEqualTo(200);
    }

    @Test
    void exportExcelWritesFacadeBytes() throws Exception {
        MatchingRecordApiFacade facade = mock(MatchingRecordApiFacade.class);
        MatchingRecordController controller = new MatchingRecordController(facade);

        byte[] excelBytes = new byte[]{1, 2, 3, 4};
        when(facade.exportExcel(20001L)).thenReturn(excelBytes);

        HttpServletResponse response = mock(HttpServletResponse.class);
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        ServletOutputStream sos = new ServletOutputStream() {
            @Override
            public boolean isReady() {
                return true;
            }

            @Override
            public void setWriteListener(jakarta.servlet.WriteListener writeListener) {
            }

            @Override
            public void write(int b) {
                baos.write(b);
            }
        };
        when(response.getOutputStream()).thenReturn(sos);

        controller.exportExcel(20001L, response);

        assertThat(baos.toByteArray()).isEqualTo(excelBytes);
    }
}
