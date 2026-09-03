package com.example.matching.event;

/**
 * 简历解析完成事件
 * <p>
 * 解析成功后发布，触发能力评估工作流的证据自动保存。
 *
 * @author system
 */
public record ResumeParseCompletedEvent(Long parseId, Long empId) {
}
