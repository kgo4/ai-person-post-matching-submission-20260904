package com.example.matching.application.assessment;

import com.example.matching.dto.assessment.EligibilityPrecheckResult;
import com.example.matching.dto.assessment.GenerateVerificationTestResponse;
import com.example.matching.dto.assessment.CreateAssessmentInterviewResponse;
import com.example.matching.dto.assessment.ProvisionalAbilitySnapshotDTO;
import com.example.matching.dto.assessment.ResumeAbilityClaimDTO;
import com.example.matching.entity.workflow.PersonCapabilityStageRun;
import com.example.matching.entity.workflow.PersonCapabilityWorkflow;
import com.example.matching.vo.assessment.CapabilityAssessmentVO;

import java.util.List;
import java.util.Map;

/**
 * 人员能力评估应用层门面接口
 * <p>
 * 唯一工作流入口，负责状态推进、任务投递和结果查询。
 * 不包含 LLM Prompt、SQL 细节或匹配算法。
 *
 * @author system
 */
public interface CapabilityAssessmentFacade {

    /**
     * 获取或创建员工活跃工作流。
     */
    CapabilityAssessmentVO.WorkflowView getOrCreateWorkflow(Long empId, Long operatorId);

    /**
     * 获取员工活跃工作流视图。
     */
    CapabilityAssessmentVO.WorkflowView getActiveWorkflow(Long empId);

    /**
     * 按 ID 获取工作流视图。
     */
    CapabilityAssessmentVO.WorkflowView getWorkflow(Long workflowId);

    /**
     * 获取评估范围（简历声明 ∩ 岗位要求 + 未覆盖岗位能力）。
     * 用于前端展示交集评估范围与岗位差距；岗位未绑定时返回 null。
     */
    com.example.matching.dto.assessment.AssessmentScopeDTO getAssessmentScope(Long workflowId);

    /**
     * 保存简历能力证据（阶段 1），并推进工作流到证据就绪。
     *
     * @return 保存的 Claim 数量
     */
    int submitResumeEvidence(Long empId, Long resumeParseId, List<ResumeAbilityClaimDTO> claims, Long operatorId);

    /**
     * 生成验证测试（阶段 2）：基于简历 Claim 与目标岗位生成。
     * 记录验证覆盖关系（claim_group -> 题目），推进工作流 TEST_GENERATING -> TEST_IN_PROGRESS。
     * 返回显式 testId + postId，前端凭此轮询测试状态。
     *
     * @param workflowId 工作流ID
     * @param postId     目标岗位ID
     * @param operatorId 操作人
     * @return 测试生成结果（含 stageRun, testId, postId）
     */
    GenerateVerificationTestResponse generateTest(Long workflowId, Long postId, Long operatorId);

    /**
     * 提交测试答案（阶段 2），推进工作流 TEST_IN_PROGRESS -> TEST_EVALUATING。
     * 评分完成后由异步监听器保存测试证据并推进 TEST_EVIDENCE_READY。
     */
    PersonCapabilityStageRun submitTest(Long workflowId, Long testId,
                                        java.util.Map<String, Object> answers, Long operatorId);

    /**
     * 创建面试（阶段 3 入口）。
     * 返回显式 sessionId + postId，前端凭此进入面试页面。
     */
    CreateAssessmentInterviewResponse createInterview(Long workflowId, Long operatorId);

    /**
     * 结束面试并推进聚合审核（Phase 5 接入）。
     */
    PersonCapabilityStageRun finishInterview(Long workflowId, Long sessionId, Long operatorId);

    /**
     * 重试失败阶段。
     */
    void retryStage(Long workflowId, String stageType, Long operatorId);

    /**
     * 员工能力画像视图（正式 + 待确立）。
     */
    Map<String, Object> getProfile(Long empId);

    /**
     * 匹配预检。
     */
    List<EligibilityPrecheckResult> precheckEligibility(List<Long> empIds, List<Long> postIds);

    /**
     * 构建强制匹配临时能力快照。
     */
    ProvisionalAbilitySnapshotDTO buildProvisionalSnapshot(Long empId, boolean acknowledged, Long operatorId);

    /**
     * 查询聚合 Harness 审核结果。
     */
    List<com.example.matching.dto.assessment.HarnessBatchItemResultDTO> getHarnessResults(Long workflowId);

    /**
     * 查询工作流等级决策记录。
     */
    List<com.example.matching.entity.workflow.PersonAbilityLevelDecision> listDecisions(Long workflowId);

    /**
     * 人工确认等级（HUMAN_CONFIRMED）。
     */
    com.example.matching.entity.workflow.PersonAbilityLevelDecision humanConfirmDecision(
            Long decisionId, Integer finalLevel, Integer finalConfidence, String reason, Long reviewerId);

    /**
     * 人工拒绝等级（REJECTED）。
     */
    com.example.matching.entity.workflow.PersonAbilityLevelDecision humanRejectDecision(
            Long decisionId, String reason, Long reviewerId);

    /**
     * 按新策略重算等级结论（AUTO_CONFIRMED 标记待重算，HUMAN_CONFIRMED 不静默改写）。
     */
    void recalculateDecisions(Long workflowId, String newPolicyVersion);

    /** 员工全部评估流程 + 报告状态（倒序）。 */
    List<com.example.matching.port.assessment.AssessmentReportPort.WorkflowReportDTO> listAssessmentReports(Long empId);

    /** 单次评估报告（不存在返回 null）。 */
    com.example.matching.port.assessment.AssessmentReportPort.ReportDTO getAssessmentReport(Long workflowId);

    /**
     * 阶段运行实体转视图（Controller 层不直接暴露实体）。
     */
    default CapabilityAssessmentVO.StageRunView toStageRunView(PersonCapabilityStageRun run) {
        CapabilityAssessmentVO.StageRunView view = new CapabilityAssessmentVO.StageRunView();
        if (run == null) {
            return view;
        }
        view.setStageRunId(run.getId());
        view.setStageType(run.getStageType());
        view.setStatus(run.getStatus());
        view.setAttemptCount(run.getAttemptCount());
        view.setStartedAt(run.getStartedAt());
        view.setCompletedAt(run.getCompletedAt());
        view.setFailureCode(run.getFailureCode());
        view.setFailureMessage(run.getFailureMessage());
        view.setSourceRefId(run.getSourceRefId());
        view.setSourceRefType(run.getSourceRefType());
        return view;
    }
}
