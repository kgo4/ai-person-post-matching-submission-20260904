package com.example.matching.service.assessment;

/**
 * 面试证据收集与聚合审核推进服务接口
 * <p>
 * 面试分析完成后：保存面试观察 Claim（AI_INTERVIEW 来源），
 * 标记证据就绪，投递 AGGREGATE_HARNESS 阶段任务。
 *
 * @author system
 */
public interface InterviewAssessmentEvidenceService {

    /**
     * 面试分析完成后保存面试证据并推进聚合审核。
     *
     * @param workflowId 能力评估工作流ID
     * @param empId      员工ID
     * @param sessionId  面试会话ID
     */
    void saveInterviewEvidenceAndAdvance(Long workflowId, Long empId, Long sessionId);
}
