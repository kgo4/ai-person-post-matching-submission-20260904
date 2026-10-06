package com.example.matching.event;

import lombok.Getter;

/**
 * 面试结束事件
 * <p>
 * 面试流程结束后发布，由 {@code InterviewPostAnalysisListener} 异步消费，
 * 执行 AI 面试能力观察和胜任力报告生成。
 */
@Getter
public class InterviewFinishedEvent {

    private final Long sessionId;

    public InterviewFinishedEvent(Long sessionId) {
        this.sessionId = sessionId;
    }
}
