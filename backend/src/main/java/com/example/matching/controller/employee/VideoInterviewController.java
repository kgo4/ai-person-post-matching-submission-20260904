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
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.task.TaskExecutor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Tag(name = "AI Video Interview", description = "AI video interview session APIs")
@RestController
@RequestMapping("/api/employee/ability/video-interview")
@RequiredArgsConstructor
@Slf4j
public class VideoInterviewController {

    private final VideoInterviewApiFacade videoInterviewApiFacade;
    private final TaskExecutor applicationTaskExecutor;

    @Operation(summary = "Create video interview session")
    @PostMapping("/session/create")
    public R<VideoInterviewSessionResponse> createSession(@Valid @RequestBody CreateVideoInterviewSessionRequest req) {
        Long userId = SecurityUtils.getCurrentUserId();
        VideoInterviewSessionResponse session = videoInterviewApiFacade.createSession(req, userId);
        return R.ok("Session created", session);
    }

    @Operation(summary = "Generate interview questions")
    @PostMapping("/{id}/generate-questions")
    public R<Void> generateQuestions(
            @Parameter(description = "Session ID") @PathVariable Long id,
            @RequestBody(required = false) VideoInterviewQuestionGenerateDTO dto) {
        if (dto == null) {
            dto = new VideoInterviewQuestionGenerateDTO();
        }
        videoInterviewApiFacade.generateQuestions(id, dto);
        return R.ok("Questions generated", null);
    }

    @Operation(summary = "Issue WebSocket ticket")
    @PostMapping("/{id}/ws-ticket")
    public R<VideoInterviewWsTicketVO> issueWebSocketTicket(
            @Parameter(description = "Session ID") @PathVariable Long id) {
        Long userId = SecurityUtils.getCurrentUserId();
        return R.ok(videoInterviewApiFacade.issueWebSocketTicket(id, userId));
    }

    @Operation(summary = "Upload real-time video frame")
    @PostMapping("/{id}/frame")
    public R<Void> uploadFrame(
            @Parameter(description = "Session ID") @PathVariable Long id,
            @Valid @RequestBody VideoInterviewFrameDTO dto) {
        videoInterviewApiFacade.uploadFrame(id, dto);
        return R.ok("Frame accepted", null);
    }

    @Operation(summary = "Start interview")
    @PostMapping("/{id}/start")
    public R<Void> startInterview(@Parameter(description = "Session ID") @PathVariable Long id) {
        videoInterviewApiFacade.startInterview(id);
        return R.ok("Interview started", null);
    }

    @Operation(summary = "Move to next question")
    @PostMapping("/{id}/next-question")
    public R<Void> nextQuestion(@Parameter(description = "Session ID") @PathVariable Long id) {
        videoInterviewApiFacade.nextQuestion(id);
        return R.ok("Next question selected", null);
    }

    @Operation(summary = "Finish interview")
    @PostMapping("/{id}/finish")
    public R<Void> finishInterview(@Parameter(description = "Session ID") @PathVariable Long id) {
        videoInterviewApiFacade.finishInterview(id);
        return R.ok("Interview finished", null);
    }

    @Operation(summary = "List employee video interview sessions")
    @GetMapping("/list/{empId}")
    public R<List<VideoInterviewSessionResponse>> listByEmpId(
            @Parameter(description = "Employee ID") @PathVariable Long empId) {
        return R.ok(videoInterviewApiFacade.listByEmpId(empId));
    }

    @Operation(summary = "List all video interview sessions")
    @GetMapping("/list")
    public R<List<VideoInterviewSessionResponse>> listAll() {
        return R.ok(videoInterviewApiFacade.listAll());
    }

    @Operation(summary = "Get video interview detail")
    @GetMapping("/{id}")
    public R<VideoInterviewDetailVO> getDetail(@Parameter(description = "Session ID") @PathVariable Long id) {
        return R.ok(videoInterviewApiFacade.getDetail(id));
    }

    @Operation(summary = "Analyze video interview")
    @PostMapping("/{id}/analyze")
    public R<Void> analyze(@Parameter(description = "Session ID") @PathVariable Long id) {
        applicationTaskExecutor.execute(() -> {
            try {
                videoInterviewApiFacade.analyze(id);
            } catch (Exception e) {
                log.error("Async video interview analysis failed, sessionId={}", id, e);
            }
        });
        return R.ok("Analysis started", null);
    }

    @Deprecated
    @Operation(summary = "Import video interview abilities (deprecated)", description = "建议使用新的AI面试接口")
    @PostMapping("/{id}/import")
    public R<Void> importToAbilityProfile(
            @Parameter(description = "Session ID") @PathVariable Long id,
            @Valid @RequestBody VideoInterviewImportDTO dto) {
        Long userId = SecurityUtils.getCurrentUserId();
        videoInterviewApiFacade.importToAbilityProfile(id, dto, userId);
        return R.ok("Abilities imported (deprecated, please use new AI interview API)", null);
    }
}
