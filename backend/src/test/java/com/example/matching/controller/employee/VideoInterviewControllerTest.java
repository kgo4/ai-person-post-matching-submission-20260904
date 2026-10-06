package com.example.matching.controller.employee;

import com.example.matching.application.employee.VideoInterviewApiFacade;
import com.example.matching.common.result.R;
import com.example.matching.dto.employee.api.CreateVideoInterviewSessionRequest;
import com.example.matching.dto.employee.api.VideoInterviewSessionResponse;
import com.example.matching.dto.employee.video.VideoInterviewFrameDTO;
import com.example.matching.dto.employee.video.VideoInterviewImportDTO;
import com.example.matching.dto.employee.video.VideoInterviewQuestionGenerateDTO;
import com.example.matching.utils.SecurityUtils;
import com.example.matching.vo.employee.video.VideoInterviewDetailVO;
import com.example.matching.vo.employee.video.VideoInterviewWsTicketVO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.task.TaskExecutor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.same;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class VideoInterviewControllerTest {

    private VideoInterviewApiFacade facade;
    private TaskExecutor taskExecutor;
    private VideoInterviewController controller;

    @BeforeEach
    void setUp() {
        SecurityUtils.clear();
        facade = mock(VideoInterviewApiFacade.class);
        taskExecutor = mock(TaskExecutor.class);
        controller = new VideoInterviewController(facade, taskExecutor);
    }

    private static VideoInterviewSessionResponse sessionResponse(Long id) {
        return new VideoInterviewSessionResponse(
                id, 100L, 200L, "面试会话", "AI",
                new BigDecimal("88.5"), 1, "ACTIVE", 2, 180, 6,
                LocalDateTime.of(2025, 1, 1, 10, 0),
                LocalDateTime.of(2025, 1, 1, 10, 0));
    }

    @Test
    void createSessionReturnsSession() {
        CreateVideoInterviewSessionRequest request = new CreateVideoInterviewSessionRequest(100L, 200L, "面试会话", "AI");
        VideoInterviewSessionResponse session = sessionResponse(1L);
        when(facade.createSession(eq(request), any())).thenReturn(session);

        R<VideoInterviewSessionResponse> response = controller.createSession(request);

        assertThat(response.getData()).isSameAs(session);
        assertThat(response.getMessage()).isEqualTo("Session created");
    }

    @Test
    void generateQuestionsWithProvidedDtoDelegates() {
        VideoInterviewQuestionGenerateDTO dto = new VideoInterviewQuestionGenerateDTO();
        dto.setQuestionCount(8);
        dto.setMode("GENERAL");

        R<Void> response = controller.generateQuestions(1L, dto);

        verify(facade).generateQuestions(eq(1L), same(dto));
        assertThat(response.getCode()).isEqualTo(200);
    }

    @Test
    void generateQuestionsWithNullDtoUsesDefault() {
        R<Void> response = controller.generateQuestions(1L, null);

        verify(facade).generateQuestions(eq(1L), any(VideoInterviewQuestionGenerateDTO.class));
        assertThat(response.getCode()).isEqualTo(200);
    }

    @Test
    void issueWebSocketTicketReturnsTicket() {
        VideoInterviewWsTicketVO ticket = new VideoInterviewWsTicketVO("tk-123", 9999999999999L);
        when(facade.issueWebSocketTicket(eq(1L), any())).thenReturn(ticket);

        R<VideoInterviewWsTicketVO> response = controller.issueWebSocketTicket(1L);

        assertThat(response.getData()).isSameAs(ticket);
        assertThat(response.getData().getTicket()).isEqualTo("tk-123");
    }

    @Test
    void uploadFrameDelegatesAndReturnsOk() {
        VideoInterviewFrameDTO frame = new VideoInterviewFrameDTO();
        frame.setQuestionOrder(1);
        frame.setCaptureSecond(15);
        frame.setImageDataUrl("data:image/png;base64,xxx");

        R<Void> response = controller.uploadFrame(1L, frame);

        verify(facade).uploadFrame(eq(1L), same(frame));
        assertThat(response.getCode()).isEqualTo(200);
    }

    @Test
    void startInterviewDelegatesAndReturnsOk() {
        R<Void> response = controller.startInterview(1L);

        verify(facade).startInterview(1L);
        assertThat(response.getCode()).isEqualTo(200);
    }

    @Test
    void nextQuestionDelegatesAndReturnsOk() {
        R<Void> response = controller.nextQuestion(1L);

        verify(facade).nextQuestion(1L);
        assertThat(response.getCode()).isEqualTo(200);
    }

    @Test
    void finishInterviewDelegatesAndReturnsOk() {
        R<Void> response = controller.finishInterview(1L);

        verify(facade).finishInterview(1L);
        assertThat(response.getCode()).isEqualTo(200);
    }

    @Test
    void listByEmpIdReturnsSessionList() {
        VideoInterviewSessionResponse session = sessionResponse(1L);
        when(facade.listByEmpId(100L)).thenReturn(List.of(session));

        R<List<VideoInterviewSessionResponse>> response = controller.listByEmpId(100L);

        assertThat(response.getData()).containsExactly(session);
    }

    @Test
    void listAllReturnsSessionList() {
        VideoInterviewSessionResponse session = sessionResponse(1L);
        when(facade.listAll()).thenReturn(List.of(session));

        R<List<VideoInterviewSessionResponse>> response = controller.listAll();

        assertThat(response.getData()).containsExactly(session);
    }

    @Test
    void getDetailReturnsDetail() {
        VideoInterviewDetailVO detail = new VideoInterviewDetailVO();
        detail.setId(1L);
        detail.setSessionName("面试会话");
        when(facade.getDetail(1L)).thenReturn(detail);

        R<VideoInterviewDetailVO> response = controller.getDetail(1L);

        assertThat(response.getData()).isSameAs(detail);
        assertThat(response.getData().getSessionName()).isEqualTo("面试会话");
    }

    @Test
    void analyzeDispatchesAsyncTaskAndReturnsOk() {
        R<Void> response = controller.analyze(1L);

        verify(taskExecutor).execute(any(Runnable.class));
        assertThat(response.getCode()).isEqualTo(200);
    }

    @Test
    void importToAbilityProfileDelegatesAndReturnsOk() {
        VideoInterviewImportDTO dto = new VideoInterviewImportDTO();
        dto.setAbilityIds(List.of(1L, 2L));

        R<Void> response = controller.importToAbilityProfile(1L, dto);

        verify(facade).importToAbilityProfile(eq(1L), same(dto), any());
        assertThat(response.getCode()).isEqualTo(200);
    }
}
