package com.example.matching.event;

/**
 * 视频终面已发起事件。
 *
 * <p>用途：把它与「沟通要点快照生成」解耦 —— 发起邀约是**用户可感知的主流程**，
 * 必须立刻返回；而沟通要点要跨模块读报告、跑差距诊断，属于**辅助材料**，
 * 放到事务提交后异步生成并回写，避免拖慢发起、也避免它的异常反过来阻断发起。</p>
 *
 * @param interviewId       终面记录ID
 * @param empId             员工档案ID
 * @param matchingRecordId  关联匹配记录ID（可空）
 */
public record CommunicationInterviewCreatedEvent(Long interviewId, Long empId, Long matchingRecordId) {
}
