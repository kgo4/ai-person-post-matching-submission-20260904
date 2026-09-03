package com.example.matching.controller.learning;

import com.example.matching.application.learning.LearningApiFacade;
import com.example.matching.common.dto.PageResponse;
import com.example.matching.common.exception.BusinessException;
import com.example.matching.common.result.R;
import com.example.matching.dto.learning.AiLearningSuggestionDTO;
import com.example.matching.dto.learning.LearningAssessmentGenerateRequest;
import com.example.matching.dto.learning.LearningPathGenerateRequest;
import com.example.matching.dto.learning.LearningPathItemDTO;
import com.example.matching.dto.learning.LearningPathPlanVO;
import com.example.matching.dto.learning.LearningProjectReviewDTO;
import com.example.matching.dto.learning.LearningProjectSubmitDTO;
import com.example.matching.dto.learning.LearningProjectTaskVO;
import com.example.matching.dto.learning.LearningResourceSaveDTO;
import com.example.matching.dto.learning.LearningStepStatusUpdateRequest;
import com.example.matching.dto.learning.api.LearningAssessmentItemResponse;
import com.example.matching.dto.learning.api.LearningProjectSubmissionResponse;
import com.example.matching.dto.learning.api.LearningResourceResponse;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class LearningControllerTest {

    private final LearningApiFacade facade = mock(LearningApiFacade.class);
    private final LearningController controller = new LearningController(facade);

    @Test
    void saveResourceReturnsResponse() {
        LearningResourceSaveDTO dto = mock(LearningResourceSaveDTO.class);
        LearningResourceResponse expected = mock(LearningResourceResponse.class);
        when(facade.saveResource(dto)).thenReturn(expected);

        R<LearningResourceResponse> response = controller.saveResource(dto);

        assertThat(response.getCode()).isEqualTo(200);
        assertThat(response.getData()).isSameAs(expected);
        verify(facade).saveResource(dto);
    }

    @Test
    void pageResourcesReturnsPage() {
        PageResponse<LearningResourceResponse> expected = new PageResponse<>(List.of(), 0, 1, 10, 0);
        when(facade.pageResources(1L, 10L, "Java", 5L, "VIDEO", "B站", "keyword", 1))
                .thenReturn(expected);

        R<PageResponse<LearningResourceResponse>> response =
                controller.pageResources(1L, 10L, "Java", 5L, "VIDEO", "B站", "keyword", 1);

        assertThat(response.getCode()).isEqualTo(200);
        assertThat(response.getData()).isSameAs(expected);
    }

    @Test
    void getResourceReturnsResponse() {
        LearningResourceResponse expected = mock(LearningResourceResponse.class);
        when(facade.getResource(42L)).thenReturn(expected);

        R<LearningResourceResponse> response = controller.getResource(42L);

        assertThat(response.getData()).isSameAs(expected);
    }

    @Test
    void deleteResourceReturnsOk() {
        R<Void> response = controller.deleteResource(42L);

        assertThat(response.getCode()).isEqualTo(200);
        verify(facade).deleteResource(42L);
    }

    @Test
    void uploadResourceCoverReturnsUrl() throws IOException {
        MultipartFile file = mock(MultipartFile.class);
        byte[] bytes = new byte[]{1, 2, 3};
        when(file.getBytes()).thenReturn(bytes);
        when(file.getContentType()).thenReturn("image/png");
        when(facade.uploadCover(any())).thenReturn("http://cover/1.png");

        R<String> response = controller.uploadResourceCover(file);

        assertThat(response.getCode()).isEqualTo(200);
        assertThat(response.getData()).isEqualTo("http://cover/1.png");
    }

    @Test
    void uploadResourceCoverThrowsBusinessExceptionOnIoError() throws IOException {
        MultipartFile file = mock(MultipartFile.class);
        when(file.getBytes()).thenThrow(new IOException("boom"));

        assertThatThrownBy(() -> controller.uploadResourceCover(file))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("读取上传文件失败");
    }

    @Test
    void updateResourceStatusReturnsOk() {
        R<Void> response = controller.updateResourceStatus(42L, 1);

        assertThat(response.getCode()).isEqualTo(200);
        verify(facade).updateResourceStatus(42L, 1);
    }

    @Test
    void batchUpdateResourceStatusReturnsOk() {
        List<Long> ids = List.of(1L, 2L);
        R<Void> response = controller.batchUpdateResourceStatus(ids, 1);

        assertThat(response.getCode()).isEqualTo(200);
        verify(facade).batchUpdateResourceStatus(ids, 1);
    }

    @Test
    void batchDeleteResourcesReturnsOk() {
        List<Long> ids = List.of(1L, 2L, 3L);
        R<Void> response = controller.batchDeleteResources(ids);

        assertThat(response.getCode()).isEqualTo(200);
        verify(facade).batchDeleteResources(ids);
    }

    @Test
    void getLearningPathReturnsItems() {
        LearningPathItemDTO item = mock(LearningPathItemDTO.class);
        when(facade.generateLearningPath(any())).thenReturn(List.of(item));

        R<List<LearningPathItemDTO>> response =
                controller.getLearningPath(List.of("Java"), 2, 4);

        assertThat(response.getCode()).isEqualTo(200);
        assertThat(response.getData()).containsExactly(item);
    }

    @Test
    void generateAiSuggestionsReturnsSuggestions() {
        AiLearningSuggestionDTO.Request request = mock(AiLearningSuggestionDTO.Request.class);
        AiLearningSuggestionDTO.Response expected = mock(AiLearningSuggestionDTO.Response.class);
        when(facade.generateAiSuggestions(request)).thenReturn(expected);

        R<AiLearningSuggestionDTO.Response> response = controller.generateAiSuggestions(request);

        assertThat(response.getCode()).isEqualTo(200);
        assertThat(response.getData()).isSameAs(expected);
    }

    @Test
    void getCachedAiSuggestionsReturnsList() {
        AiLearningSuggestionDTO.Response expected = mock(AiLearningSuggestionDTO.Response.class);
        when(facade.getCachedAiSuggestions(7L)).thenReturn(List.of(expected));

        R<List<AiLearningSuggestionDTO.Response>> response = controller.getCachedAiSuggestions(7L);

        assertThat(response.getData()).containsExactly(expected);
    }

    @Test
    void generatePathReturnsPlan() {
        LearningPathGenerateRequest request = mock(LearningPathGenerateRequest.class);
        LearningPathPlanVO expected = mock(LearningPathPlanVO.class);
        when(facade.generatePath(request)).thenReturn(expected);

        R<LearningPathPlanVO> response = controller.generatePath(request);

        assertThat(response.getData()).isSameAs(expected);
    }

    @Test
    void getPathReturnsPlan() {
        LearningPathPlanVO expected = mock(LearningPathPlanVO.class);
        when(facade.getPath(9L)).thenReturn(expected);

        R<LearningPathPlanVO> response = controller.getPath(9L);

        assertThat(response.getData()).isSameAs(expected);
    }

    @Test
    void getPathByMatchReturnsPlan() {
        LearningPathPlanVO expected = mock(LearningPathPlanVO.class);
        when(facade.getPathByMatch(88L)).thenReturn(expected);

        R<LearningPathPlanVO> response = controller.getPathByMatch(88L);

        assertThat(response.getData()).isSameAs(expected);
    }

    @Test
    void pagePathsReturnsPage() {
        PageResponse<LearningPathPlanVO> expected = new PageResponse<>(List.of(), 0, 1, 10, 0);
        when(facade.pagePaths(1L, 10L, 3L, 4L, "ACTIVE")).thenReturn(expected);

        R<PageResponse<LearningPathPlanVO>> response = controller.pagePaths(1L, 10L, 3L, 4L, "ACTIVE");

        assertThat(response.getData()).isSameAs(expected);
    }

    @Test
    void updateStepStatusReturnsOk() {
        LearningStepStatusUpdateRequest request = mock(LearningStepStatusUpdateRequest.class);
        when(request.status()).thenReturn("COMPLETED");

        R<Void> response = controller.updateStepStatus(11L, request);

        assertThat(response.getCode()).isEqualTo(200);
        verify(facade).updateStepStatus(11L, "COMPLETED");
    }

    @Test
    void pageProjectTasksReturnsPage() {
        PageResponse<LearningProjectTaskVO> expected = new PageResponse<>(List.of(), 0, 1, 10, 0);
        when(facade.pageProjectTasks(1L, 10L, 2L, 3L, "TODO")).thenReturn(expected);

        R<PageResponse<LearningProjectTaskVO>> response = controller.pageProjectTasks(1L, 10L, 2L, 3L, "TODO");

        assertThat(response.getData()).isSameAs(expected);
    }

    @Test
    void getProjectTaskReturnsTask() {
        LearningProjectTaskVO expected = mock(LearningProjectTaskVO.class);
        when(facade.getProjectTask(5L)).thenReturn(expected);

        R<LearningProjectTaskVO> response = controller.getProjectTask(5L);

        assertThat(response.getData()).isSameAs(expected);
    }

    @Test
    void submitProjectTaskReturnsSubmission() {
        LearningProjectSubmitDTO dto = mock(LearningProjectSubmitDTO.class);
        LearningProjectSubmissionResponse expected = mock(LearningProjectSubmissionResponse.class);
        when(facade.submitProjectTask(5L, dto)).thenReturn(expected);

        R<LearningProjectSubmissionResponse> response = controller.submitProjectTask(5L, dto);

        assertThat(response.getData()).isSameAs(expected);
    }

    @Test
    void reviewProjectSubmissionReturnsSubmission() {
        LearningProjectReviewDTO dto = mock(LearningProjectReviewDTO.class);
        LearningProjectSubmissionResponse expected = mock(LearningProjectSubmissionResponse.class);
        when(facade.reviewProjectSubmission(5L, dto)).thenReturn(expected);

        R<LearningProjectSubmissionResponse> response = controller.reviewProjectSubmission(5L, dto);

        assertThat(response.getData()).isSameAs(expected);
    }

    @Test
    void generateAssessmentsReturnsItems() {
        LearningAssessmentGenerateRequest request = mock(LearningAssessmentGenerateRequest.class);
        LearningAssessmentItemResponse expected = mock(LearningAssessmentItemResponse.class);
        when(facade.generateAssessments(request)).thenReturn(List.of(expected));

        R<List<LearningAssessmentItemResponse>> response = controller.generateAssessments(request);

        assertThat(response.getData()).containsExactly(expected);
    }

    @Test
    void getAssessmentsByPlanReturnsItems() {
        LearningAssessmentItemResponse expected = mock(LearningAssessmentItemResponse.class);
        when(facade.getAssessmentsByPlan(6L)).thenReturn(List.of(expected));

        R<List<LearningAssessmentItemResponse>> response = controller.getAssessmentsByPlan(6L);

        assertThat(response.getData()).containsExactly(expected);
    }
}
