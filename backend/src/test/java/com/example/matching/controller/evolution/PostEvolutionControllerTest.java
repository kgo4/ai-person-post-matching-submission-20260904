package com.example.matching.controller.evolution;

import com.example.matching.application.evolution.PostEvolutionApiFacade;
import com.example.matching.common.dto.PageResponse;
import com.example.matching.common.result.R;
import com.example.matching.dto.evolution.AgentProgressVO;
import com.example.matching.dto.evolution.CloudSyncRequest;
import com.example.matching.dto.evolution.EvolutionSourceUploadDTO;
import com.example.matching.dto.evolution.PostEvolutionAgentRequest;
import com.example.matching.dto.evolution.PostEvolutionReviewDTO;
import com.example.matching.dto.evolution.api.EvolutionTaskRequest;
import com.example.matching.dto.evolution.api.PostEvolutionChangeItemResponse;
import com.example.matching.dto.evolution.api.PostEvolutionEvidenceResponse;
import com.example.matching.dto.evolution.api.PostEvolutionScheduleConfigResponse;
import com.example.matching.dto.evolution.api.PostEvolutionTaskResponse;
import com.example.matching.dto.evolution.api.ScheduleConfigRequest;
import org.junit.jupiter.api.Test;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PostEvolutionControllerTest {

    private final PostEvolutionApiFacade facade = mock(PostEvolutionApiFacade.class);
    private final PostEvolutionController controller = new PostEvolutionController(facade);

    @Test
    void uploadIndustryWhitepaperReturnsResult() throws IOException {
        MultipartFile file = mock(MultipartFile.class);
        when(file.getOriginalFilename()).thenReturn("wp.pdf");
        when(file.getBytes()).thenReturn(new byte[]{1, 2, 3});
        when(facade.uploadIndustryWhitepaper(eq("wp.pdf"), any(byte[].class), any(EvolutionSourceUploadDTO.class), eq(0L)))
                .thenReturn(Map.of("documentId", 1L));

        R<Map<String, Object>> response = controller.uploadIndustryWhitepaper(
                file, "标题", "IT", "域", "HIGH", true, 0L);

        assertThat(response.getCode()).isEqualTo(200);
        assertThat(response.getData()).containsEntry("documentId", 1L);
    }

    @Test
    void uploadIndustryWhitepaperThrowsOnIoError() throws IOException {
        MultipartFile file = mock(MultipartFile.class);
        when(file.getBytes()).thenThrow(new IOException("boom"));

        assertThatThrownBy(() -> controller.uploadIndustryWhitepaper(
                file, "标题", "IT", null, "HIGH", true, 0L))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Unable to read uploaded document");
    }

    @Test
    void uploadInternalDocumentReturnsResult() throws IOException {
        MultipartFile file = mock(MultipartFile.class);
        when(file.getOriginalFilename()).thenReturn("doc.docx");
        when(file.getBytes()).thenReturn(new byte[]{9});
        when(facade.uploadInternalDocument(eq("doc.docx"), any(byte[].class), any(EvolutionSourceUploadDTO.class), eq(0L)))
                .thenReturn(Map.of("documentId", 2L));

        R<Map<String, Object>> response = controller.uploadInternalDocument(
                file, "内部资料", "INTERNAL_BUSINESS_UPDATE", null, null, "MEDIUM", true, 0L);

        assertThat(response.getCode()).isEqualTo(200);
        assertThat(response.getData()).containsEntry("documentId", 2L);
    }

    @Test
    void uploadInternalDocumentThrowsOnIoError() throws IOException {
        MultipartFile file = mock(MultipartFile.class);
        when(file.getBytes()).thenThrow(new IOException("boom"));

        assertThatThrownBy(() -> controller.uploadInternalDocument(
                file, "内部资料", null, null, null, "MEDIUM", true, 0L))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void syncCloudKnowledgeReturnsResult() {
        CloudSyncRequest request = mock(CloudSyncRequest.class);
        when(facade.syncCloudKnowledge(request)).thenReturn(Map.of("synced", 3));

        R<Map<String, Object>> response = controller.syncCloudKnowledge(request);

        assertThat(response.getData()).containsEntry("synced", 3);
    }

    @Test
    void indexKnowledgeSourceReturnsResult() {
        when(facade.indexKnowledgeSource(5L)).thenReturn(Map.of("indexed", true));

        R<Map<String, Object>> response = controller.indexKnowledgeSource(5L);

        assertThat(response.getData()).containsEntry("indexed", true);
    }

    @Test
    void runAgentReturnsResult() {
        PostEvolutionAgentRequest request = mock(PostEvolutionAgentRequest.class);
        when(facade.runAgent(request)).thenReturn(Map.of("taskId", 1L));

        R<Map<String, Object>> response = controller.runAgent(request);

        assertThat(response.getData()).containsEntry("taskId", 1L);
    }

    @Test
    void getAgentProgressReturnsProgress() {
        AgentProgressVO expected = mock(AgentProgressVO.class);
        when(facade.getAgentProgress(1L)).thenReturn(expected);

        R<AgentProgressVO> response = controller.getAgentProgress(1L);

        assertThat(response.getData()).isSameAs(expected);
    }

    @Test
    void createScheduleReturnsConfig() {
        ScheduleConfigRequest request = mock(ScheduleConfigRequest.class);
        PostEvolutionScheduleConfigResponse expected = mock(PostEvolutionScheduleConfigResponse.class);
        when(facade.createSchedule(request, 1L)).thenReturn(expected);

        R<PostEvolutionScheduleConfigResponse> response = controller.createSchedule(request, 1L);

        assertThat(response.getData()).isSameAs(expected);
    }

    @Test
    void updateScheduleReturnsConfig() {
        ScheduleConfigRequest request = mock(ScheduleConfigRequest.class);
        PostEvolutionScheduleConfigResponse expected = mock(PostEvolutionScheduleConfigResponse.class);
        when(facade.updateSchedule(5L, request)).thenReturn(expected);

        R<PostEvolutionScheduleConfigResponse> response = controller.updateSchedule(5L, request);

        assertThat(response.getData()).isSameAs(expected);
    }

    @Test
    void pageSchedulesReturnsPage() {
        PageResponse<PostEvolutionScheduleConfigResponse> expected = new PageResponse<>(List.of(), 0, 1, 10, 0);
        when(facade.pageSchedules(1L, 10L, 3L)).thenReturn(expected);

        R<PageResponse<PostEvolutionScheduleConfigResponse>> response = controller.pageSchedules(1L, 10L, 3L);

        assertThat(response.getData()).isSameAs(expected);
    }

    @Test
    void getScheduleReturnsConfig() {
        PostEvolutionScheduleConfigResponse expected = mock(PostEvolutionScheduleConfigResponse.class);
        when(facade.getSchedule(5L)).thenReturn(expected);

        R<PostEvolutionScheduleConfigResponse> response = controller.getSchedule(5L);

        assertThat(response.getData()).isSameAs(expected);
    }

    @Test
    void deleteScheduleReturnsOk() {
        R<Void> response = controller.deleteSchedule(5L);

        assertThat(response.getCode()).isEqualTo(200);
        verify(facade).deleteSchedule(5L);
    }

    @Test
    void runScheduleNowReturnsResult() {
        when(facade.runScheduleNow(5L)).thenReturn(Map.of("run", true));

        R<Map<String, Object>> response = controller.runScheduleNow(5L);

        assertThat(response.getData()).containsEntry("run", true);
    }

    @Test
    void createTaskReturnsTask() {
        EvolutionTaskRequest request = mock(EvolutionTaskRequest.class);
        PostEvolutionTaskResponse expected = mock(PostEvolutionTaskResponse.class);
        when(facade.createTask(request, 1L)).thenReturn(expected);

        R<PostEvolutionTaskResponse> response = controller.createTask(request, 1L);

        assertThat(response.getData()).isSameAs(expected);
    }

    @Test
    void analyzeTaskReturnsTask() {
        PostEvolutionTaskResponse expected = mock(PostEvolutionTaskResponse.class);
        when(facade.analyzeTask(7L)).thenReturn(expected);

        R<PostEvolutionTaskResponse> response = controller.analyzeTask(7L);

        assertThat(response.getData()).isSameAs(expected);
    }

    @Test
    void pageTasksReturnsPage() {
        PageResponse<PostEvolutionTaskResponse> expected = new PageResponse<>(List.of(), 0, 1, 10, 0);
        when(facade.pageTasks(1L, 10L, 3L, "RUNNING")).thenReturn(expected);

        R<PageResponse<PostEvolutionTaskResponse>> response = controller.pageTasks(1L, 10L, 3L, "RUNNING");

        assertThat(response.getData()).isSameAs(expected);
    }

    @Test
    void getTaskReturnsTask() {
        PostEvolutionTaskResponse expected = mock(PostEvolutionTaskResponse.class);
        when(facade.getTask(7L)).thenReturn(expected);

        R<PostEvolutionTaskResponse> response = controller.getTask(7L);

        assertThat(response.getData()).isSameAs(expected);
    }

    @Test
    void pageChangeItemsReturnsPage() {
        PageResponse<PostEvolutionChangeItemResponse> expected = new PageResponse<>(List.of(), 0, 1, 20, 0);
        when(facade.pageChangeItems(7L, 1L, 20L)).thenReturn(expected);

        R<PageResponse<PostEvolutionChangeItemResponse>> response = controller.pageChangeItems(7L, 1L, 20L);

        assertThat(response.getData()).isSameAs(expected);
    }

    @Test
    void reviewChangeItemReturnsOk() {
        PostEvolutionReviewDTO dto = mock(PostEvolutionReviewDTO.class);

        R<Void> response = controller.reviewChangeItem(7L, 9L, dto);

        assertThat(response.getCode()).isEqualTo(200);
        verify(facade).reviewChangeItem(7L, 9L, dto);
    }

    @Test
    void applyApprovedChangesReturnsResult() {
        when(facade.applyApprovedChanges(7L)).thenReturn(Map.of("applied", 2));

        R<Map<String, Object>> response = controller.applyApprovedChanges(7L);

        assertThat(response.getData()).containsEntry("applied", 2);
    }

    @Test
    void getTaskEvidenceReturnsList() {
        PostEvolutionEvidenceResponse evidence = mock(PostEvolutionEvidenceResponse.class);
        when(facade.getTaskEvidence(7L)).thenReturn(List.of(evidence));

        R<List<PostEvolutionEvidenceResponse>> response = controller.getTaskEvidence(7L);

        assertThat(response.getData()).containsExactly(evidence);
    }

    @Test
    void getItemEvidenceReturnsList() {
        PostEvolutionEvidenceResponse evidence = mock(PostEvolutionEvidenceResponse.class);
        when(facade.getItemEvidence(9L)).thenReturn(List.of(evidence));

        R<List<PostEvolutionEvidenceResponse>> response = controller.getItemEvidence(9L);

        assertThat(response.getData()).containsExactly(evidence);
    }

    @Test
    void getTimelineReturnsList() {
        when(facade.getTimeline(3L, "30d", 20)).thenReturn(List.of(Map.of("event", "E")));

        R<List<Map<String, Object>>> response = controller.getTimeline(3L, "30d", 20);

        assertThat(response.getData()).hasSize(1);
    }

    @Test
    void getDashboardStatsReturnsMap() {
        when(facade.getDashboardStats("30d")).thenReturn(Map.of("total", 5));

        R<Map<String, Object>> response = controller.getDashboardStats("30d");

        assertThat(response.getData()).containsEntry("total", 5);
    }

    @Test
    void getDashboardTrendsReturnsMap() {
        when(facade.getDashboardTrends("30d")).thenReturn(Map.of("trends", List.of()));

        R<Map<String, Object>> response = controller.getDashboardTrends("30d");

        assertThat(response.getData()).containsKey("trends");
    }

    @Test
    void getEvolutionGraphReturnsMap() {
        when(facade.getEvolutionGraph(3L, null)).thenReturn(Map.of("nodes", List.of()));

        R<Map<String, Object>> response = controller.getEvolutionGraph(3L, null);

        assertThat(response.getData()).containsKey("nodes");
    }
}
