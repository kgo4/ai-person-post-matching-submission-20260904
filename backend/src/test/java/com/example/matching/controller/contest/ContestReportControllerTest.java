package com.example.matching.controller.contest;

import com.example.matching.application.contest.ContestReportApiFacade;
import com.example.matching.common.result.R;
import com.example.matching.dto.contest.api.ContestReportTaskResponse;
import com.example.matching.dto.contest.api.CreateContestReportRequest;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ContestReportControllerTest {

    private static ContestReportTaskResponse task(Long id, String status) {
        return new ContestReportTaskResponse(
                id, "TASK-001", "CAPABILITY", status, "能力盘点报告",
                "# 报告", "{}", null, "AUTO", "gpt-4o", "v1",
                null, "VALID", null, 1000L, 200, "MATCH_GAP", 3,
                100L, null, null
        );
    }

    @Test
    void getReportTypesReturnsTypeList() {
        ContestReportApiFacade facade = mock(ContestReportApiFacade.class);
        ContestReportController controller = new ContestReportController(facade);
        when(facade.getReportTypes()).thenReturn(List.of(
                Map.of("type", "CAPABILITY", "label", "能力盘点", "title", "能力盘点报告")
        ));

        R<List<Map<String, Object>>> response = controller.getReportTypes();

        assertThat(response.getCode()).isEqualTo(200);
        assertThat(response.getData()).hasSize(1);
        assertThat(response.getData().get(0)).containsEntry("type", "CAPABILITY");
    }

    @Test
    void generateReportReturnsTaskResponse() {
        ContestReportApiFacade facade = mock(ContestReportApiFacade.class);
        ContestReportController controller = new ContestReportController(facade);
        CreateContestReportRequest req = new CreateContestReportRequest("CAPABILITY", "能力盘点报告");
        when(facade.generateReport(any(), any())).thenReturn(task(1L, "RUNNING"));

        R<ContestReportTaskResponse> response = controller.generateReport(req);

        assertThat(response.getCode()).isEqualTo(200);
        assertThat(response.getData().id()).isEqualTo(1L);
        assertThat(response.getData().reportType()).isEqualTo("CAPABILITY");
    }

    @Test
    void retryReportReturnsTaskResponse() {
        ContestReportApiFacade facade = mock(ContestReportApiFacade.class);
        ContestReportController controller = new ContestReportController(facade);
        when(facade.retryReport(anyLong(), any())).thenReturn(task(2L, "RUNNING"));

        R<ContestReportTaskResponse> response = controller.retryReport(2L);

        assertThat(response.getCode()).isEqualTo(200);
        assertThat(response.getData().id()).isEqualTo(2L);
        assertThat(response.getData().taskStatus()).isEqualTo("RUNNING");
    }

    @Test
    void getReportTaskPageReturnsPage() {
        ContestReportApiFacade facade = mock(ContestReportApiFacade.class);
        ContestReportController controller = new ContestReportController(facade);
        when(facade.getReportTaskPage(any(), any(), any())).thenReturn(
                Map.of("records", List.of(), "total", 0L, "current", 1, "size", 10)
        );

        R<Map<String, Object>> response = controller.getReportTaskPage("CAPABILITY", 1, 10);

        assertThat(response.getCode()).isEqualTo(200);
        assertThat(response.getData()).containsEntry("total", 0L);
    }

    @Test
    void getReportTaskByIdReturnsTaskResponse() {
        ContestReportApiFacade facade = mock(ContestReportApiFacade.class);
        ContestReportController controller = new ContestReportController(facade);
        when(facade.getReportTaskById(anyLong())).thenReturn(task(3L, "SUCCEEDED"));

        R<ContestReportTaskResponse> response = controller.getReportTaskById(3L);

        assertThat(response.getCode()).isEqualTo(200);
        assertThat(response.getData().taskStatus()).isEqualTo("SUCCEEDED");
    }

    @Test
    void getReportEvidenceReturnsEvidenceList() {
        ContestReportApiFacade facade = mock(ContestReportApiFacade.class);
        ContestReportController controller = new ContestReportController(facade);
        when(facade.getReportEvidence(anyLong())).thenReturn(List.of(
                Map.of("evidenceCode", "EV-1001", "evidenceStatus", "VERIFIED")
        ));

        R<List<Map<String, Object>>> response = controller.getReportEvidence(3L);

        assertThat(response.getCode()).isEqualTo(200);
        assertThat(response.getData()).hasSize(1);
        assertThat(response.getData().get(0)).containsEntry("evidenceStatus", "VERIFIED");
    }

    @Test
    void exportReportReturnsMarkdown() {
        ContestReportApiFacade facade = mock(ContestReportApiFacade.class);
        ContestReportController controller = new ContestReportController(facade);
        when(facade.exportReport(anyLong(), anyString())).thenReturn("# 导出的报告内容");

        R<String> response = controller.exportReport(3L, "md");

        assertThat(response.getCode()).isEqualTo(200);
        assertThat(response.getData()).isEqualTo("# 导出的报告内容");
    }

    @Test
    void getSubmissionChecklistReturnsMap() {
        ContestReportApiFacade facade = mock(ContestReportApiFacade.class);
        ContestReportController controller = new ContestReportController(facade);
        when(facade.getSubmissionChecklist()).thenReturn(Map.of("evidenceReady", true, "ragReady", false));

        R<Map<String, Object>> response = controller.getSubmissionChecklist();

        assertThat(response.getCode()).isEqualTo(200);
        assertThat(response.getData()).containsEntry("evidenceReady", true);
    }
}
