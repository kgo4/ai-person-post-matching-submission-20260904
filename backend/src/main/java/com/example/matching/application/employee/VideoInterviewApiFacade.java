package com.example.matching.application.employee;

import com.example.matching.dto.employee.api.CreateVideoInterviewSessionRequest;
import com.example.matching.dto.employee.api.VideoInterviewSessionResponse;
import com.example.matching.dto.employee.video.VideoInterviewCreateDTO;
import com.example.matching.dto.employee.video.VideoInterviewFrameDTO;
import com.example.matching.dto.employee.video.VideoInterviewImportDTO;
import com.example.matching.dto.employee.video.VideoInterviewQuestionGenerateDTO;
import com.example.matching.entity.employee.EmpVideoInterviewSession;
import com.example.matching.service.employee.VideoInterviewService;
import com.example.matching.vo.employee.video.VideoInterviewDetailVO;
import com.example.matching.vo.employee.video.VideoInterviewWsTicketVO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class VideoInterviewApiFacade {

    private final VideoInterviewService videoInterviewService;

    public VideoInterviewSessionResponse createSession(CreateVideoInterviewSessionRequest req, Long userId) {
        VideoInterviewCreateDTO dto = new VideoInterviewCreateDTO();
        dto.setEmpId(req.empId());
        dto.setPostId(req.postId());
        dto.setSessionName(req.sessionName());
        dto.setInterviewMode(req.interviewMode());
        EmpVideoInterviewSession session = videoInterviewService.createSession(dto, userId);
        return toResponse(session);
    }

    public void generateQuestions(Long id, VideoInterviewQuestionGenerateDTO dto) {
        videoInterviewService.generateQuestions(id, dto);
    }

    public VideoInterviewWsTicketVO issueWebSocketTicket(Long id, Long userId) {
        return videoInterviewService.issueWebSocketTicket(id, userId);
    }

    public void uploadFrame(Long id, VideoInterviewFrameDTO dto) {
        videoInterviewService.uploadFrame(id, dto);
    }

    public void startInterview(Long id) {
        videoInterviewService.startInterview(id);
    }

    public void nextQuestion(Long id) {
        videoInterviewService.nextQuestion(id);
    }

    public void finishInterview(Long id) {
        videoInterviewService.finishInterview(id);
    }

    public List<VideoInterviewSessionResponse> listByEmpId(Long empId) {
        return videoInterviewService.listByEmpId(empId).stream()
                .map(this::toResponse)
                .toList();
    }

    public List<VideoInterviewSessionResponse> listAll() {
        return videoInterviewService.listAll().stream()
                .map(this::toResponse)
                .toList();
    }

    public VideoInterviewDetailVO getDetail(Long id) {
        return videoInterviewService.getDetail(id);
    }

    public void analyze(Long id) {
        videoInterviewService.analyze(id);
    }

    public void importToAbilityProfile(Long id, VideoInterviewImportDTO dto, Long userId) {
        videoInterviewService.importToAbilityProfile(id, dto, userId);
    }

    private VideoInterviewSessionResponse toResponse(EmpVideoInterviewSession e) {
        if (e == null) return null;
        return new VideoInterviewSessionResponse(
                e.getId(),
                e.getEmpId(),
                e.getPostId(),
                e.getSessionName(),
                e.getInterviewMode(),
                e.getOverallScore(),
                e.getStatus(),
                e.getConversationState(),
                e.getCurrentQuestionOrder(),
                e.getDurationSeconds(),
                e.getQuestionCount(),
                e.getCreatedTime(),
                e.getUpdatedTime());
    }
}
