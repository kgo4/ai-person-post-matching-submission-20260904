package com.example.matching.service.interview;

import com.example.matching.event.InterviewWsEvent;
import com.example.matching.websocket.InterviewWebSocketHandler;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * 面试 WebSocket 事件处理器
 * <p>
 * 监听 {@link InterviewWsEvent}（业务层发布），调用 InterviewWebSocketHandler 推送消息到前端。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class InterviewWsEventHandler {

    private final InterviewWebSocketHandler webSocketHandler;
    private final InterviewSessionManager interviewSessionManager;

    /**
     * 监听业务层发布的 WebSocket 推送事件
     */
    @EventListener
    public void onInterviewWsEvent(InterviewWsEvent event) {
        try {
            switch (event.getType()) {
                case PUSH_QUESTION -> webSocketHandler.pushQuestion(
                        event.getSessionId(), event.getIntValue(), event.getText(), event.getExtraInt());
                case PUSH_COUNTDOWN -> webSocketHandler.pushCountdown(event.getSessionId(), event.getIntValue());
                case SEND_MESSAGE -> {
                    String text = event.getText();
                    int sep = text.indexOf('|');
                    if (sep >= 0) {
                        webSocketHandler.sendMessage(event.getSessionId(), text.substring(0, sep), text.substring(sep + 1));
                    }
                }
                case INTERVIEW_FINISHED -> webSocketHandler.sendMessage(event.getSessionId(), "INTERVIEW_FINISHED", "面试结束");
                case ANSWER_ANALYSIS_STARTED -> webSocketHandler.sendMessage(
                        event.getSessionId(), "ANSWER_ANALYSIS_STARTED", "正在核验回答");
                case FOLLOW_UP_QUESTION -> {
                    // text 格式: "followUpId|parentQuestionId|questionText"
                    String text = event.getText();
                    String[] parts = text.split("\\|", 3);
                    if (parts.length == 3) {
                        webSocketHandler.pushFollowUpQuestion(
                                event.getSessionId(),
                                Long.parseLong(parts[0]),
                                Long.parseLong(parts[1]),
                                parts[2],
                                event.getIntValue(),
                                event.getExtraInt());
                    }
                }
            }
        } catch (Exception e) {
            log.error("处理WebSocket推送事件失败: type={}, sessionId={}, error={}",
                    event.getType(), event.getSessionId(), e.getMessage(), e);
        }
    }

    /**
     * 监听 InterviewWebSocketHandler 发布的"开始面试"事件
     */
    @EventListener
    public void onStartInterview(StartInterviewEvent event) {
        try {
            interviewSessionManager.startInterviewFlow(event.sessionId());
        } catch (Exception e) {
            log.error("启动面试流程失败: sessionId={}, error={}", event.sessionId(), e.getMessage(), e);
        }
    }

    /**
     * Handler → Manager 的"开始面试"事件
     */
    public record StartInterviewEvent(String sessionId) {
    }
}
