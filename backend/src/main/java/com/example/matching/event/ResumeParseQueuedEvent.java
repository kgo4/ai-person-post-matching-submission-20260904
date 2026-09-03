package com.example.matching.event;

/**
 * 简历解析记录提交成功后的异步任务事件。
 */
public record ResumeParseQueuedEvent(Long parseId) {
}
