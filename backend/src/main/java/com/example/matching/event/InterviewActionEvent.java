package com.example.matching.event;

/**
 * 面试用户操作事件
 * <p>
 * 用于 InterviewWebSocketHandler（技术层）通知 InterviewSessionManager（业务层）用户触发了某个操作。
 * Handler 发布事件，SessionManager 监听事件并执行业务逻辑。
 * <p>
 * 这样 Handler 不需要依赖 InterviewSessionManager，消除循环依赖。
 */
public class InterviewActionEvent {

    /**
     * 操作类型
     */
    public enum Type {
        /** 用户点击"回答完毕"，切换到下一题 */
        MANUAL_NEXT,
        /** 用户结束面试 */
        FINISH_INTERVIEW
    }

    private final Type type;
    private final String sessionId;

    public InterviewActionEvent(Type type, String sessionId) {
        this.type = type;
        this.sessionId = sessionId;
    }

    public Type getType() {
        return type;
    }

    public String getSessionId() {
        return sessionId;
    }

    public static InterviewActionEvent manualNext(String sessionId) {
        return new InterviewActionEvent(Type.MANUAL_NEXT, sessionId);
    }

    public static InterviewActionEvent finishInterview(String sessionId) {
        return new InterviewActionEvent(Type.FINISH_INTERVIEW, sessionId);
    }
}
