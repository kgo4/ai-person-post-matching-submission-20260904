package com.example.matching.event;

/**
 * 面试 WebSocket 推送事件
 * <p>
 * 用于 InterviewSessionManager（业务层）与 InterviewWebSocketHandler（技术层）之间的解耦通信。
 * Manager 发布事件，WsEventHandler 监听事件并调用 Handler 推送消息到前端。
 */
public class InterviewWsEvent {

    /**
     * 事件类型
     */
    public enum Type {
        /** 推送题目到前端 */
        PUSH_QUESTION,
        /** 推送倒计时到前端 */
        PUSH_COUNTDOWN,
        /** 发送通用文本消息 */
        SEND_MESSAGE,
        /** 通知前端面试结束 */
        INTERVIEW_FINISHED,
        /** 推送追问到前端 */
        FOLLOW_UP_QUESTION,
        /** 已收到下一题请求，正在后台核验当前回答 */
        ANSWER_ANALYSIS_STARTED
    }

    private final Type type;
    private final String sessionId;
    private final String text;
    private final int intValue;
    private final int extraInt;

    private InterviewWsEvent(Type type, String sessionId, String text, int intValue, int extraInt) {
        this.type = type;
        this.sessionId = sessionId;
        this.text = text;
        this.intValue = intValue;
        this.extraInt = extraInt;
    }

    public Type getType() {
        return type;
    }

    public String getSessionId() {
        return sessionId;
    }

    public String getText() {
        return text;
    }

    public int getIntValue() {
        return intValue;
    }

    public int getExtraInt() {
        return extraInt;
    }

    // ==================== 静态工厂方法 ====================

    public static InterviewWsEvent pushQuestion(String sessionId, int questionOrder, String questionText, int durationSeconds) {
        return new InterviewWsEvent(Type.PUSH_QUESTION, sessionId, questionText, questionOrder, durationSeconds);
    }

    public static InterviewWsEvent pushCountdown(String sessionId, int remainingSeconds) {
        return new InterviewWsEvent(Type.PUSH_COUNTDOWN, sessionId, null, remainingSeconds, 0);
    }

    public static InterviewWsEvent sendMessage(String sessionId, String type, String content) {
        return new InterviewWsEvent(Type.SEND_MESSAGE, sessionId, type + "|" + content, 0, 0);
    }

    public static InterviewWsEvent interviewFinished(String sessionId) {
        return new InterviewWsEvent(Type.INTERVIEW_FINISHED, sessionId, null, 0, 0);
    }

    public static InterviewWsEvent answerAnalysisStarted(String sessionId) {
        return new InterviewWsEvent(Type.ANSWER_ANALYSIS_STARTED, sessionId, null, 0, 0);
    }

    /**
     * 推送追问到前端
     * <p>
     * text 字段存储追问文本
     * intValue 存储 durationSeconds
     * extraInt 存储 followUpOrder
     * followUpId 和 parentQuestionId 编码在 text 前缀中，格式："followUpId|parentQuestionId|questionText"
     */
    public static InterviewWsEvent pushFollowUpQuestion(String sessionId, Long followUpId,
                                                          Long parentQuestionId, String questionText,
                                                          int durationSeconds, int followUpOrder) {
        String encoded = followUpId + "|" + parentQuestionId + "|" + questionText;
        return new InterviewWsEvent(Type.FOLLOW_UP_QUESTION, sessionId, encoded, durationSeconds, followUpOrder);
    }
}
